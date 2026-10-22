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

let pathname = "/";
const { BottomNav } = await load("../src/components/BottomNav.tsx", {
  "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) },
  "next/navigation": { usePathname: () => pathname },
});
test("navigation exposes Plan, Weight, Nutrition and Account without removed modules", () => {
  const html = renderToStaticMarkup(React.createElement(BottomNav));
  assert.deepEqual([...html.matchAll(/href="([^"]+)"/g)].map((match) => match[1]), ["/", "/weight", "/nutrition", "/account"]);
  assert.doesNotMatch(html, />Log<|>Notes</);
});

test("each route highlights only its own bottom tab", () => {
  for (const [route, expected] of [["/", "/"], ["/day/a", "/"], ["/weight", "/weight"], ["/nutrition", "/nutrition"], ["/account", "/account"], ["/account/settings", "/account"]]) {
    pathname = route;
    const html = renderToStaticMarkup(React.createElement(BottomNav));
    const activeLinks = [...html.matchAll(/<a\b[^>]*>/g)].map((match) => match[0]).filter((tag) => tag.includes('aria-current="page"'));
    assert.equal(activeLinks.length, 1, route);
    assert.ok(activeLinks[0].includes(`href="${expected}"`), route);
  }
  pathname = "/";
});

test("Plan no longer contains a separate Account shortcut", async () => {
  const { default: Home } = await load("../src/app/page.tsx", {
    "@/components/NextUp": { NextUp: () => null },
    "@/components/TrainingPlans": { TrainingPlans: () => null },
  });
  const html = renderToStaticMarkup(React.createElement(Home));
  assert.match(html, /Training Plan/);
  assert.doesNotMatch(html, /href="\/account"|>Account</);
});

test("removed standalone modules have no route or component", async () => {
  for (const relative of ["../src/app/log/page.tsx", "../src/components/LogView.tsx", "../src/app/notes/page.tsx", "../src/lib/notes.ts"]) {
    await assert.rejects(access(new URL(relative, import.meta.url)), { code: "ENOENT" });
  }
});

for (const saved of [true, false]) {
  test(`finishing a workout ${saved ? "returns to Plan after confirmation" : "stays put when saving fails"}`, async () => {
    const calls = [];
    let confirmSave;
    const confirmation = new Promise((resolve) => { confirmSave = resolve; });
    const { Workout } = await load("../src/components/Workout.tsx", {
      react: { ...React, useState: (initial) => [initial, () => {}] },
      "next/navigation": { useRouter: () => ({ push: (path) => calls.push(["push", path]) }) },
      "@/lib/plan": {},
      "@/lib/store": {
        useStore: () => ({ log: [], dayExercises: {}, drafts: { a: { exercises: {
          "1": { sets: [{ weight: "40", reps: "10", done: true }], notes: "Good form", useSub: false },
        } } } }),
        todayString: () => "2026/10/21",
        hasLoggedValue: ({ weight, reps }) => !!(weight || reps),
        unitFor: () => "kg",
        finishDraft: (dayId, entries) => calls.push(["finish", dayId, entries]),
        flushChanges: () => confirmation,
      },
      ...Object.fromEntries(["RestTimer", "UnitSelect", "VideoModal", "WeightSelect", "ExercisePicker", "AddedExerciseCard"].map((name) => [`@/components/${name}`, {}])),
    });
    const tree = Workout({ day: { id: "a", name: "Push", blocks: [{ exercises: [{ order: "1", name: "Bench press", sets: 2, substitute: "" }] }] } });
    function findFinish(node) {
      if (!React.isValidElement(node)) return undefined;
      if (node.type === "button" && node.props.children === "Finish & save workout") return node;
      return React.Children.toArray(node.props.children).map(findFinish).find(Boolean);
    }
    const finish = findFinish(tree);
    assert.ok(finish);
    finish.props.onClick();
    assert.equal(calls.length, 1, "must save history before navigating");
    assert.equal(calls[0][0], "finish");
    assert.equal(calls[0][1], "a");
    assert.deepEqual(calls[0][2][0].sets, [{ weight: "40", reps: "10" }]);
    assert.equal(calls[0][2][0].notes, "Good form");
    confirmSave(saved);
    await confirmation;
    assert.deepEqual(calls.slice(1), saved ? [["push", "/"]] : []);
  });
}
