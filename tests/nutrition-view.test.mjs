import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { test } from "node:test";
import ts from "typescript";

const require = createRequire(import.meta.url);
const compile = async (path) => ts.transpileModule(await readFile(new URL(path, import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
const calculator = {};
new Function("exports", "require", await compile("../src/lib/nutrition.ts"))(calculator, require);
const viewSource = await compile("../src/components/NutritionView.tsx");

// Walk the React element tree without a browser or authenticated database.
// Keep calculator state across renders while allowing live store replacements.
function harness({ weights = [], unit = "kg", hydrated = true } = {}) {
  let cursor = 0;
  const state = [];
  const exports = {};
  new Function("exports", "require", viewSource)(exports, (name) => {
    if (name === "react") return {
      useState(initial) {
        const index = cursor++;
        if (!(index in state)) state[index] = typeof initial === "function" ? initial() : initial;
        return [state[index], (value) => { state[index] = value; }];
      },
    };
    if (name === "@/lib/nutrition") return calculator;
    if (name === "@/lib/store") return {
      useStore: () => ({ bodyWeights: weights, bodyUnit: unit }),
      useHydrated: () => hydrated,
      convertWeight: (weight, from, to) => from === to ? weight : from === "kg" ? weight * 2.20462 : weight / 2.20462,
    };
    if (name === "next/link") return { __esModule: true, default: "a" };
    return require(name);
  });
  const render = () => {
    cursor = 0;
    const element = exports.NutritionView();
    return element ? element.type(element.props) : null;
  };
  const nodes = (tree) => {
    if (tree == null || typeof tree !== "object") return [];
    if (Array.isArray(tree)) return tree.flatMap(nodes);
    return [tree, ...nodes(tree.props?.children)];
  };
  const text = (tree) => {
    if (tree == null || typeof tree === "boolean") return "";
    if (Array.isArray(tree)) return tree.map(text).join(" ");
    if (typeof tree === "object") return text(tree.props?.children);
    return String(tree);
  };
  const field = (id) => nodes(render()).find((node) => node.props?.id === id);
  const section = (id, tree = render()) => nodes(tree).find((node) => node.props?.["aria-labelledby"] === id);
  return {
    render, nodes, text, field, section,
    set: (id, value) => field(id).props.onChange({ target: { value } }),
    updateStore(next) {
      if ("weights" in next) weights = next.weights;
      if ("unit" in next) unit = next.unit;
      if ("hydrated" in next) hydrated = next.hydrated;
    },
  };
}

const loggedWeight = (weight, unit = "kg", date = "2026/10/21") => ({ date, weight, unit });
const withWeight = (weight = 70) => harness({ weights: [loggedWeight(weight)] });
const assertReadOnlyWeight = (app) => {
  assert(!app.nodes(app.render()).some((node) => node.type === "input" || node.type === "button" || node.type === "unit-select"));
  assert.equal(app.field("nutrition-weight"), undefined);
};

test("nutrition waits for account hydration before using the loaded log", () => {
  const app = harness({ weights: [loggedWeight(70)], hydrated: false });
  assert.equal(app.render(), null);
  app.updateStore({ hydrated: true });
  assert.match(app.text(app.render()), /2,590/);
});

test("nutrition starts empty without a logged weight and links to Weight instead of accepting input", () => {
  const app = harness();
  assert.match(app.text(app.render()), /Log your weight to get started/);
  assert.match(app.text(app.render()), /No need to enter it twice/);
  assert(app.nodes(app.render()).some((node) => node.props?.href === "/weight" && /Log weight/.test(app.text(node))));
  assertReadOnlyWeight(app);
  assert.equal(app.field("nutrition-activity"), undefined);
  assert.equal(app.field("nutrition-goal"), undefined);
  assert.equal(app.section("nutrition-results"), undefined);
});

test("nutrition displays the latest loaded weight in the preferred unit but calculates from its exact stored value", () => {
  for (const [storedUnit, displayUnit, weight, display] of [
    ["kg", "lb", 70.049, /154\.4\s+lb/],
    ["lb", "kg", 70.049 * 2.20462, /70\s+kg/],
  ]) {
    const weights = Object.freeze([
      Object.freeze(loggedWeight(60, "kg", "2026/10/20")),
      Object.freeze(loggedWeight(weight, storedUnit)),
    ]);
    const app = harness({ weights, unit: displayUnit });
    assert.match(app.text(app.section("nutrition-weight-title")), display);
    assert.match(app.text(app.section("nutrition-weight-title")), /Logged\s+Oct 21, 2026\s+in Weight/);
    // Rounding the display before calculation would give different targets.
    assert.match(app.text(app.section("nutrition-workout-title")), /2,592/);
    assert.match(app.text(app.section("nutrition-rest-title")), /2,312/);
    assert(app.nodes(app.render()).some((node) => node.props?.href === "/weight" && /Update weight/.test(app.text(node))));
    assertReadOnlyWeight(app);
    assert.equal(weights.at(-1).weight, weight);
  }
});

test("nutrition follows live store entries and display-unit changes without resetting activity or goal", () => {
  const app = harness();
  app.updateStore({ weights: [loggedWeight(70)] });
  assert.match(app.text(app.render()), /2,590/);
  app.set("nutrition-activity", "high");
  app.set("nutrition-goal", "gain");
  app.updateStore({ weights: [loggedWeight(70), loggedWeight(80, "kg", "2026/10/22")] });
  assert.match(app.text(app.section("nutrition-weight-title")), /80\s+kg/);
  assert.match(app.text(app.section("nutrition-weight-title")), /Oct 22, 2026/);
  assert.match(app.text(app.section("nutrition-workout-title")), /3,696/);
  assert.match(app.text(app.section("nutrition-rest-title")), /3,344/);
  assert.equal(app.field("nutrition-activity").props.value, "high");
  assert.equal(app.field("nutrition-goal").props.value, "gain");
  app.updateStore({ unit: "lb" });
  assert.match(app.text(app.section("nutrition-weight-title")), /176\.4\s+lb/);
  assert.match(app.text(app.section("nutrition-workout-title")), /3,696/);
  app.updateStore({ weights: [] });
  assert.match(app.text(app.render()), /Log your weight to get started/);
  assert.equal(app.section("nutrition-results"), undefined);
});

test("nutrition updates goal and activity targets immediately", () => {
  const app = withWeight();
  app.set("nutrition-goal", "lose");
  assert.match(app.text(app.section("nutrition-rest-title")), /1,964/);
  assert.match(app.text(app.section("nutrition-workout-title")), /2,202/);
  assert.match(app.text(app.render()), /15% calorie deficit/);
  app.set("nutrition-activity", "high");
  app.set("nutrition-goal", "gain");
  assert.match(app.text(app.section("nutrition-rest-title")), /2,926/);
  assert.match(app.text(app.section("nutrition-workout-title")), /3,234/);
  assert.match(app.text(app.render()), /10% calorie surplus/);
});

test("nutrition rejects nonfinite, negative and out-of-range logged weights with accessible guidance", () => {
  for (const value of [0, -70, NaN, Infinity, -Infinity, 34.9, 300.1]) {
    const app = withWeight(value);
    const status = app.nodes(app.render()).find((node) => node.props?.role === "status");
    assert(status, String(value));
    assert.match(app.text(status), /Unable to estimate from this weight/);
    assert.match(app.text(status), /35–300 kg/);
    assert(!app.nodes(app.render()).some((node) => node.type === "dl"));
    assert.equal(app.section("nutrition-results"), undefined);
    assert.equal(app.field("nutrition-activity"), undefined);
    assertReadOnlyWeight(app);
  }
});

test("nutrition accepts both adult-weight bounds in kg and equivalent lb", () => {
  for (const weight of [35, 300]) {
    for (const unit of ["kg", "lb"]) {
      const app = harness({ weights: [loggedWeight(unit === "kg" ? weight : weight * 2.20462, unit)] });
      assert(app.section("nutrition-results"));
      assert(!app.nodes(app.render()).some((node) => node.props?.role === "status"));
    }
  }
});

test("nutrition withholds all numerical targets for low calorie estimates", () => {
  const app = withWeight(35);
  assert.match(app.text(app.render()), /below 1,500 kcal/);
  assert(!app.nodes(app.render()).some((node) => node.type === "dl"));
  assert.doesNotMatch(app.text(app.render()), /Estimated maintenance:/);
  assert.doesNotMatch(app.text(app.section("nutrition-results")), /Calories \/ day/);
});

test("nutrition applies the low-calorie guardrail independently to each day", () => {
  const app = withWeight(50);
  app.set("nutrition-goal", "lose");
  const tree = app.render();
  const workout = app.section("nutrition-workout-title", tree);
  const rest = app.section("nutrition-rest-title", tree);
  assert.match(app.text(workout), /1,573/);
  assert(app.nodes(workout).some((node) => node.type === "dl"));
  assert.match(app.text(rest), /below 1,500 kcal/);
  assert(!app.nodes(rest).some((node) => node.type === "dl"));
  assert.doesNotMatch(app.text(rest), /Estimated maintenance:/);
  assert.doesNotMatch(app.text(rest), /1,403/);
});

test("nutrition holds recovery protein and fat steady while increasing workout carbs", () => {
  const app = withWeight();
  const tree = app.render();
  const workout = app.section("nutrition-workout-title", tree);
  const rest = app.section("nutrition-rest-title", tree);
  const macros = (section) => Object.fromEntries(app.nodes(section)
    .filter((node) => node.type === "div" && node.props?.children?.[0]?.type === "dt")
    .map((node) => [app.text(node.props.children[0]), Number(node.props.children[1].props.children[0])]));
  const w = macros(workout);
  const r = macros(rest);
  assert.equal(w.Protein, 126);
  assert.equal(w.Protein, r.Protein);
  assert.equal(w.Fat, r.Fat);
  assert(w.Carbs > r.Carbs);
  assert.match(app.text(tree), /Exclude your workout/);
  assert.match(app.text(tree), /not selected automatically/);
});

test("nutrition keeps control labels, help and read-only results accessible", () => {
  const app = withWeight();
  const nodes = app.nodes(app.render());
  for (const id of ["nutrition-activity", "nutrition-goal"]) {
    assert(nodes.some((node) => node.type === "label" && node.props.htmlFor === id));
    assert.equal(app.field(id).type, "select");
  }
  const describedBy = app.field("nutrition-activity").props["aria-describedby"];
  assert(nodes.some((node) => node.props?.id === describedBy));
  for (const section of nodes.filter((node) => node.type === "section")) {
    const labelledBy = section.props["aria-labelledby"];
    assert(labelledBy);
    assert(nodes.some((node) => node.props?.id === labelledBy));
  }
  const results = app.section("nutrition-results");
  assert.equal(results.props["aria-live"], "polite");
  assert.equal(results.props["aria-atomic"], "true");
  assertReadOnlyWeight(app);
});
