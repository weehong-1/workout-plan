"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useHydrated, useStore } from "@/lib/store";
import { syncEnabled } from "@/lib/supabase";
import { type SyncStatus, startSync, useSyncStatus } from "@/lib/sync";

// Starts background sync once for the whole app.
export function SyncStarter() {
  useEffect(() => startSync(), []);
  return null;
}

function ago(t: number, now: number) {
  const s = Math.round((now - t) / 1000);
  if (s < 45) return "just now";
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  return new Date(t).toLocaleDateString();
}

export function syncLine(st: SyncStatus, pending: number, now: number): string {
  switch (st.phase) {
    case "syncing":
      return "Syncing…";
    case "synced":
      return pending ? `${pending} change${pending > 1 ? "s" : ""} waiting to sync` : `Synced ${ago(st.lastSynced!, now)}`;
    case "offline":
      return pending ? `Offline. ${pending} change${pending > 1 ? "s" : ""} will sync when you're back online` : "Offline";
    case "error":
      return `Couldn't sync: ${st.error}`;
    default:
      return "";
  }
}

export function useNow(every = 30_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), every);
    return () => clearInterval(id);
  }, [every]);
  return now;
}

export function SyncCard() {
  const st = useSyncStatus();
  const { pending } = useStore();
  const hydrated = useHydrated();
  const now = useNow();
  if (!syncEnabled || !hydrated) return null;

  const signedIn = st.phase !== "signed-out" && st.phase !== "off";
  return (
    <Link
      href="/account"
      className="mt-8 flex items-center gap-3 rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 active:scale-[0.99]"
    >
      <span
        className={`grid h-10 w-10 shrink-0 place-items-center rounded-full ${
          st.phase === "error" ? "bg-red-500/15 text-red-600 dark:text-red-400" : "bg-[var(--soft)] text-[var(--muted)]"
        }`}
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
          <path d="M7 18a4.5 4.5 0 0 1-.5-9A6 6 0 0 1 18 9.5a4 4 0 0 1-1 8.5H7Z" strokeLinejoin="round" />
        </svg>
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{signedIn ? "Backup & sync" : "Back up your training"}</p>
        <p className="truncate text-sm text-[var(--muted)]">
          {signedIn ? syncLine(st, pending.length, now) : "Sign in to save it to your account and use it on any device."}
        </p>
      </div>
      <svg viewBox="0 0 24 24" className="h-5 w-5 shrink-0 text-[var(--muted)]" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
        <path d="m9 6 6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </Link>
  );
}
