import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import * as React from "react";
import ts from "typescript";

const require = createRequire(import.meta.url);
async function load(relative, imports) {
  const source = await readFile(new URL(relative, import.meta.url), "utf8");
  const compiled = ts.transpileModule(source, {
    fileName: relative,
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  const exports = {};
  new Function("exports", "require", compiled)(exports, (name) => {
    if (name === "react/jsx-runtime") return require(name);
    if (Object.hasOwn(imports, name)) return imports[name];
    throw new Error(`Unexpected import: ${name}`);
  });
  return exports;
}

const plan = await load("../src/lib/plan.ts", {});

test("no screen uses the browser's confirm(), which in-app browsers can silently block", async () => {
  const dir = new URL("../src/components/", import.meta.url);
  for (const file of await readdir(dir)) {
    if (!file.endsWith(".tsx")) continue;
    const code = (await readFile(new URL(file, dir), "utf8")).replace(/\/\/.*$/gm, "");
    assert.doesNotMatch(code, /(?<![\w.])confirm\(/, `${file} must use askConfirm()`);
  }
});

test("a new confirmation answers the one still open with no", async () => {
  const { askConfirm } = await load("../src/components/ConfirmDialog.tsx", { react: React });
  const first = askConfirm({ title: "First?", confirmLabel: "Yes" });
  void askConfirm({ title: "Second?", confirmLabel: "Yes" });
  assert.equal(await first, false);
});

test("exercise counts read naturally", () => {
  assert.equal(plan.exerciseCount(1), "1 exercise");
  assert.equal(plan.exerciseCount(0), "0 exercises");
  assert.equal(plan.exerciseCount(8), "8 exercises");
});

test("a saved workout's photos include every exercise's and the workout's own, once", async () => {
  const store = await load("../src/lib/store.ts", { react: React, "@/lib/database": {}, "@/lib/plan": plan });
  const state = {
    log: [
      { date: "2026/11/24", dayId: "a", savedAt: 5, photos: ["u/1.jpg"] },
      { date: "2026/11/24", dayId: "a", savedAt: 5, photos: ["u/2.jpg", "u/1.jpg"] },
      { date: "2026/11/24", dayId: "a", savedAt: 9, photos: ["u/other-save.jpg"] },
      { date: "2026/11/24", dayId: "b", savedAt: 5, photos: ["u/other-day.jpg"] },
    ],
    sessions: [{ dayId: "a", savedAt: 5, date: "2026/11/24", photos: ["u/session.jpg"], notes: "", durationMin: null }],
  };
  assert.deepEqual(store.sessionPhotos(state, "2026/11/24", "a", 5), ["u/1.jpg", "u/2.jpg", "u/session.jpg"]);
  assert.deepEqual(store.sessionPhotos(state, "2026/11/25", "a", 5), ["u/session.jpg"]);
});

for (const confirmed of [true, false]) {
  test(`deleting a workout from an exercise's history ${confirmed ? "removes the whole workout, then its photos" : "does nothing when cancelled"}`, async () => {
    const calls = [];
    const day = plan.DAYS[0];
    const ex = plan.dayExercises(day)[0];
    const entry = { id: "e1", date: "2026/11/24", dayId: day.id, savedAt: 7, exercise: ex.name, sets: [{ weight: "50", reps: "10" }], unit: "kg", notes: "", photos: ["u/a.jpg"] };
    const other = { ...entry, id: "e2", exercise: "Something else", photos: [] };
    const state = { log: [entry, other], sessions: [{ dayId: day.id, savedAt: 7, date: entry.date, photos: ["u/s.jpg"], notes: "", durationMin: 30 }] };
    const store = await load("../src/lib/store.ts", { react: React, "@/lib/database": {}, "@/lib/plan": plan });
    const { ExerciseDetail } = await load("../src/components/ExerciseDetail.tsx", {
      react: { ...React, useState: (initial) => [initial, () => {}] },
      "@/components/ProgressView": { reasonFor: () => "", sessionValue: () => 50 },
      "@/components/ui": { BackLink: () => null },
      "@/components/ConfirmDialog": { askConfirm: async (request) => { calls.push(["ask", request.title, request.body]); return confirmed; } },
      "@/lib/plan": plan,
      "@/lib/photos": { removePhotos: async (paths) => { calls.push(["removePhotos", paths]); } },
      "@/lib/store": {
        useStore: () => state,
        usePlan: () => ({ days: plan.DAYS, weekdays: [1, 3, 5] }),
        useHydrated: () => true,
        historyFor: (log, name) => log.filter((e) => e.exercise === name),
        formatSet: store.formatSet,
        sessionPhotos: store.sessionPhotos,
        deleteSession: (...args) => calls.push(["deleteSession", ...args]),
        flushChanges: async () => { calls.push(["flush"]); return true; },
      },
    });
    const tree = ExerciseDetail({ dayId: day.id, order: ex.order });
    const find = (node) => {
      if (!React.isValidElement(node)) return undefined;
      if (node.type === "button" && String(node.props["aria-label"]).startsWith("Delete the")) return node;
      return React.Children.toArray(node.props.children).map(find).find(Boolean);
    };
    const button = find(tree);
    assert.ok(button, "Each session row has a delete button");
    assert.equal(button.props["aria-label"], "Delete the Tue 24 Nov workout");
    await button.props.onClick();
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(calls[0], ["ask", `Delete the ${day.name} workout from Tue 24 Nov?`, "This also removes 1 other exercise and its photos. This can't be undone."]);
    assert.deepEqual(calls.slice(1), confirmed
      ? [["deleteSession", "2026/11/24", day.id, 7], ["flush"], ["removePhotos", ["u/a.jpg", "u/s.jpg"]]]
      : []);
  });
}
