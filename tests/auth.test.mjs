import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";

const source = ts.transpileModule(await readFile(new URL("../src/lib/auth.ts", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
function auth(store, signOut, extraClient = {}, window = {}) {
  const exports = {};
  new Function("exports", "require", "window", source)(exports, (name) => {
    if (name === "react") return { createContext: () => ({ Provider: {} }), useContext: () => null };
    if (name === "@/lib/store") return store;
    if (name === "@/lib/supabase") return { supabase: () => ({ ...extraClient, auth: { signOut, ...extraClient.auth } }) };
    throw new Error(name);
  }, window);
  return exports;
}

test("sign-out locks edits before flush and refuses failed saves", async () => {
  const calls = [];
  const api = auth({
    getAccountGeneration: () => 1,
    lockAccountEdits: (locked) => calls.push(locked ? "lock" : "unlock"),
    flushChanges: async () => { calls.push("flush"); return false; },
    resetAccount: () => assert.fail("must not reset on failed flush"),
  }, () => assert.fail("must not sign out on failed flush"));
  await assert.rejects(api.signOut(), /could not be saved/);
  assert.deepEqual(calls, ["lock", "flush", "unlock"]);
});

test("sign-out failure unlocks editing without resetting account", async () => {
  const calls = [];
  const api = auth({
    getAccountGeneration: () => 1,
    lockAccountEdits: (locked) => calls.push(locked),
    flushChanges: async () => true,
    resetAccount: () => assert.fail("must not reset on failed signout"),
  }, async () => ({ error: { message: "Auth unavailable" } }));
  await assert.rejects(api.signOut(), /Auth unavailable/);
  assert.deepEqual(calls, [true, false]);
});

test("late sign-out does not reset or unlock a newer account generation", async () => {
  let generation = 1;
  let complete;
  const waiting = new Promise((resolve) => { complete = resolve; });
  const api = auth({
    getAccountGeneration: () => generation,
    lockAccountEdits: (locked) => assert.equal(locked, true, "old request must not unlock new identity"),
    flushChanges: async () => true,
    resetAccount: () => assert.fail("old request must not reset new identity"),
  }, async () => waiting);
  const signingOut = api.signOut();
  await Promise.resolve();
  generation = 2;
  complete({ error: null });
  await signingOut;
});

function deletion(options = {}) {
  const calls = [];
  let generation = 1;
  const api = auth({
    getAccountGeneration: () => generation,
    getPersistenceStatus: () => ({ userId: "A", ready: true, locked: false, ...options.status }),
    lockAccountEdits: (locked) => calls.push(locked ? "lock" : "unlock"),
    flushChanges: async () => { calls.push("flush"); return options.flush ? options.flush() : true; },
    resetAccount: () => { calls.push("reset"); generation++; },
  }, async () => { calls.push("signout"); if (options.signOutError) throw new Error("invalid token"); return { error: null }; }, {
    auth: { getSession: async () => { calls.push("session"); return { data: { session: { user: { id: options.sessionId ?? "A" } } }, error: null }; } },
    rpc: async (name, args) => { calls.push({ name, args }); return options.rpc ? options.rpc() : { error: null }; },
  }, { location: { replace: (url) => calls.push(`redirect:${url}`) } });
  return { api, calls, changeAccount: () => generation++ };
}

test("delete-account requires exact confirmation, matching ready account and unlocked edits", async () => {
  for (const confirmation of ["", "delete", "DELETE ", null]) {
    const { api, calls } = deletion();
    await assert.rejects(api.deleteAccount("A", confirmation), /Type DELETE/);
    assert.deepEqual(calls, []);
  }
  for (const status of [{ ready: false }, { locked: true }, { userId: "B" }]) {
    const { api, calls } = deletion({ status });
    await assert.rejects(api.deleteAccount("A", "DELETE"), /not ready/);
    assert.deepEqual(calls, []);
  }
});

test("delete-account drains pending writes, deletes only expected account, clears memory and signs out", async () => {
  const { api, calls } = deletion({ flush: async () => false });
  await api.deleteAccount("A", "DELETE");
  assert.deepEqual(calls, ["lock", "flush", "session", { name: "delete_own_account", args: { expected_account_id: "A", confirmation: "DELETE" } }, "reset", "signout", "redirect:/account"]);
});

test("deletion failure preserves account, unlocks edits and explains missing migration", async () => {
  for (const code of ["PGRST202", "42883", "42501"]) {
    const { api, calls } = deletion({ rpc: async () => ({ error: { code } }) });
    await assert.rejects(api.deleteAccount("A", "DELETE"), code === "42501" ? /Could not confirm/ : /latest database schema/);
    assert.equal(calls.at(-1), "unlock");
    assert(!calls.includes("reset"));
    assert(!calls.includes("signout"));
  }
});

test("deletion rejects stale sessions and does not mutate a newer account after waiting", async () => {
  const stale = deletion({ sessionId: "B" });
  await assert.rejects(stale.api.deleteAccount("A", "DELETE"), /session changed/);
  assert(!stale.calls.some((value) => typeof value === "object"));
  let release;
  const app = deletion({ flush: () => new Promise((resolve) => { release = resolve; }) });
  const pending = app.api.deleteAccount("A", "DELETE");
  app.changeAccount();
  release(true);
  await assert.rejects(pending, /session changed/);
  assert(!app.calls.includes("unlock"));
  assert(!app.calls.includes("reset"));
});

test("concurrent deletion is rejected and a late response cannot reset the new account", async () => {
  let release;
  const app = deletion({ rpc: () => new Promise((resolve) => { release = resolve; }) });
  const pending = app.api.deleteAccount("A", "DELETE");
  await new Promise((resolve) => setImmediate(resolve));
  await assert.rejects(app.api.deleteAccount("A", "DELETE"), /already in progress/);
  await assert.rejects(app.api.signOut(), /wait for account deletion/);
  app.changeAccount();
  release({ error: null });
  await pending;
  assert(!app.calls.includes("reset"));
  assert(!app.calls.includes("unlock"));
});

test("confirmed deletion clears memory and redirects even when local sign-out fails", async () => {
  const { api, calls } = deletion({ signOutError: true });
  await api.deleteAccount("A", "DELETE");
  assert(calls.includes("reset"));
  assert.equal(calls.at(-1), "redirect:/account");
});
