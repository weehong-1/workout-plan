"use client";

import { useSyncExternalStore } from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import {
  type BodyWeight,
  type LogEntry,
  type Op,
  type State,
  type Unit,
  coalesce,
  getState,
  setPendingListener,
  setSyncState,
} from "@/lib/store";
import { supabase, syncEnabled } from "@/lib/supabase";

// Phone first sync: the app always reads and writes the local copy, then
// each sync (1) sends queued changes to Supabase and (2) pulls everything
// back down, so every device ends up with the same data.

export type SyncStatus = {
  phase: "off" | "signed-out" | "syncing" | "synced" | "offline" | "error";
  email?: string;
  lastSynced?: number;
  error?: string;
};

let status: SyncStatus = { phase: syncEnabled ? "signed-out" : "off" };
const listeners = new Set<() => void>();

function setStatus(next: SyncStatus) {
  status = next;
  listeners.forEach((l) => l());
}

export function useSyncStatus(): SyncStatus {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => status,
    () => ({ phase: "off" }) as SyncStatus,
  );
}

// ---- Row mapping ----

type LogRow = {
  user_id: string;
  id: string;
  date: string;
  day_id: string;
  exercise: string;
  substitute: string | null;
  sets: { weight: string; reps: string }[];
  unit: Unit;
  notes: string;
  saved_at: number;
};

type WeightRow = { user_id: string; date: string; weight: number; unit: Unit };

type SettingsRow = {
  user_id: string;
  units: Record<string, Unit>;
  default_unit: Unit;
  body_unit: Unit;
};

const toLogRow = (e: LogEntry, user: string): LogRow => ({
  user_id: user,
  id: e.id,
  date: e.date,
  day_id: e.dayId,
  exercise: e.exercise,
  substitute: e.substitute ?? null,
  sets: e.sets,
  unit: e.unit ?? "kg",
  notes: e.notes,
  saved_at: e.savedAt,
});

const fromLogRow = (r: LogRow): LogEntry => ({
  id: r.id,
  date: r.date,
  dayId: r.day_id,
  exercise: r.exercise,
  ...(r.substitute ? { substitute: r.substitute } : {}),
  sets: r.sets,
  unit: r.unit,
  notes: r.notes,
  savedAt: Number(r.saved_at),
});

const toSettingsRow = (s: State, user: string): SettingsRow => ({
  user_id: user,
  units: s.units,
  default_unit: s.defaultUnit,
  body_unit: s.bodyUnit,
});

// ---- Sync ----

let running: Promise<void> | null = null;
let again = false;

export function requestSync(): Promise<void> {
  if (running) {
    again = true;
    return running;
  }
  running = run().finally(() => {
    running = null;
    if (again) {
      again = false;
      void requestSync();
    }
  });
  return running;
}

async function run() {
  const sb = supabase();
  if (!sb) return;
  const { data } = await sb.auth.getSession();
  const user = data.session?.user;
  if (!user) {
    setStatus({ phase: "signed-out" });
    return;
  }
  const base = { email: user.email, lastSynced: status.lastSynced };
  if (!navigator.onLine) {
    setStatus({ ...base, phase: "offline" });
    return;
  }
  setStatus({ ...base, phase: "syncing" });
  try {
    if (getState().syncedUser !== user.id) {
      await mergeSettings(sb, user.id);
      adopt(user.id);
    }
    const sent = [...getState().pending];
    await push(sb, user.id, sent);
    const sentSet = new Set(sent);
    setSyncState({ ...getState(), pending: getState().pending.filter((op) => !sentSet.has(op)) });
    await pull(sb, user.id);
    setStatus({ ...base, phase: "synced", lastSynced: Date.now() });
  } catch (e) {
    const offline = !navigator.onLine || (e instanceof TypeError && /fetch/i.test(e.message));
    setStatus({
      ...base,
      phase: offline ? "offline" : "error",
      error: e instanceof Error ? e.message : String(e),
    });
  }
}

// First sync for this user on this phone: upload everything already here.
function adopt(user: string) {
  const s = getState();
  const local =
    s.syncedUser === null
      ? s
      : // Data left by a different account never gets uploaded to this one.
        { ...s, log: [], bodyWeights: [], units: {}, pending: [] };
  const ops: Op[] = [
    ...local.log.map((entry) => ({ t: "log", entry }) as const),
    ...local.bodyWeights.map((w) => ({ t: "weight", w }) as const),
  ];
  setSyncState({ ...local, syncedUser: user, pending: coalesce([...local.pending, ...ops]) });
}

// Before the first sync on this phone, combine its kg/lb choices with the
// account's, so a fresh phone never wipes the account's settings.
async function mergeSettings(sb: SupabaseClient, user: string) {
  const [remote] = await fetchAll<SettingsRow>(sb, "settings", user, []);
  const s = getState();
  const fromOtherAccount = s.syncedUser !== null;
  const localUnits = fromOtherAccount ? {} : s.units;
  if (!remote) {
    if (fromOtherAccount) setSyncState({ ...s, units: {} });
    else setSyncState({ ...s, pending: coalesce([...s.pending, { t: "settings" }]) });
    return;
  }
  const units = { ...(remote.units ?? {}), ...localUnits };
  const changed = Object.entries(localUnits).some(([k, v]) => remote.units?.[k] !== v);
  setSyncState({
    ...s,
    units,
    defaultUnit: remote.default_unit,
    bodyUnit: remote.body_unit,
    pending: changed ? coalesce([...s.pending, { t: "settings" }]) : s.pending,
  });
}

async function push(sb: SupabaseClient, user: string, ops: Op[]) {
  const s = getState();
  const logs = ops.flatMap((o) => (o.t === "log" ? [toLogRow(o.entry, user)] : []));
  const delLogs = ops.flatMap((o) => (o.t === "delLog" ? [o.id] : []));
  const weights = ops.flatMap((o) =>
    o.t === "weight" ? [{ user_id: user, date: o.w.date, weight: o.w.weight, unit: o.w.unit }] : [],
  );
  const delWeights = ops.flatMap((o) => (o.t === "delWeight" ? [o.date] : []));
  const check = ({ error }: { error: unknown }) => {
    if (error) throw new Error((error as { message?: string }).message ?? "Sync failed");
  };
  if (logs.length) check(await sb.from("log_entries").upsert(logs, { onConflict: "user_id,id" }));
  if (delLogs.length) check(await sb.from("log_entries").delete().eq("user_id", user).in("id", delLogs));
  if (weights.length) check(await sb.from("body_weights").upsert(weights, { onConflict: "user_id,date" }));
  if (delWeights.length) check(await sb.from("body_weights").delete().eq("user_id", user).in("date", delWeights));
  if (ops.some((o) => o.t === "settings")) {
    check(await sb.from("settings").upsert(toSettingsRow(s, user), { onConflict: "user_id" }));
  }
}

async function fetchAll<T>(sb: SupabaseClient, table: string, user: string, order: string[]) {
  const rows: T[] = [];
  const PAGE = 1000;
  for (let from = 0; ; from += PAGE) {
    let q = sb.from(table).select("*").eq("user_id", user);
    for (const col of order) q = q.order(col);
    const { data, error } = await q.range(from, from + PAGE - 1);
    if (error) throw new Error(error.message);
    rows.push(...(data as T[]));
    if (data.length < PAGE) return rows;
  }
}

async function pull(sb: SupabaseClient, user: string) {
  const [logRows, weightRows, settingsRows] = await Promise.all([
    fetchAll<LogRow>(sb, "log_entries", user, ["saved_at", "id"]),
    fetchAll<WeightRow>(sb, "body_weights", user, ["date"]),
    fetchAll<SettingsRow>(sb, "settings", user, []),
  ]);

  // Changes made while this sync was running are still pending: keep them
  // on top of what the server sent.
  const s = getState();
  let log = logRows.map(fromLogRow);
  let bodyWeights: BodyWeight[] = weightRows.map((r) => ({
    date: r.date,
    weight: Number(r.weight),
    unit: r.unit,
  }));
  for (const op of s.pending) {
    if (op.t === "log" && !log.some((e) => e.id === op.entry.id)) log.push(op.entry);
    if (op.t === "delLog") log = log.filter((e) => e.id !== op.id);
    if (op.t === "weight") bodyWeights = [...bodyWeights.filter((b) => b.date !== op.w.date), op.w];
    if (op.t === "delWeight") bodyWeights = bodyWeights.filter((b) => b.date !== op.date);
  }
  log.sort((a, b) => a.savedAt - b.savedAt || a.id.localeCompare(b.id));
  bodyWeights.sort((a, b) => a.date.localeCompare(b.date));

  const remote = settingsRows[0];
  const keepLocalSettings = !remote || s.pending.some((o) => o.t === "settings");
  setSyncState({
    ...s,
    log,
    bodyWeights,
    ...(keepLocalSettings
      ? {}
      : { units: remote.units ?? {}, defaultUnit: remote.default_unit, bodyUnit: remote.body_unit }),
  });
}

// ---- Auth ----

export async function sendCode(email: string) {
  const sb = supabase()!;
  const { error } = await sb.auth.signInWithOtp({
    email,
    options: { shouldCreateUser: true, emailRedirectTo: `${location.origin}/account` },
  });
  if (error) throw new Error(error.message);
}

export async function verifyCode(email: string, token: string) {
  const sb = supabase()!;
  const { error } = await sb.auth.verifyOtp({ email, token, type: "email" });
  if (error) throw new Error(error.message);
  await requestSync();
}

// Signing out clears this phone's copy; it's all safe in the account.
export async function signOut() {
  const sb = supabase()!;
  await sb.auth.signOut({ scope: "local" });
  const s = getState();
  setSyncState({ ...s, log: [], bodyWeights: [], units: {}, pending: [], syncedUser: null });
  setStatus({ phase: "signed-out" });
}

// ---- Triggers ----

let started = false;

export function startSync() {
  const sb = supabase();
  if (!sb || started) return;
  started = true;
  let timer: ReturnType<typeof setTimeout> | undefined;
  setPendingListener(() => {
    clearTimeout(timer);
    timer = setTimeout(() => void requestSync(), 1500);
  });
  // Supabase warns against awaiting its own calls inside this callback.
  sb.auth.onAuthStateChange((event, session) => {
    if (session) setTimeout(() => void requestSync(), 0);
    else if (event === "SIGNED_OUT" || event === "INITIAL_SESSION") setStatus({ phase: "signed-out" });
  });
  window.addEventListener("online", () => void requestSync());
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void requestSync();
  });
}
