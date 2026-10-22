// New selections retain their full text definition in the account database.
// Legacy references are read-only compatible; no old provider calls are made.
export type AddedExercise = {
  key: string;
  source: "free-exercise-db" | "custom";
  exerciseId: string;
  name: string;
  equipment: string;
  primaryMuscles: string[];
  instructions: string[];
  sets: number;
  reps: string;
  restSeconds: number;
  musclewikiId?: number;
};
