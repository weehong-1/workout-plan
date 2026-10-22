"use client";

import { useEffect, useRef, useState } from "react";
import type { AddedExercise } from "@/lib/added-exercise";
import type { Exercise } from "@/lib/plan";
import { flushChanges, getPersistenceStatus, removeDayExercise, usePersistenceStatus } from "@/lib/store";

function isLegacyExercise(exercise: AddedExercise): boolean {
  return typeof exercise.musclewikiId === "number" && Number.isFinite(exercise.musclewikiId);
}

export function addedExerciseLogName(exercise: AddedExercise): string {
  // Preserve the exact historical log key without resolving the retired provider.
  return isLegacyExercise(exercise) ? `MuscleWiki #${exercise.musclewikiId}` : exercise.name;
}

export function AddedExerciseCard({ dayId, exercise, index, render }: {
  dayId: string;
  exercise: AddedExercise;
  index: number;
  render: (exercise: Exercise, historyName: string) => React.ReactNode;
}) {
  const { ready, locked } = usePersistenceStatus();
  const [error, setError] = useState("");
  const [removing, setRemoving] = useState(false);
  const mounted = useRef(false);
  const busy = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const legacy = isLegacyExercise(exercise);
  const logName = addedExerciseLogName(exercise);
  const name = legacy ? `Retired library exercise (MuscleWiki #${exercise.musclewikiId})` : exercise.name;
  const card: Exercise = {
    order: exercise.key,
    name,
    muscles: legacy ? "Legacy exercise" : exercise.primaryMuscles.join(", ") || "Muscles not specified",
    warmUp: "As needed",
    sets: exercise.sets,
    reps: exercise.reps,
    rest: `${exercise.restSeconds} sec`,
    cues: legacy
      ? "The old MuscleWiki library has been retired. Its name and instructions are no longer fetched. Remove this exercise and add it again from Free Exercise DB or as a custom exercise. Your saved workout logs will remain available."
      : exercise.instructions.join("\n") || "No instructions provided. Use a movement and technique you already know safely.",
    video: "",
    substitute: "",
    substituteVideo: "",
  };

  async function remove() {
    const status = getPersistenceStatus();
    if (busy.current || !status.ready || status.locked) return;
    if (!confirm("Remove this added exercise from this day? Its in-progress sets will be removed; saved workout logs will stay.")) return;
    busy.current = true;
    setRemoving(true);
    setError("");
    try {
      removeDayExercise(dayId, exercise.key);
      if (!(await flushChanges())) {
        // The global persistence notice retains this optimistic mutation even
        // when removing the exercise has already unmounted its card.
        throw new Error(getPersistenceStatus().error ?? "Removal has not saved. Retry saving above.");
      }
    } catch (cause) {
      if (mounted.current) setError(cause instanceof Error ? cause.message : "Unable to save this removal. Retry saving above.");
    } finally {
      busy.current = false;
      if (mounted.current) setRemoving(false);
    }
  }

  return <section aria-label={`Added exercise ${index + 1}`}>
    <div className="mb-2 flex items-center justify-between gap-3 px-1 text-xs text-[var(--muted)]">
      <span>Added {index + 1} · {legacy ? "Retired MuscleWiki library" : exercise.source === "custom" ? "Custom exercise" : "Free Exercise DB"}{!legacy && exercise.equipment ? ` · ${exercise.equipment}` : ""}</span>
      <button type="button" onClick={() => void remove()} disabled={removing || !ready || locked} className="min-h-10 rounded-lg px-2 font-semibold text-red-600 disabled:opacity-40 dark:text-red-400" aria-label={`Remove ${name}`}>{removing ? "Removing…" : "Remove"}</button>
    </div>
    {error && <p role="alert" className="mb-2 rounded-xl bg-[var(--soft)] p-3 text-sm text-[var(--muted)]">{error}</p>}
    {render(card, logName)}
  </section>;
}
