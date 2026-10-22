import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
const source = ts.transpileModule(await readFile(new URL("../src/components/BodyWeightView.tsx", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
function harness({ flush = async () => true, accept = true } = {}) {
  let cursor = 0;
  const state = [];
  const writes = [];
  const exports = {};
  const todayString = (date) => date ? `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}` : "2026/10/21";
  new Function("exports", "require", source)(exports, (name) => {
    if (name === "react") return {
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = typeof initial === "function" ? initial() : initial;
        return [state[index], (value) => { state[index] = value; }];
      },
      useRef(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = { current: initial };
        return state[index];
      },
    };
    if (name === "@/lib/store") return {
      useStore: () => ({ bodyWeights: [], bodyUnit: "kg" }), useHydrated: () => true, todayString,
      saveBodyWeight: (entry) => { if (accept) writes.push(entry); return accept; }, flushChanges: flush,
      getPersistenceStatus: () => ({ error: "Connection unavailable" }),
    };
    if (name === "@/components/UnitSelect") return { UnitSelect: "unit-select" };
    if (name === "@/components/WeightChart") return { WeightChart: "weight-chart", shortDate: () => "Oct 21" };
    return require(name);
  });
  const render = () => { cursor = 0; return exports.BodyWeightView(); };
  const nodes = (tree) => !tree || typeof tree !== "object" ? [] : Array.isArray(tree) ? tree.flatMap(nodes) : [tree, ...nodes(tree.props?.children)];
  const text = (tree) => tree == null || typeof tree === "boolean" ? "" : Array.isArray(tree) ? tree.map(text).join(" ") : typeof tree === "object" ? text(tree.props?.children) : String(tree);
  const input = () => nodes(render()).find((node) => node.type === "input" && node.props.inputMode === "decimal");
  const date = () => nodes(render()).find((node) => node.type === "input" && node.props.type === "date");
  const submit = () => nodes(render()).find((node) => node.type === "form").props.onSubmit({ preventDefault() {} });
  return { render, nodes, text, input, date, submit, writes, set: (value) => input().props.onChange({ target: { value } }) };
}
const settle = () => new Promise((resolve) => setImmediate(resolve));

test("body weight submits decimal weight, date and unit and confirms only after database acknowledgement", async () => {
  let confirm;
  const app = harness({ flush: () => new Promise((resolve) => { confirm = resolve; }) });
  app.set("70,25");
  app.submit();
  assert.deepEqual(app.writes, [{ date: "2026/10/21", weight: 70.25, unit: "kg" }]);
  assert.match(app.text(app.render()), /Saving your weight to the database/);
  assert.equal(app.input().props.value, "70.25");
  app.submit();
  assert.equal(app.writes.length, 1);
  confirm(true);
  await settle();
  assert.equal(app.input().props.value, "");
  assert.match(app.text(app.render()), /Weight saved to your account/);
});

test("body weight retains input on failed saves and offers a working retry", async () => {
  let success = false;
  const app = harness({ flush: async () => success });
  app.set("71.5");
  app.submit();
  await settle();
  assert.equal(app.input().props.value, "71.5");
  assert.match(app.text(app.render()), /Weight has not been saved to the database: Connection unavailable/);
  assert.match(app.text(app.render()), /Retry saving/);
  success = true;
  app.submit();
  await settle();
  assert.match(app.text(app.render()), /Weight saved to your account/);
  const blocked = harness({ accept: false });
  blocked.set("70");
  blocked.submit();
  await settle();
  assert.equal(blocked.writes.length, 0);
  assert.match(blocked.text(blocked.render()), /not ready to save/);
  assert.equal(blocked.input().props.value, "70");
});

test("body weight rejects malformed values, impossible dates and future dates without database writes", async () => {
  const app = harness();
  for (const value of ["", "70kg", "70.2.5", "1e2", "Infinity", "NaN", "-1", "0", "1000"]) {
    app.set(value);
    app.submit();
    await settle();
  }
  app.set("70");
  for (const value of ["", "2026-02-30", "2026-13-01", "2026-10-22"]) {
    app.date().props.onChange({ target: { value } });
    app.submit();
    await settle();
  }
  assert.equal(app.writes.length, 0);
});
