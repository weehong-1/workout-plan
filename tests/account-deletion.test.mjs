import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { PGlite } from "@electric-sql/pglite";

const schema = await readFile(new URL("../supabase/schema.sql", import.meta.url), "utf8");
const A = "00000000-0000-0000-0000-000000000001";
const B = "00000000-0000-0000-0000-000000000002";
const MISSING = "00000000-0000-0000-0000-000000000003";
const appTables = ["log_entries", "body_weights", "settings", "workout_drafts", "training_plans", "workout_mutations"];

async function setup(t, { legacySettings = false } = {}) {
  const db = new PGlite();
  t.after(() => db.close());
  await db.exec(`
    create role anon; create role authenticated; create role unprivileged;
    create schema auth; create table auth.users (id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema public, auth to authenticated, anon, unprivileged;
    grant execute on function auth.uid() to authenticated, anon, unprivileged;
    insert into auth.users values ('${A}'), ('${B}');
  `);
  if (legacySettings) {
    // Match the existing database tests' pre-migration, non-cascading FK.
    await db.exec(`
      create table public.settings (
        user_id uuid primary key default auth.uid() references auth.users(id),
        units jsonb not null default '{}', default_unit text not null default 'kg',
        body_unit text not null default 'kg', updated_at timestamptz not null default now()
      );
      insert into public.settings(user_id, units) values ('${A}', '{"Press":"lb"}');
    `);
  }
  await db.exec(schema);
  for (const user of [A, B]) {
    await db.query(`insert into public.log_entries
      (user_id, id, date, day_id, exercise, sets, saved_at)
      values ($1, 'log-1', '2026/10/22', 'a', 'Press', '[{"weight":"60","reps":"8"}]', 123)`, [user]);
    await db.query(`insert into public.body_weights(user_id, date, weight, unit)
      values ($1, '2026/10/22', 75, 'kg')`, [user]);
    await db.query(`insert into public.settings(user_id, units, day_exercises)
      values ($1, '{"Press":"lb"}', '{"a":[]}')
      on conflict (user_id) do update set day_exercises = excluded.day_exercises`, [user]);
    await db.query(`insert into public.workout_drafts(user_id, day_id, draft)
      values ($1, 'a', '{"startedAt":123,"exercises":{}}')`, [user]);
    await db.query(`insert into public.training_plans(user_id, id, name, blocks, created_at)
      values ($1, 'plan-00000000-0000-0000-0000-000000000001', 'Private plan', '[]', 123)`, [user]);
    await db.query(`insert into public.workout_mutations(user_id, id)
      values ($1, '00000000-0000-0000-0000-000000000001')`, [user]);
  }
  return db;
}

async function asRole(db, role, user = null) {
  // Role names are fixed test fixtures, never application/caller input.
  assert(["authenticated", "anon", "unprivileged"].includes(role));
  await db.exec(`reset role; set role ${role};`);
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [user ?? ""]);
}

function deleteAccount(db, expected = A, confirmation = "DELETE") {
  return db.query("select public.delete_own_account($1::uuid, $2::text)", [expected, confirmation]);
}

async function snapshot(db) {
  await db.exec("reset role;");
  const result = { users: (await db.query("select * from auth.users order by id")).rows };
  for (const table of appTables) {
    result[table] = (await db.query(`select * from public.${table} order by user_id`)).rows;
  }
  return result;
}

function assertOnlyBRemains(before, after) {
  assert.deepEqual(after.users, [{ id: B }]);
  for (const table of appTables) {
    assert.equal(before[table].filter((row) => row.user_id === A).length, 1, `${table}: A was seeded`);
    assert.deepEqual(after[table], before[table].filter((row) => row.user_id === B), `${table}: preserve B exactly`);
    assert.equal(after[table].length, 1, `${table}: B was seeded`);
  }
}

test("account deletion is SECURITY DEFINER with a locked search path and authenticated-only execute", async (t) => {
  const db = await setup(t);
  const { rows: [fn] } = await db.query(`select prosecdef, proconfig,
    has_function_privilege('authenticated', oid, 'EXECUTE') as authenticated,
    has_function_privilege('anon', oid, 'EXECUTE') as anon,
    has_function_privilege('unprivileged', oid, 'EXECUTE') as unprivileged
    from pg_proc where oid = 'public.delete_own_account(uuid,text)'::regprocedure`);
  assert.equal(fn.prosecdef, true);
  assert.deepEqual(fn.proconfig, ['search_path=""']);
  assert.equal(fn.authenticated, true);
  assert.equal(fn.anon, false);
  assert.equal(fn.unprivileged, false);
  const before = await snapshot(db);
  for (const role of ["anon", "unprivileged"]) {
    // Even a supplied subject cannot bypass the function's execute privilege.
    await asRole(db, role, A);
    await assert.rejects(deleteAccount(db), { code: "42501" });
  }
  for (const expected of [A, null]) {
    await asRole(db, "authenticated");
    await assert.rejects(deleteAccount(db, expected), { code: "42501", message: "Not authorized" });
  }
  assert.deepEqual(await snapshot(db), before);
});

test("account deletion blocks cross-user/null IDs and requires exact non-null DELETE confirmation", async (t) => {
  const db = await setup(t);
  const before = await snapshot(db);
  for (const expected of [B, MISSING, null]) {
    await asRole(db, "authenticated", A);
    await assert.rejects(deleteAccount(db, expected), { code: "42501", message: "Not authorized" });
    assert.deepEqual(await snapshot(db), before);
  }
  for (const confirmation of [null, "", "delete", "Delete", " DELETE", "DELETE ", "DELETE\n", "DELETE'; delete from auth.users; --"]) {
    await asRole(db, "authenticated", A);
    await assert.rejects(deleteAccount(db, A, confirmation), { code: "22023" });
    assert.deepEqual(await snapshot(db), before);
  }
  // Reject a stale/wrong identity before inspecting its confirmation.
  await asRole(db, "authenticated", B);
  await assert.rejects(deleteAccount(db, A, null), { code: "42501" });
  assert.deepEqual(await snapshot(db), before);
});

test("authenticated callers cannot delete auth.users directly but can delete only their own account via RPC", async (t) => {
  const db = await setup(t);
  const before = await snapshot(db);
  await asRole(db, "authenticated", A);
  for (const target of [A, B]) {
    await assert.rejects(db.query("delete from auth.users where id = $1", [target]), { code: "42501" });
  }
  assert.deepEqual(await snapshot(db), before);
  await asRole(db, "authenticated", A);
  await deleteAccount(db);
  assertOnlyBRemains(before, await snapshot(db));
});

test("account deletion fails for a missing auth row instead of reporting success", async (t) => {
  const db = await setup(t);
  const before = await snapshot(db);
  await asRole(db, "authenticated", MISSING);
  await assert.rejects(deleteAccount(db, MISSING), { code: "P0002", message: "Account not found" });
  assert.deepEqual(await snapshot(db), before);
  await asRole(db, "authenticated", A);
  await deleteAccount(db);
  const after = await snapshot(db);
  await asRole(db, "authenticated", A);
  await assert.rejects(deleteAccount(db), { code: "P0002", message: "Account not found" });
  assert.deepEqual(await snapshot(db), after);
});

test("a restrictive FK rolls back the whole account deletion including the explicit settings delete", async (t) => {
  const db = await setup(t, { legacySettings: true });
  await db.exec(`create table public.deletion_blocker (
    user_id uuid primary key references auth.users(id) on delete restrict
  ); insert into public.deletion_blocker values ('${A}');`);
  const before = await snapshot(db);
  await asRole(db, "authenticated", A);
  await assert.rejects(deleteAccount(db), { code: "23001" });
  assert.deepEqual(await snapshot(db), before);
  assert.deepEqual((await db.query("select * from public.deletion_blocker")).rows, [{ user_id: A }]);
  // Removing only the deliberate blocker permits the same ordinary caller to retry.
  await db.exec("delete from public.deletion_blocker;");
  await asRole(db, "authenticated", A);
  await deleteAccount(db);
  assertOnlyBRemains(before, await snapshot(db));
});

test("schema reruns are idempotent and deletion supports legacy non-cascading settings without changing their FK", async (t) => {
  const db = await setup(t, { legacySettings: true });
  const before = await snapshot(db);
  for (let i = 0; i < 2; i++) {
    await db.exec(schema);
    assert.deepEqual(await snapshot(db), before);
  }
  const { rows: [fk] } = await db.query(`select confdeltype from pg_constraint
    where conrelid = 'public.settings'::regclass and contype = 'f'`);
  assert.equal(fk.confdeltype, "a", "legacy settings FK remains NO ACTION");
  await asRole(db, "anon", A);
  await assert.rejects(deleteAccount(db), { code: "42501" });
  await asRole(db, "authenticated", A);
  await deleteAccount(db);
  const after = await snapshot(db);
  assertOnlyBRemains(before, after);
  await db.exec(schema);
  assert.deepEqual(await snapshot(db), after);
});
