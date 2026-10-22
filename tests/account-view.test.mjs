import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
const source = ts.transpileModule(await readFile(new URL("../src/components/AccountView.tsx", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
function account(session = { user: { id: "A", email: "person@example.com" } }) {
  let cursor = 0;
  const state = [];
  const calls = [];
  const exports = {};
  new Function("exports", "require", source)(exports, (name) => {
    if (name === "react") return {
      useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], (value) => { state[i] = value; }]; },
    };
    if (name === "@/lib/auth") return { useAccountSession: () => session, signOut: () => calls.push("signOut") };
    if (name === "@/lib/supabase") return { databaseConfigured: true };
    if (name === "@/components/DeleteAccountDialog") return { DeleteAccountDialog: "delete-dialog" };
    if (name === "next/link") return { __esModule: true, default: "a" };
    return require(name);
  });
  const render = () => { cursor = 0; return exports.AccountView(); };
  const nodes = (node) => !node || typeof node !== "object" ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
  const text = (node) => node == null || typeof node === "boolean" ? "" : Array.isArray(node) ? node.map(text).join(" ") : typeof node === "object" ? text(node.props?.children) : String(node);
  return { render, nodes, text, calls };
}

test("account removal is collapsed by default and no longer competes with normal actions", () => {
  const app = account();
  const nodes = app.nodes(app.render());
  const details = nodes.find((node) => node.type === "details");
  assert(details);
  assert.equal(details.props.open, undefined);
  assert.equal(app.text(app.nodes(details).find((node) => node.type === "summary")).trim(), "Account removal");
  const deletion = nodes.find((node) => node.type === "button" && app.text(node) === "Delete account…");
  assert(app.nodes(details).includes(deletion), "Destructive action belongs inside the collapsed disclosure");
  assert(!deletion.props.className.includes("w-full"));
  assert.equal(deletion.props["aria-haspopup"], "dialog");
  assert.match(app.text(details), /This cannot be undone/);
  const back = nodes.find((node) => node.type === "a" && node.props.href === "/");
  assert.equal(app.text(back), "Back to plan");
  assert.match(back.props.className, /bg-\[var\(--accent\)\]/);
  const signout = nodes.find((node) => node.type === "button" && app.text(node) === "Sign out");
  assert.doesNotMatch(signout.props.className, /red-/);
  assert.match(app.text(app.render()), /Signing out keeps your saved data/);
  assert.equal(nodes.find((node) => node.type === "delete-dialog"), undefined);
  assert.deepEqual(app.calls, []);
});

test("account removal still opens the confirmation dialog without deleting anything", () => {
  const app = account();
  app.nodes(app.render()).find((node) => node.type === "button" && app.text(node) === "Delete account…").props.onClick();
  const dialog = app.nodes(app.render()).find((node) => node.type === "delete-dialog");
  assert.equal(dialog.props.userId, "A");
  assert.equal(dialog.props.email, "person@example.com");
  assert.deepEqual(app.calls, []);
  dialog.props.onClose();
  assert.equal(app.nodes(app.render()).find((node) => node.type === "delete-dialog"), undefined);
});

test("signed-out account view never exposes account removal", () => {
  const app = account(null);
  assert.doesNotMatch(app.text(app.render()), /Account removal|Delete account/);
  assert.match(app.text(app.render()), /Sign in/);
});
