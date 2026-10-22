import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
const source = ts.transpileModule(await readFile(new URL("../src/components/DeleteAccountDialog.tsx", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
}).outputText;

function dialog(remove = async () => {}) {
  const state = [];
  let cursor = 0;
  let effect;
  const calls = [];
  const exports = {};
  new Function("exports", "require", "document", "HTMLElement", source)(exports, (name) => {
    if (name === "react") return {
      useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], (v) => { state[i] = v; }]; },
      useRef(initial) { const i = cursor++; if (!(i in state)) state[i] = { current: initial }; return state[i]; },
      useEffect(fn) { effect = fn; },
    };
    if (name === "react-dom") return { createPortal: (element) => element };
    if (name === "@/lib/auth") return { deleteAccount: async (...args) => { calls.push(args); return remove(); } };
    return require(name);
  }, { body: { style: { overflow: "" } }, activeElement: null }, class {});
  const render = () => { cursor = 0; return exports.DeleteAccountDialog({ userId: "A", email: "person@example.com", onClose: () => calls.push("close") }); };
  const nodes = (node) => !node || typeof node !== "object" ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
  const text = (node) => node == null || typeof node === "boolean" ? "" : Array.isArray(node) ? node.map(text).join(" ") : typeof node === "object" ? text(node.props?.children) : String(node);
  render();
  effect();
  return {
    render, nodes, text, calls,
    input: () => nodes(render()).find((node) => node.type === "input"),
    submit: () => nodes(render()).find((node) => node.type === "form").props.onSubmit({ preventDefault() {} }),
    button: (type) => nodes(render()).find((node) => node.type === "button" && node.props.type === type),
  };
}

test("delete dialog explains permanent data loss and requires exact DELETE", async () => {
  const app = dialog();
  const tree = app.render();
  assert.equal(tree.type, "dialog");
  assert.equal(tree.props["aria-modal"], "true");
  assert.match(app.text(tree), /person@example.com/);
  assert.match(app.text(tree), /This cannot be undone/);
  assert.match(app.text(tree), /Body-weight entries/);
  assert.equal(app.button("submit").props.disabled, true);
  await app.submit();
  assert.deepEqual(app.calls, []);
  for (const value of ["delete", "DELETE ", "DELET"]) {
    app.input().props.onChange({ target: { value } });
    assert.equal(app.button("submit").props.disabled, true);
    await app.submit();
  }
  assert.deepEqual(app.calls, []);
  app.input().props.onChange({ target: { value: "DELETE" } });
  assert.equal(app.button("submit").props.disabled, false);
  assert(app.nodes(app.render()).some((node) => node.type === "label" && node.props.htmlFor === app.input().props.id));
});

test("cancel never deletes; pending submission prevents dismissal and duplicates", async () => {
  let release;
  const app = dialog(() => new Promise((resolve) => { release = resolve; }));
  app.button("button").props.onClick();
  assert.deepEqual(app.calls, ["close"]);
  app.input().props.onChange({ target: { value: "DELETE" } });
  const pending = app.submit();
  assert.equal(app.button("submit").props.disabled, true);
  assert.equal(app.button("button").props.disabled, true);
  assert.equal(app.input().props.disabled, true);
  await app.submit();
  app.render().props.onCancel({ preventDefault() {} });
  assert.deepEqual(app.calls, ["close", ["A", "DELETE"]]);
  release();
  await pending;
});

test("failed deletion keeps confirmation open and offers retry without false success", async () => {
  let fail = true;
  const app = dialog(async () => { if (fail) throw new Error("Database unavailable"); });
  app.input().props.onChange({ target: { value: "DELETE" } });
  await app.submit();
  const alert = app.nodes(app.render()).find((node) => node.props?.role === "alert");
  assert.match(app.text(alert), /Database unavailable/);
  assert.equal(app.button("submit").props.disabled, false);
  assert.equal(app.input().props.value, "DELETE");
  fail = false;
  await app.submit();
  assert.deepEqual(app.calls, [["A", "DELETE"], ["A", "DELETE"]]);
});
