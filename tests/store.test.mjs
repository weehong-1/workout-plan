import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";
import { webcrypto } from "node:crypto";

const source = ts.transpileModule(await readFile(new URL("../src/lib/store.ts", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const empty = () => ({ trainingPlans: [], dayExercises: {}, log: [], drafts: {}, defaultUnit: "kg", units: {}, bodyWeights: [], bodyUnit: "kg", timer: null, keepScreenOn: false });
function store(database, crypto = webcrypto) {
  const exports = {};
  const require = (name) => {
    if (name === "react") return { useSyncExternalStore: () => {} };
    if (name === "@/lib/database") return database;
    throw new Error(`Unexpected import: ${name}`);
  };
  new Function("exports", "require", "crypto", source)(exports, require, crypto);
  return exports;
}
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((a, b) => { resolve = a; reject = b; });
  return { promise, resolve, reject };
};

test("every editable account field writes immediately, ordered, with confirmed completion", async () => {
  const calls = [];
  const first = deferred();
  const s = store({ readAccount: async () => empty(), writeChange: async (user, change) => {
    calls.push({ user, change });
    if (calls.length === 1) await first.promise;
  } });
  await s.loadAccount("A");
  s.updateDraftExercise("a", "1", 2, (ex) => ({ ...ex, notes: "First", useSub: true }));
  s.updateDraftExercise("a", "1", 2, (ex) => ({ ...ex, notes: "Second", sets: [{ weight: "60", reps: "10", done: true }] }));
  s.setExerciseUnit("Press", "lb");
  s.setBodyUnit("lb");
  s.saveBodyWeight({ date: "2026/10/21", weight: 70, unit: "kg" });
  s.setRestTimer({ endAt: 5, total: 4, label: "Press" });
  s.setKeepScreenOn(true);
  assert.equal(calls.length, 1);
  assert.equal(s.getPersistenceStatus().unsaved, 7);
  first.resolve();
  assert.equal(await s.flushChanges(), true);
  assert.deepEqual(calls.map((c) => c.change.type), ["draft", "draft", "exerciseUnit", "bodyUnit", "weight", "timer", "keepScreenOn"]);
  assert.equal(calls[1].change.payload.draft.exercises["1"].notes, "Second");
  assert(calls.every((c) => c.user === "A"));
  assert.equal(s.getPersistenceStatus().unsaved, 0);
  assert.equal(s.getPersistenceStatus().phase, "idle");
  s.finishDraft("a", [{ date: "2026/10/21", dayId: "a", exercise: "Press", sets: [], notes: "Test" }]);
  await s.flushChanges();
  assert.equal(s.getState().drafts.a, undefined);
  assert.equal(calls.at(-1).change.type, "finish");
  assert.equal(calls.at(-1).change.payload.entries[0].id, s.getState().log[0].id);
  s.deleteSession("2026/10/21", "a", s.getState().log[0].savedAt);
  s.deleteBodyWeight("2026/10/21");
  s.discardDraft("b");
  await s.flushChanges();
  assert.deepEqual(calls.slice(-3).map((c) => c.change.type), ["deleteSession", "deleteWeight", "draft"]);
});

test("training plans create, edit and reload through the ordered account queue", async () => {
  const accounts = new Map();
  const calls = [];
  const first = deferred();
  const s = store({
    readAccount: async (user) => structuredClone(accounts.get(user) ?? empty()),
    writeChange: async (user, change) => {
      calls.push({ user, change });
      if (calls.length === 1) await first.promise;
      const account = accounts.get(user) ?? empty();
      if (change.type === "trainingPlan") {
        const plan = structuredClone(change.payload.plan);
        account.trainingPlans = [...account.trainingPlans.filter((p) => p.id !== plan.id), plan];
      }
      accounts.set(user, account);
    },
  });
  await s.loadAccount("A");
  const created = s.saveTrainingPlan("  My plan  ", "  A simple routine  ");
  assert.match(created.id, /^plan-[0-9a-f-]{36}$/);
  assert.equal(created.name, "My plan");
  assert.equal(created.description, "A simple routine");
  assert.deepEqual(created.blocks, []);
  assert.equal(typeof created.createdAt, "number");
  const edited = s.saveTrainingPlan("  Updated  ", " ", created.id);
  assert.equal(edited.id, created.id);
  assert.equal(edited.createdAt, created.createdAt);
  assert.equal(edited.description, "");
  assert.equal(s.getState().trainingPlans.length, 1);
  assert.equal(s.getPersistenceStatus().unsaved, 2);
  assert.equal(calls.length, 1);
  first.resolve();
  assert.equal(await s.flushChanges(), true);
  assert.deepEqual(calls.map((c) => c.change.type), ["trainingPlan", "trainingPlan"]);
  assert.notEqual(calls[0].change.mutationId, calls[1].change.mutationId);
  await s.loadAccount("A");
  assert.deepEqual(s.getState().trainingPlans, [edited]);
  await s.loadAccount("B");
  assert.deepEqual(s.getState().trainingPlans, []);
  assert.equal(s.saveTrainingPlan("Other account", "", created.id), null);
  await s.loadAccount("A");
  const blocks = [{ format: "Straight Sets", exercises: [{ name: "Press" }] }];
  accounts.get("A").trainingPlans[0].blocks = blocks;
  await s.loadAccount("A");
  const withExercises = s.saveTrainingPlan("Renamed", "Preserve exercises", created.id);
  assert.deepEqual(withExercises.blocks, blocks);
  assert.equal(withExercises.createdAt, created.createdAt);
  assert.equal(await s.flushChanges(), true);
  s.resetAccount();
  assert.deepEqual(s.getState().trainingPlans, []);
});

test("training plan validation and unavailable or locked accounts never enqueue edits", async () => {
  const loading = deferred();
  const s = store({ readAccount: async () => loading.promise, writeChange: async () => {} });
  assert.equal(s.saveTrainingPlan("Plan", ""), null);
  const load = s.loadAccount("A");
  assert.equal(s.saveTrainingPlan("Plan", ""), null);
  loading.resolve(empty());
  await load;
  const initial = s.getState();
  for (const [name, description] of [["", ""], [" \n\t", ""], ["x".repeat(81), ""], ["Plan", "x".repeat(501)], [null, ""], ["Plan", null]]) {
    assert.equal(s.saveTrainingPlan(name, description), null);
  }
  assert.equal(s.saveTrainingPlan("Plan", "", "missing"), null);
  s.lockAccountEdits(true);
  assert.equal(s.saveTrainingPlan("Plan", ""), null);
  assert.equal(s.getState(), initial);
  assert.equal(s.getPersistenceStatus().unsaved, 0);
  s.lockAccountEdits(false);
  assert(s.saveTrainingPlan(` ${"n".repeat(80)} `, ` ${"d".repeat(500)} `));
  assert.equal(await s.flushChanges(), true);
});

test("training plan failed writes retry without creating duplicates", async () => {
  let fail = true;
  const saved = [];
  const s = store({ readAccount: async () => empty(), writeChange: async (_, change) => {
    if (fail) throw new Error("Offline");
    saved.push(change);
  } });
  await s.loadAccount("A");
  const created = s.saveTrainingPlan("Plan", "");
  assert.equal(await s.flushChanges(), false);
  assert.equal(s.getPersistenceStatus().unsaved, 1);
  const edited = s.saveTrainingPlan("Plan retry", "", created.id);
  fail = false;
  assert.equal(await s.flushChanges(), true);
  assert.deepEqual(s.getState().trainingPlans, [edited]);
  assert.equal(saved.length, 2);
  assert(saved.every((c) => c.payload.plan.id === created.id));
});

const prescription = { source: "free-exercise-db", exerciseId: "Dumbbell_Curl", name: "Dumbbell Curl", equipment: "dumbbell", primaryMuscles: ["biceps"], instructions: ["Curl with control."], sets: 3, reps: "12", restSeconds: 90 };

test("exercise snapshots persist across sessions/reload; duplicate IDs never write twice", async () => {
  const accounts = new Map();
  const calls = [];
  const s = store({
    readAccount: async (user) => structuredClone(accounts.get(user) ?? empty()),
    writeChange: async (user, change) => {
      calls.push(change);
      const account = accounts.get(user) ?? empty();
      if (change.type === "dayExercises") account.dayExercises[change.payload.dayId] = structuredClone(change.payload.exercises);
      accounts.set(user, account);
    },
  });
  await s.loadAccount("A");
  const added = s.addDayExercise("a", { ...prescription, name: "  Dumbbell Curl  ", video: "Do not persist" });
  assert.match(added.key, /^ex-[0-9a-f-]{36}$/);
  assert.deepEqual(added, { ...prescription, key: added.key });
  assert.equal(added.video, undefined);
  await s.flushChanges();
  assert.equal(s.addDayExercise("a", { ...prescription, sets: 5 }), added);
  assert.equal(calls.length, 1);
  const secondDay = s.addDayExercise("b", { ...prescription, exerciseId: "Barbell_Curl" });
  assert.equal(secondDay.exerciseId, "Barbell_Curl");
  assert.notEqual(secondDay.key, added.key);
  await s.flushChanges();
  s.updateDraftExercise("a", added.key, 3, (ex) => ({ ...ex, notes: "Keep reference" }));
  s.discardDraft("a");
  s.finishDraft("b", []);
  await s.flushChanges();
  assert.deepEqual(s.getState().dayExercises, { a: [added], b: [secondDay] });
  await s.loadAccount("A");
  assert.deepEqual(s.getState().dayExercises, { a: [added], b: [secondDay] });
  await s.loadAccount("B");
  assert.deepEqual(s.getState().dayExercises, {});
  s.resetAccount();
  assert.deepEqual(s.getState().dayExercises, {});
});

test("removing a reference and only its draft exercise uses one retry-safe mutation", async () => {
  const added = { ...prescription, key: `ex-${webcrypto.randomUUID()}` };
  const keep = { sets: [{ weight: "60", reps: "8", done: true }], notes: "Preserve", useSub: false };
  const calls = [];
  let fail = true;
  const s = store({
    readAccount: async () => ({ ...empty(), dayExercises: { a: [added] }, drafts: { a: { startedAt: 123, exercises: { "1": keep, [added.key]: { ...keep, notes: "Remove" } } } } }),
    writeChange: async (_, change) => { calls.push(change); if (fail) throw new Error("Offline"); },
  });
  await s.loadAccount("A");
  s.removeDayExercise("a", added.key);
  assert.deepEqual(s.getState().dayExercises.a, []);
  assert.deepEqual(s.getState().drafts.a, { startedAt: 123, exercises: { "1": keep } });
  assert.equal(await s.flushChanges(), false);
  assert.equal(s.getPersistenceStatus().unsaved, 1);
  fail = false;
  assert.equal(await s.flushChanges(), true);
  assert.equal(calls.length, 2);
  assert.equal(calls[0].mutationId, calls[1].mutationId);
  assert.deepEqual(calls[0].payload, { dayId: "a", exercises: [], removedKey: added.key });
  s.removeDayExercise("a", added.key);
  assert.equal(calls.length, 2);
});

test("added references reject invalid prescriptions and unavailable/locked accounts", async () => {
  const calls = [];
  const loading = deferred();
  const s = store({ readAccount: async () => loading.promise, writeChange: async (_, change) => calls.push(change) });
  assert.equal(s.addDayExercise("a", prescription), null);
  const load = s.loadAccount("A");
  assert.equal(s.addDayExercise("a", prescription), null);
  loading.resolve(empty());
  await load;
  for (const patch of [
    { source: "musclewiki" }, { exerciseId: "" }, { exerciseId: "x".repeat(161) }, { exerciseId: 42 },
    { source: "custom", exerciseId: "not-a-uuid" }, { name: " " }, { name: "x".repeat(121) },
    { primaryMuscles: [null] }, { primaryMuscles: ["x".repeat(101)] }, { primaryMuscles: Array(11).fill("biceps") },
    { instructions: ["x".repeat(3001)] }, { instructions: Array(31).fill("step") }, { instructions: ["x".repeat(3000), "x".repeat(3000), "x"] },
    { sets: 0 }, { sets: 11 }, { sets: 1.5 }, { reps: "0" }, { reps: "101" }, { reps: "30s" },
    { reps: "01" }, { reps: 12 }, { restSeconds: -1 }, { restSeconds: 601 }, { restSeconds: 1.5 },
  ]) assert.equal(s.addDayExercise("a", { ...prescription, ...patch }), null);
  for (const dayId of ["", "e", "aa", "a\n"]) assert.equal(s.addDayExercise(dayId, prescription), null);
  assert.equal(s.addDayExercise("a", null), null);
  assert.equal(calls.length, 0);
  const added = s.addDayExercise("a", prescription);
  await s.flushChanges();
  s.lockAccountEdits(true);
  assert.equal(s.addDayExercise("b", prescription), null);
  s.removeDayExercise("a", added.key);
  assert.deepEqual(s.getState().dayExercises.a, [added]);
  assert.equal(calls.length, 1);
  s.lockAccountEdits(false);
  for (let id = 1; id <= 29; id++) assert(s.addDayExercise("a", { ...prescription, exerciseId: `exercise-${id}` }));
  assert.equal(s.getState().dayExercises.a.length, 30);
  assert.equal(s.addDayExercise("a", { ...prescription, exerciseId: "exercise-99" }), null);
  assert.equal(s.addDayExercise("a", prescription), added);
  await s.flushChanges();
});

test("added key generation retries collisions with authored plan and live draft keys", async () => {
  const first = webcrypto.randomUUID();
  const second = webcrypto.randomUUID();
  const third = webcrypto.randomUUID();
  const ids = [first, second, third];
  const s = store({
    readAccount: async () => ({ ...empty(),
      trainingPlans: [{ blocks: [{ exercises: [{ order: `ex-${first}` }] }] }],
      drafts: { a: { startedAt: 1, exercises: { [`ex-${second}`]: {} } } },
    }),
    writeChange: async () => {},
  }, { randomUUID: () => ids.shift() ?? webcrypto.randomUUID() });
  await s.loadAccount("A");
  assert.equal(s.addDayExercise("a", prescription).key, `ex-${third}`);
  await s.flushChanges();
});

test("failed writes remain explicitly unsaved and retry in order", async () => {
  let fail = true;
  const calls = [];
  const s = store({ readAccount: async () => empty(), writeChange: async (_, change) => {
    if (fail) throw new Error("Database unavailable");
    calls.push(change);
  } });
  await s.loadAccount("A");
  s.setBodyUnit("lb");
  assert.equal(await s.flushChanges(), false);
  assert.equal(s.getPersistenceStatus().phase, "error");
  assert.equal(s.getPersistenceStatus().unsaved, 1);
  s.setKeepScreenOn(true);
  assert.equal(s.getPersistenceStatus().unsaved, 2);
  fail = false;
  assert.equal(await s.flushChanges(), true);
  assert.deepEqual(calls.map((c) => c.type), ["bodyUnit", "keepScreenOn"]);
});

test("late loads and writes from another account cannot alter the current account", async () => {
  const oldLoad = deferred();
  const oldWrite = deferred();
  const s = store({ readAccount: (user) => user === "A" ? oldLoad.promise : Promise.resolve({ ...empty(), bodyUnit: "lb" }), writeChange: async () => oldWrite.promise });
  const a = s.loadAccount("A");
  await s.loadAccount("B");
  oldLoad.resolve({ ...empty(), keepScreenOn: true });
  await a;
  assert.equal(s.getPersistenceStatus().userId, "B");
  assert.equal(s.getState().keepScreenOn, false);
  s.setBodyUnit("kg");
  const writing = s.flushChanges();
  s.resetAccount();
  await s.loadAccount("C");
  oldWrite.resolve();
  assert.equal(await writing, false);
  assert.equal(s.getPersistenceStatus().userId, "C");
  assert.equal(s.getState().bodyUnit, "lb");
  assert.equal(s.getPersistenceStatus().unsaved, 0);
});

test("account mutation lock prevents new writes while signing out", async () => {
  const s = store({ readAccount: async () => empty(), writeChange: async () => assert.fail("locked edits must not write") });
  await s.loadAccount("A");
  s.lockAccountEdits(true);
  s.setBodyUnit("lb");
  s.setRestTimer({ endAt: 5, total: 4, label: "Press" });
  assert.equal(s.getState().bodyUnit, "kg");
  assert.equal(s.getState().timer, null);
  assert.equal(s.getPersistenceStatus().unsaved, 0);
  s.resetAccount();
  assert.equal(s.getPersistenceStatus().locked, false);
});

test("load failures block editing; reset removes all account-scoped state", async () => {
  const s = store({ readAccount: async () => { throw new Error("Schema missing"); }, writeChange: async () => assert.fail("must not write before load") });
  await assert.rejects(s.loadAccount("A"), /Schema missing/);
  s.setBodyUnit("lb");
  assert.equal(s.getPersistenceStatus().ready, false);
  assert.equal(s.getState().bodyUnit, "kg");
  assert.equal(await s.flushChanges(), true);
  s.resetAccount();
  assert.deepEqual(s.getState(), empty());
  assert.equal(s.getPersistenceStatus().userId, null);
});


test("custom exercise metadata is trimmed, saved and reloads from database", async () => {
  let saved = empty();
  const s = store({
    readAccount: async () => structuredClone(saved),
    writeChange: async (_, change) => { if (change.type === "dayExercises") saved.dayExercises[change.payload.dayId] = structuredClone(change.payload.exercises); },
  });
  await s.loadAccount("A");
  const custom = s.addDayExercise("a", { ...prescription, source: "custom", exerciseId: webcrypto.randomUUID(), name: "  My gym machine  ", equipment: "  machine  ", primaryMuscles: [" back "], instructions: [" Pull slowly ", ""] });
  assert.equal(custom.name, "My gym machine");
  assert.deepEqual(custom.instructions, ["Pull slowly"]);
  await s.flushChanges();
  await s.loadAccount("A");
  assert.deepEqual(s.getState().dayExercises.a, [custom]);
});

test("every bundled exercise can be persisted without truncating instructions", async () => {
  const catalog = JSON.parse(await readFile(new URL("../src/data/exercises.json", import.meta.url), "utf8"));
  for (const definition of catalog) {
    const s = store({ readAccount: async () => empty(), writeChange: async () => {} });
    await s.loadAccount("A");
    const { level, id, ...text } = definition;
    const added = s.addDayExercise("a", { ...text, source: "free-exercise-db", exerciseId: id, sets: 2, reps: "10", restSeconds: 90 });
    assert(added, `Rejected catalog exercise: ${id}`);
    assert.deepEqual(added.instructions, definition.instructions);
    assert.equal(await s.flushChanges(), true);
  }
});

test("legacy provider entries remain removable without contacting the old provider", async () => {
  const legacy = { key: `mw-${webcrypto.randomUUID()}`, musclewikiId: 42, sets: 2, reps: "10", restSeconds: 90 };
  const s = store({ readAccount: async () => ({ ...empty(), dayExercises: { a: [legacy] } }), writeChange: async () => {} });
  await s.loadAccount("A");
  assert.deepEqual(s.getState().dayExercises.a, [legacy]);
  assert(s.addDayExercise("a", prescription));
  s.removeDayExercise("a", legacy.key);
  await s.flushChanges();
  assert.equal(s.getState().dayExercises.a.length, 1);
  assert.equal(s.getState().dayExercises.a[0].source, "free-exercise-db");
});


test("body weights validate account, value and date, then persist and reload by user", async () => {
  const accounts = new Map();
  const calls = [];
  const s = store({
    readAccount: async (user) => structuredClone(accounts.get(user) ?? empty()),
    writeChange: async (user, change) => {
      calls.push(change);
      const account = accounts.get(user) ?? empty();
      if (change.type === "weight") account.bodyWeights = [...account.bodyWeights.filter((entry) => entry.date !== change.payload.date), structuredClone(change.payload)];
      accounts.set(user, account);
    },
  });
  const entry = { date: "2026/01/21", weight: 70.25, unit: "kg" };
  assert.equal(s.saveBodyWeight(entry), false);
  await s.loadAccount("A");
  for (const patch of [{ weight: NaN }, { weight: Infinity }, { weight: 0 }, { weight: -1 }, { weight: 1000 }, { weight: "70" }, { unit: "oz" }, { date: "2026/02/30" }, { date: "2026/13/01" }, { date: "2999/01/01" }, { date: "invalid" }]) {
    assert.equal(s.saveBodyWeight({ ...entry, ...patch }), false);
  }
  assert.equal(calls.length, 0);
  s.lockAccountEdits(true);
  assert.equal(s.saveBodyWeight(entry), false);
  s.lockAccountEdits(false);
  assert.equal(s.saveBodyWeight(entry), true);
  assert.equal(await s.flushChanges(), true);
  await s.loadAccount("A");
  assert.deepEqual(s.getState().bodyWeights, [entry]);
  const updated = { ...entry, weight: 155.75, unit: "lb" };
  assert.equal(s.saveBodyWeight(updated), true);
  assert.equal(await s.flushChanges(), true);
  await s.loadAccount("A");
  assert.deepEqual(s.getState().bodyWeights, [updated]);
  await s.loadAccount("B");
  assert.deepEqual(s.getState().bodyWeights, []);
});
