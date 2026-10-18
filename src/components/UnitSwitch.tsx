"use client";

import type { Unit } from "@/lib/store";

export function UnitSwitch({ value, onChange }: { value: Unit; onChange: (u: Unit) => void }) {
  return (
    <div role="radiogroup" aria-label="Weight unit" className="flex rounded-lg bg-[var(--soft)] p-0.5 text-xs font-semibold">
      {(["kg", "lb"] as const).map((u) => (
        <button
          key={u}
          role="radio"
          aria-checked={value === u}
          onClick={() => onChange(u)}
          className={`h-8 w-11 rounded-md ${
            value === u ? "bg-[var(--card)] text-[var(--fg)] shadow-sm" : "text-[var(--muted)]"
          }`}
        >
          {u}
        </button>
      ))}
    </div>
  );
}
