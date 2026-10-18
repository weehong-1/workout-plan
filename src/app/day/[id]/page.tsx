import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { DayBadge } from "@/components/DayBadge";
import { WakeLockToggle } from "@/components/WakeLock";
import { Workout } from "@/components/Workout";
import { DAYS, dayLabel, getDay } from "@/lib/plan";

type Props = { params: Promise<{ id: string }> };

export function generateStaticParams() {
  return DAYS.map((d) => ({ id: d.id }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const day = getDay((await params).id);
  return { title: day ? dayLabel(day) : "Training Plan" };
}

export default async function DayPage({ params }: Props) {
  const day = getDay((await params).id);
  if (!day) notFound();

  const hasSupersets = day.blocks.some((b) => b.exercises.length > 1);
  const hasStraight = day.blocks.some((b) => b.exercises.length === 1);

  return (
    <>
      <Link href="/" className="-ml-1 inline-flex h-10 items-center gap-1 text-sm font-medium text-[var(--muted)]">
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
          <path d="m15 6-6 6 6 6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        All days
      </Link>

      <header className="mt-1 mb-4">
        <div className="flex items-center gap-4">
          <DayBadge letter={day.letter} size="lg" />
          <div className="min-w-0">
            <h1 className="text-2xl font-bold leading-tight">
              {day.name}
              {day.optional && <span className="ml-2 align-middle text-sm font-medium text-[var(--muted)]">Optional</span>}
            </h1>
            <p className="text-sm text-[var(--muted)]">About {day.duration}</p>
          </div>
        </div>
        <p className="mt-3 text-sm text-[var(--muted)]">{day.focus}</p>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <WakeLockToggle />
        </div>

        <details className="group mt-3 rounded-2xl border border-[var(--line)] bg-[var(--card)] px-4 py-3 text-sm">
          <summary className="flex cursor-pointer list-none items-center justify-between font-semibold">
            How to run this session
            <svg viewBox="0 0 24 24" className="h-5 w-5 text-[var(--muted)] transition-transform group-open:rotate-180" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
              <path d="m6 9 6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </summary>
          <ul className="mt-2 list-disc space-y-1.5 pl-5 text-[var(--muted)]">
            <li>
              Warm up every exercise with 8 / 5 / 3 reps at roughly 50%, 70% and 85% of your working weight. Rest 30~60 sec between warm up sets.
            </li>
            {hasStraight && (
              <li>Straight sets come first. Do all sets of one exercise before moving on.</li>
            )}
            {hasSupersets && (
              <li>
                Supersets: do A1, go straight into A2, then rest. That's one round. Finish both warm ups before the paired working sets.
              </li>
            )}
            <li>Leave 1~2 reps in reserve. Never train to failure or let your form break down.</li>
            <li>Tap ✓ after each working set to log it and start the rest timer. An empty set takes last session's weight and the target reps.</li>
          </ul>
        </details>
      </header>

      <Workout day={day} />
    </>
  );
}
