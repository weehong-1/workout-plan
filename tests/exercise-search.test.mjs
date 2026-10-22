import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
function load(source, imports, fileName) {
  const exports = {};
  const compiled = ts.transpileModule(source, {
    fileName,
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  new Function("exports", "require", compiled)(exports, (name) => {
    assert.ok(Object.hasOwn(imports, name), `Unexpected import: ${name}`);
    return imports[name];
  });
  return exports;
}
const catalog = JSON.parse(await readFile(new URL("../src/data/exercises.json", import.meta.url), "utf8"));
const library = load(await readFile(new URL("../src/lib/exercise-library.ts", import.meta.url), "utf8"), { "@/data/exercises.json": catalog }, "exercise-library.ts");
const source = await readFile(new URL("../src/components/ExerciseSearch.tsx", import.meta.url), "utf8");

// Exercise the component's real handlers with a small hook host, without adding a DOM dependency.
function mount({ query = "squat", added = [] } = {}) {
  let cursor = 0;
  const state = [];
  const selected = [];
  let created = 0;
  const { ExerciseSearch } = load(source, {
    react: {
      useId: () => "search-test",
      useRef: () => ({ current: { focus() {} } }),
      useMemo: (fn) => fn(),
      useEffect: () => {},
      useState: (initial) => {
        const index = cursor++;
        if (!(index in state)) state[index] = initial;
        return [state[index], (value) => { state[index] = typeof value === "function" ? value(state[index]) : value; }];
      },
    },
    "react/jsx-runtime": require("react/jsx-runtime"),
    "@/lib/exercise-library": library,
  }, "ExerciseSearch.tsx");
  let tree;
  function render() {
    cursor = 0;
    tree = ExerciseSearch({ query, added, onQueryChange: (value) => { query = value; }, onSelect: (exercise) => selected.push(exercise), onCreate: () => { created++; } });
  }
  function all(node = tree) {
    if (!node || typeof node !== "object") return [];
    return [node, ...[node.props?.children].flat(Infinity).filter((child) => child != null).flatMap((child) => all(child))];
  }
  const input = () => all().find((node) => node.props?.role === "combobox");
  function key(key, composing = false) {
    const event = { key, nativeEvent: { isComposing: composing }, prevented: false, stopped: false,
      preventDefault() { this.prevented = true; }, stopPropagation() { this.stopped = true; } };
    input().props.onKeyDown(event);
    render();
    return event;
  }
  render();
  return { all, input, key, selected, get created() { return created; },
    focus() { input().props.onFocus(); render(); },
    change(value) { input().props.onChange({ target: { value } }); render(); },
    click(node) { node.props.onClick(); render(); },
    blur(inside) { tree.props.onBlur({ currentTarget: { contains: () => inside }, relatedTarget: {} }); render(); },
  };
}
const options = (ui) => ui.all().filter((node) => node.props?.role === "option");
const active = (ui) => options(ui).find((node) => node.props["aria-selected"]);

test("combobox exposes its label, listbox and bounded results panel", () => {
  const ui = mount();
  const input = ui.input();
  assert.equal(input.props["aria-expanded"], false);
  assert.equal(input.props["aria-autocomplete"], "list");
  assert.ok(ui.all().some((node) => node.type === "label" && node.props.htmlFor === input.props.id));
  ui.focus();
  assert.equal(ui.input().props["aria-expanded"], true);
  const list = ui.all().find((node) => node.props?.role === "listbox");
  assert.equal(ui.input().props["aria-controls"], list.props.id);
  assert.match(list.props.className, /max-h-.*overflow-y-auto/);
  assert.equal(options(ui).length, library.searchExercises("squat").length + 1);
});

test("arrow navigation skips added exercises and Enter selects a library exercise", () => {
  const results = library.searchExercises("squat");
  const ui = mount({ added: [{ source: "free-exercise-db", exerciseId: results[0].id }] });
  ui.focus();
  assert.equal(options(ui)[0].props["aria-disabled"], true);
  ui.click(options(ui)[0]);
  assert.equal(ui.selected.length, 0);
  assert.equal(ui.key("ArrowDown").prevented, true);
  assert.equal(active(ui).props.id, options(ui)[1].props.id);
  assert.equal(ui.input().props["aria-activedescendant"], active(ui).props.id);
  ui.key("Enter");
  assert.equal(ui.selected[0].id, results[1].id);
  assert.equal(ui.input().props["aria-expanded"], false);
});

test("custom creation is available for empty searches and no matches by mouse and keyboard", () => {
  const empty = mount({ query: "" });
  empty.focus();
  assert.equal(options(empty).length, 1);
  empty.click(options(empty)[0]);
  assert.equal(empty.created, 1);
  const missing = mount({ query: "zzzz-no-such-gym-machine" });
  missing.key("ArrowUp");
  missing.key("Enter");
  assert.equal(missing.created, 1);
});

test("Escape dismisses only the dropdown first; Tab and outside blur dismiss it", () => {
  const ui = mount();
  ui.focus();
  const first = ui.key("Escape");
  assert.ok(first.prevented && first.stopped, "First Escape must not reach the dialog");
  assert.equal(ui.input().props["aria-expanded"], false);
  const second = ui.key("Escape");
  assert.ok(!second.prevented && !second.stopped, "Second Escape can close the dialog");
  ui.focus();
  assert.equal(ui.key("Tab").prevented, false);
  assert.equal(ui.input().props["aria-expanded"], false);
  ui.focus();
  ui.blur(true);
  assert.equal(ui.input().props["aria-expanded"], true);
  ui.blur(false);
  assert.equal(ui.input().props["aria-expanded"], false);
});

test("changing or clearing a search resets navigation; IME Enter cannot select", () => {
  const ui = mount();
  ui.key("ArrowDown");
  ui.change("curl");
  assert.equal(ui.input().props["aria-activedescendant"], undefined);
  assert.equal(options(ui).length, library.searchExercises("curl").length + 1);
  ui.key("ArrowDown");
  assert.equal(ui.key("Enter", true).prevented, false);
  assert.equal(ui.selected.length, 0);
  ui.click(ui.all().find((node) => node.props?.["aria-label"] === "Clear exercise search"));
  assert.equal(ui.input().props.value, "");
  assert.equal(ui.input().props["aria-activedescendant"], undefined);
  assert.equal(options(ui).length, 1);
});

test("Home and End navigate the list, and mouse selection preserves exercise details", () => {
  const ui = mount();
  ui.key("ArrowDown");
  ui.key("End");
  assert.equal(active(ui).props.id, options(ui).at(-1).props.id);
  ui.key("Home");
  assert.equal(active(ui).props.id, options(ui)[0].props.id);
  ui.click(options(ui)[2]);
  assert.deepEqual(ui.selected[0], library.searchExercises("squat")[2]);
});
