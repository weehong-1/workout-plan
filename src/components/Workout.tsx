"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { VideoModal } from "@/components/VideoModal";
import { WakeLockToggle } from "@/components/WakeLock";
import { Check, VerdictPill } from "@/components/ui";
import {
  type Day,
  type Exercise,
  type Suggestion,
  dayExercises,
  splitReps,
  suggestFor,
  targetReps,
  warmUpSets,
  youtubeId,
} from "@/lib/plan";
import { removePhotos } from "@/lib/photos";
import {
  type DraftExercise,
  type LogEntry,
  type Unit,
  discardDraft,
  draftPhotos,
  emptyExercise,
  flushChanges,
  historyFor,
  setExerciseUnit,
  unitFor,
  updateDraftExercise,
  useHydrated,
  usePlan,
  useStore,
} from "@/lib/store";

const GRID = "grid grid-cols-[2.25rem_minmax(0,1fr)_4.75rem_3.75rem_2.75rem] items-center gap-1.5 px-1";

export function WorkoutScreen({ dayId }: { dayId: string }) {
  const plan = usePlan();
  const hydrated = useHydrated();
  if (!hydrated) return null;
  const day = plan.days.find((d) => d.id === dayId);
  if (!day) {
    return (
      <div className="py-16 text-center">
        <p className="text-lg font-semibold">This training day no longer exists.</p>
        <Link href="/" className="mt-4 inline-flex min-h-11 items-center font-semibold text-[var(--accent)]">Back to Today</Link>
      </div>
    );
  }
  return <Workout day={day} />;
}

// Live clock for the duration, refreshed every 30 s.
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}

export type WorkoutStats = { minutes: number; volume: number; done: number; total: number };

export function workoutStats(day: Day, draft: { startedAt: number; exercises: Record<string, DraftExercise> } | undefined, now: number): WorkoutStats {
  let done = 0, total = 0, volume = 0;
  for (const ex of dayExercises(day)) {
    const d = draft?.exercises[ex.order];
    const started = !!d?.sets.some((s) => s.done);
    if (ex.optional && !started) continue;
    const sets = d?.sets ?? [];
    total += Math.max(sets.length, ex.sets);
    for (const s of sets) {
      if (!s.done) continue;
      done++;
      volume += (parseFloat(s.weight) || 0) * (parseInt(s.reps, 10) || 0);
    }
  }
  const minutes = draft ? Math.max(0, Math.round((now - draft.startedAt) / 60_000)) : 0;
  return { minutes, volume, done, total };
}

export function Workout({ day }: { day: Day }) {
  const store = useStore();
  const router = useRouter();
  const now = useNow();
  const draft = store.drafts[day.id];
  const exercises = dayExercises(day);
  const stats = workoutStats(day, draft, now);

  const discard = async () => {
    if (!confirm("Discard this workout? Everything you've logged today, including photos, will be cleared.")) return;
    const photos = draftPhotos(draft);
    discardDraft(day.id);
    router.push("/");
    if (photos.length && await flushChanges()) void removePhotos(photos);
  };

  return (
    <div className="-mx-4 lg:mx-auto lg:max-w-3xl">
      <header className="sticky top-0 z-20 border-b border-[var(--line)] bg-[var(--bg)]/95 px-4 pt-1 pb-3 backdrop-blur-md">
        <div className="flex items-center gap-2">
          <Link href="/" aria-label="Minimise workout" className="-ml-2 grid h-11 w-11 place-items-center rounded-xl">
            <svg viewBox="0 0 24 24" className="h-[22px] w-[22px]" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="m6 9 6 6 6-6" /></svg>
          </Link>
          <h1 className="font-display flex-1 truncate text-xl font-bold">{day.name}</h1>
          <Link href={`/day/${day.id}/save`} className="flex h-10 items-center rounded-xl bg-[var(--accent)] px-[18px] text-sm font-bold text-[var(--accent-fg)]">Finish</Link>
        </div>
        <dl className="mt-2 grid grid-cols-3 px-1">
          <div><dt className="text-xs text-[var(--muted)]">Duration</dt><dd className="font-display mt-0.5 text-[17px] font-bold text-[var(--accent)]">{stats.minutes} min</dd></div>
          <div><dt className="text-xs text-[var(--muted)]">Volume</dt><dd className="font-display mt-0.5 text-[17px] font-bold">{Math.round(stats.volume).toLocaleString("en-US")} kg</dd></div>
          <div><dt className="text-xs text-[var(--muted)]">Sets</dt><dd className="font-display mt-0.5 text-[17px] font-bold" aria-live="polite">{stats.done} / {stats.total}</dd></div>
        </dl>
      </header>

      {exercises.map((ex) => (
        <ExerciseBlock key={ex.order} dayId={day.id} ex={ex} draft={draft?.exercises[ex.order]} log={store.log} />
      ))}

      {!exercises.length && (
        <p className="px-4 py-10 text-center text-[var(--muted)]">
          No exercises on this day yet. <Link href={`/plan/${day.id}`} className="font-semibold text-[var(--accent)]">Add some</Link>
        </p>
      )}

      <div className="flex flex-col gap-3 px-4 pt-6">
        <Link href={`/day/${day.id}/save`} className="flex h-14 items-center justify-center rounded-2xl bg-[var(--accent)] text-[17px] font-bold text-[var(--accent-fg)]">Finish workout</Link>
        <div className="flex items-center justify-between">
          <WakeLockToggle />
          {draft && <button type="button" onClick={() => void discard()} className="h-11 px-2 text-sm font-semibold text-[var(--danger)]">Discard workout</button>}
        </div>
      </div>
    </div>
  );
}

function formatPrev(set: { weight: string; reps: string } | undefined, unit: Unit) {
  if (!set) return "—";
  const w = set.weight.trim();
  const weight = !w ? "" : /^\d+(\.\d+)?$/.test(w) ? w : w;
  return weight ? `${weight}${unit === "lb" && weight !== "BW" ? "lb" : ""} × ${set.reps}` : `${set.reps}`;
}

export function ExerciseBlock({ dayId, ex, draft, log }: {
  dayId: string;
  ex: Exercise;
  draft: DraftExercise | undefined;
  log: LogEntry[];
}) {
  const titleId = useId();
  const store = useStore();
  const [showForm, setShowForm] = useState(false);
  const [watching, setWatching] = useState(false);
  const d = draft ?? emptyExercise(ex.sets);
  const name = d.useSub && ex.substitute ? ex.substitute : ex.name;
  const unit = unitFor(store, name);
  const history = historyFor(log, ex.name);
  const last = history.at(-1);
  const lastUnit: Unit = last?.unit ?? "kg";
  const suggestion: Suggestion = d.useSub
    ? { weight: "", verdict: "first", label: "Substitute" }
    : suggestFor(ex, history);
  const target = targetReps(ex.reps);
  const timed = /sec|min/.test(ex.reps);
  const reps = splitReps(ex.reps);
  const timedHint = ex.reps.match(/\d+/)?.[0] ?? "";
  const workWeight = parseFloat(d.sets.find((s) => s.weight)?.weight || suggestion.weight);
  const warm = d.useSub ? [] : warmUpSets(ex.warmUp, Number.isFinite(workWeight) ? workWeight : null, unit === "lb" ? 2.5 : 1.25);
  const completed = d.sets.filter((s) => s.done).length;
  const allDone = d.sets.length > 0 && completed === d.sets.length;
  const video = d.useSub ? ex.substituteVideo : ex.video;
  const playable = !!video && youtubeId(video) !== null;

  const update = (fn: (cur: DraftExercise) => DraftExercise) => updateDraftExercise(dayId, ex.order, ex.sets, fn);
  const setField = (i: number, field: "weight" | "reps", value: string) =>
    update((cur) => ({ ...cur, sets: cur.sets.map((s, j) => (j === i ? { ...s, [field]: value.replace(",", ".") } : s)) }));
  const toggle = (i: number) =>
    update((cur) => ({
      ...cur,
      sets: cur.sets.map((s, j) => {
        if (j !== i) return s;
        if (s.done) return { ...s, done: false };
        // Ticking a row with blank cells logs the suggested values.
        return {
          weight: s.weight || (suggestion.weight === "BW" ? "BW" : suggestion.weight),
          reps: s.reps || (target !== null ? String(target) : timedHint),
          done: true,
        };
      }),
    }));

  return (
    <section aria-labelledby={titleId} className="border-b-8 border-[var(--panel)] px-3 pt-4 pb-3">
      <div className="flex items-start gap-2.5 px-1">
        <div className="min-w-0 flex-1">
          <h2 id={titleId} className="text-[17px] leading-snug font-bold">
            <Link href={`/progress/${dayId}/${encodeURIComponent(ex.order)}`} className="hover:underline">{name}</Link>
          </h2>
          <p className="mt-0.5 text-[13px] text-[var(--muted)]">
            {d.useSub ? `Instead of ${ex.name}` : `${ex.sets} × ${timed ? ex.reps : `${reps.count}${reps.per ? ` ${reps.per}` : ""}`}`}
          </p>
        </div>
        {allDone ? <VerdictPill verdict="done">Done</VerdictPill>
          : ex.optional && !completed ? <VerdictPill verdict="hold">Optional</VerdictPill>
          : <VerdictPill verdict={suggestion.verdict}>{suggestion.label}</VerdictPill>}
      </div>

      {ex.note && !d.useSub && <p className="mx-1 mt-2 rounded-lg bg-[var(--warn-bg)] px-2.5 py-2 text-[13px] text-[var(--warn)]">{ex.note}</p>}

      <label className="mx-1 mt-1 block">
        <span className="sr-only">Notes for {name}</span>
        <input
          value={d.notes}
          onChange={(e) => update((cur) => ({ ...cur, notes: e.target.value }))}
          placeholder="Add a note…"
          className="h-9 w-full bg-transparent text-sm text-[#d4d4d8] outline-none"
        />
      </label>

      <div role="table" aria-label={`Sets for ${name}`} className="mt-1">
        <div role="row" className={`${GRID} h-7 text-[11px] font-bold uppercase tracking-[0.06em] text-[var(--faint)]`}>
          <span role="columnheader">Set</span>
          <span role="columnheader">Previous</span>
          <span role="columnheader" className="text-center">
            <button type="button" onClick={() => setExerciseUnit(name, unit === "kg" ? "lb" : "kg")}
              aria-label={`Weight in ${unit}. Switch to ${unit === "kg" ? "lb" : "kg"}`}
              className="uppercase underline decoration-dotted underline-offset-2">{unit}</button>
          </span>
          <span role="columnheader" className="text-center">{timed ? "Sec" : "Reps"}</span>
          <span role="columnheader" className="text-center"><span className="sr-only">Done</span><Check className="mx-auto h-3.5 w-3.5" /></span>
        </div>

        {warm.length > 0 && (
          <div role="row" className={`${GRID} h-12 ${d.warmDone ? "bg-[var(--up-bg)]" : ""}`}>
            <span role="cell" className="font-bold text-[var(--warn)]">W</span>
            <span role="cell" className="truncate text-[13px] text-[var(--muted)]">Warm-up</span>
            <span role="cell" className="font-display truncate text-center text-[13px] font-bold">{warm.map((w) => w.weight).join("·")}</span>
            <span role="cell" className="font-display truncate text-center text-[13px] font-bold">{warm.map((w) => w.reps).join("·")}</span>
            <span role="cell" className="grid place-items-center">
              <button type="button" onClick={() => update((cur) => ({ ...cur, warmDone: !cur.warmDone }))} aria-pressed={!!d.warmDone}
                aria-label={d.warmDone ? "Undo warm-up" : "Complete warm-up"}
                className={`grid h-10 w-10 place-items-center rounded-[10px] ${d.warmDone ? "bg-[var(--up-solid)] text-[#05200f]" : "bg-[var(--soft)] text-[var(--faint)]"}`}>
                <Check className="h-[18px] w-[18px]" />
              </button>
            </span>
          </div>
        )}

        {d.sets.map((s, i) => {
          const prev = !d.useSub && last ? last.sets[i] : undefined;
          return (
            <div key={i} role="row" className={`${GRID} h-12 ${s.done ? "bg-[var(--up-bg)]" : ""}`}>
              <span role="cell" className={`font-bold ${s.done ? "text-[var(--up)]" : "text-[var(--muted)]"}`}>{i + 1}</span>
              <span role="cell" className="truncate text-sm text-[var(--muted)] tabular-nums">{formatPrev(prev, lastUnit)}</span>
              <span role="cell">
                <label>
                  <span className="sr-only">Set {i + 1} weight in {unit}</span>
                  <input inputMode="decimal" enterKeyHint="next" value={s.weight}
                    placeholder={suggestion.weight || (prev?.weight ?? "")}
                    onChange={(e) => setField(i, "weight", e.target.value)}
                    className={`font-display h-9 w-full rounded-lg text-center text-[17px] font-bold outline-none focus:ring-2 focus:ring-[var(--accent)] ${s.done ? "bg-transparent" : "bg-[var(--card)]"}`} />
                </label>
              </span>
              <span role="cell">
                <label>
                  <span className="sr-only">Set {i + 1} {timed ? "seconds" : "reps"}</span>
                  <input inputMode="numeric" enterKeyHint="done" value={s.reps}
                    placeholder={target !== null ? String(target) : timedHint}
                    onChange={(e) => setField(i, "reps", e.target.value)}
                    className={`font-display h-9 w-full rounded-lg text-center text-[17px] font-bold outline-none focus:ring-2 focus:ring-[var(--accent)] ${s.done ? "bg-transparent" : "bg-[var(--card)]"}`} />
                </label>
              </span>
              <span role="cell" className="grid place-items-center">
                <button type="button" onClick={() => toggle(i)} aria-pressed={s.done}
                  aria-label={s.done ? `Undo set ${i + 1}` : `Complete set ${i + 1}`}
                  className={`grid h-10 w-10 place-items-center rounded-[10px] active:scale-90 ${s.done ? "bg-[var(--up-solid)] text-[#05200f]" : "bg-[var(--soft)] text-[var(--faint)]"}`}>
                  <Check className="h-[18px] w-[18px]" />
                </button>
              </span>
            </div>
          );
        })}
      </div>

      <div className="mt-2 flex gap-2 px-1">
        <button type="button" onClick={() => update((cur) => ({ ...cur, sets: [...cur.sets, { weight: "", reps: "", done: false }] }))}
          className="h-10 flex-1 rounded-[10px] bg-[var(--card)] text-sm font-semibold text-[#d4d4d8]">+ Add set</button>
        {d.sets.length > ex.sets && (
          <button type="button" onClick={() => update((cur) => ({ ...cur, sets: cur.sets.slice(0, -1) }))}
            className="h-10 rounded-[10px] bg-[var(--card)] px-3 text-sm font-semibold text-[#d4d4d8]">Remove set {d.sets.length}</button>
        )}
      </div>

      <div className="mt-1 flex flex-wrap gap-x-4 px-1">
        <button type="button" aria-expanded={showForm} onClick={() => setShowForm(!showForm)} className="flex min-h-10 items-center text-[13px] font-semibold text-[var(--muted)]">
          {showForm ? "Hide form & video" : "Form & video"}
        </button>
        {ex.substitute && (
          <button type="button" aria-pressed={d.useSub} onClick={() => update((cur) => ({ ...cur, useSub: !cur.useSub }))}
            className="flex min-h-10 items-center text-[13px] font-semibold text-[var(--muted)]">
            {d.useSub ? `Back to ${ex.name}` : `Equipment taken? ${ex.substitute}`}
          </button>
        )}
      </div>

      {showForm && (
        <div className="mx-1 mt-1 space-y-2 rounded-xl bg-[var(--card)] p-3 text-sm leading-relaxed">
          {ex.warmUp && <p><strong>Warm-up · {ex.warmUp}</strong></p>}
          <p className="text-[#d4d4d8]">{d.useSub ? `Use technique specific to ${ex.substitute}.` : ex.cues || "No form notes for this exercise yet."}</p>
          {video && (
            <button type="button" onClick={() => (playable ? setWatching(true) : window.open(video, "_blank", "noopener,noreferrer"))}
              className="flex min-h-11 items-center gap-2 font-bold text-[var(--accent)]">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden><path d="M8 5.5v13l11-6.5-11-6.5Z" /></svg>
              {playable ? "Watch tutorial" : "Find tutorial on YouTube ↗"}
            </button>
          )}
        </div>
      )}
      {watching && playable && <VideoModal url={video} title={name} onClose={() => setWatching(false)} />}
    </section>
  );
}
