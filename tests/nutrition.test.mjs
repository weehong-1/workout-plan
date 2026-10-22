import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";

const source = ts.transpileModule(await readFile(new URL("../src/lib/nutrition.ts", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const exports = {};
new Function("exports", source)(exports);
const { ACTIVITY_LEVELS, NUTRITION_GOALS, NUTRITION_DAY_TYPES, WORKOUT_KCAL_PER_KG, calculateNutrition } = exports;
const LB_PER_KG = 2.20462;

function estimate(weight, unit = "kg", activity = "moderate", goal = "maintain", dayType = "rest") {
  const result = calculateNutrition(weight, unit, activity, goal, dayType);
  assert.notEqual(result, null);
  return result;
}

function approximately(actual, expected, tolerance = 1e-9) {
  assert(Math.abs(actual - expected) <= tolerance, `${actual} should be within ${tolerance} of ${expected}`);
}

test("activity, goal and day options expose stable IDs, labels and calorie rates", () => {
  assert.deepEqual(ACTIVITY_LEVELS.map(({ id, label, kcalPerKg }) => ({ id, label, kcalPerKg })), [
    { id: "light", label: "Lightly active", kcalPerKg: 28 },
    { id: "moderate", label: "Moderately active", kcalPerKg: 33 },
    { id: "high", label: "Very active", kcalPerKg: 38 },
  ]);
  assert.deepEqual(ACTIVITY_LEVELS.map(({ description }) => description), [
    "Mostly sitting with little walking.",
    "Regular walking or on your feet for part of the day.",
    "Physically demanding job or lots of daily movement.",
  ]);
  assert.deepEqual(NUTRITION_GOALS, [
    { id: "lose", label: "Lose weight" },
    { id: "maintain", label: "Maintain weight" },
    { id: "gain", label: "Gain muscle" },
  ]);
  assert.deepEqual(NUTRITION_DAY_TYPES, [
    { id: "workout", label: "Workout day" },
    { id: "rest", label: "Non-workout day" },
  ]);
  assert.equal(WORKOUT_KCAL_PER_KG, 4);
});

test("all 18 activity, goal and day combinations preserve rest baselines and add workout energy", () => {
  const expected = {
    rest: {
      light: { maintenance: 1960, lose: 1666, maintain: 1960, gain: 2156 },
      moderate: { maintenance: 2310, lose: 1964, maintain: 2310, gain: 2541 },
      high: { maintenance: 2660, lose: 2261, maintain: 2660, gain: 2926 },
    },
    workout: {
      light: { maintenance: 2240, lose: 1904, maintain: 2240, gain: 2464 },
      moderate: { maintenance: 2590, lose: 2202, maintain: 2590, gain: 2849 },
      high: { maintenance: 2940, lose: 2499, maintain: 2940, gain: 3234 },
    },
  };
  let combinations = 0;
  for (const { id: dayType } of NUTRITION_DAY_TYPES) {
    for (const { id: activity } of ACTIVITY_LEVELS) {
      for (const { id: goal } of NUTRITION_GOALS) {
        const result = estimate(70, "kg", activity, goal, dayType);
        const calories = expected[dayType][activity][goal];
        const proteinGrams = goal === "lose" ? 140 : 126;
        const fatGrams = Math.round(expected.rest[activity][goal] * 0.25 / 9);
        assert.deepEqual(result, {
          dayType,
          weightKg: 70,
          maintenanceCalories: expected[dayType][activity].maintenance,
          calories,
          proteinGrams,
          fatGrams,
          carbGrams: Math.round((calories - proteinGrams * 4 - fatGrams * 9) / 4),
          belowMinimum: false,
        });
        combinations++;
      }
    }
  }
  assert.equal(combinations, 18);
  const fractional = estimate(70.25, "kg", "light", "lose", "rest");
  assert.equal(fractional.maintenanceCalories, 1967);
  assert.equal(fractional.calories, 1672);
  const noIntermediateRounding = estimate(70.017, "kg", "light", "gain", "rest");
  assert.equal(noIntermediateRounding.maintenanceCalories, 1960);
  assert.equal(noIntermediateRounding.calories, 2157);
  const workoutFractional = estimate(70.0143, "kg", "light", "gain", "workout");
  assert.equal(workoutFractional.maintenanceCalories, 2240);
  assert.equal(workoutFractional.calories, 2465);
});

test("equivalent kg and lb physical masses produce the same rounded targets on both days", () => {
  for (const weight of [35, 35.01, 55.5, 70, 83.25, 100, 299.99, 300]) {
    for (const { id: dayType } of NUTRITION_DAY_TYPES) {
      for (const { id: activity } of ACTIVITY_LEVELS) {
        for (const { id: goal } of NUTRITION_GOALS) {
          const kg = estimate(weight, "kg", activity, goal, dayType);
          const lb = estimate(weight * LB_PER_KG, "lb", activity, goal, dayType);
          approximately(lb.weightKg, kg.weightKg);
          const { weightKg: _kg, ...kgTargets } = kg;
          const { weightKg: _lb, ...lbTargets } = lb;
          assert.deepEqual(lbTargets, kgTargets);
        }
      }
    }
  }
  for (const { id: dayType } of NUTRITION_DAY_TYPES) {
    assert.equal(estimate(154.3234, "lb", "moderate", "maintain", dayType).weightKg, 70);
  }
});

test("finite weights from 35 through 300 kg are accepted in both units without clamping", () => {
  for (const { id: dayType } of NUTRITION_DAY_TYPES) {
    for (const unit of ["kg", "lb"]) {
      const factor = unit === "lb" ? LB_PER_KG : 1;
      for (const weight of [35, 35.000001, 299.999999, 300]) {
        approximately(estimate(weight * factor, unit, "moderate", "maintain", dayType).weightKg, weight);
      }
      for (const weight of [34.999999, 300.000001, 1, 1000]) {
        assert.equal(calculateNutrition(weight * factor, unit, "moderate", "maintain", dayType), null);
      }
    }
    // Only tiny conversion noise is accepted; its value is retained, not clamped.
    const nearLower = estimate((35 - 1e-10) * LB_PER_KG, "lb", "moderate", "maintain", dayType);
    const nearUpper = estimate((300 + 1e-10) * LB_PER_KG, "lb", "moderate", "maintain", dayType);
    assert(nearLower.weightKg < 35);
    assert(nearUpper.weightKg > 300);
  }
});

test("invalid weights and runtime enum values return null instead of coercing inputs", () => {
  for (const { id: dayType } of NUTRITION_DAY_TYPES) {
    for (const weight of [NaN, Infinity, -Infinity, 0, -1, null, undefined, "70", "", true, {}, [], 70n]) {
      assert.equal(calculateNutrition(weight, "kg", "moderate", "maintain", dayType), null);
    }
    for (const unit of ["lbs", "KG", "", null, undefined, 1, {}, "toString", "__proto__"]) {
      assert.equal(calculateNutrition(70, unit, "moderate", "maintain", dayType), null);
    }
    for (const activity of ["sedentary", "MODERATE", "", null, undefined, 1, {}, "toString", "__proto__"]) {
      assert.equal(calculateNutrition(70, "kg", activity, "maintain", dayType), null);
    }
    for (const goal of ["loss", "MAINTAIN", "", null, undefined, 1, {}, "toString", "__proto__"]) {
      assert.equal(calculateNutrition(70, "kg", "moderate", goal, dayType), null);
    }
  }
  for (const dayType of ["training", "REST", "WORKOUT", "", null, undefined, 0, 1, true, {}, [], ["rest"], "toString", "__proto__"]) {
    assert.equal(calculateNutrition(70, "kg", "moderate", "maintain", dayType), null);
  }
});

test("workout targets add calories and carbs while keeping protein and fat equal to rest", () => {
  for (const weight of [35, 50, 70.25, 100, 300]) {
    for (const { id: activity } of ACTIVITY_LEVELS) {
      for (const { id: goal } of NUTRITION_GOALS) {
        const rest = estimate(weight, "kg", activity, goal, "rest");
        const workout = estimate(weight, "kg", activity, goal, "workout");
        assert(workout.maintenanceCalories > rest.maintenanceCalories);
        assert(workout.calories > rest.calories);
        assert(workout.carbGrams > rest.carbGrams);
        assert.equal(workout.proteinGrams, rest.proteinGrams);
        assert.equal(workout.fatGrams, rest.fatGrams);
        approximately((workout.carbGrams - rest.carbGrams) * 4, workout.calories - rest.calories, 3);
      }
    }
  }
});

test("rounded macros approximately match target energy across supported inputs on both days", () => {
  for (const weight of [35, 35.5, 50, 70.25, 100, 175.75, 300]) {
    for (const { id: dayType } of NUTRITION_DAY_TYPES) {
      for (const { id: activity, kcalPerKg } of ACTIVITY_LEVELS) {
        for (const { id: goal } of NUTRITION_GOALS) {
          const result = estimate(weight, "kg", activity, goal, dayType);
          const goalMultiplier = goal === "lose" ? 0.85 : goal === "gain" ? 1.1 : 1;
          const rate = kcalPerKg + (dayType === "workout" ? 4 : 0);
          assert.equal(result.maintenanceCalories, Math.round(weight * rate));
          assert.equal(result.calories, Math.round(weight * rate * goalMultiplier));
          assert.equal(result.proteinGrams, Math.round(weight * (goal === "lose" ? 2 : 1.8)));
          assert.equal(result.fatGrams, Math.round(Math.round(weight * kcalPerKg * goalMultiplier) * 0.25 / 9));
          for (const field of ["maintenanceCalories", "calories", "proteinGrams", "fatGrams", "carbGrams"]) {
            assert(Number.isInteger(result[field]));
            assert(result[field] > 0);
          }
          const macroCalories = result.proteinGrams * 4 + result.fatGrams * 9 + result.carbGrams * 4;
          approximately(macroCalories, result.calories, 2);
        }
      }
    }
  }
});

test("low estimates are flagged using rounded target calories without raising or hiding calculator values", () => {
  assert.deepEqual(estimate(35, "kg", "light", "lose", "rest"), {
    dayType: "rest",
    weightKg: 35,
    maintenanceCalories: 980,
    calories: 833,
    proteinGrams: 70,
    fatGrams: 23,
    carbGrams: 87,
    belowMinimum: true,
  });
  for (const { id: dayType } of NUTRITION_DAY_TYPES) {
    const rate = 28 + (dayType === "workout" ? 4 : 0);
    const below = estimate(1499.4 / rate, "kg", "light", "maintain", dayType);
    assert.equal(below.calories, 1499);
    assert.equal(below.belowMinimum, true);
    const atMinimum = estimate(1499.6 / rate, "kg", "light", "maintain", dayType);
    assert.equal(atMinimum.calories, 1500);
    assert.equal(atMinimum.belowMinimum, false);
    assert.equal(estimate(70, "kg", "moderate", "maintain", dayType).belowMinimum, false);
  }
});

test("the low-calorie guardrail is independent for rest and workout estimates", () => {
  const rest = estimate(50, "kg", "moderate", "lose", "rest");
  const workout = estimate(50, "kg", "moderate", "lose", "workout");
  assert.equal(rest.calories, 1403);
  assert.equal(rest.belowMinimum, true);
  assert.equal(workout.calories, 1573);
  assert.equal(workout.belowMinimum, false);
  assert.equal(rest.proteinGrams, workout.proteinGrams);
  assert.equal(rest.fatGrams, workout.fatGrams);
});

test("repeated calculations are deterministic and return independent result objects for both days", () => {
  for (const { id: dayType } of NUTRITION_DAY_TYPES) {
    const first = estimate(70, "kg", "moderate", "maintain", dayType);
    const second = estimate(70, "kg", "moderate", "maintain", dayType);
    assert.deepEqual(first, second);
    assert.notEqual(first, second);
    first.calories = 0;
    assert.equal(estimate(70, "kg", "moderate", "maintain", dayType).calories, dayType === "workout" ? 2590 : 2310);
  }
  const rest = estimate(70, "kg", "moderate", "maintain", "rest");
  const workout = estimate(70, "kg", "moderate", "maintain", "workout");
  assert.notEqual(rest, workout);
  rest.proteinGrams = 0;
  workout.fatGrams = 0;
  assert.equal(workout.proteinGrams, 126);
  assert.equal(rest.fatGrams, 64);
});
