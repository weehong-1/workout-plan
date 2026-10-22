"use client";

import { useEffect, useRef, useState } from "react";
import { setKeepScreenOn, useStore } from "@/lib/store";

// Keeps the phone screen on while you train, where the browser supports it.
export function WakeLockToggle() {
  const [supported, setSupported] = useState(false);
  const { keepScreenOn: on } = useStore();
  const lock = useRef<WakeLockSentinel | null>(null);

  useEffect(() => setSupported("wakeLock" in navigator), []);

  useEffect(() => {
    if (!on) return;
    let cancelled = false;
    const acquire = async () => {
      try {
        const l = await navigator.wakeLock.request("screen");
        if (cancelled) void l.release();
        else lock.current = l;
      } catch {
        setKeepScreenOn(false);
      }
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") void acquire();
    };
    void acquire();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", onVisible);
      void lock.current?.release();
      lock.current = null;
    };
  }, [on]);

  if (!supported) return null;

  return (
    <button
      onClick={() => setKeepScreenOn(!on)}
      aria-pressed={on}
      className={`flex h-9 items-center gap-1.5 rounded-full border px-3 text-xs font-medium ${
        on
          ? "border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-fg)]"
          : "border-[var(--line)] text-[var(--muted)]"
      }`}
    >
      <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
        <path d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.4-6.4-.7.7M6.3 17.7l-.7.7m12.8 0-.7-.7M6.3 6.3l-.7-.7M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z" strokeLinecap="round" />
      </svg>
      {on ? "Screen stays on" : "Keep screen on"}
    </button>
  );
}
