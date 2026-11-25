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
const unexpectedSideEffect = () => assert.fail("Static rendering must not change the store");
const Link = ({ children, href, ...props }) => React.createElement("a", { href, ...props }, children);
const ui = load(transpile(await readFile(new URL("../src/components/ui.tsx", import.meta.url), "utf8"), "ui.tsx"), {
  "react/jsx-runtime": require("react/jsx-runtime"),
  "next/link": { default: Link },
});
const store = {
  useStore: () => ({ defaultUnit: "kg", units: {}, drafts: {}, log: [] }),
  usePlan: () => ({ days: plan.DAYS, weekdays: [1, 3, 5] }),
  useHydrated: () => true,
  emptyExercise,
  unitFor: (state, name) => state.units[name] ?? state.defaultUnit,
  historyFor: (log, name) => log.filter((entry) => entry.exercise === name && !entry.substitute),
  draftPhotos: () => [],
  updateDraftExercise: unexpectedSideEffect,
  setExerciseUnit: unexpectedSideEffect,
  discardDraft: unexpectedSideEffect,
  flushChanges: unexpectedSideEffect,
};
const workoutSource = await readFile(new URL("../src/components/Workout.tsx", import.meta.url), "utf8");
const workout = load(transpile(workoutSource, "Workout.tsx"), {
  react: React,
  "react/jsx-runtime": require("react/jsx-runtime"),
  "next/link": { default: Link },
  "next/navigation": { useRouter: () => ({ push: unexpectedSideEffect }) },
  "@/lib/plan": plan,
  "@/lib/store": store,
  "@/lib/photos": { removePhotos: unexpectedSideEffect },
  "@/components/ui": ui,
  "@/components/WakeLock": { WakeLockToggle: () => null },
  "@/components/ConfirmDialog": { askConfirm: unexpectedSideEffect },
  "@/components/VideoModal": { VideoModal: () => assert.fail("Tutorial must not open during initial rendering") },
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
const all = plan.DAYS.flatMap((day) => day.blocks.flatMap((block) => block.exercises));
const find = (name) => all.find((entry) => entry.name === name);
// Wide Grip Lat Pulldown: 2 × 10, 8 / 5 / 3 warm-up, restart weight 40 kg.
const exercise = find("Wide Grip Lat Pulldown");
function render(overrides = {}) {
  return parse(renderToStaticMarkup(React.createElement(workout.ExerciseBlock, {
    dayId: "a", ex: exercise, draft: undefined, log: [], ...overrides,
  })));
}
const history = (overrides = {}) => ({
  id: "previous", date: "2026/10/19", dayId: "a", exercise: exercise.name,
  sets: [{ weight: "60", reps: "10" }, { weight: "60", reps: "10" }],
  unit: "kg", notes: "", savedAt: 1, ...overrides,
});
const entry = (weight = "", reps = "", done = false) => ({ weight, reps, done });
const input = (root, name) => byTag(root, "input").find((node) => (name instanceof RegExp ? name.test(accessibleName(root, node)) : accessibleName(root, node) === name));

test("a fresh exercise shows its target, warm-up row and restart weight as the suggestion", () => {
  const root = render();
  assert.equal(text(byTag(root, "h2")[0]), exercise.name);
  const visible = visibleText(root);
  assert.match(visible, /2 × 10/);
  assert.match(visible, /First time/);
  assert.match(visible, /Previous/);
  assert.match(visible, /20·27\.5·33\.75/, "8 / 5 / 3 warm-up at about 50 / 70 / 85% of 40 kg");
  assert.equal(input(root, "Set 1 weight in kg").attrs.placeholder, "40");
  assert.equal(input(root, "Set 1 reps").attrs.placeholder, "10");
  assert.equal(input(root, "Set 1 weight in kg").attrs.value, "", "Suggestions are placeholders, never silently filled");
  assert.equal(button(root, "Complete set 1").attrs["aria-pressed"], "false");
  assert.equal(button(root, "Complete set 2").attrs["aria-pressed"], "false");
  assert.ok(button(root, "Complete warm-up"));
  assert.equal(button(root, "Remove set 2"), undefined, "Planned sets cannot be removed");
  assert.ok(input(root, /Notes for/), "Notes need a label, not only a placeholder");
});

test("previous session sits next to each set and drives today's suggestion", () => {
  const root = render({ log: [history(), history({ id: "sub", substitute: exercise.substitute, sets: [entry("999", "99")] })] });
  const visible = visibleText(root);
  assert.match(visible, /60 × 10/);
  assert.doesNotMatch(visible, /999/, "Substitute history is not the original exercise's history");
  assert.match(visible, /↑ \+1\.25 kg/);
  assert.equal(input(root, "Set 1 weight in kg").attrs.placeholder, "61.25");
  const short = render({ log: [history({ sets: [{ weight: "60", reps: "10" }, { weight: "60", reps: "9" }] })] });
  assert.match(visibleText(short), /Hold/);
  assert.equal(input(short, "Set 1 weight in kg").attrs.placeholder, "60");
});

test("logged sets keep their values, completion state and notes", () => {
  const root = render({ draft: { ...emptyExercise(2), sets: [entry("40", "10", true), entry("42.5", "8")], notes: "Use straps" } });
  assert.equal(input(root, "Set 1 weight in kg").attrs.value, "40");
  assert.equal(input(root, "Set 2 reps").attrs.value, "8");
  assert.equal(button(root, "Undo set 1").attrs["aria-pressed"], "true");
  assert.equal(button(root, "Complete set 2").attrs["aria-pressed"], "false");
  assert.equal(input(root, /Notes for/).attrs.value, "Use straps");
  const done = render({ draft: { ...emptyExercise(3), sets: [entry("40", "10", true), entry("40", "10", true), entry("40", "10", true)] } });
  assert.match(visibleText(done), /Done/);
  assert.ok(button(done, "Remove set 3"), "Extra sets can be removed");
});

test("using the substitute names it and hides the original exercise's history", () => {
  const root = render({ draft: { ...emptyExercise(2), useSub: true }, log: [history()] });
  assert.equal(text(byTag(root, "h2")[0]), exercise.substitute);
  assert.match(visibleText(root), new RegExp(`Instead of ${exercise.name}`));
  assert.doesNotMatch(visibleText(root), /60 × 10/);
  assert.equal(button(root, new RegExp(`Back to ${exercise.name}`)).attrs["aria-pressed"], "true");
});

test("timed exercises log seconds, and sheet notes stay visible", () => {
  const plank = render({ ex: find("Side Plank") });
  assert.match(visibleText(plank), /\bSec\b/);
  assert.ok(input(plank, "Set 1 seconds"));
  assert.equal(input(plank, "Set 1 seconds").attrs.placeholder, "30");
  const split = render({ ex: find("Split Squat") });
  assert.match(visibleText(split), /ankle hurts/);
  assert.match(visibleText(split), /10 per leg/);
  assert.ok(button(split, "Form & video"), "Form and tutorial stay one tap away");
});

test("suggestions follow the sheet: up after every rep, hold when short, back off under 8", () => {
  const ex = { reps: "10", sets: 2, startWeight: "40kg" };
  const s = (weight, ...reps) => ({ sets: reps.map((r) => ({ weight, reps: String(r) })), unit: "kg" });
  assert.deepEqual(plan.suggestFor(ex, []), { weight: "40", verdict: "first", label: "First time" });
  assert.deepEqual(plan.suggestFor(ex, [s("40", 10, 10)]), { weight: "41.25", verdict: "up", label: "↑ +1.25 kg" });
  assert.deepEqual(plan.suggestFor(ex, [s("40", 10, 9)]), { weight: "40", verdict: "hold", label: "Hold" });
  assert.deepEqual(plan.suggestFor(ex, [s("40", 10, 10), s("42.5", 7, 6)]), { weight: "40", verdict: "back", label: "↓ back to 40" });
  assert.equal(plan.suggestFor({ reps: "8", sets: 3, startWeight: "BW" }, []).weight, "BW");
  assert.deepEqual(plan.warmUpSets("8 / 5", 12.5), [{ weight: "6.25", reps: "8" }, { weight: "8.75", reps: "5" }]);
  assert.deepEqual(plan.warmUpSets("None", 50), []);
});

test("the rotation skips optional days and follows the plan's order", () => {
  const days = [{ id: "a", optional: false }, { id: "b", optional: false }, { id: "d", optional: true }];
  assert.equal(plan.nextPlanDay(days, []).id, "a");
  assert.equal(plan.nextPlanDay(days, [{ dayId: "a" }]).id, "b");
  assert.equal(plan.nextPlanDay(days, [{ dayId: "b" }, { dayId: "d" }]).id, "a", "An optional day doesn't move the rotation");
  assert.equal(plan.nextPlanDay(days, [{ dayId: "gone" }]).id, "a");
  assert.deepEqual(plan.relabel([{ id: "x", letter: "Z" }, { id: "y", letter: "Q" }]).map((d) => d.letter), ["A", "B"]);
});

test("progression advice follows the sheet: add after 10s, back off under 8 after an increase", () => {
  const ex = { reps: "10", sets: 2 };
  const s = (weight, ...reps) => ({ sets: reps.map((r) => ({ weight, reps: String(r) })) });
  assert.deepEqual(plan.progressionAdvice([s("40", 10, 10)], ex), { kind: "increase", target: 10, bodyweight: false });
  assert.equal(plan.progressionAdvice([s("40", 10, 9)], ex), null);
  assert.deepEqual(plan.progressionAdvice([s("40", 10, 10), s("42.5", 8, 7)], ex), { kind: "backOff", weight: "40" });
  assert.equal(plan.progressionAdvice([s("42.5", 7, 7)], ex), null, "No lighter session to go back to");
  assert.equal(plan.progressionAdvice([s("45", 10, 10), s("42.5", 7, 7)], ex), null, "Not after an increase");
  assert.deepEqual(plan.progressionAdvice([s("BW", 8, 8, 8)], { reps: "8", sets: 3 }), { kind: "increase", target: 8, bodyweight: true });
  assert.equal(plan.progressionAdvice([s("", 30, 30)], { reps: "30 sec per side", sets: 2 }), null);
});

test("plan matches the restart tab of the sheet", () => {
  assert.deepEqual(plan.DAYS.map((day) => `${day.letter} ${day.name}`), ["A Hinge Day", "B Squat Day", "C Back Day"]);
  const all = plan.DAYS.flatMap((day) => day.blocks.flatMap((block) => block.exercises));
  assert.equal(all.length, 20);
  assert.deepEqual(all.filter((e) => e.optional).map((e) => e.name), ["Dumbbell Lateral Raise", "Standing Calf Raise"]);
  const adductor = all[0];
  assert.deepEqual([adductor.name, adductor.sets, adductor.reps, adductor.startWeight], ["Adductor Machine", 1, "10", "50kg"]);
  for (const e of all) assert.ok(plan.restSeconds(e.rest), `${e.name} needs a rest the timer understands`);
  assert.equal(plan.nextDayId("c"), "a");
  assert.equal(plan.nextDayId("d"), "a", "Sessions from the old optional D day restart the rotation");
});
