import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
async function load(relative, imports = {}) {
  const compiled = ts.transpileModule(await readFile(new URL(relative, import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const exports = {};
  new Function("exports", "require", compiled)(exports, (name) => (Object.hasOwn(imports, name) ? imports[name] : require(name)));
  return exports;
}
const plan = await load("../src/lib/plan.ts");
const weekly = await load("../src/lib/weekly.ts");
const { authorized } = await load("../src/lib/mcp/auth.ts");
const { createTools } = await load("../src/lib/mcp/tools.ts", { "@/lib/plan": plan, "@/lib/weekly": weekly });

const TOKEN = "x".repeat(40);
test("the connector accepts only the configured Bearer token, and stays closed without a strong one", () => {
  assert.equal(authorized(`Bearer ${TOKEN}`, TOKEN), true);
  assert.equal(authorized(`bearer ${TOKEN}`, TOKEN), true);
  assert.equal(authorized(`Bearer ${TOKEN}y`, TOKEN), false);
  assert.equal(authorized(TOKEN, TOKEN), false);
  assert.equal(authorized(null, TOKEN), false);
  assert.equal(authorized("Bearer short", "short"), false);
  assert.equal(authorized("Bearer ", undefined), false);
});

function fakeAccount(tables) {
  const calls = [];
  const client = {
    from: (table) => {
      const query = { select: () => query, eq: () => query, order: () => query, range: async () => ({ data: tables[table] ?? [], error: null }) };
      return query;
    },
    rpc: async (name, args) => { calls.push([name, args.change_type, args.payload]); return { error: null }; },
    storage: { from: () => ({ remove: async (paths) => { calls.push(["remove", paths]); } }) },
  };
  return { account: async () => ({ client, userId: "u1" }), calls };
}

test("body weight comes back grouped by week with a dropping verdict, in the account's unit", async () => {
  const { account } = fakeAccount({
    settings: [{ body_unit: "kg" }],
    body_weights: [
      { date: "2026/11/02", weight: 95, unit: "kg" }, { date: "2026/11/04", weight: 94.6, unit: "kg" },
      { date: "2026/11/10", weight: 94, unit: "kg" }, { date: "2026/11/17", weight: 93, unit: "kg" },
      { date: "2026/11/24", weight: 92.2, unit: "kg" },
    ],
  });
  const result = await createTools(account, "Asia/Singapore").get_body_weight({});
  assert.deepEqual(result.weeks.map((w) => [w.weekOf, w.average, w.change]), [
    ["2026/11/02", 94.8, null], ["2026/11/09", 94, -0.8], ["2026/11/16", 93, -1], ["2026/11/23", 92.2, -0.8],
  ]);
  assert.deepEqual(result.trend, { perWeek: -0.9, overWeeks: 4, verdict: "dropping" });
  assert.deepEqual(result.latest, { date: "2026/11/24", weight: 92.2 });
});

test("logging a workout checks the plan's exercise names and saves like the app's Save button", async () => {
  const { account, calls } = fakeAccount({ settings: [{}] });
  const tools = createTools(account, "Asia/Singapore");
  const day = plan.DAYS[0];
  const [first] = plan.dayExercises(day);
  await assert.rejects(tools.log_workout({ dayId: day.id, exercises: [{ exercise: "Made-up Press", sets: [{ weight: 10, reps: 10 }] }] }), /is not on/);
  await assert.rejects(tools.log_workout({ dayId: "zz", exercises: [{ exercise: first.name, sets: [{ weight: 10, reps: 10 }] }] }), /No day "zz"/);
  await assert.rejects(tools.log_workout({ dayId: day.id, date: "2999-01-01", exercises: [{ exercise: first.name, sets: [{ weight: 10, reps: 10 }] }] }), /in the future/);
  assert.equal(calls.length, 0);
  await tools.log_workout({ dayId: day.id, date: "2026-11-24", notes: "Good", durationMin: 45,
    exercises: [{ exercise: first.name.toUpperCase(), sets: [{ weight: 50, reps: 10 }, { weight: "BW", reps: 8 }] }] });
  const [[rpc, type, payload]] = calls;
  assert.equal(rpc, "apply_workout_change");
  assert.equal(type, "finish");
  assert.match(payload._mutationId, /^[0-9a-f-]{36}$/);
  assert.equal(payload.entries[0].exercise, first.name);
  assert.deepEqual(payload.entries[0].sets, [{ weight: "50", reps: "10" }, { weight: "BW", reps: "8" }]);
  assert.deepEqual({ ...payload.session, savedAt: 0 }, { dayId: day.id, date: "2026/11/24", savedAt: 0, notes: "Good", photos: [], durationMin: 45 });
});

test("deleting a weigh-in removes its photos only after the database delete", async () => {
  const { account, calls } = fakeAccount({ body_weights: [{ date: "2026/11/24", weight: 93.1, unit: "kg", photos: ["u1/p.jpg"] }] });
  await createTools(account, "Asia/Singapore").delete_body_weight({ date: "2026-11-24" });
  assert.deepEqual(calls.map((c) => c[0] === "remove" ? c : c.slice(0, 2)), [["apply_workout_change", "deleteWeight"], ["remove", ["u1/p.jpg"]]]);
});

test("an app-generated token is accepted only when it hashes to the stored hash", async () => {
  const { createHash } = await import("node:crypto");
  const { bearerToken, matchesHash } = await load("../src/lib/mcp/auth.ts");
  const sha = (value) => createHash("sha256").update(value).digest("hex");
  const token = `wp_${"ab".repeat(32)}`;
  assert.equal(bearerToken(`Bearer ${token}`), token);
  assert.equal(bearerToken("Basic abc"), null);
  assert.equal(matchesHash(token, sha(token)), true);
  assert.equal(matchesHash(`${token}0`, sha(token)), false);
  assert.equal(matchesHash(token, null), false);
  assert.equal(matchesHash(token, "not-a-hash"), false);
  assert.equal(matchesHash(null, sha(token)), false);
  assert.equal(matchesHash("short", sha("short")), false);
});

test("connector token hashes are private to each account and removed with it", async () => {
  const { PGlite } = await import("@electric-sql/pglite");
  const A = "00000000-0000-0000-0000-000000000001";
  const B = "00000000-0000-0000-0000-000000000002";
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon; create role authenticated;
      create schema auth; create table auth.users (id uuid primary key);
      create function auth.uid() returns uuid language sql stable as
        $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
      grant usage on schema public, auth to authenticated, anon;
      grant execute on function auth.uid() to authenticated, anon;
      insert into auth.users values ('${A}'), ('${B}');
    `);
    const schema = await readFile(new URL("../supabase/schema.sql", import.meta.url), "utf8");
    await db.exec(schema);
    await db.exec(schema);
    const hash = "a".repeat(64);
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    await db.query("insert into public.connector_tokens (token_hash) values ($1)", [hash]);
    await db.query("insert into public.connector_tokens (token_hash) values ($1) on conflict (user_id) do update set token_hash = excluded.token_hash", ["b".repeat(64)]);
    await assert.rejects(db.query("insert into public.connector_tokens (user_id, token_hash) values ($1, $2)", [B, hash]), /row-level security/);
    await assert.rejects(db.query("update public.connector_tokens set token_hash = 'plain'"), /check constraint/);
    assert.deepEqual((await db.query("select token_hash from public.connector_tokens")).rows, [{ token_hash: "b".repeat(64) }]);
    await db.exec(`set request.jwt.claim.sub = '${B}';`);
    assert.deepEqual((await db.query("select * from public.connector_tokens")).rows, []);
    await db.exec("set role anon; reset request.jwt.claim.sub;");
    await assert.rejects(db.query("select * from public.connector_tokens"), /permission denied/);
    await db.exec("reset role;");
    await db.query("delete from auth.users where id = $1", [A]);
    assert.equal((await db.query("select count(*)::int as n from public.connector_tokens")).rows[0].n, 0);
  } finally { await db.close(); }
});
