import catalog from "@/data/exercises.json";

export type ExerciseDefinition = {
  id: string;
  name: string;
  equipment: string;
  primaryMuscles: string[];
  instructions: string[];
  level: string;
};

// Vendored text catalog: searches never send user input to an outside service.
const exercises: ExerciseDefinition[] = catalog;
export const EXERCISE_LIBRARY_COUNT = exercises.length;
export function normalizeExerciseQuery(value: string): string {
  return value.normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, " ").trim().replace(/\s+/g, " ");
}
const indexed = exercises.map((exercise) => ({
  exercise,
  name: normalizeExerciseQuery(exercise.name),
  text: normalizeExerciseQuery([exercise.name, exercise.equipment, ...exercise.primaryMuscles].join(" ")),
}));
const byId = new Map(exercises.map((exercise) => [exercise.id, exercise]));
export function getLibraryExercise(id: string): ExerciseDefinition | undefined {
  return byId.get(id);
}
export function searchExercises(query: string): ExerciseDefinition[] {
  const normalized = normalizeExerciseQuery(query.slice(0, 200));
  if (normalized.length < 2) return [];
  const tokens = normalized.split(" ");
  return indexed.filter((entry) => tokens.every((token) => entry.text.includes(token)))
    .sort((a, b) => {
      const rank = (name: string) => name === normalized ? 0 : name.startsWith(normalized) ? 1 : name.includes(normalized) ? 2 : 3;
      return rank(a.name) - rank(b.name) || a.name.localeCompare(b.name);
    }).slice(0, 40).map((entry) => entry.exercise);
}
