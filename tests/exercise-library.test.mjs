import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import ts from "typescript";

const catalog = JSON.parse(await readFile(new URL("../src/data/exercises.json", import.meta.url), "utf8"));
const source = ts.transpileModule(await readFile(new URL("../src/lib/exercise-library.ts", import.meta.url), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true },
}).outputText;
const library = {};
new Function("exports", "require", source)(library, (name) => {
  assert.equal(name, "@/data/exercises.json");
  return catalog;
});

test("vendored dataset has unique complete text-only exercise definitions", () => {
  assert.equal(catalog.length, 876);
  assert.equal(new Set(catalog.map((exercise) => exercise.id)).size, catalog.length);
  for (const exercise of catalog) {
    assert.deepEqual(Object.keys(exercise).sort(), ["equipment", "id", "instructions", "level", "name", "primaryMuscles"]);
    assert(exercise.id && exercise.name && exercise.name.length <= 120);
    assert(exercise.primaryMuscles.every((muscle) => typeof muscle === "string"));
    assert(exercise.instructions.every((step) => typeof step === "string" && step.length <= 3000));
    assert(exercise.instructions.reduce((n, step) => n + step.length, 0) <= 6000);
    assert.equal(library.getLibraryExercise(exercise.id), exercise);
  }
});

test("search normalizes case, punctuation and spacing; ranks exact names first", () => {
  const first = library.searchExercises("  DUMBBELL   CURL ");
  assert(first.length > 0 && first.length <= 40);
  assert.deepEqual(first, library.searchExercises("dumbbell-curl"));
  assert(first.every((exercise) => `${exercise.name} ${exercise.equipment} ${exercise.primaryMuscles.join(" ")}`.toLowerCase().includes("dumbbell")));
  const exact = catalog.find((exercise) => exercise.id === "Power_Clean");
  assert.equal(library.searchExercises(exact.name)[0].id, exact.id);
  assert.equal(library.normalizeExerciseQuery("BÍCEPS / curl"), "biceps curl");
});

test("missing exercises return no matches so UI can offer custom entry", () => {
  assert.deepEqual(library.searchExercises("zzzz-no-such-gym-machine"), []);
  assert.deepEqual(library.searchExercises(""), []);
  assert.deepEqual(library.searchExercises("a"), []);
  assert.deepEqual(library.searchExercises("---"), []);
  assert.equal(library.getLibraryExercise("missing"), undefined);
  assert(library.searchExercises("biceps").length > 0);
  assert(library.searchExercises("cable").length > 0);
  assert(library.searchExercises("press").length <= 40);
});
