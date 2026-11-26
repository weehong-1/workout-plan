import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { PGlite } from "@electric-sql/pglite";

const schema = await readFile(new URL("../supabase/schema.sql", import.meta.url), "utf8");
const A = "00000000-0000-0000-0000-00000000000a";
const B = "00000000-0000-0000-0000-00000000000b";
const TOKEN = "k".repeat(43);
const hash = (token) => createHash("sha256").update(token, "utf8").digest("hex");

async function setup(t) {
  const db = new PGlite();
  t.after(() => db.close());
  await db.exec(`
    create role anon; create role authenticated;
    create schema auth; create table auth.users (id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema public, auth to authenticated, anon;
    grant execute on function auth.uid() to authenticated, anon;
    insert into auth.users values ('${A}'), ('${B}');
  `);
  await db.exec(schema);
  return db;
}

async function asRole(db, role, user = null) {
  assert(["authenticated", "anon"].includes(role));
  await db.exec(`reset role; set role ${role};`);
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [user ?? ""]);
}

function insertLink(db, { token = TOKEN, expiresAt = null, paths = [`${A}/share-1-2.jpg`], name = "Coach" } = {}) {
  return db.query(
    `insert into public.share_links
      (token_hash, trainer_name, period_kind, period_start, period_label, includes, summary, photo_paths, expires_at)
     values ($1, $2, 'month', '2026-11-01', 'November 2026', '{"activity":true}', '{"version":1}', $3, $4)
     returning id`,
    [hash(token), name, paths, expiresAt],
  );
}

const review = (db, token) => db.query("select public.get_shared_review($1) as review", [token]);

test("a trainer link is readable by token without signing in, and hides the trainer name", async (t) => {
  const db = await setup(t);
  await asRole(db, "authenticated", A);
  await insertLink(db);

  await asRole(db, "anon");
  const { rows: [row] } = await review(db, TOKEN);
  assert.equal(row.review.period_label, "November 2026");
  assert.deepEqual(row.review.summary, { version: 1 });
  assert.equal("trainer_name" in row.review, false);

  for (const wrong of ["x".repeat(43), "", null, "k".repeat(20)]) {
    const { rows: [miss] } = await review(db, wrong);
    assert.equal(miss.review, null);
  }
  // Anonymous visitors cannot list or read the table itself.
  await assert.rejects(db.query("select * from public.share_links"), { code: "42501" });
});

test("expired and revoked links stop working, and so do their photos", async (t) => {
  const db = await setup(t);
  await asRole(db, "authenticated", A);
  await insertLink(db, { token: "e".repeat(43), expiresAt: "2000-01-01T00:00:00Z", paths: [`${A}/share-old.jpg`] });
  const { rows: [{ id }] } = await insertLink(db);

  await asRole(db, "anon");
  assert.equal((await review(db, "e".repeat(43))).rows[0].review, null);
  const shared = (name) => db.query("select public.is_shared_photo($1) as ok", [name]).then((r) => r.rows[0].ok);
  assert.equal(await shared(`${A}/share-1-2.jpg`), true);
  assert.equal(await shared(`${A}/share-old.jpg`), false);
  assert.equal(await shared(`${A}/private.jpg`), false);

  // Revoking deletes the row.
  await asRole(db, "authenticated", A);
  await db.query("delete from public.share_links where id = $1", [id]);
  await asRole(db, "anon");
  assert.equal((await review(db, TOKEN)).rows[0].review, null);
  assert.equal(await shared(`${A}/share-1-2.jpg`), false);
});

test("links belong to their owner: others cannot see, delete or point at foreign photos", async (t) => {
  const db = await setup(t);
  await asRole(db, "authenticated", A);
  await insertLink(db);

  await asRole(db, "authenticated", B);
  assert.equal((await db.query("select * from public.share_links")).rows.length, 0);
  await db.query("delete from public.share_links");
  await assert.rejects(insertLink(db, { token: "b".repeat(43), paths: [`${A}/share-1-2.jpg`] }), { code: "23514" });
  await assert.rejects(insertLink(db, { token: "c".repeat(43), paths: [`${B}/nested/share-1.jpg`] }), { code: "23514" });
  await assert.rejects(insertLink(db, { token: "d".repeat(43), name: "x".repeat(81), paths: [] }), { code: "23514" });
  // Links are never edited after creation.
  await assert.rejects(db.query("update public.share_links set expires_at = null"), { code: "42501" });

  await asRole(db, "authenticated", A);
  assert.equal((await db.query("select * from public.share_links")).rows.length, 1);
});

test("deleting the account removes its links", async (t) => {
  const db = await setup(t);
  await asRole(db, "authenticated", A);
  await insertLink(db);
  await db.query("select public.delete_own_account($1::uuid, 'DELETE')", [A]);
  await db.exec("reset role;");
  assert.equal((await db.query("select * from public.share_links")).rows.length, 0);
});
