import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
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
const Link = ({ children, ...props }) => React.createElement("a", props, children);

let pathname = "/";
const { BottomNav } = await load("../src/components/BottomNav.tsx", {
  "next/link": { default: Link },
  "next/navigation": { usePathname: () => pathname },
});

test("navigation has three jobs: Today, Progress and Body", () => {
  pathname = "/";
  const html = renderToStaticMarkup(React.createElement(BottomNav));
  assert.deepEqual([...html.matchAll(/href="([^"]+)"/g)].map((match) => match[1]), ["/", "/progress", "/weight"]);
  assert.match(html, />Today<.*>Progress<.*>Body</s);
});

test("each route highlights only its own tab", () => {
  for (const [route, expected] of [
    ["/", "/"], ["/day/a/save", "/"], ["/plan", "/"], ["/plan/new", "/"], ["/account", "/"],
    ["/progress", "/progress"], ["/progress/a/1", "/progress"], ["/weight", "/weight"], ["/nutrition", "/weight"],
  ]) {
    pathname = route;
    const html = renderToStaticMarkup(React.createElement(BottomNav));
    const activeLinks = [...html.matchAll(/<a\b[^>]*>/g)].map((match) => match[0]).filter((tag) => tag.includes('aria-current="page"'));
    assert.equal(activeLinks.length, 1, route);
    assert.ok(activeLinks[0].includes(`href="${expected}"`), route);
  }
  pathname = "/";
});

test("the workout screen hides the tab bar so logging has the whole screen", () => {
  pathname = "/day/a";
  assert.equal(renderToStaticMarkup(React.createElement(BottomNav)), "");
  pathname = "/";
});

test("removed standalone modules have no route or component", async () => {
  for (const relative of [
    "../src/app/log/page.tsx", "../src/components/LogView.tsx", "../src/app/notes/page.tsx", "../src/lib/notes.ts",
    "../src/components/RestTimer.tsx", "../src/components/NextUp.tsx", "../src/components/TrainingPlans.tsx",
  ]) {
    await assert.rejects(access(new URL(relative, import.meta.url)), { code: "ENOENT" });
  }
});

const plan = await load("../src/lib/plan.ts", {});
for (const saved of [true, false]) {
  test(`saving a workout ${saved ? "returns to Today after confirmation" : "stays put when saving fails"}`, async () => {
    const calls = [];
    let confirmSave;
    const confirmation = new Promise((resolve) => { confirmSave = resolve; });
    const day = { id: "a", name: "Push", blocks: [{ format: "Straight Sets", exercises: [{ order: "1", name: "Bench press", sets: 2, reps: "10", substitute: "" }] }] };
    const { SaveForm } = await load("../src/components/SaveWorkout.tsx", {
      react: { ...React, useState: (initial) => [initial, () => {}] },
      "next/link": { default: Link },
      "next/navigation": { useRouter: () => ({ push: (path) => calls.push(["push", path]) }) },
      "@/lib/plan": plan,
      "@/lib/photos": { removePhotos: () => assert.fail("Saving must not delete photos") },
      "@/components/PhotoStrip": { PhotoStrip: () => null },
      "@/components/ui": { VERDICT_TEXT: { up: "", hold: "", back: "", first: "" }, btnPrimary: "" },
      "@/components/Workout": { useNow: () => 0, workoutStats: () => ({ minutes: 42, volume: 400, done: 1, total: 2 }) },
      "@/lib/store": {
        useStore: () => ({ log: [], units: {}, defaultUnit: "kg", drafts: { a: { startedAt: 0, notes: "Felt strong", photos: [], exercises: {
          "1": { sets: [{ weight: "40", reps: "10", done: true }, { weight: "", reps: "", done: false }], notes: "Good form", useSub: false },
        } } } }),
        todayString: () => "2026/10/21",
        hasLoggedValue: ({ weight, reps }) => !!(weight || reps),
        historyFor: () => [],
        unitFor: () => "kg",
        updateDraftMeta: () => assert.fail("Rendering must not edit the draft"),
        discardDraft: () => assert.fail("Saving must not discard"),
        draftPhotos: () => [],
        getPersistenceStatus: () => ({ error: "Offline" }),
        finishDraft: (dayId, entries, meta) => calls.push(["finish", dayId, entries, meta]),
        flushChanges: () => confirmation,
      },
    });
    const tree = SaveForm({ day });
    const find = (node, label) => {
      if (!React.isValidElement(node)) return undefined;
      if (node.type === "button" && node.props.children === label) return node;
      return React.Children.toArray(node.props.children).map((child) => find(child, label)).find(Boolean);
    };
    const save = find(tree, "Save workout");
    assert.ok(save);
    void save.props.onClick();
    assert.equal(calls.length, 1, "must save history before navigating");
    const [, dayId, entries, meta] = calls[0];
    assert.equal(dayId, "a");
    assert.deepEqual(entries[0].sets, [{ weight: "40", reps: "10" }], "Only sets with values are saved");
    assert.equal(entries[0].notes, "Good form");
    assert.deepEqual(meta, { notes: "Felt strong", photos: [], durationMin: 42 });
    confirmSave(saved);
    await confirmation;
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(calls.slice(1), saved ? [["push", "/"]] : []);
  });
}
