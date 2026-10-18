"use client";

import Link from "next/link";
import { DayBadge } from "@/components/DayBadge";
import { DAYS, dayLabel, getDay, nextDayId } from "@/lib/plan";
import { useHydrated, useStore } from "@/lib/store";

export function NextUp() {
  const { log, drafts } = useStore();
  const hydrated = useHydrated();

  const last = log.length ? log[log.length - 1] : undefined;
  const inProgress = DAYS.find((d) => drafts[d.id]);
  const day = inProgress ?? getDay(nextDayId(last?.dayId))!;
  const exerciseCount = day.blocks.reduce((n, b) => n + b.exercises.length, 0);

  return (
    <section
      className={`rounded-3xl border border-[var(--line)] bg-[var(--card)] p-5 transition-opacity ${
        hydrated ? "opacity-100" : "opacity-0"
      }`}
    >
      <p className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
        {inProgress ? "In progress" : "Next up"}
      </p>
      <div className="mt-3 flex items-center gap-4">
        <DayBadge letter={day.letter} size="lg" />
        <div className="min-w-0">
          <h2 className="text-2xl font-bold leading-tight">{day.name}</h2>
          <p className="text-sm text-[var(--muted)]">
            {exerciseCount} exercises · {day.duration}
          </p>
        </div>
      </div>
      <p className="mt-3 text-sm text-[var(--muted)]">{day.focus}</p>
      {last && !inProgress && (
        <p className="mt-2 text-sm text-[var(--muted)]">
          Last session: {dayLabel(getDay(last.dayId)!)} on {last.date}
        </p>
      )}
      <Link
        href={`/day/${day.id}`}
        className="mt-4 flex h-14 items-center justify-center rounded-2xl bg-[var(--accent)] text-lg font-semibold text-[var(--accent-fg)] active:scale-[0.98]"
      >
        {inProgress ? "Continue workout" : "Start workout"}
      </Link>
      {last?.dayId === "c" && !inProgress && (
        <p className="mt-3 text-center text-sm text-[var(--muted)]">
          Feeling good this week?{" "}
          <Link href="/day/d" className="font-medium text-[var(--day-d)] underline">
            Add D Arm Day
          </Link>
        </p>
      )}
    </section>
  );
}
