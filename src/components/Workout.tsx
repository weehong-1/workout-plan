"use client";

import { useRouter } from "next/navigation";
import { useRestTimer } from "@/components/RestTimer";
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
  discardDraft,
  emptyExercise,
  finishDraft,
  formatSet,
  hasLoggedValue,
  lastEntryFor,
  todayString,
  updateDraftExercise,
  useStore,
} from "@/lib/store";

export function Workout({ day }: { day: Day }) {
  const { log, drafts } = useStore();
  const router = useRouter();
  const draft = drafts[day.id];
  const exercises = day.blocks.flatMap((b) => b.exercises);
  const doneSets = exercises.reduce(
    (n, ex) => n + (draft?.exercises[ex.order]?.sets.filter((s) => s.done).length ?? 0),
    0,
  );
  const totalSets = exercises.reduce((n, ex) => n + ex.sets, 0);

  const finish = () => {
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
        notes: d.notes.trim(),
      });
    }
    if (!entries.length) {
      alert("Log at least one set before finishing.");
      return;
    }
    finishDraft(day.id, entries);
    router.push("/log");
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

      <div className="space-y-4">
        {day.blocks.map((block, i) => (
          <BlockView key={i} day={day} block={block} draft={draft?.exercises} log={log} />
        ))}
      </div>

      <div className="mt-8 space-y-3">
        <button
          onClick={finish}
          className="flex h-14 w-full items-center justify-center rounded-2xl bg-[var(--accent)] text-lg font-semibold text-[var(--accent-fg)] active:scale-[0.98]"
        >
          Finish &amp; save workout
        </button>
        {draft && (
          <button onClick={discard} className="h-11 w-full text-sm font-medium text-[var(--muted)]">
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
}: {
  dayId: string;
  ex: Exercise;
  draft: DraftExercise | undefined;
  log: LogEntry[];
  straightToNext: boolean;
}) {
  const timer = useRestTimer();
  const d = draft ?? emptyExercise(ex.sets);
  const name = d.useSub ? ex.substitute : ex.name;
  const video = d.useSub ? ex.substituteVideo : ex.video;
  const last = lastEntryFor(log, ex.name);
  const target = targetReps(ex.reps);
  const hitTarget =
    !!last &&
    target !== null &&
    last.sets.length >= ex.sets &&
    last.sets.every((s) => parseInt(s.reps, 10) >= target);
  const allDone = d.sets.every((s) => s.done);

  const update = (fn: (ex: DraftExercise) => DraftExercise) =>
    updateDraftExercise(dayId, ex.order, ex.sets, fn);

  // Previous set's weight this session, else the same set last session.
  const suggestedWeight = (cur: DraftExercise, i: number) =>
    cur.sets[i - 1]?.weight || (cur.useSub ? "" : last?.sets[i]?.weight ?? "");

  const setField = (i: number, field: "weight" | "reps", value: string) =>
    update((cur) => ({
      ...cur,
      sets: cur.sets.map((s, j) => (j === i ? { ...s, [field]: value } : s)),
    }));

  const toggleDone = (i: number) => {
    const wasDone = d.sets[i].done;
    update((cur) => ({
      ...cur,
      sets: cur.sets.map((s, j) => {
        if (j !== i) return s;
        if (s.done) return { ...s, done: false };
        // Tapping ✓ on an empty set accepts the suggested weight and the target reps.
        return {
          weight: s.weight || suggestedWeight(cur, j),
          reps: s.reps || (target !== null ? String(target) : ""),
          done: true,
        };
      }),
    }));
    if (!wasDone && !straightToNext) {
      const sec = restSeconds(ex.rest);
      if (sec) timer.start(sec, name);
    }
  };

  return (
    <article
      className={`rounded-2xl border bg-[var(--card)] p-4 transition-colors ${
        allDone ? "border-emerald-500/60" : "border-[var(--line)]"
      }`}
    >
      <div className="flex items-start gap-3">
        <span className="mt-0.5 grid h-8 min-w-8 shrink-0 place-items-center rounded-lg bg-[var(--soft)] px-1.5 text-xs font-bold">
          {ex.order === "Finisher" ? "Fin" : ex.order}
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="text-lg font-semibold leading-snug">{name}</h3>
          {d.useSub && <p className="text-xs text-[var(--muted)]">Substitute for {ex.name}</p>}
          <p className="text-sm text-[var(--muted)]">{ex.muscles}</p>
        </div>
        {allDone && (
          <svg viewBox="0 0 24 24" className="mt-1 h-6 w-6 shrink-0 text-emerald-500" fill="none" stroke="currentColor" strokeWidth={2.5} aria-label="Done">
            <path d="m5 12.5 4.5 4.5L19 7.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
      </div>

      <dl className="mt-3 grid grid-cols-3 gap-2 text-center">
        <Stat label="Warm up" value={ex.warmUp.replace(/ per leg$/, "")} />
        <Stat label="Working" value={`${ex.sets} × ${ex.reps.replace(/ per leg$/, "")}`} />
        <Stat label="Rest" value={straightToNext ? "None" : ex.rest.replace(/^Rest /, "").replace(/ after each round$/, "")} />
      </dl>
      {(ex.reps.includes("per leg") || straightToNext || ex.rest.includes("round")) && (
        <p className="mt-2 text-xs text-[var(--muted)]">
          {[ex.reps.includes("per leg") && "Reps are per leg.", straightToNext ? `${ex.rest}.` : ex.rest.includes("round") && `${ex.rest}.`]
            .filter(Boolean)
            .join(" ")}
        </p>
      )}

      <p className="mt-3 rounded-xl bg-[var(--soft)] px-3 py-2.5 text-sm leading-relaxed">
        <span className="font-semibold">Form: </span>
        {ex.cues}
      </p>

      <div className="mt-3 flex flex-wrap gap-2">
        <a
          href={video}
          target="_blank"
          rel="noopener noreferrer"
          className="flex h-10 items-center gap-1.5 rounded-full bg-red-600 px-4 text-sm font-semibold text-white active:scale-95"
        >
          <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden>
            <path d="M8 5.5v13l11-6.5-11-6.5Z" />
          </svg>
          Tutorial
        </a>
        <button
          onClick={() => update((cur) => ({ ...cur, useSub: !cur.useSub }))}
          className="flex h-10 items-center rounded-full border border-[var(--line)] px-4 text-sm font-medium active:scale-95"
        >
          {d.useSub ? `Back to ${ex.name}` : `Taken? Use ${ex.substitute}`}
        </button>
      </div>

      {last && !d.useSub && (
        <div className="mt-3 text-sm">
          <p className="text-[var(--muted)]">
            Last ({last.date}):{" "}
            <span className="font-medium text-[var(--fg)]">{last.sets.map(formatSet).join(", ")}</span>
          </p>
          {hitTarget && (
            <p className="mt-1 font-medium text-emerald-600 dark:text-emerald-400">
              You hit {target} on every set: add {isLowerBody(ex.muscles) ? "2.5~5 kg" : "1~2.5 kg"} today.
            </p>
          )}
        </div>
      )}

      <div className="mt-3 space-y-2">
        {d.sets.map((s, i) => (
          <div key={i} className="flex items-center gap-2">
            <span className="w-10 shrink-0 text-xs font-semibold text-[var(--muted)]">Set {i + 1}</span>
            <label className="relative min-w-0 flex-1">
              <span className="sr-only">Set {i + 1} weight</span>
              <input
                inputMode="decimal"
                enterKeyHint="next"
                value={s.weight}
                placeholder={suggestedWeight(d, i)}
                onChange={(e) => setField(i, "weight", e.target.value)}
                className="h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] pr-9 pl-3 tabular-nums outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]"
              />
              <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-xs text-[var(--muted)]">kg</span>
            </label>
            <span className="text-[var(--muted)]">×</span>
            <label className="relative w-[4.75rem] shrink-0">
              <span className="sr-only">Set {i + 1} reps</span>
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
              className={`grid h-12 w-12 shrink-0 place-items-center rounded-xl border-2 active:scale-90 ${
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

      <input
        value={d.notes}
        onChange={(e) => update((cur) => ({ ...cur, notes: e.target.value }))}
        placeholder="Notes (optional)"
        className="mt-2 h-11 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]"
      />
    </article>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-[var(--line)] px-1 py-2">
      <dt className="text-[11px] uppercase tracking-wide text-[var(--muted)]">{label}</dt>
      <dd className="mt-0.5 text-sm font-semibold leading-tight">{value}</dd>
    </div>
  );
}
