"use client";

import Link from "next/link";
import { useState } from "react";
import { DayBadge } from "@/components/DayBadge";
import { TrainingPlanDialog } from "@/components/TrainingPlanDialog";
import { DAYS } from "@/lib/plan";
import { usePersistenceStatus, useStore } from "@/lib/store";
import type { TrainingPlan } from "@/lib/training-plan";

function Chevron() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5 shrink-0 text-[var(--muted)]" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
      <path d="m9 6 6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function TrainingPlans() {
  const { trainingPlans, dayExercises } = useStore();
  const { ready, locked } = usePersistenceStatus();
  const [dialog, setDialog] = useState<{ plan?: TrainingPlan } | null>(null);
  const [announcement, setAnnouncement] = useState("");

  return (
    <>
      <h2 className="mt-8 mb-3 text-sm font-semibold uppercase tracking-wider text-[var(--muted)]">
        All days
      </h2>
      <ul className="space-y-3">
        {DAYS.map((day) => {
          const exercises = day.blocks.flatMap((block) => block.exercises);
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
                    {exercises.map((exercise) => exercise.name).join(" · ")}
                  </p>
                  <p className="mt-0.5 text-xs text-[var(--muted)]">
                    {exercises.length + (dayExercises[day.id]?.length ?? 0)} exercises · {day.duration}
                  </p>
                </div>
                <Chevron />
              </Link>
            </li>
          );
        })}
        {trainingPlans.map((plan) => {
          const count = plan.blocks.reduce((total, block) => total + block.exercises.length, 0);
          return (
            <li key={plan.id}>
              <button
                type="button"
                onClick={() => setDialog({ plan })}
                aria-haspopup="dialog"
                aria-label={`Edit ${plan.name}, ${count} ${count === 1 ? "exercise" : "exercises"}`}
                disabled={!ready || locked}
                className="flex w-full items-center gap-4 rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 text-left active:scale-[0.99] disabled:opacity-40"
              >
                <span aria-hidden="true" className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--soft)] text-lg font-bold text-[var(--accent)]">
                  {plan.name.slice(0, 1).toUpperCase()}
                </span>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-semibold">{plan.name}</h3>
                  <p className="truncate text-sm text-[var(--muted)]">{plan.description || "No description"}</p>
                  <p className="mt-0.5 text-xs text-[var(--muted)]">{count} {count === 1 ? "exercise" : "exercises"}</p>
                </div>
                <Chevron />
              </button>
            </li>
          );
        })}
        <li>
          <button
            type="button"
            onClick={() => { setAnnouncement(""); setDialog({}); }}
            aria-label="Add Training Plan"
            aria-haspopup="dialog"
            disabled={!ready || locked}
            className="flex w-full items-center gap-4 rounded-2xl border-2 border-dotted border-[var(--muted)] bg-[var(--card)] p-4 text-left transition-colors hover:border-[var(--accent)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[var(--accent)] active:scale-[0.99] disabled:opacity-40"
          >
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--soft)] text-[var(--accent)]">
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
                <path d="M12 5v14M5 12h14" strokeLinecap="round" />
              </svg>
            </span>
            <span className="min-w-0 flex-1">
              <span className="block font-semibold">Add Training Plan</span>
              <span className="block text-sm text-[var(--muted)]">Give your next plan a name and description</span>
            </span>
            <Chevron />
          </button>
        </li>
      </ul>
      <p role="status" className="mt-2 text-sm text-[var(--muted)]">{announcement}</p>
      {dialog && (
        <TrainingPlanDialog
          plan={dialog.plan}
          onClose={() => setDialog(null)}
          onSaved={(plan, created) => {
            setDialog(null);
            setAnnouncement(`${plan.name} ${created ? "created" : "updated"}.`);
          }}
        />
      )}
    </>
  );
}
