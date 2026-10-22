import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { PGlite } from "@electric-sql/pglite";
import ts from "typescript";

const databaseSource = ts.transpileModule(await readFile(new URL("../src/lib/database.ts", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

const A = "00000000-0000-0000-0000-000000000001";
const B = "00000000-0000-0000-0000-000000000002";

test("readAccount loads per-user training plans with stable pagination and RPC serialization", async () => {
  const plans = Array.from({ length: 1001 }, (_, i) => ({ user_id: A, id: `plan-${String(i).padStart(4, "0")}`, name: `Plan ${i}`, description: "", blocks: [], created_at: String(i) }));
  plans.push({ user_id: B, id: "other-account", name: "Private", description: "", blocks: [], created_at: "1" });
  const ranges = [];
  const rpcCalls = [];
  const sb = {
    from: (table) => {
      let user;
      const order = [];
      const query = {
        select: () => query,
        eq: (column, value) => { assert.equal(column, "user_id"); user = value; return query; },
        order: (key) => { order.push(key); return query; },
        range: async (start, end) => {
          if (table === "training_plans") {
            ranges.push({ user, order: [...order], start, end });
            return { data: plans.filter((p) => p.user_id === user).slice(start, end + 1), error: null };
          }
          return { data: [], error: null };
        },
      };
      return query;
    },
    rpc: async (name, args) => { rpcCalls.push({ name, args }); return { error: null }; },
  };
  const database = {};
  new Function("exports", "require", databaseSource)(database, (name) => {
    assert.equal(name, "@/lib/supabase");
    return { supabase: () => sb };
  });
  const a = await database.readAccount(A);
  assert.equal(a.trainingPlans.length, 1001);
  assert.deepEqual(a.trainingPlans[1000], { id: "plan-1000", name: "Plan 1000", description: "", blocks: [], createdAt: 1000 });
  assert.deepEqual(ranges, [
    { user: A, order: ["created_at", "id"], start: 0, end: 999 },
    { user: A, order: ["created_at", "id"], start: 1000, end: 1999 },
  ]);
  const b = await database.readAccount(B);
  assert.deepEqual(b.trainingPlans.map((p) => p.name), ["Private"]);
  const mutationId = crypto.randomUUID();
  await database.writeChange(A, { type: "trainingPlan", payload: { plan: a.trainingPlans[0] }, mutationId });
  assert.deepEqual(rpcCalls, [{ name: "apply_workout_change", args: {
    account_id: A, change_type: "trainingPlan", payload: { plan: a.trainingPlans[0], _mutationId: mutationId },
  } }]);
  sb.rpc = async () => ({ error: { message: "Plan rejected" } });
  await assert.rejects(database.writeChange(A, { type: "trainingPlan", payload: { plan: a.trainingPlans[0] } }), /Plan rejected/);
  sb.from = () => ({ select() { return this; }, eq() { return this; }, order() { return this; }, range: async () => ({ data: null, error: { message: "Schema missing" } }) });
  await assert.rejects(database.readAccount(A), /Schema missing/);
});

test("readAccount hydrates snapshots and legacy references, defaults old settings, and serializes atomic removal", async () => {
  const legacy = { key: `mw-${crypto.randomUUID()}`, musclewikiId: 42, sets: 3, reps: "12", restSeconds: 90 };
  const library = { key: `ex-${crypto.randomUUID()}`, source: "free-exercise-db", exerciseId: "Barbell_Bench_Press", name: "Barbell Bench Press", equipment: "barbell", primaryMuscles: ["chest"], instructions: ["Press upward."], sets: 3, reps: "12", restSeconds: 90 };
  const custom = { ...library, key: `ex-${crypto.randomUUID()}`, source: "custom", exerciseId: crypto.randomUUID(), name: "My press", equipment: "", primaryMuscles: [], instructions: [] };
  let settings = [];
  const calls = [];
  const sb = {
    from: (table) => {
      const query = { select: () => query, eq: () => query, order: () => query,
        range: async () => ({ data: table === "settings" ? settings : [], error: null }) };
      return query;
    },
    rpc: async (name, args) => { calls.push({ name, args }); return { error: null }; },
  };
  const database = {};
  new Function("exports", "require", databaseSource)(database, () => ({ supabase: () => sb }));
  assert.deepEqual((await database.readAccount(A)).dayExercises, {});
  settings = [{ units: { Press: "kg" } }];
  assert.deepEqual((await database.readAccount(A)).dayExercises, {});
  settings = [{ day_exercises: { a: [legacy, library, custom] } }];
  assert.deepEqual((await database.readAccount(A)).dayExercises, { a: [legacy, library, custom] });
  for (const removed of [library, legacy]) {
    const mutationId = crypto.randomUUID();
    const exercises = [legacy, library, custom].filter((entry) => entry.key !== removed.key);
    await database.writeChange(A, { type: "dayExercises", payload: { dayId: "a", exercises, removedKey: removed.key }, mutationId });
    assert.deepEqual(calls.at(-1), { name: "apply_workout_change", args: { account_id: A, change_type: "dayExercises",
      payload: { dayId: "a", exercises, removedKey: removed.key, _mutationId: mutationId } } });
  }
});

test("legacy MuscleWiki references survive migration, validate, remove atomically, and stay account-isolated", async () => {
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
      -- Simulate settings on an installation predating this migration.
      create table public.settings (
        user_id uuid primary key default auth.uid() references auth.users(id),
        units jsonb not null default '{}', default_unit text not null default 'kg',
        body_unit text not null default 'kg', updated_at timestamptz not null default now()
      );
      insert into public.settings(user_id,units) values ('${A}','{"Press":"lb"}');
    `);
    const schema = await readFile(new URL("../supabase/schema.sql", import.meta.url), "utf8");
    await db.exec(schema);
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    const apply = (type, payload, user = A) => db.query("select public.apply_workout_change($1::uuid,$2,$3::jsonb)", [user, type, JSON.stringify({ _mutationId: crypto.randomUUID(), ...payload })]);
    const table = async (name) => (await db.query(`select * from public.${name}`)).rows;
    assert.deepEqual((await table("settings"))[0].day_exercises, {});
    assert.deepEqual((await table("settings"))[0].units, { Press: "lb" });
    const added = { key: `mw-${crypto.randomUUID()}`, musclewikiId: 0, sets: 1, reps: "1", restSeconds: 0 };
    const boundary = { key: `mw-${crypto.randomUUID()}`, musclewikiId: 2147483647, sets: 10, reps: "100", restSeconds: 600 };
    await apply("dayExercises", { dayId: "a", exercises: [added] });
    await apply("dayExercises", { dayId: "b", exercises: [boundary] });
    await db.exec("reset role;");
    await db.exec(schema);
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    const expected = { a: [added], b: [boundary] };
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    const invalid = [null, {}, { ...added, key: "1" }, { ...added, key: `${added.key}\n` },
      { ...added, musclewikiId: -1 }, { ...added, musclewikiId: 2147483648 }, { ...added, musclewikiId: 1.5 },
      { ...added, musclewikiId: "42" }, { ...added, sets: 0 }, { ...added, sets: 11 }, { ...added, sets: 1.5 },
      { ...added, reps: "0" }, { ...added, reps: "101" }, { ...added, reps: "30s" }, { ...added, reps: "12\n" },
      { ...added, reps: 12 }, { ...added, restSeconds: -1 }, { ...added, restSeconds: 601 },
      { ...added, restSeconds: 1.5 }, { ...added, name: "Legacy references cannot carry snapshots" }, { ...added, media: [] }];
    const invalidPayloads = invalid.map((exercise) => ({ dayId: "a", exercises: [exercise] }));
    invalidPayloads.push(...[null, {}, "bad"].map((exercises) => ({ dayId: "a", exercises })));
    invalidPayloads.push(...[null, "", "e", "aa", "a\n"].map((dayId) => ({ dayId, exercises: [] })));
    invalidPayloads.push({ dayId: "a", exercises: [added, { ...boundary, key: added.key }] },
      { dayId: "a", exercises: [added, { ...boundary, musclewikiId: added.musclewikiId }] },
      { dayId: "a", exercises: Array.from({ length: 31 }, (_, i) => ({ ...added, key: `mw-${crypto.randomUUID()}`, musclewikiId: i + 1 })) },
      { dayId: "a", exercises: [], removedKey: "1" }, { dayId: "a", exercises: [], removedKey: null },
      { dayId: "a", exercises: [added], removedKey: added.key });
    for (const payload of invalidPayloads) {
      const receipts = (await table("workout_mutations")).length;
      await assert.rejects(apply("dayExercises", payload));
      assert.equal((await table("workout_mutations")).length, receipts);
      assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    }
    const authored = { sets: [{ weight: "60", reps: "8", done: true }], notes: "Keep", useSub: false };
    const draft = { startedAt: 123, exercises: { "1": authored, [added.key]: { ...authored, notes: "Remove" } } };
    await apply("draft", { dayId: "a", draft });
    await apply("draft", { dayId: "b", draft });
    const removal = { dayId: "a", exercises: [], removedKey: added.key, _mutationId: crypto.randomUUID() };
    // A failing draft update must roll back settings and the receipt too.
    await db.exec(`reset role;
      create function public.reject_test_removal() returns trigger language plpgsql as $$
      begin raise exception 'Test draft update failure'; end; $$;
      create trigger reject_test_removal before update on public.workout_drafts
        for each row execute function public.reject_test_removal();
      set role authenticated;`);
    await assert.rejects(apply("dayExercises", removal), /Test draft update failure/);
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    assert.deepEqual((await table("workout_drafts")).find((r) => r.day_id === "a").draft, draft);
    assert(!(await table("workout_mutations")).some((r) => r.id === removal._mutationId));
    await db.exec("reset role; drop trigger reject_test_removal on public.workout_drafts; set role authenticated;");
    await apply("dayExercises", removal);
    assert.deepEqual((await table("settings"))[0].day_exercises, { a: [], b: [boundary] });
    assert.deepEqual((await table("workout_drafts")).find((r) => r.day_id === "a").draft, { startedAt: 123, exercises: { "1": authored } });
    assert.deepEqual((await table("workout_drafts")).find((r) => r.day_id === "b").draft, draft);
    // Lost-response retry cannot erase a newer restored reference or draft.
    await apply("dayExercises", { dayId: "a", exercises: [added] });
    await apply("draft", { dayId: "a", draft });
    await apply("dayExercises", removal);
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    assert.deepEqual((await table("workout_drafts")).find((r) => r.day_id === "a").draft, draft);
    await apply("finish", { dayId: "a", entries: [] });
    await apply("draft", { dayId: "b", draft: null });
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    await assert.rejects(apply("dayExercises", { dayId: "a", exercises: [] }, B), /Not authorized/);
    await db.exec(`set request.jwt.claim.sub = '${B}';`);
    assert.deepEqual(await table("settings"), []);
    await apply("dayExercises", { dayId: "a", exercises: [added] }, B);
    assert.deepEqual((await table("settings"))[0].day_exercises, { a: [added] });
    await db.exec(`set request.jwt.claim.sub = '${A}';`);
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    await db.exec("set role anon; reset request.jwt.claim.sub;");
    await assert.rejects(apply("dayExercises", { dayId: "a", exercises: [] }), /permission denied/);
  } finally { await db.close(); }
});

test("library/custom database snapshots roundtrip with strict bounds, atomic removal and RLS", async () => {
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
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    const apply = (type, payload, user = A) => db.query("select public.apply_workout_change($1::uuid,$2,$3::jsonb)", [user, type, JSON.stringify({ _mutationId: crypto.randomUUID(), ...payload })]);
    const table = async (name) => (await db.query(`select * from public.${name}`)).rows;
    const library = { key: `ex-${crypto.randomUUID()}`, source: "free-exercise-db", exerciseId: "Barbell_Bench_Press", name: "Barbell Bench Press", equipment: "barbell", primaryMuscles: ["chest", "triceps"], instructions: ["Lie on the bench.", "Press upward."], sets: 3, reps: "12", restSeconds: 90 };
    const custom = { ...library, key: `ex-${crypto.randomUUID()}`, source: "custom", exerciseId: crypto.randomUUID(), name: "My press", equipment: "", primaryMuscles: [], instructions: [], sets: 1, reps: "1", restSeconds: 0 };
    const legacy = { key: `mw-${crypto.randomUUID()}`, musclewikiId: 42, sets: 2, reps: "8", restSeconds: 60 };
    const boundary = { ...library, key: `ex-${crypto.randomUUID()}`, exerciseId: "x".repeat(160), name: "x".repeat(120), equipment: "x".repeat(100), primaryMuscles: Array(10).fill("x".repeat(100)), instructions: Array(30).fill("x".repeat(200)), sets: 10, reps: "100", restSeconds: 600 };
    const longInstructions = { ...library, key: `ex-${crypto.randomUUID()}`, exerciseId: "long-instructions", instructions: ["x".repeat(3000), "x".repeat(3000)] };
    await apply("dayExercises", { dayId: "a", exercises: [legacy, library, custom] });
    await apply("dayExercises", { dayId: "b", exercises: [boundary, longInstructions] });
    // Same ID in different sources is valid; custom IDs remain UUIDs.
    const sharedDatasetId = { ...library, key: `ex-${crypto.randomUUID()}`, exerciseId: custom.exerciseId };
    await apply("dayExercises", { dayId: "c", exercises: [sharedDatasetId, custom] });
    await apply("dayExercises", { dayId: "d", exercises: [{ ...custom, name: " \t\n\u00a0\ufeffMy press\u00a0 " }] });
    const expected = { a: [legacy, library, custom], b: [boundary, longInstructions], c: [sharedDatasetId, custom], d: [custom] };
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    // Additive reruns preserve snapshots and legacy rows byte-for-byte.
    await db.exec("reset role;");
    await db.exec(schema);
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    const invalid = [null, {}, [], "bad",
      ...Object.keys(library).map((field) => Object.fromEntries(Object.entries(library).filter(([key]) => key !== field))),
      { ...library, key: "ex-bad" }, { ...library, key: `${library.key}\n` }, { ...library, key: legacy.key },
      { ...library, source: "musclewiki" }, { ...library, source: null }, { ...library, exerciseId: "" },
      { ...library, exerciseId: " \n\t\u00a0\ufeff" }, { ...library, exerciseId: "x".repeat(161) }, { ...library, exerciseId: 42 },
      { ...custom, exerciseId: "not-a-uuid" }, { ...custom, exerciseId: `${custom.exerciseId}\n` },
      { ...library, name: "" }, { ...library, name: " \n\t\u00a0\ufeff" }, { ...library, name: "x".repeat(121) }, { ...library, name: null },
      { ...library, equipment: "x".repeat(101) }, { ...library, equipment: null },
      { ...library, primaryMuscles: null }, { ...library, primaryMuscles: "chest" }, { ...library, primaryMuscles: Array(11).fill("chest") },
      { ...library, primaryMuscles: [""] }, { ...library, primaryMuscles: [" \n\u00a0"] }, { ...library, primaryMuscles: ["x".repeat(101)] },
      { ...library, primaryMuscles: [null] }, { ...library, primaryMuscles: [{}] },
      { ...library, instructions: null }, { ...library, instructions: {} }, { ...library, instructions: Array(31).fill("") },
      { ...library, instructions: [null] }, { ...library, instructions: [42] }, { ...library, instructions: [{ text: "unsafe" }] },
      { ...library, instructions: ["x".repeat(3001)] }, { ...library, instructions: ["x".repeat(3000), "x".repeat(3000), "x"] },
      { ...library, sets: 0 }, { ...library, sets: 11 }, { ...library, sets: 1.5 }, { ...library, sets: "3" },
      { ...library, reps: "0" }, { ...library, reps: "101" }, { ...library, reps: "01" }, { ...library, reps: "12\n" }, { ...library, reps: 12 },
      { ...library, restSeconds: -1 }, { ...library, restSeconds: 601 }, { ...library, restSeconds: 0.5 }, { ...library, restSeconds: "60" },
      { ...library, musclewikiId: 42 }, { ...library, media: [] }, { ...library, user_id: B },
      { ...library, __proto__: null, constructor: { unsafe: true } }, { ...library, primaryMuscles: ["chest", ["nested"]] },
    ];
    const invalidPayloads = invalid.map((exercise) => ({ dayId: "a", exercises: [exercise] }));
    invalidPayloads.push(
      { dayId: "a", exercises: [library, { ...custom, key: library.key }] },
      { dayId: "a", exercises: [library, { ...library, key: `ex-${crypto.randomUUID()}` }] },
      { dayId: "a", exercises: [custom, { ...custom, key: `ex-${crypto.randomUUID()}`, name: "Renamed duplicate" }] },
      { dayId: "a", exercises: Array.from({ length: 31 }, (_, i) => ({ ...library, key: `ex-${crypto.randomUUID()}`, exerciseId: `exercise-${i}` })) },
      { dayId: "a", exercises: [library], removedKey: library.key },
      { dayId: "a", exercises: [], removedKey: `ex-${crypto.randomUUID()}\n` },
    );
    for (const payload of invalidPayloads) {
      const receiptCount = (await table("workout_mutations")).length;
      await assert.rejects(apply("dayExercises", payload), undefined, JSON.stringify(payload).slice(0, 160));
      assert.equal((await table("workout_mutations")).length, receiptCount);
      assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    }
    const authored = { sets: [{ weight: "60", reps: "8", done: true }, { weight: "65", reps: "6", done: false }], notes: "Keep authored sets", useSub: false };
    const draft = { startedAt: 123, exercises: { "1": authored, [legacy.key]: { ...authored, notes: "Legacy" }, [library.key]: { ...authored, notes: "Library" }, [custom.key]: { ...authored, notes: "Custom" } } };
    await apply("draft", { dayId: "a", draft });
    await apply("draft", { dayId: "b", draft });
    await apply("exerciseUnit", { exercise: library.key, unit: "lb" });
    const removal = { dayId: "a", exercises: [legacy, custom], removedKey: library.key, _mutationId: crypto.randomUUID() };
    await apply("dayExercises", removal);
    const remainingDraft = structuredClone(draft);
    delete remainingDraft.exercises[library.key];
    expected.a = [legacy, custom];
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    assert.deepEqual((await table("settings"))[0].units, { [library.key]: "lb" });
    assert.deepEqual((await table("workout_drafts")).find((row) => row.day_id === "a").draft, remainingDraft);
    assert.deepEqual((await table("workout_drafts")).find((row) => row.day_id === "b").draft, draft);
    await apply("dayExercises", { dayId: "a", exercises: [legacy], removedKey: custom.key });
    delete remainingDraft.exercises[custom.key];
    expected.a = [legacy];
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    assert.deepEqual((await table("workout_drafts")).find((row) => row.day_id === "a").draft, remainingDraft);
    // Old receipt cannot replay removal over a restored snapshot/draft.
    await apply("dayExercises", { dayId: "a", exercises: [legacy, library, custom] });
    await apply("draft", { dayId: "a", draft });
    await apply("dayExercises", removal);
    expected.a = [legacy, library, custom];
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    assert.deepEqual((await table("workout_drafts")).find((row) => row.day_id === "a").draft, draft);
    await assert.rejects(apply("dayExercises", { dayId: "a", exercises: [] }, B), /Not authorized/);
    await db.exec(`set request.jwt.claim.sub = '${B}';`);
    assert.deepEqual(await table("settings"), []);
    assert.deepEqual(await table("workout_drafts"), []);
    await assert.rejects(db.query("insert into public.settings(user_id,day_exercises) values ($1,$2::jsonb)", [A, JSON.stringify({ a: [custom] })]), /row-level security/);
    assert.equal((await db.query("update public.settings set day_exercises = '{}' where user_id = $1 returning user_id", [A])).rows.length, 0);
    await apply("dayExercises", { dayId: "a", exercises: [custom] }, B);
    await apply("draft", { dayId: "a", draft }, B);
    assert.deepEqual((await table("settings"))[0].day_exercises, { a: [custom] });
    await apply("dayExercises", { dayId: "a", exercises: [], removedKey: custom.key }, B);
    assert.deepEqual((await table("settings"))[0].day_exercises, { a: [] });
    const bDraft = structuredClone(draft);
    delete bDraft.exercises[custom.key];
    assert.deepEqual((await table("workout_drafts"))[0].draft, bDraft);
    await db.exec(`set request.jwt.claim.sub = '${A}';`);
    assert.deepEqual((await table("settings"))[0].day_exercises, expected);
    assert.deepEqual((await table("workout_drafts")).find((row) => row.day_id === "a").draft, draft);
    await db.exec("set role anon; reset request.jwt.claim.sub;");
    await assert.rejects(apply("dayExercises", { dayId: "a", exercises: [library] }), /permission denied/);
  } finally { await db.close(); }
});

test("database migration, transactional edits, retry safety and account isolation", async () => {
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create schema auth;
      create table auth.users (id uuid primary key);
      create function auth.uid() returns uuid language sql stable as
        $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
      grant usage on schema public, auth to authenticated, anon;
      grant execute on function auth.uid() to authenticated, anon;
      insert into auth.users values ('${A}'), ('${B}');
    `);
    const schema = await readFile(new URL("../supabase/schema.sql", import.meta.url), "utf8");
    await db.exec(schema);
    // Existing account records survive rerunning the upgrade.
    await db.query("insert into public.body_weights(user_id,date,weight,unit) values ($1,'2026/10/20',70,'kg')", [A]);
    await db.exec(schema);
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    const apply = (type, payload, user = A) => db.query("select public.apply_workout_change($1::uuid,$2,$3::jsonb)", [user, type, JSON.stringify({ _mutationId: crypto.randomUUID(), ...payload })]);
    const table = async (name) => (await db.query(`select * from public.${name}`)).rows;
    assert.equal((await table("body_weights")).length, 1);
    const plan = { id: `plan-${crypto.randomUUID()}`, name: "  Custom routine  ", description: "  My plan  ", blocks: [], createdAt: 100 };
    const creation = { plan, _mutationId: crypto.randomUUID() };
    await apply("trainingPlan", creation);
    await apply("trainingPlan", creation);
    assert.equal((await table("training_plans")).length, 1);
    assert.equal((await table("training_plans"))[0].name, "Custom routine");
    assert.equal((await table("training_plans"))[0].description, "My plan");
    // Edits only change metadata, including if another client sends stale blocks/time.
    await apply("trainingPlan", { plan: { ...plan, name: "Renamed", description: "", createdAt: 200 } });
    assert.equal((await table("training_plans"))[0].created_at, 100);
    assert.deepEqual((await table("training_plans"))[0].blocks, []);
    await apply("trainingPlan", creation); // Retried create cannot overwrite a later rename.
    assert.equal((await table("training_plans"))[0].name, "Renamed");
    const invalidPlans = [null, {}, { ...plan, id: "bad-id" }, { ...plan, name: " \n\t" },
      { ...plan, name: "x".repeat(81) }, { ...plan, name: 42 }, { ...plan, description: null },
      { ...plan, description: "x".repeat(501) }, { ...plan, blocks: null }, { ...plan, blocks: {} },
      { ...plan, blocks: [null] }, { ...plan, blocks: [{ format: "Straight Sets", exercises: {} }] },
      { ...plan, blocks: [{ format: "Straight Sets", exercises: [{ name: "Press" }] }] },
      { ...plan, createdAt: null }, { ...plan, createdAt: -1 }, { ...plan, createdAt: 1.5 }];
    for (const invalid of invalidPlans) {
      const receipts = (await table("workout_mutations")).length;
      await assert.rejects(apply("trainingPlan", { plan: invalid }), /Invalid training plan/);
      assert.equal((await table("workout_mutations")).length, receipts);
    }
    assert.equal((await table("training_plans"))[0].name, "Renamed");
    const exercise = { order: "1", name: "Press", muscles: "Chest", warmUp: "", sets: 2, reps: "10", rest: "2 min", cues: "", video: "", substitute: "", substituteVideo: "" };
    const populated = { ...plan, id: `plan-${crypto.randomUUID()}`, name: "Exercises", blocks: [{ format: "Straight Sets", exercises: [exercise] }] };
    await apply("trainingPlan", { plan: populated });
    await apply("trainingPlan", { plan: { ...populated, name: "Keep blocks", blocks: [], createdAt: 999 } });
    assert.deepEqual((await table("training_plans")).find((p) => p.id === populated.id).blocks, populated.blocks);
    await assert.rejects(apply("trainingPlan", { plan: { ...populated, blocks: [{ format: "Straight Sets", exercises: [{ ...exercise, sets: 1.5 }] }] } }), /Invalid training plan blocks/);
    // Rerunning an additive migration preserves populated and empty plans alike.
    await db.exec("reset role;");
    await db.exec(schema);
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${A}';`);
    assert.equal((await table("training_plans")).length, 2);
    assert.equal((await table("training_plans"))[0].created_at, 100);
    const draft = { startedAt: 1, exercises: { "1": { sets: [
      { weight: "60.25", reps: "10", done: true },
      { weight: "0", reps: "8", done: false },
      { weight: "BW", reps: "12", done: true },
    ], notes: "Test", useSub: true } } };
    await apply("draft", { dayId: "a", draft });
    assert.deepEqual((await table("workout_drafts"))[0].draft, draft);
    // Subsequent input edits upsert the same draft without coercing string weights or flags.
    draft.exercises["1"].sets[0] = { weight: "62.50", reps: "9", done: false };
    await apply("draft", { dayId: "a", draft });
    assert.equal((await table("workout_drafts")).length, 1);
    assert.deepEqual((await table("workout_drafts"))[0].draft, draft);
    await apply("exerciseUnit", { exercise: "Press", unit: "lb" });
    await apply("exerciseUnit", { exercise: "Row", unit: "kg" });
    await apply("bodyUnit", { unit: "lb" });
    await apply("keepScreenOn", { enabled: true });
    const timer = { endAt: 123, total: 60, label: "Press" };
    await apply("timer", { timer });
    const settings = (await table("settings"))[0];
    assert.deepEqual(settings.units, { Press: "lb", Row: "kg" });
    assert.equal(settings.body_unit, "lb");
    assert.equal(settings.keep_screen_on, true);
    assert.deepEqual(settings.rest_timer, timer);
    const bodyWeight = { date: "2026/10/21", weight: 158.75, unit: "lb" };
    await apply("weight", { date: bodyWeight.date, weight: 71.25, unit: "kg" });
    await apply("weight", bodyWeight);
    const weightRows = await table("body_weights");
    assert.equal(weightRows.length, 2); // Prior migration row plus one updated row for today.
    assert.deepEqual(weightRows.filter((row) => row.date === bodyWeight.date).map(({ date, weight, unit }) => ({ date, weight, unit })), [bodyWeight]);
    const entry = { id: "test-entry", date: "2026/10/21", dayId: "a", exercise: "Press", sets: draft.exercises["1"].sets.map(({ weight, reps }) => ({ weight, reps })), unit: "kg", notes: "Test", savedAt: 100 };
    // Invalid log insertion rolls back the whole finish, preserving the draft.
    await assert.rejects(apply("finish", { dayId: "a", entries: [entry, { ...entry, id: "bad", unit: "invalid" }] }));
    assert.equal((await table("workout_drafts")).length, 1);
    assert.equal((await table("log_entries")).length, 0);
    const finish = { dayId: "a", entries: [entry], _mutationId: crypto.randomUUID() };
    await apply("finish", finish);
    assert.equal((await table("workout_drafts")).length, 0);
    const savedEntry = (await table("log_entries"))[0];
    assert.deepEqual(savedEntry.sets, entry.sets);
    assert.equal(savedEntry.unit, entry.unit);
    await apply("draft", { dayId: "a", draft: { ...draft, startedAt: 2 } });
    await apply("finish", finish); // Lost response retry must not delete new draft.
    assert.equal((await table("log_entries")).length, 1);
    assert.equal((await table("workout_drafts"))[0].draft.startedAt, 2);
    await apply("draft", { dayId: "a", draft: null });
    await apply("timer", { timer: null });
    assert.equal((await table("settings"))[0].rest_timer, null);
    await apply("draft", { dayId: "b", draft });
    await apply("draft", { dayId: "b", draft: null });
    assert.equal((await table("workout_drafts")).length, 0);
    await assert.rejects(apply("bodyUnit", { unit: "kg" }, B), /Not authorized/);
    await db.exec(`set request.jwt.claim.sub = '${B}';`);
    for (const name of ["settings", "log_entries", "body_weights", "workout_drafts", "training_plans"]) assert.equal((await table(name)).length, 0);
    await assert.rejects(apply("weight", { ...bodyWeight, weight: 1 }, A), /Not authorized/);
    await assert.rejects(apply("finish", { dayId: "a", entries: [entry] }, A), /Not authorized/);
    assert.equal((await db.query("update public.body_weights set weight = 1 where user_id = $1 returning date", [A])).rows.length, 0);
    assert.equal((await db.query("delete from public.log_entries where user_id = $1 returning id", [A])).rows.length, 0);
    // Identical dates and log IDs belong independently to each account.
    const otherWeight = { ...bodyWeight, weight: 80.5, unit: "kg" };
    const otherEntry = { ...entry, sets: [{ weight: "20.5", reps: "5" }], unit: "lb" };
    await apply("weight", otherWeight, B);
    await apply("finish", { dayId: "a", entries: [otherEntry] }, B);
    assert.deepEqual((await table("body_weights")).map(({ date, weight, unit }) => ({ date, weight, unit })), [otherWeight]);
    assert.equal((await table("log_entries")).length, 1);
    assert.deepEqual((await table("log_entries"))[0].sets, otherEntry.sets);
    assert.equal((await table("log_entries"))[0].unit, otherEntry.unit);
    await assert.rejects(db.query("insert into public.workout_drafts(user_id,day_id,draft) values ($1,'a','{}')", [A]), /row-level security/);
    await assert.rejects(apply("trainingPlan", { plan }, A), /Not authorized/);
    await assert.rejects(db.query("insert into public.training_plans(user_id,id,name,description,blocks,created_at) values ($1,$2,'Plan','','[]',100)", [A, `plan-${crypto.randomUUID()}`]), /row-level security/);
    await apply("trainingPlan", { plan: { ...plan, name: "B's plan" } }, B);
    assert.equal((await table("training_plans")).length, 1);
    assert.equal((await table("training_plans"))[0].name, "B's plan");
    await db.exec(`set request.jwt.claim.sub = '${A}';`);
    assert.deepEqual((await table("body_weights")).filter((row) => row.date === bodyWeight.date).map(({ date, weight, unit }) => ({ date, weight, unit })), [bodyWeight]);
    assert.equal((await table("log_entries")).length, 1);
    assert.deepEqual((await table("log_entries"))[0].sets, entry.sets);
    assert.equal((await table("log_entries"))[0].unit, entry.unit);
    assert.equal((await table("training_plans")).length, 2);
    assert.equal((await table("training_plans")).find((p) => p.id === plan.id).name, "Renamed");
    await assert.rejects(db.query("insert into public.training_plans(user_id,id,name,description,blocks,created_at) values ($1,$2,'Plan','','{}',100)", [A, `plan-${crypto.randomUUID()}`]), /check constraint/);
    await apply("deleteWeight", { date: "2026/10/21" });
    await apply("deleteSession", { date: entry.date, dayId: "a", savedAt: 100 });
    assert.equal((await table("body_weights")).length, 1);
    assert.equal((await table("log_entries")).length, 0);
    await db.exec("set role anon; reset request.jwt.claim.sub;");
    await assert.rejects(apply("bodyUnit", { unit: "kg" }), /permission denied/);
  } finally {
    await db.close();
  }
});
