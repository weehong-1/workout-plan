"use client";

import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { useRestTimer } from "@/components/RestTimer";
import { UnitSelect } from "@/components/UnitSelect";
import { VideoModal } from "@/components/VideoModal";
import { WeightSelect } from "@/components/WeightSelect";
import { ExercisePicker } from "@/components/ExercisePicker";
import { AddedExerciseCard, addedExerciseLogName } from "@/components/AddedExerciseCard";
import {
  type Block,
  type Day,
  type Exercise,
  isLowerBody,
  restSeconds,
  targetReps,
} from "@/lib/plan";
import {
  type DraftExercise,
  type LogEntry,
  type Unit,
  discardDraft,
  emptyExercise,
  finishDraft,
  flushChanges,
  formatSet,
  hasLoggedValue,
  lastEntryFor,
  setExerciseUnit,
  unitFor,
  todayString,
  updateDraftExercise,
  useStore,
} from "@/lib/store";

export function Workout({ day }: { day: Day }) {
  const store = useStore();
  const [saving, setSaving] = useState(false);
  const [pickingExercise, setPickingExercise] = useState(false);
  const { log, drafts } = store;
  const added = store.dayExercises[day.id] ?? [];
  const router = useRouter();
  const draft = drafts[day.id];
  const exercises = [
    ...day.blocks.flatMap((b) => b.exercises),
    ...added.map((exercise) => ({ order: exercise.key, name: addedExerciseLogName(exercise), sets: exercise.sets, substitute: "" })),
  ];
  const doneSets = exercises.reduce(
    (n, ex) => n + (draft?.exercises[ex.order]?.sets.filter((s) => s.done).length ?? 0),
    0,
  );
  const totalSets = exercises.reduce(
    (n, ex) => n + (draft?.exercises[ex.order]?.sets.length ?? ex.sets),
    0,
  );

  const finish = async () => {
    if (saving) return;
    const date = todayString();
    const entries: Omit<LogEntry, "id" | "savedAt">[] = [];
    for (const ex of exercises) {
      const d = draft?.exercises[ex.order];
      if (!d) continue;
      const sets = d.sets.filter(hasLoggedValue).map(({ weight, reps }) => ({ weight, reps }));
      if (!sets.length && !d.notes.trim()) continue;
      entries.push({
        date,
        dayId: day.id,
        exercise: ex.name,
        substitute: d.useSub ? ex.substitute : undefined,
        sets,
        unit: unitFor(store, d.useSub ? ex.substitute : ex.name),
        notes: d.notes.trim(),
      });
    }
    if (!entries.length) {
      alert("Log at least one set before finishing.");
      return;
    }
    setSaving(true);
    finishDraft(day.id, entries);
    const saved = await flushChanges();
    setSaving(false);
    if (saved) router.push("/");
  };

  const discard = () => {
    if (confirm("Clear everything you've entered for this workout?")) discardDraft(day.id);
  };

  return (
    <>
      <div className="sticky top-0 z-20 -mx-4 mb-4 border-b border-[var(--line)] bg-[var(--bg)]/90 px-4 py-2 backdrop-blur-md">
        <div className="flex items-center justify-between text-xs font-medium text-[var(--muted)]">
          <span>
            {doneSets} / {totalSets} working sets
          </span>
          <span>{Math.round((doneSets / totalSets) * 100)}%</span>
        </div>
        <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-[var(--soft)]">
          <div
            className="h-full rounded-full bg-[var(--accent)] transition-[width]"
            style={{ width: `${(doneSets / totalSets) * 100}%` }}
          />
        </div>
      </div>

      <div className="mb-4 flex justify-end">
        <button type="button" onClick={() => setPickingExercise(true)} disabled={saving || added.length >= 30} className="min-h-11 rounded-xl border border-[var(--line)] bg-[var(--card)] px-4 text-sm font-semibold disabled:opacity-40">
          <span aria-hidden="true">＋ </span>Add exercise
        </button>
      </div>
      <fieldset disabled={saving} className="space-y-4">
        {day.blocks.map((block, i) => (
          <BlockView key={i} day={day} block={block} draft={draft?.exercises} log={log} />
        ))}
        {added.length > 0 && <h2 className="pt-2 text-lg font-bold">Your added exercises</h2>}
        {added.map((exercise, index) => (
          <AddedExerciseCard key={exercise.key} dayId={day.id} exercise={exercise} index={index}
            render={(ex, historyName) => <ExerciseCard dayId={day.id} ex={ex} historyName={historyName} displayOrder={`+${index + 1}`} draft={draft?.exercises[exercise.key]} log={log} straightToNext={false} />} />
        ))}
        <button type="button" onClick={() => setPickingExercise(true)} disabled={added.length >= 30}
          className="flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-[var(--line)] bg-[var(--card)] px-4 py-3 font-semibold text-[var(--fg)] active:scale-[0.98] disabled:opacity-40">
          <span className="text-xl" aria-hidden="true">＋</span> Add exercise
        </button>
        {added.length >= 30 && <p className="text-sm text-[var(--muted)]">This day has reached the limit of 30 added exercises.</p>}
      </fieldset>
      {pickingExercise && <ExercisePicker dayId={day.id} dayName={day.name} added={added} onClose={() => setPickingExercise(false)} />}

      <div className="mt-8 space-y-3">
        <button
          onClick={() => void finish()}
          disabled={saving}
          className="flex h-14 w-full items-center justify-center rounded-2xl bg-[var(--accent)] text-lg font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40"
        >
          {saving ? "Saving workout…" : "Finish & save workout"}
        </button>
        {draft && (
          <button disabled={saving} onClick={discard} className="h-11 w-full text-sm font-medium text-[var(--muted)]">
            Discard this workout
          </button>
        )}
      </div>
    </>
  );
}

function BlockView({
  day,
  block,
  draft,
  log,
}: {
  day: Day;
  block: Block;
  draft: Record<string, DraftExercise> | undefined;
  log: LogEntry[];
}) {
  const superset = block.exercises.length > 1;
  const cards = block.exercises.map((ex, i) => (
    <ExerciseCard
      key={ex.order}
      dayId={day.id}
      ex={ex}
      draft={draft?.[ex.order]}
      log={log}
      straightToNext={superset && i < block.exercises.length - 1}
    />
  ));

  if (!superset) return cards;

  const orders = block.exercises.map((e) => e.order).join(" → ");
  return (
    <section className="rounded-[1.4rem] bg-[var(--soft)] p-1.5">
      <header className="flex items-center justify-between px-3 pt-2 pb-2.5">
        <h2 className="text-sm font-bold">{block.format}</h2>
        <p className="text-xs text-[var(--muted)]">
          {orders}, then rest · {block.exercises[0].sets} rounds
        </p>
      </header>
      <div className="space-y-1.5">{cards}</div>
    </section>
  );
}

function ExerciseCard({
  dayId,
  ex,
  draft,
  log,
  straightToNext,
  historyName,
  displayOrder,
}: {
  dayId: string;
  ex: Exercise;
  draft: DraftExercise | undefined;
  log: LogEntry[];
  straightToNext: boolean;
  historyName?: string;
  displayOrder?: string;
}) {
  const titleId = useId();
  const timer = useRestTimer();
  const store = useStore();
  const [watching, setWatching] = useState(false);
  const d = draft ?? emptyExercise(ex.sets);
  const name = d.useSub ? ex.substitute : ex.name;
  const video = d.useSub ? ex.substituteVideo : ex.video;
  const unitKey = historyName ?? name;
  const unit = unitFor(store, unitKey);
  const last = lastEntryFor(log, historyName ?? ex.name);
  const lastUnit = last?.unit ?? "kg";
  const target = targetReps(ex.reps);
  const hitTarget =
    !!last &&
    target !== null &&
    last.sets.length >= ex.sets &&
    last.sets.every((s) => parseInt(s.reps, 10) >= target);
  const completedSets = d.sets.filter((s) => s.done).length;
  const allDone = d.sets.length > 0 && completedSets === d.sets.length;
  const timed = /sec|min/.test(ex.reps);
  const restLabel = ex.rest.replace(/^Rest /, "").replace(/ after each round$/, "");

  const update = (fn: (ex: DraftExercise) => DraftExercise) =>
    updateDraftExercise(dayId, ex.order, ex.sets, fn);

  // Where the weight picker opens: the previous set's weight this session,
  // else the same set last session (only when logged in the same unit).
  // It positions the wheel only; nothing gets filled in.
  const nearWeight = (cur: DraftExercise, i: number) =>
    cur.sets[i - 1]?.weight ||
    (cur.useSub || lastUnit !== unit ? "" : last?.sets[i]?.weight ?? "");

  const setField = (i: number, field: "weight" | "reps", value: string) =>
    update((cur) => ({
      ...cur,
      sets: cur.sets.map((s, j) => (j === i ? { ...s, [field]: value } : s)),
    }));

  const addSet = () =>
    update((cur) => ({ ...cur, sets: [...cur.sets, { weight: "", reps: "", done: false }] }));

  // Only sets beyond the planned count can be removed.
  const removeSet = () =>
    update((cur) =>
      cur.sets.length > ex.sets ? { ...cur, sets: cur.sets.slice(0, -1) } : cur,
    );

  const toggleDone = (i: number) => {
    const wasDone = d.sets[i].done;
    update((cur) => ({
      ...cur,
      sets: cur.sets.map((s, j) => {
        if (j !== i) return s;
        if (s.done) return { ...s, done: false };
        // Tapping ✓ with no reps entered counts the target reps.
        return {
          weight: s.weight,
          reps: s.reps || (target !== null ? String(target) : ""),
          done: true,
        };
      }),
    }));
    if (!wasDone && !straightToNext) {
      const sec = restSeconds(ex.rest);
      if (sec) timer.start(sec, historyName ?? name);
    }
  };

  return (
    <article
      aria-labelledby={titleId}
      className={`overflow-hidden rounded-2xl border bg-[var(--card)] transition-colors ${
        allDone ? "border-emerald-500/60" : "border-[var(--line)]"
      }`}
    >
      <div className="p-4">
        <header className="flex items-start gap-3">
          <span className="grid h-10 min-w-10 shrink-0 place-items-center rounded-xl bg-[var(--soft)] px-1.5 text-sm font-bold">
            {displayOrder ?? (ex.order === "Finisher" ? "Fin" : ex.order)}
          </span>
          <div className="min-w-0 flex-1">
            <h3 id={titleId} className="text-lg font-bold leading-snug">{name}</h3>
            {d.useSub && <p className="mt-0.5 text-xs text-[var(--muted)]">Substitute for {ex.name}</p>}
            <p className="mt-0.5 text-sm text-[var(--muted)]">{ex.muscles}</p>
          </div>
        </header>

        <dl className="mt-4 grid grid-cols-2 gap-2">
          <Stat label="Target" value={`${ex.sets} × ${timed ? ex.reps : `${ex.reps.replace(/ per leg$/, "")} reps${ex.reps.includes("per leg") ? " per leg" : ""}`}`} />
          <Stat label={straightToNext ? "Next exercise" : ex.rest.includes("round") ? "Rest after round" : "Rest between sets"}
            value={straightToNext ? ex.rest : restLabel} />
        </dl>

        <details className="group mt-3 rounded-xl border border-[var(--line)]">
          <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 rounded-xl px-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] [&::-webkit-details-marker]:hidden">
            Form & warm-up
            <svg viewBox="0 0 24 24" className="h-4 w-4 shrink-0 text-[var(--muted)] transition-transform group-open:rotate-180 motion-reduce:transition-none" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
              <path d="m6 9 6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </summary>
          <div className="space-y-3 border-t border-[var(--line)] px-3 py-3 text-sm leading-relaxed">
            <div>
              <p className="font-semibold">Warm-up · {ex.warmUp}</p>
              <p className="mt-1 text-[var(--muted)]">
                {ex.warmUp.startsWith("8 / 5 / 3")
                  ? "Use roughly 50%, 70% and 85% of your working weight. Rest 30–60 sec between warm-up sets."
                  : "Prepare with lighter sets as needed before your working sets."}
              </p>
            </div>
            <div>
              <p className="font-semibold">Form cues</p>
              <p className="mt-1 text-[var(--muted)]">{d.useSub
                ? `Use technique specific to ${ex.substitute}; the original exercise’s form cues may not apply.${video ? " Watch the substitute tutorial below for guidance." : ""}`
                : ex.cues}</p>
            </div>
            {video && <button
              type="button"
              onClick={() => setWatching(true)}
              className="flex min-h-11 items-center gap-2 rounded-xl border border-[var(--line)] bg-[var(--soft)] px-3 font-semibold active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden>
                <path d="M8 5.5v13l11-6.5-11-6.5Z" />
              </svg>
              Watch tutorial
            </button>}
          </div>
        </details>
        {watching && video && <VideoModal url={video} title={name} onClose={() => setWatching(false)} />}
        {ex.substitute && <button
          type="button"
          aria-pressed={d.useSub}
          onClick={() => { setWatching(false); update((cur) => ({ ...cur, useSub: !cur.useSub })); }}
          className="mt-1 flex min-h-11 w-full items-center gap-2 rounded-xl px-1 py-2 text-left text-sm font-medium text-[var(--muted)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          <svg viewBox="0 0 24 24" className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
            <path d="M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          {d.useSub ? `Back to ${ex.name}` : `Equipment taken? Use ${ex.substitute}`}
        </button>}
      </div>

      <div className="border-t border-[var(--line)] bg-[var(--bg)]/50 p-3 sm:p-4">
        {last && !d.useSub && (
          <div className="mb-4 rounded-xl border border-[var(--line)] bg-[var(--card)] p-3 text-sm">
            <p className="text-xs font-medium text-[var(--muted)]">Last session · {last.date}</p>
            <p className="mt-1 font-semibold tabular-nums">{last.sets.map((s) => formatSet(s, lastUnit)).join(" · ")}</p>
            {hitTarget && (
              <p className="mt-1 font-medium text-emerald-600 dark:text-emerald-400">
                You hit {target} on every set: add {weightStep(isLowerBody(ex.muscles), unit)} today.
              </p>
            )}
          </div>
        )}

        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h4 className="text-sm font-bold">Working sets</h4>
            <p role="status" className={`mt-0.5 text-xs font-medium ${allDone ? "text-emerald-700 dark:text-emerald-400" : "text-[var(--muted)]"}`}>
              {completedSets} / {d.sets.length} sets complete{allDone && " · Exercise done"}
            </p>
          </div>
          <UnitSelect value={unit} onChange={(u) => setExerciseUnit(unitKey, u)} />
        </div>

        <div aria-hidden="true" className="mb-2 grid grid-cols-[1.75rem_minmax(0,1fr)_4rem_3rem] items-center gap-2 px-1 text-center text-[11px] font-semibold uppercase tracking-wide text-[var(--muted)] sm:grid-cols-[2rem_minmax(0,1fr)_4.75rem_3rem]">
          <span>Set</span>
          <span>Weight</span>
          <span>{timed ? "Seconds" : "Reps"}</span>
          <span>Done</span>
        </div>
        <div className="space-y-2">
          {d.sets.map((s, i) => (
            <div key={i} className={`grid grid-cols-[1.75rem_minmax(0,1fr)_4rem_3rem] items-center gap-2 rounded-xl p-1 sm:grid-cols-[2rem_minmax(0,1fr)_4.75rem_3rem] ${s.done ? "bg-emerald-500/10" : ""}`}>
              <span className={`text-center text-sm font-bold tabular-nums ${s.done ? "text-emerald-700 dark:text-emerald-400" : "text-[var(--muted)]"}`}><span className="sr-only">Set </span>{i + 1}</span>
              <WeightSelect
                value={s.weight}
                unit={unit}
                near={nearWeight(d, i)}
                label={`Set ${i + 1} weight`}
                onChange={(v) => setField(i, "weight", v)}
              />
              <label className="relative min-w-0">
                <span className="sr-only">Set {i + 1} {timed ? "seconds" : "reps"}</span>
                <input
                  inputMode="numeric"
                  enterKeyHint="done"
                  value={s.reps}
                  placeholder={target !== null ? String(target) : "sec"}
                  onChange={(e) => setField(i, "reps", e.target.value)}
                  className="h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 text-center tabular-nums outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]"
                />
              </label>
              <button
                onClick={() => toggleDone(i)}
                aria-label={s.done ? `Undo set ${i + 1}` : `Complete set ${i + 1}`}
                aria-pressed={s.done}
                type="button"
                className={`grid h-12 w-12 shrink-0 place-items-center rounded-xl border-2 active:scale-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${
                  s.done
                    ? "border-emerald-500 bg-emerald-500 text-white"
                    : "border-[var(--line)] text-[var(--muted)]"
                }`}
              >
                <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2.6} aria-hidden>
                  <path d="m5 12.5 4.5 4.5L19 7.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </button>
            </div>
          ))}
        </div>

        <p className="mt-2 text-xs leading-relaxed text-[var(--muted)]">
          {straightToNext ? `Complete a set, then ${ex.rest.toLowerCase()}. No rest timer between paired exercises.` : "Tap ✓ to complete a set and start your rest timer."}
          {target !== null && ` Blank reps use the target (${target}); choose your weight explicitly.`}
        </p>

        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={addSet}
            className="flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border border-dashed border-[var(--line)] text-sm font-medium text-[var(--muted)] active:scale-[0.98]"
          >
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.4} aria-hidden>
              <path d="M12 5v14M5 12h14" strokeLinecap="round" />
            </svg>
            Add set
          </button>
          {d.sets.length > ex.sets && (
            <button
              type="button"
              onClick={removeSet}
              className="flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border border-dashed border-[var(--line)] text-sm font-medium text-[var(--muted)] active:scale-[0.98]"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.4} aria-hidden>
                <path d="M5 12h14" strokeLinecap="round" />
              </svg>
              Remove set {d.sets.length}
            </button>
          )}
        </div>

        <label className="mt-3 block">
          <span className="mb-1.5 block text-xs font-medium text-[var(--muted)]">Exercise notes (optional)</span>
          <input
            value={d.notes}
            onChange={(e) => update((cur) => ({ ...cur, notes: e.target.value }))}
            placeholder="How did it feel?"
            className="h-11 w-full rounded-xl border border-[var(--line)] bg-[var(--card)] px-3 outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]"
          />
        </label>
      </div>
    </article>
  );
}

// Progression steps from the Notes tab, with lb equivalents.
function weightStep(lower: boolean, unit: Unit) {
  if (unit === "lb") return lower ? "5~10 lb" : "2.5~5 lb";
  return lower ? "2.5~5 kg" : "1~2.5 kg";
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-[var(--soft)] px-3 py-2.5">
      <dt className="text-[11px] font-medium uppercase tracking-wide text-[var(--muted)]">{label}</dt>
      <dd className="mt-1 text-sm font-bold leading-snug">{value}</dd>
    </div>
  );
}
