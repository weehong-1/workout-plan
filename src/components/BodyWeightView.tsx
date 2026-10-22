"use client";

import { useRef, useState } from "react";
import { UnitSelect } from "@/components/UnitSelect";
import { WeightChart, type WeightPoint, shortDate } from "@/components/WeightChart";
import {
  convertWeight,
  deleteBodyWeight,
  flushChanges,
  getPersistenceStatus,
  saveBodyWeight,
  setBodyUnit,
  todayString,
  useHydrated,
  useStore,
} from "@/lib/store";

const RANGES = [
  { id: "1m", label: "1M", days: 30, name: "30 days" },
  { id: "3m", label: "3M", days: 91, name: "3 months" },
  { id: "1y", label: "1Y", days: 365, name: "year" },
  { id: "all", label: "All", days: Infinity, name: "all time" },
] as const;

const DAY = 86_400_000;
const HISTORY_PREVIEW = 14;

const parse = (date: string) => {
  const [y, m, d] = date.split("/").map(Number);
  return new Date(y, m - 1, d).getTime();
};

// Signed change with a true minus sign, e.g. "+0.4" or "−1.2".
const signed = (n: number) => `${n > 0 ? "+" : n < 0 ? "−" : "±"}${Math.abs(n).toFixed(1)}`;

export function BodyWeightView() {
  const { bodyWeights, bodyUnit } = useStore();
  const hydrated = useHydrated();
  const [date, setDate] = useState(() => todayString().replace(/\//g, "-"));
  const [value, setValue] = useState("");
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const submitting = useRef(false);
  const [showAll, setShowAll] = useState(false);
  const [range, setRange] = useState<(typeof RANGES)[number]["id"]>("3m");

  if (!hydrated) return null;

  // Everything is shown in the chosen unit, whatever unit it was logged in.
  const all: WeightPoint[] = bodyWeights.map((b) => ({
    date: b.date,
    t: parse(b.date),
    value: convertWeight(b.weight, b.unit, bodyUnit),
  }));
  const latest = all.at(-1);
  const r = RANGES.find((x) => x.id === range)!;
  const cutoff = Date.now() - r.days * DAY;
  const points = all.filter((p) => p.t >= cutoff);
  const entryDate = date.replace(/-/g, "/");
  const existing = bodyWeights.some((b) => b.date === entryDate);
  const num = /^\d+(?:\.\d*)?$/.test(value.trim()) ? Number(value) : NaN;
  const validDate = /^\d{4}-\d{2}-\d{2}$/.test(date)
    && todayString(new Date(parse(entryDate))) === entryDate && entryDate <= todayString();
  const valid = Number.isFinite(num) && num > 0 && num < 1000 && validDate;

  const save = async () => {
    if (!valid || submitting.current) return;
    submitting.current = true;
    setSaving(true);
    setSaved(false);
    setSaveError("");
    try {
      if (!saveBodyWeight({ date: entryDate, weight: num, unit: bodyUnit })) {
        throw new Error("Your account is not ready to save, or the weight or date is invalid.");
      }
      if (!await flushChanges()) throw new Error(getPersistenceStatus().error ?? "Please keep this page open and retry.");
      setValue("");
      setSaved(true);
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Please try again.");
    } finally {
      submitting.current = false;
      setSaving(false);
    }
  };

  const download = () => {
    const rows = [
      ["Date", `Weight (${bodyUnit})`],
      ...all.map((p) => [p.date, p.value.toFixed(1)]),
    ];
    const url = URL.createObjectURL(
      new Blob([rows.map((r) => r.join(",")).join("\n")], { type: "text/csv" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "body-weight.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
        className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4"
      >
        <fieldset disabled={saving}>
        <div className="flex items-center justify-between">
          <h2 className="font-semibold">Log your weight</h2>
          <UnitSelect value={bodyUnit} onChange={setBodyUnit} />
        </div>
        <div className="mt-3 flex gap-2">
          <label className="relative min-w-0 flex-1">
            <span className="sr-only">Weight in {bodyUnit}</span>
            <input
              inputMode="decimal"
              enterKeyHint="done"
              value={value}
              onChange={(e) => { setValue(e.target.value.replace(",", ".")); setSaved(false); setSaveError(""); }}
              placeholder={latest ? latest.value.toFixed(1) : "0.0"}
              className="h-14 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] pr-10 pl-4 text-xl font-semibold tabular-nums outline-none placeholder:font-normal placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]"
            />
            <span className="pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-sm text-[var(--muted)]">{bodyUnit}</span>
          </label>
          <label className="w-[9.5rem] shrink-0">
            <span className="sr-only">Date</span>
            <input
              type="date"
              value={date}
              max={todayString().replace(/\//g, "-")}
              onChange={(e) => { setDate(e.target.value); setSaved(false); setSaveError(""); }}
              className="h-14 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none focus:border-[var(--accent)]"
            />
          </label>
        </div>
        <button
          type="submit"
          disabled={!valid || saving}
          className="mt-3 h-12 w-full rounded-xl bg-[var(--accent)] font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40"
        >
          {saving ? "Saving…" : saveError ? "Retry saving" : saved ? "Saved ✓" : existing ? `Update ${shortDate(parse(entryDate))}` : "Save"}
        </button>
        </fieldset>
        <p role={saveError ? "alert" : "status"} className={`mt-2 text-xs ${saveError ? "text-red-600 dark:text-red-400" : "text-[var(--muted)]"}`}>
          {saveError ? `Weight has not been saved to the database: ${saveError}` : saving ? "Saving your weight to the database…" : saved ? "Weight saved to your account." : "Save stores your weight in your account, available after a reload or on another device."}
        </p>
      </form>

      {latest ? (
        <>
          <section className="mt-4 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
            <p className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Latest</p>
            <p className="mt-1 text-4xl font-bold tabular-nums">
              {latest.value.toFixed(1)}
              <span className="ml-1 text-lg font-medium text-[var(--muted)]">{bodyUnit}</span>
            </p>
            <p className="mt-1 text-sm text-[var(--muted)]">
              {shortDate(latest.t)}
              {all.length > 1 && ` · ${signed(latest.value - all[all.length - 2].value)} ${bodyUnit} vs previous`}
            </p>
          </section>

          <section className="mt-4 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
            <div className="flex items-center justify-between gap-3">
              <h2 className="font-semibold">Body weight ({bodyUnit})</h2>
              <div role="radiogroup" aria-label="Range" className="flex shrink-0 rounded-lg bg-[var(--soft)] p-0.5 text-xs font-semibold">
                {RANGES.map((x) => (
                  <button
                    key={x.id}
                    role="radio"
                    aria-checked={range === x.id}
                    onClick={() => setRange(x.id)}
                    className={`h-8 w-10 rounded-md ${
                      range === x.id ? "bg-[var(--card)] text-[var(--fg)] shadow-sm" : "text-[var(--muted)]"
                    }`}
                  >
                    {x.label}
                  </button>
                ))}
              </div>
            </div>
            {points.length > 1 && (
              <p className="mt-1 text-sm text-[var(--muted)]">
                {signed(points[points.length - 1].value - points[0].value)} {bodyUnit} over {r.days === Infinity ? "all time" : `the last ${r.name}`}
              </p>
            )}
            <div className="mt-4">
              {points.length ? (
                <WeightChart points={points} unit={bodyUnit} />
              ) : (
                <p className="py-12 text-center text-sm text-[var(--muted)]">No entries in this range.</p>
              )}
            </div>
            {points.length > 0 && (
              <p className="mt-1 text-center text-xs text-[var(--muted)]">Touch the chart to see each day.</p>
            )}
          </section>

          <section className="mt-4">
            <div className="mb-2 flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-[var(--muted)]">History</h2>
              <button onClick={download} className="h-9 px-1 text-sm font-medium text-[var(--accent)]">
                Download CSV
              </button>
            </div>
            <ul className="divide-y divide-[var(--line)] rounded-2xl border border-[var(--line)] bg-[var(--card)]">
              {all
                .map((p, i) => ({ ...p, delta: i ? p.value - all[i - 1].value : null }))
                .reverse()
                .slice(0, showAll ? undefined : HISTORY_PREVIEW)
                .map((p) => (
                  <li key={p.date} className="flex items-center gap-3 py-1 pr-1 pl-4">
                    <span className="flex-1 text-sm text-[var(--muted)]">{p.date}</span>
                    <span className="w-14 text-right text-xs tabular-nums text-[var(--muted)]">
                      {p.delta !== null && signed(p.delta)}
                    </span>
                    <span className="w-20 text-right font-semibold tabular-nums">
                      {p.value.toFixed(1)} <span className="text-xs font-normal text-[var(--muted)]">{bodyUnit}</span>
                    </span>
                    <button
                      onClick={() => confirm(`Delete the ${p.date} entry?`) && deleteBodyWeight(p.date)}
                      aria-label={`Delete ${p.date}`}
                      className="grid h-11 w-11 place-items-center text-[var(--muted)]"
                    >
                      <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.2} aria-hidden>
                        <path d="M6 6l12 12M18 6 6 18" strokeLinecap="round" />
                      </svg>
                    </button>
                  </li>
                ))}
            </ul>
            {all.length > HISTORY_PREVIEW && (
              <button
                onClick={() => setShowAll((v) => !v)}
                className="mt-2 h-11 w-full text-sm font-medium text-[var(--muted)]"
              >
                {showAll ? "Show less" : `Show all ${all.length} entries`}
              </button>
            )}
          </section>
        </>
      ) : (
        <p className="mt-8 text-center text-sm text-[var(--muted)]">
          No entries yet. Weigh in at the same time of day (like first thing in the morning) so the trend is fair.
        </p>
      )}
    </>
  );
}
