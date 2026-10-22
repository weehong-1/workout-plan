import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const require = createRequire(import.meta.url);
function transpile(source, fileName) {
  return ts.transpileModule(source, {
    fileName,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX,
    },
  }).outputText;
}
function load(source, imports) {
  const exports = {};
  new Function("exports", "require", source)(exports, (name) => {
    if (Object.hasOwn(imports, name)) return imports[name];
    throw new Error(`Unexpected test import: ${name}`);
  });
  return exports;
}
const plan = load(transpile(await readFile(new URL("../src/lib/plan.ts", import.meta.url), "utf8"), "plan.ts"), {});
const emptyExercise = (sets) => ({
  sets: Array.from({ length: sets }, () => ({ weight: "", reps: "", done: false })),
  notes: "",
  useSub: false,
});
const unexpectedSideEffect = () => assert.fail("Static rendering must not mutate the store or start a timer");
const store = {
  useStore: () => ({ defaultUnit: "kg", units: {}, drafts: {}, log: [], dayExercises: {} }),
  emptyExercise,
  unitFor: (state, name) => state.units[name] ?? state.defaultUnit,
  lastEntryFor: (log, name) => log.findLast((entry) => entry.exercise === name && !entry.substitute),
  formatSet: ({ weight, reps }, unit) => `${weight}${/^\d+(\.\d+)?$/.test(weight) ? unit : ""}${weight && reps ? "×" : ""}${reps}`,
  updateDraftExercise: unexpectedSideEffect,
  setExerciseUnit: unexpectedSideEffect,
  discardDraft: unexpectedSideEffect,
  finishDraft: unexpectedSideEffect,
  flushChanges: unexpectedSideEffect,
  hasLoggedValue: ({ weight, reps }) => !!(weight.trim() || reps.trim()),
  todayString: () => "2026/10/21",
};
const workoutSource = await readFile(new URL("../src/components/Workout.tsx", import.meta.url), "utf8");
const { ExerciseCardForTest } = load(transpile(`${workoutSource}\nexport { ExerciseCard as ExerciseCardForTest };`, "Workout.tsx"), {
  react: React,
  "react/jsx-runtime": require("react/jsx-runtime"),
  "next/navigation": { useRouter: () => ({ push: unexpectedSideEffect }) },
  "@/lib/plan": plan,
  "@/lib/store": store,
  "@/components/RestTimer": { useRestTimer: () => ({ start: unexpectedSideEffect }) },
  // Preserve each child control's labeling/value contract without involving its UI implementation.
  "@/components/WeightSelect": { WeightSelect: ({ label, value, onChange }) => React.createElement("input", {
    "aria-label": label, value, onChange: (event) => onChange(event.target.value),
  }) },
  "@/components/UnitSelect": { UnitSelect: ({ value, onChange }) => React.createElement("select", {
    "aria-label": "Weight unit", value, onChange,
  }, React.createElement("option", { value: "kg" }, "kg"), React.createElement("option", { value: "lb" }, "lb")) },
  "@/components/VideoModal": { VideoModal: () => assert.fail("Tutorial must not open during initial rendering") },
  "@/components/ExercisePicker": { ExercisePicker: () => assert.fail("Only ExerciseCard is under test") },
  "@/components/AddedExerciseCard": { AddedExerciseCard: () => assert.fail("Only ExerciseCard is under test"), addedExerciseLogName: () => "" },
});

// A small reader for React's static HTML; no browser, server, or DOM dependency is needed.
const decode = (value) => value.replace(/&(#x[\da-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (_, entity) => {
  if (entity[0] === "#") return String.fromCodePoint(entity[1].toLowerCase() === "x" ? parseInt(entity.slice(2), 16) : Number(entity.slice(1)));
  return { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" }[entity];
});
function parse(html) {
  const root = { tag: "root", attrs: {}, children: [] };
  const stack = [root];
  for (const token of html.match(/<!--[\s\S]*?-->|<[^>]+>|[^<]+/g) ?? []) {
    if (token.startsWith("<!--")) continue;
    if (token.startsWith("</")) { stack.pop(); continue; }
    if (token.startsWith("<")) {
      const tag = token.match(/^<([\w-]+)/)?.[1];
      if (!tag) continue;
      const attrs = {};
      for (const match of token.slice(tag.length + 1, -1).matchAll(/([^\s=/>]+)(?:="([^"]*)")?/g)) attrs[match[1]] = decode(match[2] ?? "");
      const node = { tag, attrs, children: [], parent: stack.at(-1) };
      node.parent.children.push(node);
      if (!/^(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)$/.test(tag) && !token.endsWith("/>")) stack.push(node);
    } else stack.at(-1).children.push(decode(token));
  }
  return root;
}
const elements = (node) => node.children.flatMap((child) => typeof child === "string" ? [] : [child, ...elements(child)]);
const text = (node) => node.children.map((child) => typeof child === "string" ? child : text(child)).join(" ").replace(/\s+/g, " ").trim();
function visibleText(node) {
  // aria-hidden removes redundant labels from the accessibility tree, not from sight.
  if (node.tag === "details" || node.attrs.hidden !== undefined || /(?:^|\s)sr-only(?:\s|$)/.test(node.attrs.class ?? "")) return "";
  return node.children.map((child) => typeof child === "string" ? child : visibleText(child)).join(" ").replace(/\s+/g, " ").trim();
}
const byTag = (root, tag) => elements(root).filter((node) => node.tag === tag);
const inside = (node, ancestor) => { for (let at = node.parent; at; at = at.parent) if (at === ancestor) return true; return false; };
function accessibleName(root, node) {
  if (node.attrs["aria-label"]) return node.attrs["aria-label"];
  if (node.attrs["aria-labelledby"]) return node.attrs["aria-labelledby"].split(/\s+/).map((id) => text(elements(root).find((entry) => entry.attrs.id === id) ?? { children: [] })).join(" ");
  const label = byTag(root, "label").find((entry) => inside(node, entry) || (node.attrs.id && entry.attrs.for === node.attrs.id));
  return label ? text(label) : text(node);
}
const button = (root, name) => byTag(root, "button").find((node) => name instanceof RegExp ? name.test(accessibleName(root, node)) : accessibleName(root, node) === name);
function render(overrides = {}) {
  return parse(renderToStaticMarkup(React.createElement(ExerciseCardForTest, {
    dayId: "a", ex: exercise, draft: undefined, log: [], straightToNext: false, ...overrides,
  })));
}
const exercise = plan.DAYS[0].blocks[0].exercises[0];
const history = (overrides = {}) => ({
  id: "previous", date: "2026/10/19", dayId: "a", exercise: exercise.name,
  sets: [{ weight: "60", reps: "10" }, { weight: "62.5", reps: "10" }],
  unit: "kg", notes: "", savedAt: 1, ...overrides,
});
const draftWith = (sets, overrides = {}) => ({ ...emptyExercise(sets.length), sets, ...overrides });
const entry = (weight = "", reps = "", done = false) => ({ weight, reps, done });
function assertCount(root, completed, total) {
  assert.match(visibleText(root), new RegExp(`\\b${completed}\\s*/\\s*${total}\\b`), "Visible completed / total working-set count");
}
function assertCompletionButton(root, index, done) {
  const control = button(root, `${done ? "Undo" : "Complete"} set ${index}`);
  assert.ok(control, `Set ${index} needs an accessible ${done ? "undo" : "complete"} button`);
  assert.equal(control.attrs["aria-pressed"], String(done));
}

test("default exercise card names its article and keeps targets, rest and logging visible", () => {
  const root = render();
  const article = byTag(root, "article")[0];
  const heading = byTag(root, "h3")[0];
  assert.ok(article && heading);
  assert.equal(text(heading), exercise.name);
  assert.ok(heading.attrs.id, "Exercise heading needs an id");
  assert.ok(article.attrs["aria-labelledby"]?.split(/\s+/).includes(heading.attrs.id));
  assert.equal(accessibleName(root, article), exercise.name);
  const visible = visibleText(article);
  assert.match(visible, /2\s*×\s*10/);
  assert.ok(visible.includes(exercise.rest), "Rest must be visible without expanding form guidance");
  assert.match(visible, /\bWeight\b/);
  assert.match(visible, /\bReps\b/);
  assertCount(root, 0, 2);
  assertCompletionButton(root, 1, false);
  assertCompletionButton(root, 2, false);
  assert.equal(button(root, /taken\? Use/i).attrs["aria-pressed"], "false");
  const notes = byTag(root, "input").concat(byTag(root, "textarea")).find((node) => /notes/i.test(accessibleName(root, node)));
  assert.ok(notes, "Notes need an associated label, not only a placeholder");
  assert.equal(button(root, "Remove set 2"), undefined, "Planned sets cannot be removed");
});

test("form, warm-up and tutorial live inside a native details disclosure collapsed by default", () => {
  const root = render();
  const details = byTag(root, "details");
  assert.equal(details.length, 1);
  assert.equal(details[0].attrs.open, undefined, "Guidance starts collapsed");
  assert.equal(text(byTag(details[0], "summary")[0]), "Form & warm-up");
  assert.ok(text(details[0]).includes(exercise.cues));
  assert.ok(text(details[0]).includes(exercise.warmUp));
  assert.match(text(details[0]), /warm[ -]?up/i);
  assert.ok(button(details[0], /Tutorial/i));
  assert.ok(!visibleText(root).includes(exercise.cues), "Cues should not crowd the initial logging view");
});

test("partially completed draft preserves logged values, notes and individual completion states", () => {
  const root = render({ draft: draftWith([entry("60", "10", true), entry("62.5", "8")], { notes: "Keep heels planted" }) });
  assertCount(root, 1, 2);
  assertCompletionButton(root, 1, true);
  assertCompletionButton(root, 2, false);
  const controls = elements(root).filter((node) => ["input", "textarea"].includes(node.tag));
  assert.equal(controls.find((node) => accessibleName(root, node) === "Set 1 weight")?.attrs.value, "60");
  assert.equal(controls.find((node) => /set 2 reps/i.test(accessibleName(root, node)))?.attrs.value, "8");
  const notes = controls.find((node) => /notes/i.test(accessibleName(root, node)));
  assert.ok(notes);
  assert.equal(notes.tag === "textarea" ? text(notes) : notes.attrs.value, "Keep heels planted");
});

test("completed draft counts actual sets and leaves accessible undo controls", () => {
  const root = render({ draft: draftWith([entry("60", "10", true), entry("60", "10", true), entry("60", "10", true)]) });
  assertCount(root, 3, 3);
  for (let index = 1; index <= 3; index++) assertCompletionButton(root, index, true);
  assert.ok(button(root, "Remove set 3"), "An extra set can still be removed");
  assert.equal(button(root, "Complete set 1"), undefined);
});

test("superset handoff shows Next exercise and preserves the actual next-exercise instruction", () => {
  const ex = plan.DAYS[0].blocks[2].exercises[0];
  const root = render({ ex, straightToNext: true });
  assert.match(visibleText(root), /Next exercise/);
  assert.ok(visibleText(root).includes(ex.rest), "Do not replace the next exercise with an ambiguous 'None'");
});

test("previous history and earned progression remain next to logging, outside collapsed guidance", () => {
  const root = render({ log: [history(), history({ id: "sub-log", substitute: exercise.substitute, sets: [entry("999", "99")] })] });
  const details = byTag(root, "details")[0];
  const last = byTag(root, "p").find((node) => /Last(?: session)?(?: \(| · )2026\/10\/19/.test(text(node)));
  assert.ok(last, "Latest original-exercise history should be visible");
  assert.ok(!inside(last, details));
  const priorPerformance = text(last.parent);
  assert.match(priorPerformance, /60kg×10/);
  assert.match(priorPerformance, /62\.5kg×10/);
  assert.ok(!priorPerformance.includes("999"), "Substitute history must not be presented as original history");
  assert.match(visibleText(root), /You hit 10 on every set: add 2\.5~5 kg today\./);
  const nodes = elements(root);
  const firstWeight = nodes.find((node) => node.tag === "input" && accessibleName(root, node) === "Set 1 weight");
  assert.ok(nodes.indexOf(last) < nodes.indexOf(firstWeight), "Prior performance belongs before the set-entry rows");
  assert.ok(!nodes.slice(nodes.indexOf(last), nodes.indexOf(firstWeight)).some((node) => node.tag === "details"), "Guidance must not separate history from logging");
  assert.equal(firstWeight.attrs.value, "", "History must not silently prefill today's weight");
  const belowTarget = render({ log: [history({ sets: [{ weight: "60", reps: "10" }, { weight: "60", reps: "9" }] })] });
  assert.doesNotMatch(visibleText(belowTarget), /You hit .*every set: add/);
  const tooFewSets = render({ log: [history({ sets: [{ weight: "60", reps: "10" }] })] });
  assert.doesNotMatch(visibleText(tooFewSets), /You hit .*every set: add/);
});

test("substitute state names the substitute, hides original form/history and explains missing substitute guidance", () => {
  const root = render({ draft: { ...emptyExercise(exercise.sets), useSub: true }, log: [history()] });
  assert.equal(text(byTag(root, "h3")[0]), exercise.substitute);
  assert.equal(accessibleName(root, byTag(root, "article")[0]), exercise.substitute);
  assert.match(visibleText(root), new RegExp(`Substitute for ${exercise.name}`));
  assert.equal(button(root, new RegExp(`Back to ${exercise.name}`))?.attrs["aria-pressed"], "true");
  assert.ok(!text(root).includes(exercise.cues), "Original form cues are not instructions for the substitute");
  assert.doesNotMatch(text(root), /Last(?: session)?(?: \(| · )|You hit .*every set: add/);
  const guidance = text(byTag(root, "details")[0]);
  assert.match(guidance, /substitute/i);
  assert.match(guidance, /form|cues|guidance/i);
  assert.match(guidance, /not (?:available|provided|included)|no (?:specific |saved )?(?:form|cues|guidance)|(?:specific|different|own) (?:form|cues|guidance|technique)|original exercise[’']s form cues may not apply/i);
  assert.ok(button(root, /Tutorial/i), "Substitute tutorial stays available");
});

test("timed exercises label the working-set column and per-set input as seconds rather than reps", () => {
  const ex = plan.DAYS[3].blocks.at(-1).exercises[0];
  const root = render({ ex });
  assert.match(visibleText(root), /\bSeconds\b/);
  const timedInput = byTag(root, "input").find((node) => /set 1 seconds/i.test(accessibleName(root, node)));
  assert.ok(timedInput, "Timed duration needs a per-set seconds label");
  assert.ok(!byTag(root, "input").some((node) => /set 1 reps/i.test(accessibleName(root, node))));
  assert.match(visibleText(root), /2\s*×\s*30~40 sec/);
  assertCount(root, 0, 2);
});
