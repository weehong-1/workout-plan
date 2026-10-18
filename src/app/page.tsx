import Link from "next/link";
import { DayBadge } from "@/components/DayBadge";
import { DefaultUnit } from "@/components/DefaultUnit";
import { NextUp } from "@/components/NextUp";
import { DAYS } from "@/lib/plan";

export default function Home() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Training Plan</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">
          Push / Pull + Supersets + Arm Day · 3 days a week, A → B → C
        </p>
      </header>

      <NextUp />

      <h2 className="mt-8 mb-3 text-sm font-semibold uppercase tracking-wider text-[var(--muted)]">
        All days
      </h2>
      <ul className="space-y-3">
        {DAYS.map((day) => {
          const exercises = day.blocks.flatMap((b) => b.exercises);
          return (
            <li key={day.id}>
              <Link
                href={`/day/${day.id}`}
                className="flex items-center gap-4 rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 active:scale-[0.99]"
              >
                <DayBadge letter={day.letter} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline gap-2">
                    <h3 className="font-semibold">{day.name}</h3>
                    {day.optional && (
                      <span className="rounded-full bg-[var(--soft)] px-2 py-0.5 text-[11px] font-medium text-[var(--muted)]">
                        Optional
                      </span>
                    )}
                  </div>
                  <p className="truncate text-sm text-[var(--muted)]">
                    {exercises.map((e) => e.name).join(" · ")}
                  </p>
                  <p className="mt-0.5 text-xs text-[var(--muted)]">
                    {exercises.length} exercises · {day.duration}
                  </p>
                </div>
                <svg viewBox="0 0 24 24" className="h-5 w-5 shrink-0 text-[var(--muted)]" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
                  <path d="m9 6 6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </Link>
            </li>
          );
        })}
      </ul>

      <DefaultUnit />
    </>
  );
}
