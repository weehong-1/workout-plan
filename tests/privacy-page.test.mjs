import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
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

test("the privacy policy and trainer links open without an account", async () => {
  const { isPublicPath } = await load("../src/components/AuthGate.tsx", {
    react: React,
    "next/navigation": { usePathname: () => "/" },
    "@/components/AccountView": {},
    "@/lib/auth": {},
    "@/lib/store": {},
    "@/lib/supabase": {},
  });
  assert.equal(isPublicPath("/privacy"), true);
  assert.equal(isPublicPath("/privacy/"), true);
  assert.equal(isPublicPath("/share/abc123"), true);
  for (const path of ["/", "/weight", "/privacy/x", "/share/", "/privacy-x", null]) {
    assert.equal(isPublicPath(path), false, String(path));
  }
});

test("the privacy page names the operator, contact and WHOOP data use", async () => {
  const { default: PrivacyPolicy, metadata } = await load("../src/app/privacy/page.tsx", {});
  const html = renderToStaticMarkup(React.createElement(PrivacyPolicy));
  assert.equal(metadata.title, "Slopevo Privacy Policy");
  assert.match(html, /Vernon Koh, Singapore/);
  assert.match(html, /mailto:vernonweehongkoh\.developer@outlook\.com/);
  assert.match(html, /WHOOP data/);
  assert.match(html, /we do not sell your data/);
});
