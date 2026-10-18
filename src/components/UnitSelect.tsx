"use client";

import type { Unit } from "@/lib/store";

export function UnitSelect({ value, onChange }: { value: Unit; onChange: (u: Unit) => void }) {
  return (
    <label className="relative">
      <span className="sr-only">Weight unit</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as Unit)}
        className="h-9 appearance-none rounded-lg border border-[var(--line)] bg-[var(--soft)] pr-8 pl-3 text-base font-semibold outline-none focus:border-[var(--accent)]"
      >
        <option value="kg">kg</option>
        <option value="lb">lb</option>
      </select>
      <svg viewBox="0 0 24 24" className="pointer-events-none absolute top-1/2 right-2 h-4 w-4 -translate-y-1/2 text-[var(--muted)]" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
        <path d="m7 10 5 5 5-5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </label>
  );
}
