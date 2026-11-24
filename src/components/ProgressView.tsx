"use client";

import Link from "next/link";
import { useState } from "react";
import { DayLetter, VERDICT_TEXT } from "@/components/ui";
import { type Exercise, dayExercises, nextPlanDay, sessionTop, suggestFor, targetReps } from "@/lib/plan";
import { type LogEntry, historyFor, useHydrated, usePlan, useStore } from "@/lib/store";

// Bodyweight lifts track reps; loaded lifts track the heaviest working set.
export function sessionValue(entry: LogEntry): number | null {
  const top = sessionTop(entry);
  if (top !== null) return top;
  const reps = entry.sets.map((s) => parseInt(s.reps, 10)).filter(Number.isFinite);
  return reps.length ? Math.min(...reps) : null;
}

export function reasonFor(ex: Exercise, history: LogEntry[]): string {
  const last = history.at(-1);
  if (!last) return "not logged yet";
  const target = targetReps(ex.reps);
  const s = suggestFor(ex, history);
  if (s.verdict === "up") return target ? `hit ${target} on every set` : "every set done";
  if (s.verdict === "back") return "under 8 reps after adding weight";
  const short = last.sets.findIndex((x) => target !== null && parseInt(x.reps, 10) < target);
  if (short >= 0) return `set ${short + 1} stopped at ${last.sets[short].reps}`;
  if (last.sets.length < ex.sets) return `${last.sets.length} of ${ex.sets} sets logged`;
  return "keep building";
}

export function Sparkline({ values, tone }: { values: number[]; tone: string }) {
  if (values.length < 2) return <span className="block h-8 w-24" aria-hidden />;
  const min = Math.min(...values), max = Math.max(...values);
  const span = max - min || 1;
  const pts = values.map((v, i) => `${2 + (i * 92) / (values.length - 1)},${28 - ((v - min) / span) * 22 - (max === min ? -8 : 0)}`);
  const lastPt = pts.at(-1)!.split(",");
  return (
    <svg viewBox="0 0 96 32" className="h-8 w-24" aria-hidden>
      <polyline points={pts.join(" ")} fill="none" stroke={tone} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={lastPt[0]} cy={lastPt[1]} r={3.5} fill={tone} />
    </svg>
  );
}

const TONE: Record<string, string> = { up: "var(--up)", hold: "var(--muted)", back: "var(--warn)", first: "var(--warn)" };

export function ProgressView() {
  const store = useStore();
  const plan = usePlan();
  const hydrated = useHydrated();
  const [selected, setSelected] = useState<string | null>(null);
  if (!hydrated) return null;
  const fallback = nextPlanDay(plan.days, store.log);
  const day = plan.days.find((d) => d.id === selected) ?? fallback;

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-2xl">
      <header>
        <h1 className="font-display text-[34px] font-bold tracking-tight">Progress</h1>
        <p className="mt-1.5 text-sm text-[var(--muted)]">Top working weight per session, and what to do next time.</p>
      </header>

      <div role="tablist" aria-label="Training day" className="flex gap-1 overflow-x-auto rounded-2xl bg-[var(--card)] p-1">
        {plan.days.map((d) => (
          <button key={d.id} type="button" role="tab" aria-selected={d.id === day.id} onClick={() => setSelected(d.id)}
            className={`h-10 min-w-24 flex-1 shrink-0 rounded-xl px-3 text-sm whitespace-nowrap ${d.id === day.id ? "bg-[var(--fg)] font-bold text-[var(--bg)]" : "font-semibold text-[var(--muted)]"}`}>
            {d.letter} {d.name.replace(/ Day$/, "")}
          </button>
        ))}
      </div>

      <ul className="flex flex-col gap-2" aria-label={`${day.name} exercises`}>
        {dayExercises(day).map((ex) => {
          const history = historyFor(store.log, ex.name);
          const values = history.map(sessionValue).filter((v): v is number => v !== null).slice(-8);
          const s = suggestFor(ex, history);
          const current = values.at(-1);
          const bodyweight = history.length > 0 && sessionTop(history.at(-1)!) === null;
          const started = values[0];
          return (
            <li key={ex.order}>
              <Link href={`/progress/${day.id}/${encodeURIComponent(ex.order)}`}
                className="grid grid-cols-[minmax(0,1fr)_6rem] items-center gap-x-3 gap-y-1 rounded-[18px] bg-[var(--card)] px-4 py-3.5">
                <span className="truncate font-bold">{ex.name} <span aria-hidden className="text-[var(--faint)]">›</span></span>
                <span className="row-span-2"><Sparkline values={values} tone={TONE[s.verdict]} /></span>
                <span className="text-[13px] text-[var(--muted)]">
                  {current !== undefined ? (
                    <><strong className="font-display text-lg text-[var(--fg)]">{current}</strong> {bodyweight ? "reps" : "kg"} · started {started}</>
                  ) : ex.startWeight ? `Start at ${ex.startWeight === "BW" ? "bodyweight" : ex.startWeight}` : "Not logged yet"}
                </span>
                <span className={`col-span-2 mt-1.5 border-t border-[var(--line)] pt-2 text-[13px] font-bold ${VERDICT_TEXT[s.verdict]}`}>
                  Next: {s.weight && s.weight !== "BW" ? `${s.verdict === "up" ? "↑" : s.verdict === "back" ? "↓" : "="} ${s.weight} kg` : s.label} — {reasonFor(ex, history)}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
      {!dayExercises(day).length && <p className="text-[var(--muted)]">No exercises on this day. <Link href={`/plan/${day.id}`} className="font-semibold text-[var(--accent)]">Edit {day.name}</Link></p>}
      <p className="flex items-center gap-2 text-xs text-[var(--faint)]"><DayLetter letter={day.letter} size="sm" /> Tap an exercise for its chart and every session.</p>
    </div>
  );
}
