"use client";

import type { Unit } from "@/lib/store";

// kg: whole kilos plus 1.25 kg plate steps. lb: 2.5 lb steps.
function build(max: number, include: (v: number) => boolean, step: number) {
  const out: string[] = [];
  for (let i = 0; i * step <= max; i++) {
    const v = i * step;
    if (include(v)) out.push(String(Number(v.toFixed(2))));
  }
  return out;
}

const OPTIONS: Record<Unit, string[]> = {
  kg: build(300, (v) => Number.isInteger(v) || Number.isInteger(v / 1.25), 0.25),
  lb: build(600, () => true, 2.5),
};

const OTHER = "__other";

export function WeightSelect({
  value,
  unit,
  near,
  label,
  onChange,
}: {
  value: string;
  unit: Unit;
  // Weight the wheel should open next to when nothing is picked yet. It is
  // never filled in for you.
  near: string;
  label: string;
  onChange: (v: string) => void;
}) {
  const options = [...OPTIONS[unit]];
  // A typed in or other unit value still has to show up as selected.
  if (value && value !== "BW" && !options.includes(value)) {
    options.push(value);
    options.sort((a, b) => parseFloat(a) - parseFloat(b));
  }
  // The empty choice sits just before `near`, so the native picker opens there.
  const at = near ? options.findIndex((o) => parseFloat(o) >= parseFloat(near)) : -1;
  const placeholder = <option key="none" value="">—</option>;
  const items = options.map((o) => (
    <option key={o} value={o}>
      {o}
    </option>
  ));
  if (at > 0) items.splice(at, 0, placeholder);
  else items.unshift(placeholder);

  return (
    <label className="relative min-w-0 flex-1">
      <span className="sr-only">{label}</span>
      <select
        value={value}
        onChange={(e) => {
          if (e.target.value !== OTHER) return onChange(e.target.value);
          const typed = window.prompt(`Weight in ${unit}`, value || near)?.trim().replace(",", ".");
          if (typed && Number.isFinite(Number(typed)) && Number(typed) >= 0) {
            onChange(String(Number(typed)));
          }
        }}
        className={`h-12 w-full appearance-none rounded-xl border border-[var(--line)] bg-[var(--bg)] pr-12 pl-3 tabular-nums outline-none focus:border-[var(--accent)] ${
          value ? "" : "text-[var(--muted)]"
        }`}
      >
        <option value="BW">BW (bodyweight)</option>
        {items}
        <option value={OTHER}>Other…</option>
      </select>
      <span className="pointer-events-none absolute top-1/2 right-3 flex -translate-y-1/2 items-center gap-1 text-xs text-[var(--muted)]">
        {value && value !== "BW" && unit}
        <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
          <path d="m7 10 5 5 5-5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    </label>
  );
}
