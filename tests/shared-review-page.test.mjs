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

let rpcResult = { data: null, error: null };
let signedPaths = [];
const supabaseStub = {
  createClient: () => ({
    rpc: async (name, args) => {
      assert.equal(name, "get_shared_review");
      assert.ok(args.link_token);
      return rpcResult;
    },
    storage: {
      from: (bucket) => {
        assert.equal(bucket, "photos");
        return {
          createSignedUrls: async (paths) => {
            signedPaths = paths;
            return { data: paths.map((path) => ({ path, signedUrl: `https://signed.example/${path}` })) };
          },
        };
      },
    },
  }),
};

const lib = await load("../src/lib/shared-review.ts", { "@supabase/supabase-js": supabaseStub });
const view = await load("../src/components/SharedReviewView.tsx", {});

const TOKEN = "A".repeat(43);
const review = {
  version: 1,
  period: { kind: "month", label: "November 2026", start: "2026-11-01", end: "2026-11-30", days: 26 },
  expiresOn: null,
  activity: {
    averageSteps: 7860, averageSleepMinutes: 444, averageMoveKcal: 512, ringsClosedDays: 18,
    daily: [{ date: "2026-11-01", steps: 9000, moveKcal: 400, sleepMinutes: 450, ringsClosed: true },
      { date: "2026-11-02", steps: 5000, moveKcal: 300, sleepMinutes: null, ringsClosed: false }],
  },
  weight: {
    unit: "kg", count: 2, first: { date: "2026-11-02", value: 80.2 }, last: { date: "2026-11-26", value: 78.4 },
    change: -1.8, entries: [{ date: "2026-11-02", value: 80.2 }, { date: "2026-11-26", value: 78.4 }],
    firstPhoto: { path: "u/share-l-1.jpg", date: "2026-11-02" }, lastPhoto: null,
  },
  food: { daysWithPhotos: 1, photos: [{ path: "u/share-l-2.jpg", date: "2026-11-03", time: "07:45", label: "Breakfast" }] },
};

test("only well-formed tokens reach the database", async () => {
  process.env.NEXT_PUBLIC_SUPABASE_URL = "https://example.supabase.co";
  process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_test";
  for (const bad of ["", "short", "../../etc", "A".repeat(129), "has space".padEnd(40, "x")]) {
    assert.equal(lib.isWellFormedToken(bad), false);
    assert.equal(await lib.loadSharedLink(bad), null);
  }
  assert.equal(lib.isWellFormedToken(TOKEN), true);
});

test("an unknown, revoked or expired link loads as null", async () => {
  rpcResult = { data: null, error: null };
  assert.equal(await lib.loadSharedLink(TOKEN), null);
});

test("a link loads its review and signs only its own photos", async () => {
  rpcResult = { data: { summary: review, expires_at: null, created_at: "2026-11-26T10:00:00Z" }, error: null };
  const link = await lib.loadSharedLink(TOKEN);
  assert.deepEqual(signedPaths, ["u/share-l-1.jpg", "u/share-l-2.jpg"]);
  assert.equal(link.photoUrls["u/share-l-2.jpg"], "https://signed.example/u/share-l-2.jpg");

  const html = renderToStaticMarkup(React.createElement(view.SharedReviewView, { link }));
  assert.match(html, /November 2026/);
  assert.match(html, /7,860/);
  assert.match(html, /7 hr 24 min/);
  assert.match(html, /18 of 26/);
  assert.match(html, /80\.2 → 78\.4 kg · 2 weigh-ins/);
  assert.match(html, /−1\.8 kg/);
  assert.match(html, /First · 2 Nov/);
  assert.match(html, /07:45 · Breakfast/);
  assert.match(html, /src="https:\/\/signed\.example\/u\/share-l-1\.jpg"/);
});

test("sections left out of the link are not shown", () => {
  const link = { review: { ...review, weight: null, food: null, expiresOn: "2026-12-31" }, expiresAt: "2026-12-31T16:00:00Z", createdAt: "", photoUrls: {} };
  const html = renderToStaticMarkup(React.createElement(view.SharedReviewView, { link }));
  assert.match(html, /Activity/);
  assert.doesNotMatch(html, />Weight</);
  assert.doesNotMatch(html, />Food</);
  assert.match(html, /link works until 31 Dec 2026/);
});

test("the unavailable page explains what happened", () => {
  const html = renderToStaticMarkup(React.createElement(view.SharedLinkUnavailable));
  assert.match(html, /no longer available/);
});

test("trainer pages skip sign-in and the tab bar", async () => {
  let pathname = "/share/abc";
  const { BottomNav } = await load("../src/components/BottomNav.tsx", {
    "next/link": { default: ({ children, ...props }) => React.createElement("a", props, children) },
    "next/navigation": { usePathname: () => pathname },
  });
  assert.equal(renderToStaticMarkup(React.createElement(BottomNav)), "");
  pathname = "/";
  assert.notEqual(renderToStaticMarkup(React.createElement(BottomNav)), "");

  const source = await readFile(new URL("../src/components/AuthGate.tsx", import.meta.url), "utf8");
  const match = source.match(/return \/(.+)\/\.test\(pathname \?\? ""\)/);
  assert.ok(match, "AuthGate has a public path pattern");
  const isPublic = new RegExp(match[1]);
  assert.equal(isPublic.test("/share/abc"), true);
  for (const path of ["/", "/share", "/share/", "/share/a/b", "/account", "/weight"]) {
    assert.equal(isPublic.test(path), false, path);
  }
});
