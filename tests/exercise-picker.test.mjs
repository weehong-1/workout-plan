import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const require = createRequire(import.meta.url);
const source = ts.transpileModule(await readFile(new URL("../src/components/ExercisePicker.tsx", import.meta.url), "utf8"), {
  fileName: "ExercisePicker.tsx",
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
const exercise = {
  id: "rollout", name: "Barbell Ab Rollout - On Knees", equipment: "barbell",
  level: "expert", primaryMuscles: ["abdominals"],
  instructions: ["Kneel on the floor.", "Roll forward slowly."],
};

function harness(selection) {
  let cursor = 0;
  const states = ["", selection];
  const exports = {};
  new Function("exports", "require", "document", source)(exports, (name) => {
    if (name === "react") return {
      ...React,
      useEffect: () => {},
      useRef: (value) => ({ current: value }),
      useState: (initial) => {
        const index = cursor++;
        if (!(index in states)) states[index] = initial;
        return [states[index], (value) => { states[index] = value; }];
      },
    };
    if (name === "react/jsx-runtime") return require(name);
    if (name === "react-dom") return { createPortal: (children) => children };
    if (name === "next/link") return { default: ({ children, ...props }) => React.createElement("a", props, children) };
    if (name === "@/components/ExerciseSearch") return { ExerciseSearch: "exercise-search" };
    if (name === "@/lib/store") return { usePersistenceStatus: () => ({ ready: true, locked: false }) };
    throw new Error(`Unexpected import: ${name}`);
  }, { body: {} });
  return () => {
    cursor = 0;
    return exports.ExercisePicker({ dayId: "a", dayName: "Push Day", added: [], onClose: () => {} });
  };
}
function nodes(element) {
  if (Array.isArray(element)) return element.flatMap(nodes);
  if (!React.isValidElement(element)) return [];
  return [element, ...nodes(element.props.children)];
}

test("library instructions use a native disclosure collapsed by default", () => {
  const tree = harness({ kind: "library", exercise })();
  const details = nodes(tree).find((node) => node.type === "details");
  assert.ok(details);
  assert.equal(details.props.open, undefined);
  assert.equal(details.key, exercise.id, "a different exercise gets a fresh closed disclosure");
  const summary = nodes(details).find((node) => node.type === "summary");
  assert.match(renderToStaticMarkup(summary), /Exercise instructions/);
  const html = renderToStaticMarkup(tree);
  assert.match(html, /<details[^>]*><summary/);
  assert.doesNotMatch(html, /<details[^>]*\bopen(?:=|\s|>)/);
  for (const step of exercise.instructions) assert.ok(renderToStaticMarkup(details).includes(step));
});

test("exercise name, metadata, targets and add action stay outside the disclosure", () => {
  const tree = harness({ kind: "library", exercise })();
  const details = nodes(tree).find((node) => node.type === "details");
  const outside = nodes(tree).filter((node) => !nodes(details).includes(node));
  assert.ok(outside.some((node) => node.type === "h3" && node.props.children === exercise.name));
  assert.ok(outside.some((node) => node.type === "p" && String(node.props.children).includes("barbell")));
  assert.deepEqual(outside.filter((node) => ["Sets", "Reps", "Rest (sec)"].includes(node.props.label)).map((node) => node.props.label), ["Sets", "Reps", "Rest (sec)"]);
  assert.ok(outside.some((node) => node.type === "button" && node.props.children === "Add to this day" && !node.props.disabled));
});

test("missing instructions are explained within a closed disclosure", () => {
  const tree = harness({ kind: "library", exercise: { ...exercise, instructions: [] } })();
  const details = nodes(tree).find((node) => node.type === "details");
  assert.equal(details.props.open, undefined);
  assert.match(renderToStaticMarkup(details), /No instructions are available for this exercise/);
});

test("custom exercise instruction entry stays visible and editable", () => {
  const tree = harness({ kind: "custom" })();
  assert.equal(nodes(tree).filter((node) => node.type === "details").length, 0);
  assert.ok(nodes(tree).some((node) => node.type === "textarea" && node.props.placeholder === "One step per line"));
});
