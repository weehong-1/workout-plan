"use client";

import { UnitSwitch } from "@/components/UnitSwitch";
import { setDefaultUnit, useHydrated, useStore } from "@/lib/store";

export function DefaultUnit() {
  const { defaultUnit } = useStore();
  const hydrated = useHydrated();
  return (
    <div
      className={`mt-8 flex items-center justify-between gap-4 rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 ${
        hydrated ? "" : "invisible"
      }`}
    >
      <div className="min-w-0">
        <p className="font-semibold">Default weight unit</p>
        <p className="text-sm text-[var(--muted)]">
          Switch any exercise to the other unit on its card. It remembers per exercise.
        </p>
      </div>
      <UnitSwitch value={defaultUnit} onChange={setDefaultUnit} />
    </div>
  );
}
