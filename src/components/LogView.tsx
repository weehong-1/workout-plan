"use client";

import Link from "next/link";
import { useState } from "react";
import { DayBadge } from "@/components/DayBadge";
import { dayLabel, getDay } from "@/lib/plan";
import { type LogEntry, deleteSession, formatSet, sessionsOf, useHydrated, useStore } from "@/lib/store";

// One row per exercise, columns matching the sheet:
// Date | Day | Exercise | Set 1 | Set 2 | Notes
// Extra sets get their own Set 3, Set 4... columns before Notes.
function setColumns(log: LogEntry[]): number {
  return Math.max(2, ...log.map((e) => e.sets.length));
}

function toRows(log: LogEntry[]): string[][] {
  const columns = setColumns(log);
  return log.map((e) => {
    const day = getDay(e.dayId);
    const notes = [e.substitute && `Used substitute: ${e.substitute}`, e.notes].filter(Boolean).join(". ");
    return [
      e.date,
      day ? dayLabel(day) : e.dayId,
      e.exercise,
      ...Array.from({ length: columns }, (_, i) => formatSet(e.sets[i] ?? { weight: "", reps: "" })),
      notes,
    ];
  });
}

function header(log: LogEntry[]): string[] {
  const sets = Array.from({ length: setColumns(log) }, (_, i) => `Set ${i + 1}`);
  return ["Date", "Day", "Exercise", ...sets, "Notes"];
}

function csvCell(v: string) {
  return /[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v;
}

export function LogView() {
  const { log } = useStore();
  const hydrated = useHydrated();
  const [copied, setCopied] = useState(false);
  const sessions = sessionsOf(log);

  if (!hydrated) return null;

  if (!sessions.length) {
    return (
      <div className="rounded-3xl border border-dashed border-[var(--line)] p-8 text-center">
        <p className="font-semibold">No workouts logged yet</p>
        <p className="mt-1 text-sm text-[var(--muted)]">
          Finish a workout and it shows up here.
        </p>
        <Link
          href="/"
          className="mt-4 inline-flex h-11 items-center rounded-xl bg-[var(--accent)] px-5 font-semibold text-[var(--accent-fg)]"
        >
          Go to plan
        </Link>
      </div>
    );
  }

  const copy = async () => {
    // Tab separated so it pastes straight into Google Sheets columns.
    const text = toRows(log).map((r) => r.join("\t")).join("\n");
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      alert("Couldn't access the clipboard. Try Download CSV instead.");
    }
  };

  const download = () => {
    const csv = [header(log), ...toRows(log)].map((r) => r.map(csvCell).join(",")).join("\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "training-log.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <div className="mb-5 grid grid-cols-2 gap-2">
        <button
          onClick={copy}
          className="h-12 rounded-xl bg-[var(--fg)] text-sm font-semibold text-[var(--bg)] active:scale-[0.98]"
        >
          {copied ? "Copied!" : "Copy for Sheets"}
        </button>
        <button
          onClick={download}
          className="h-12 rounded-xl border border-[var(--line)] text-sm font-semibold active:scale-[0.98]"
        >
          Download CSV
        </button>
      </div>

      <ul className="space-y-3">
        {sessions.map((s) => {
          const day = getDay(s.dayId);
          return (
            <li key={`${s.date}-${s.dayId}-${s.savedAt}`} className="rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4">
              <div className="flex items-center gap-3">
                {day && <DayBadge letter={day.letter} />}
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{day ? dayLabel(day) : s.dayId}</p>
                  <p className="text-sm text-[var(--muted)]">{s.date}</p>
                </div>
                <button
                  onClick={() => {
                    if (confirm(`Delete the ${s.date} ${day?.name ?? ""} session?`)) {
                      deleteSession(s.date, s.dayId, s.savedAt);
                    }
                  }}
                  className="h-10 rounded-lg px-3 text-sm font-medium text-red-600 dark:text-red-400"
                >
                  Delete
                </button>
              </div>
              <ul className="mt-3 divide-y divide-[var(--line)] text-sm">
                {s.entries.map((e) => (
                  <li key={e.id} className="py-2">
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="min-w-0 font-medium">{e.substitute ?? e.exercise}</span>
                      <span className="shrink-0 tabular-nums text-[var(--muted)]">
                        {e.sets.map(formatSet).join(", ")}
                      </span>
                    </div>
                    {(e.substitute || e.notes) && (
                      <p className="mt-0.5 text-xs text-[var(--muted)]">
                        {[e.substitute && `Sub for ${e.exercise}`, e.notes].filter(Boolean).join(" · ")}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </li>
          );
        })}
      </ul>
    </>
  );
}
