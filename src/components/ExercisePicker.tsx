"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import Link from "next/link";
import type { AddedExercise } from "@/lib/added-exercise";
import type { ExerciseDefinition } from "@/lib/exercise-library";
import { ExerciseSearch } from "@/components/ExerciseSearch";
import { addDayExercise, flushChanges, getPersistenceStatus, usePersistenceStatus } from "@/lib/store";

type Selection = { kind: "library"; exercise: ExerciseDefinition } | { kind: "custom" };

export function ExercisePicker({ dayId, dayName, added, onClose }: {
  dayId: string;
  dayName: string;
  added: AddedExercise[];
  onClose: () => void;
}) {
  const { ready, locked } = usePersistenceStatus();
  const dialog = useRef<HTMLDialogElement>(null);
  const selectionHeading = useRef<HTMLHeadingElement>(null);
  const customNameInput = useRef<HTMLInputElement>(null);
  const customId = useRef<string | null>(null);
  const mounted = useRef(false);
  const busy = useRef(false);
  const [query, setQuery] = useState("");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [customName, setCustomName] = useState("");
  const [customEquipment, setCustomEquipment] = useState("");
  const [customMuscles, setCustomMuscles] = useState("");
  const [customInstructions, setCustomInstructions] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [pendingAdd, setPendingAdd] = useState(false);
  const [sets, setSets] = useState("2");
  const [reps, setReps] = useState("10");
  const [rest, setRest] = useState("90");
  const muscles = customMuscles.split(",").map((value) => value.trim()).filter(Boolean);
  const instructions = customInstructions.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
  const duplicate = selection?.kind === "library" && added.some((exercise) => exercise.source === "free-exercise-db" && exercise.exerciseId === selection.exercise.id);
  const validTargets = /^\d+$/.test(sets) && Number(sets) >= 1 && Number(sets) <= 10 && /^(?:[1-9][0-9]?|100)$/.test(reps) && /^\d+$/.test(rest) && Number(rest) <= 600;
  const validCustom = customName.trim().length > 0 && customName.length <= 120 && customEquipment.length <= 100 && customMuscles.length <= 100 && muscles.length <= 10 && customInstructions.length <= 3000 && instructions.length <= 30;
  const valid = validTargets && (selection?.kind !== "custom" || validCustom);

  useEffect(() => {
    mounted.current = true;
    const element = dialog.current;
    const trigger = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element?.showModal();
    element?.querySelector<HTMLInputElement>('[role="combobox"]')?.focus();
    return () => {
      mounted.current = false;
      element?.close();
      document.body.style.overflow = overflow;
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  useEffect(() => {
    if (selection?.kind === "custom") customNameInput.current?.focus();
    else if (selection) selectionHeading.current?.focus();
    else dialog.current?.querySelector<HTMLInputElement>('[role="combobox"]')?.focus();
  }, [selection]);

  function chooseCustom() {
    customId.current = null;
    setCustomName(query.trim().slice(0, 120));
    setCustomEquipment("");
    setCustomMuscles("");
    setCustomInstructions("");
    setError("");
    setSelection({ kind: "custom" });
  }

  async function add() {
    if (!selection || busy.current) return;
    const status = getPersistenceStatus();
    if (!status.ready || status.locked) {
      setError("Your workout is not ready to edit. Wait for it to finish loading or resolve the saving notice first.");
      return;
    }
    if (!pendingAdd && (!valid || duplicate)) return;
    busy.current = true;
    setSaving(true);
    setError("");
    try {
      if (!pendingAdd) {
        let snapshot: Omit<AddedExercise, "key">;
        const targets = { sets: Number(sets), reps: reps.trim(), restSeconds: Number(rest) };
        if (selection.kind === "library") {
          const exercise = selection.exercise;
          snapshot = {
            source: "free-exercise-db", exerciseId: exercise.id,
            name: exercise.name, equipment: exercise.equipment,
            primaryMuscles: [...exercise.primaryMuscles], instructions: [...exercise.instructions],
            ...targets,
          };
        } else {
          // Retain this identity if validation or persistence needs a retry.
          customId.current ??= crypto.randomUUID();
          snapshot = {
            source: "custom", exerciseId: customId.current,
            name: customName.trim(), equipment: customEquipment.trim(),
            primaryMuscles: muscles, instructions,
            ...targets,
          };
        }
        const saved = addDayExercise(dayId, snapshot);
        if (!saved) throw new Error("Unable to add this exercise. Check its details and choose 1–10 sets, 1–100 reps and 0–600 seconds rest.");
        setPendingAdd(true);
      }
      if (!(await flushChanges())) throw new Error(getPersistenceStatus().error ?? "Your exercise has not saved. Keep this page open and retry.");
      if (mounted.current) onClose();
    } catch (cause) {
      if (mounted.current) setError(cause instanceof Error ? cause.message : "Unable to add this exercise.");
    } finally {
      busy.current = false;
      if (mounted.current) setSaving(false);
    }
  }

  return createPortal(
    <dialog ref={dialog} aria-labelledby="exercise-picker-title" aria-describedby="exercise-picker-hint"
      onCancel={(event) => { event.preventDefault(); if (!busy.current) onClose(); }}
      onKeyDown={(event) => { if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); if (!busy.current) onClose(); } }}
      onClick={(event) => { if (event.target === event.currentTarget && !busy.current) onClose(); }}
      className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-3xl border border-[var(--line)] bg-[var(--card)] p-0 text-[var(--fg)] shadow-2xl backdrop:bg-black/50 backdrop:backdrop-blur-sm">
      <div className="p-5 sm:p-6">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="exercise-picker-title" className="text-xl font-bold">Add exercise</h2>
            <p id="exercise-picker-hint" className="mt-1 text-sm text-[var(--muted)]">Search Free Exercise DB or create your own exercise for {dayName}. Added exercises stay on this day for future sessions.</p>
          </div>
          <button type="button" onClick={() => { if (!busy.current) onClose(); }} disabled={saving} aria-label="Close exercise picker" className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[var(--soft)] disabled:opacity-40">✕</button>
        </div>
        {selection ? (
          <div className="mt-5 space-y-4">
            <button type="button" disabled={saving || pendingAdd} onClick={() => { setSelection(null); setError(""); }} className="h-10 text-sm font-semibold text-[var(--muted)] disabled:opacity-40">← Search results</button>
            {selection.kind === "library" ? (
              <div className="rounded-2xl bg-[var(--soft)] p-4">
                <h3 ref={selectionHeading} tabIndex={-1} className="text-lg font-semibold">{selection.exercise.name}</h3>
                <p className="mt-1 text-sm text-[var(--muted)]">{[selection.exercise.equipment || "No equipment specified", selection.exercise.level, selection.exercise.primaryMuscles.join(", ")].filter(Boolean).join(" · ")}</p>
                <details key={selection.exercise.id} className="group mt-3 border-t border-[var(--line)] pt-1">
                  <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 rounded-lg text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] [&::-webkit-details-marker]:hidden">
                    Exercise instructions
                    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="shrink-0 transition-transform group-open:rotate-180"><path d="m6 9 6 6 6-6" /></svg>
                  </summary>
                  {selection.exercise.instructions.length > 0 ? <ol className="mt-2 list-decimal space-y-2 pl-5 text-sm">{selection.exercise.instructions.map((step, index) => <li key={index}>{step}</li>)}</ol> : <p className="mt-2 text-sm text-[var(--muted)]">No instructions are available for this exercise.</p>}
                </details>
              </div>
            ) : (
              <fieldset disabled={saving || pendingAdd} className="space-y-4">
                <legend className="mb-3 text-lg font-semibold">Custom exercise</legend>
                <label className="block text-sm font-semibold">Name <span className="text-[var(--muted)]">(required)</span>
                  <input ref={customNameInput} type="text" required maxLength={120} value={customName} onChange={(event) => setCustomName(event.target.value)} className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 font-normal outline-none focus:border-[var(--accent)]" />
                </label>
                <label className="block text-sm font-semibold">Equipment <span className="text-[var(--muted)]">(optional)</span>
                  <input type="text" maxLength={100} value={customEquipment} onChange={(event) => setCustomEquipment(event.target.value)} placeholder="e.g. dumbbells" className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 font-normal outline-none focus:border-[var(--accent)]" />
                </label>
                <label className="block text-sm font-semibold">Muscles <span className="text-[var(--muted)]">(optional)</span>
                  <input type="text" maxLength={100} value={customMuscles} onChange={(event) => setCustomMuscles(event.target.value)} aria-describedby="custom-muscles-hint" aria-invalid={muscles.length > 10} placeholder="e.g. chest, triceps" className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 font-normal outline-none focus:border-[var(--accent)]" />
                </label>
                <p id="custom-muscles-hint" className="text-xs text-[var(--muted)]">Separate up to 10 muscles with commas (100 characters total).{muscles.length > 10 && " Please use no more than 10 muscles."}</p>
                <label className="block text-sm font-semibold">Instructions <span className="text-[var(--muted)]">(optional)</span>
                  <textarea rows={5} maxLength={3000} value={customInstructions} onChange={(event) => setCustomInstructions(event.target.value)} aria-describedby="custom-instructions-hint" aria-invalid={instructions.length > 30} placeholder="One step per line" className="mt-2 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] p-3 font-normal outline-none focus:border-[var(--accent)]" />
                </label>
                <p id="custom-instructions-hint" className="text-xs text-[var(--muted)]">One step per line, up to 30 steps and 3,000 characters.{instructions.length > 30 && " Please use no more than 30 steps."}</p>
              </fieldset>
            )}
            <fieldset disabled={saving || pendingAdd} className="grid grid-cols-3 gap-3">
              <NumberField label="Sets" value={sets} onChange={setSets} min={1} max={10} />
              <NumberField label="Reps" value={reps} onChange={setReps} min={1} max={100} />
              <NumberField label="Rest (sec)" value={rest} onChange={setRest} min={0} max={600} />
            </fieldset>
            <p className="text-xs text-[var(--muted)]">Choose 1–10 sets, 1–100 reps and 0–600 seconds rest. These are your working-set targets. Warm up as appropriate and use a technique you know safely.</p>
            {duplicate && !pendingAdd && <p className="text-sm text-[var(--muted)]">This library exercise is already added to this day.</p>}
            {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
            <button type="button" onClick={() => void add()} disabled={saving || !ready || locked || (!pendingAdd && (!valid || duplicate))} className="h-12 w-full rounded-xl bg-[var(--accent)] px-3 font-semibold text-[var(--accent-fg)] disabled:opacity-40">{saving ? "Saving exercise…" : pendingAdd ? "Retry saving exercise" : "Add to this day"}</button>
          </div>
        ) : (
          <div className="mt-5">
            <ExerciseSearch query={query} onQueryChange={setQuery} added={added}
              onSelect={(exercise) => { setSelection({ kind: "library", exercise }); setError(""); }}
              onCreate={chooseCustom} />
          </div>
        )}
        <p className="mt-5 border-t border-[var(--line)] pt-3 text-xs text-[var(--muted)]">Library text from <a href="https://github.com/yuhonas/free-exercise-db" target="_blank" rel="noopener noreferrer" className="underline">Free Exercise DB</a>. Exercise details are saved with your plan; no media is downloaded. <Link href="/data-notice" className="underline">Data notice</Link></p>
      </div>
    </dialog>, document.body,
  );
}

function NumberField({ label, value, onChange, min, max }: { label: string; value: string; onChange: (value: string) => void; min: number; max: number }) {
  return <label className="text-sm font-semibold">{label}<input type="number" min={min} max={max} step={1} value={value} onChange={(event) => onChange(event.target.value)} className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 font-normal outline-none focus:border-[var(--accent)]" /></label>;
}
