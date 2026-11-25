"use client";

import Link from "next/link";
import { DayLetter, VERDICT_TEXT, btnPrimary } from "@/components/ui";
import { type Day, DAY_LETTERS, dayExercises, exerciseCount, nextPlanDay, suggestFor } from "@/lib/plan";
import { historyFor, unitFor, useHydrated, usePlan, useStore } from "@/lib/store";

// One job: tell you which day it is and what to lift, then start.
export function TodayView() {
  const store = useStore();
  const plan = usePlan();
  const hydrated = useHydrated();
  if (!hydrated) return null;

  const inProgress = plan.days.find((d) => store.drafts[d.id]);
  const day = inProgress ?? nextPlanDay(plan.days, store.log);
  const others = plan.days.filter((d) => d.id !== day.id);
  const exercises = dayExercises(day);
  const rotation = plan.days.filter((d) => !d.optional);
  const position = rotation.findIndex((d) => d.id === day.id);
  const date = new Date().toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-2xl">
      <header className="flex items-center justify-between">
        <p className="text-sm font-semibold text-[var(--muted)]">{date}</p>
        <Link href="/account" aria-label="Account" className="grid h-11 w-11 place-items-center rounded-full bg-[var(--card)] text-[var(--fg)]">
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" aria-hidden>
            <circle cx="12" cy="8" r="4" />
            <path d="M4 21a8 8 0 0 1 16 0" />
          </svg>
        </Link>
      </header>

      <section aria-labelledby="today-day">
        <p className="text-sm font-bold" style={{ color: `var(${["--day-a", "--day-b", "--day-c", "--day-d"][Math.max(0, DAY_LETTERS.indexOf(day.letter)) % 4]})` }}>
          {inProgress ? `${day.letter} · in progress` : day.optional ? `${day.letter} · optional extra` : `${day.letter} · ${ordinal(position + 1)} of ${rotation.length} in the rotation`}
        </p>
        <h1 id="today-day" className="font-display mt-1.5 text-5xl leading-none font-bold tracking-tight">{day.name}</h1>
        <p className="mt-2.5 text-[15px] text-[var(--muted)]">
          {exerciseCount(exercises.length)}{day.duration ? ` · about ${day.duration}` : ""}
        </p>
      </section>

      <section aria-label="Today's weights" className="overflow-hidden rounded-[22px] bg-[var(--card)]">
        <div className="flex justify-between px-4 pt-3.5 pb-1.5 text-xs font-bold uppercase tracking-[0.06em] text-[var(--muted)]">
          <span>Exercise</span>
          <span>Today</span>
        </div>
        {exercises.length ? (
          <ol>
            {exercises.map((ex) => {
              const history = historyFor(store.log, ex.name);
              const s = suggestFor(ex, history);
              const unit = unitFor(store, ex.name);
              return (
                <li key={ex.order} className="flex min-h-14 items-center gap-3 border-t border-[var(--line)] px-4 py-2">
                  <span className="min-w-0 flex-1">
                    <span className="block font-semibold">{ex.name}{ex.optional && <span className="ml-2 text-xs font-medium text-[var(--muted)]">optional</span>}</span>
                    <span className="block text-[13px] text-[var(--muted)]">{ex.sets} × {ex.reps}</span>
                  </span>
                  <span className="text-right">
                    <span className="font-display block text-xl font-bold">
                      {s.weight || "—"}{s.weight && s.weight !== "BW" && <span className="ml-0.5 text-xs font-medium text-[var(--muted)]">{unit}</span>}
                    </span>
                    <span className={`block text-xs font-bold ${VERDICT_TEXT[s.verdict]}`}>{s.label}</span>
                  </span>
                </li>
              );
            })}
          </ol>
        ) : (
          <p className="border-t border-[var(--line)] px-4 py-6 text-sm text-[var(--muted)]">
            No exercises yet. <Link href={`/plan/${day.id}`} className="font-semibold text-[var(--accent)]">Add some to {day.name}</Link>
          </p>
        )}
      </section>

      <Link href={`/day/${day.id}`} className={btnPrimary}>{inProgress ? "Continue workout" : "Start workout"}</Link>

      {others.length > 0 && (
        <section aria-labelledby="other-days" className="flex flex-col gap-2">
          <h2 id="other-days" className="text-xs font-bold uppercase tracking-[0.08em] text-[var(--muted)]">Or train another day</h2>
          <div className="flex flex-wrap gap-2">
            {others.map((d: Day) => (
              <Link key={d.id} href={`/day/${d.id}`} className="flex min-h-11 items-center gap-2 rounded-xl bg-[var(--card)] py-1.5 pr-3.5 pl-1.5 text-sm font-semibold">
                <DayLetter letter={d.letter} size="sm" />
                {d.name}
                {d.optional && <span className="text-xs font-medium text-[var(--muted)]">optional</span>}
              </Link>
            ))}
          </div>
        </section>
      )}
      <Link href="/plan" className="flex min-h-11 items-center justify-center text-sm font-semibold text-[var(--accent)]">Edit plan</Link>
    </div>
  );
}

function ordinal(n: number) {
  return n === 1 ? "1st" : n === 2 ? "2nd" : n === 3 ? "3rd" : `${n}th`;
}
