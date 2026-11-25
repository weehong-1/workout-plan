"use client";

import { useState } from "react";
import { askConfirm } from "@/components/ConfirmDialog";
import { reasonFor, sessionValue } from "@/components/ProgressView";
import { BackLink } from "@/components/ui";
import { dayExercises, sessionVolume, suggestFor, trainerWeight } from "@/lib/plan";
import { removePhotos } from "@/lib/photos";
import {
  type LogEntry,
  deleteSession,
  flushChanges,
  formatSet,
  historyFor,
  sessionPhotos,
  useHydrated,
  usePlan,
  useStore,
} from "@/lib/store";

const VERDICT_BOX: Record<string, string> = {
  up: "bg-[var(--up-bg)] text-[var(--up)]",
  hold: "bg-[var(--card)] text-[var(--fg)]",
  back: "bg-[var(--warn-bg)] text-[var(--warn)]",
  first: "bg-[var(--warn-bg)] text-[var(--warn)]",
};

function shortDate(date: string) {
  const [y, m, d] = date.split("/").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });
}

export function ExerciseDetail({ dayId, order }: { dayId: string; order: string }) {
  const store = useStore();
  const plan = usePlan();
  const hydrated = useHydrated();
  const [metric, setMetric] = useState<"top" | "volume">("top");
  if (!hydrated) return null;
  const day = plan.days.find((d) => d.id === dayId);
  const ex = day && dayExercises(day).find((e) => e.order === order);
  if (!day || !ex) return <p className="py-16 text-center">This exercise is no longer in your plan.</p>;

  const history = historyFor(store.log, ex.name);
  const s = suggestFor(ex, history);
  const points = history.map((e) => ({ date: e.date, value: metric === "top" ? sessionValue(e) : sessionVolume(e) }))
    .filter((p): p is { date: string; value: number } => p.value !== null).slice(-12);
  const trainer = metric === "top" ? trainerWeight(ex) : null;
  const latest = points.at(-1);
  const first = points[0];
  // Deletes the whole saved workout this entry belongs to, then its photo files.
  const removeWorkout = async (e: LogEntry) => {
    const name = plan.days.find((d) => d.id === e.dayId)?.name ?? "this";
    const photos = sessionPhotos(store, e.date, e.dayId, e.savedAt);
    const others = store.log.filter((x) => x.date === e.date && x.dayId === e.dayId && x.savedAt === e.savedAt).length - 1;
    const what = [others > 0 && `${others} other ${others === 1 ? "exercise" : "exercises"}`, photos.length && "its photos"].filter(Boolean).join(" and ");
    if (!(await askConfirm({ title: `Delete the ${name} workout from ${shortDate(e.date)}?`, body: `${what ? `This also removes ${what}. ` : ""}This can't be undone.`, confirmLabel: "Delete", danger: true }))) return;
    deleteSession(e.date, e.dayId, e.savedAt);
    // Delete the files only once the database no longer references them.
    if (photos.length && await flushChanges()) void removePhotos(photos);
  };
  const unit = metric === "volume" ? "kg" : history.length && sessionValue(history.at(-1)!) !== null && !history.at(-1)!.sets.some((x) => parseFloat(x.weight)) ? "reps" : "kg";

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-2xl">
      <header className="flex items-center gap-2">
        <BackLink href="/progress" label="Back to progress" />
        <h1 className="flex-1 truncate text-[17px] font-bold">{ex.name}</h1>
      </header>

      <section aria-label="Next session" className={`rounded-2xl px-4 py-3.5 ${VERDICT_BOX[s.verdict]}`}>
        <p className="text-[13px] font-semibold">Next session</p>
        <p className="font-display mt-1 text-2xl font-bold">
          {s.weight && s.weight !== "BW" ? `${s.weight} kg` : "Bodyweight"} × {ex.reps}
        </p>
        <p className="mt-1 text-[13px] opacity-80">{s.label} · {reasonFor(ex, history)}</p>
      </section>

      <section aria-label="Trend" className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between">
          <p>
            <strong className="font-display text-[28px]">{latest ? `${Math.round(latest.value * 100) / 100} ${unit}` : "—"}</strong>
            {latest && <span className="ml-2 text-[13px] text-[var(--muted)]">{shortDate(latest.date)}</span>}
          </p>
          {latest && first && latest !== first && (
            <span className={`text-[13px] font-bold ${latest.value >= first.value ? "text-[var(--up)]" : "text-[var(--warn)]"}`}>
              {latest.value >= first.value ? "+" : ""}{Math.round((latest.value - first.value) * 100) / 100} since {shortDate(first.date)}
            </span>
          )}
        </div>
        <TrendChart values={points.map((p) => p.value)} first={first?.date} last={latest?.date} reference={trainer} label={metric === "top" ? "Heaviest set per session" : "Volume per session"} />
        <div role="radiogroup" aria-label="Metric" className="flex gap-1.5">
          {([["top", "Heaviest"], ["volume", "Volume"]] as const).map(([key, label]) => (
            <button key={key} type="button" role="radio" aria-checked={metric === key} onClick={() => setMetric(key)}
              className={`h-9 rounded-full px-3.5 text-[13px] font-bold ${metric === key ? "bg-[var(--accent)] text-[var(--accent-fg)]" : "bg-[var(--card)] text-[#d4d4d8]"}`}>{label}</button>
          ))}
        </div>
      </section>

      <section aria-labelledby="recent" className="flex flex-col gap-2">
        <h2 id="recent" className="text-[13px] font-semibold text-[var(--muted)]">Sessions</h2>
        {history.length ? (
          <ul className="overflow-hidden rounded-2xl bg-[var(--card)]">
            {[...history].reverse().slice(0, 20).map((e) => (
              <li key={e.id} className="flex items-center gap-2 border-b border-[var(--soft)] py-1.5 pr-1 pl-3.5 last:border-0">
                <div className="min-w-0 flex-1 py-1.5">
                  <p className="flex justify-between gap-3 text-[13px] text-[var(--muted)]"><span className="shrink-0">{shortDate(e.date)}</span>{e.notes && <span className="truncate">“{e.notes}”</span>}</p>
                  <p className="font-display mt-1 font-bold">{e.sets.map((x) => formatSet(x, e.unit ?? "kg")).join(" · ")}</p>
                </div>
                <button type="button" onClick={() => void removeWorkout(e)} aria-label={`Delete the ${shortDate(e.date)} workout`}
                  className="grid h-11 w-11 shrink-0 place-items-center rounded-xl text-[var(--muted)] hover:text-[var(--danger)]">
                  <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 12h10l1-12M9 7V4h6v3" />
                  </svg>
                </button>
              </li>
            ))}
          </ul>
        ) : <p className="rounded-2xl bg-[var(--card)] p-4 text-sm text-[var(--muted)]">No sessions yet. {ex.startWeight ? `Start at ${ex.startWeight === "BW" ? "bodyweight" : ex.startWeight}.` : ""}</p>}
      </section>
    </div>
  );
}

function TrendChart({ values, first, last, reference, label }: { values: number[]; first?: string; last?: string; reference: number | null; label: string }) {
  if (values.length < 2) {
    return <p className="rounded-2xl bg-[var(--card)] p-6 text-center text-sm text-[var(--muted)]">Log two sessions to see the trend.</p>;
  }
  const all = reference !== null ? [...values, reference] : values;
  const min = Math.min(...all), max = Math.max(...all);
  const span = max - min || 1;
  const y = (v: number) => 140 - ((v - min) / span) * 112;
  const pts = values.map((v, i) => [10 + (i * 338) / (values.length - 1), y(v)]);
  return (
    <svg viewBox="0 0 358 160" className="h-40 w-full" role="img"
      aria-label={`${label}: ${values.join(", ")}${reference !== null ? `. Trainer level, June 2025: ${reference}` : ""}`}>
      <path d="M0 140H358" stroke="var(--soft)" />
      {reference !== null && (
        <>
          <path d={`M0 ${y(reference)}H358`} stroke="#3a3a40" strokeDasharray="4 4" />
          <text x="358" y={y(reference) - 6} fill="var(--muted)" fontSize="11" textAnchor="end">Trainer, Jun 2025 · {reference}</text>
        </>
      )}
      <polyline points={pts.map((p) => p.join(",")).join(" ")} fill="none" stroke="var(--accent)" strokeWidth={3} strokeLinejoin="round" strokeLinecap="round" />
      {pts.map(([x, py], i) => <circle key={i} cx={x} cy={py} r={i === pts.length - 1 ? 5 : 3.5} fill="var(--accent)" />)}
      {first && <text x="10" y="156" fill="var(--faint)" fontSize="11">{shortDate(first)}</text>}
      {last && <text x="348" y="156" fill="var(--faint)" fontSize="11" textAnchor="end">{shortDate(last)}</text>}
    </svg>
  );
}
