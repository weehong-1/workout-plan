"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { getState, setRestTimer, useStore } from "@/lib/store";

type Ctx = {
  start: (seconds: number, label: string) => void;
};

const TimerContext = createContext<Ctx>({ start: () => {} });

export const useRestTimer = () => useContext(TimerContext);

function fmt(sec: number) {
  const s = Math.max(0, Math.ceil(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

export function RestTimerProvider({ children }: { children: React.ReactNode }) {
  const { timer } = useStore();
  const [now, setNow] = useState(() => Date.now());
  const audio = useRef<AudioContext | null>(null);
  const alerted = useRef(false);

  // The account's running timer is restored with the rest of its database data.
  useEffect(() => {
    if (!timer) return;
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [timer]);

  const remaining = timer ? (timer.endAt - now) / 1000 : 0;

  useEffect(() => {
    if (!timer || remaining > 0 || alerted.current) return;
    alerted.current = true;
    navigator.vibrate?.([300, 120, 300, 120, 300]);
    const ctx = audio.current;
    if (ctx) {
      [0, 0.25, 0.5].forEach((offset) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.value = 880;
        gain.gain.setValueAtTime(0.25, ctx.currentTime + offset);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + offset + 0.2);
        osc.connect(gain).connect(ctx.destination);
        osc.start(ctx.currentTime + offset);
        osc.stop(ctx.currentTime + offset + 0.2);
      });
    }
  }, [timer, remaining]);

  const start = useCallback((seconds: number, label: string) => {
    // Audio must be unlocked from a tap, so create it here.
    try {
      audio.current ??= new AudioContext();
      void audio.current.resume();
    } catch {}
    alerted.current = false;
    setNow(Date.now());
    setRestTimer({ endAt: Date.now() + seconds * 1000, total: seconds, label });
  }, []);

  const adjust = (delta: number) => {
    const t = getState().timer;
    if (!t) return;
    alerted.current = false;
    setNow(Date.now());
    setRestTimer({ ...t, endAt: Math.max(Date.now(), t.endAt) + delta * 1000, total: Math.max(1, t.total + delta) });
  };

  const done = timer !== null && remaining <= 0;
  const progress = timer ? Math.min(1, Math.max(0, 1 - remaining / timer.total)) : 0;

  return (
    <TimerContext.Provider value={{ start }}>
      {children}
      {timer && (
        <div className="fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-30 px-3 pb-2">
          <div
            className={`mx-auto max-w-xl overflow-hidden rounded-2xl border shadow-lg ${
              done
                ? "border-emerald-500 bg-emerald-600 text-white"
                : "border-[var(--line)] bg-[var(--card)]"
            }`}
          >
            <div
              className="h-1 bg-[var(--accent)] transition-[width] duration-200"
              style={{ width: `${progress * 100}%` }}
            />
            <div className="flex items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className={`truncate text-xs ${done ? "text-white/80" : "text-[var(--muted)]"}`}>
                  {done ? "Rest over. Next set!" : `Resting · ${timer.label}`}
                </p>
                <p className="font-mono text-3xl font-semibold tabular-nums leading-tight">
                  {done ? "0:00" : fmt(remaining)}
                </p>
              </div>
              {!done && (
                <>
                  <button
                    onClick={() => adjust(-15)}
                    className="h-11 rounded-xl border border-[var(--line)] px-3 text-sm font-medium active:scale-95"
                  >
                    −15s
                  </button>
                  <button
                    onClick={() => adjust(15)}
                    className="h-11 rounded-xl border border-[var(--line)] px-3 text-sm font-medium active:scale-95"
                  >
                    +15s
                  </button>
                </>
              )}
              <button
                onClick={() => setRestTimer(null)}
                className={`h-11 rounded-xl px-4 text-sm font-semibold active:scale-95 ${
                  done ? "bg-white text-emerald-700" : "bg-[var(--fg)] text-[var(--bg)]"
                }`}
              >
                {done ? "OK" : "Skip"}
              </button>
            </div>
          </div>
        </div>
      )}
    </TimerContext.Provider>
  );
}
