"use client";

import { useSyncExternalStore } from "react";

// Everything lives in localStorage on the phone, so the app works offline.
// When signed in, changes are also queued in `pending` and sync.ts sends
// them to Supabase.

export type Unit = "kg" | "lb";

export type SetLog = { weight: string; reps: string; done: boolean };

export type DraftExercise = {
  sets: SetLog[];
  notes: string;
  useSub: boolean;
};

export type Draft = {
  startedAt: number;
  exercises: Record<string, DraftExercise>;
};

export type LogEntry = {
  id: string;
  date: string; // YYYY/MM/DD, same as the sheet
  dayId: string;
  exercise: string; // planned exercise name
  substitute?: string; // set when the substitute was used
  sets: { weight: string; reps: string }[];
  unit?: Unit; // missing on entries saved before units existed, which were kg
  notes: string;
  savedAt: number;
};

export type BodyWeight = {
  date: string; // YYYY/MM/DD, one entry per day
  weight: number;
  unit: Unit;
};

// A change waiting to be sent to Supabase. Only the latest op per key is
// kept (see opKey), so the order they're sent in doesn't matter.
export type Op =
  | { t: "log"; entry: LogEntry }
  | { t: "delLog"; id: string }
  | { t: "weight"; w: BodyWeight }
  | { t: "delWeight"; date: string }
  | { t: "settings" };

export type State = {
  log: LogEntry[];
  drafts: Record<string, Draft>;
  defaultUnit: Unit;
  // Per exercise choice, keyed by the name shown (substitutes have their own).
  units: Record<string, Unit>;
  bodyWeights: BodyWeight[]; // sorted by date
  bodyUnit: Unit;
  pending: Op[];
  // Supabase user whose data this phone holds; null when not signed in.
  syncedUser: string | null;
};

const KEY = "workout-plan:v1";
const EMPTY: State = {
  log: [],
  drafts: {},
  defaultUnit: "kg",
  units: {},
  bodyWeights: [],
  bodyUnit: "kg",
  pending: [],
  syncedUser: null,
};

let state: State = EMPTY;
let loaded = false;
const listeners = new Set<() => void>();

function load() {
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (raw) state = { ...EMPTY, ...JSON.parse(raw) };
  } catch {
    // Private mode or corrupt data: start empty.
  }
  window.addEventListener("storage", (e) => {
    if (e.key !== KEY) return;
    try {
      state = e.newValue ? { ...EMPTY, ...JSON.parse(e.newValue) } : EMPTY;
    } catch {
      state = EMPTY;
    }
    listeners.forEach((l) => l());
  });
}

function set(next: State) {
  state = next;
  try {
    window.localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // Storage full or unavailable: keep the in memory state.
  }
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  load();
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getSnapshot() {
  load();
  return state;
}

const getServerSnapshot = () => EMPTY;

export function getState(): State {
  load();
  return state;
}

function opKey(op: Op): string {
  switch (op.t) {
    case "log":
      return `log:${op.entry.id}`;
    case "delLog":
      return `log:${op.id}`;
    case "weight":
      return `weight:${op.w.date}`;
    case "delWeight":
      return `weight:${op.date}`;
    case "settings":
      return "settings";
  }
}

export function coalesce(ops: Op[]): Op[] {
  const byKey = new Map<string, Op>();
  for (const op of ops) {
    byKey.delete(opKey(op));
    byKey.set(opKey(op), op);
  }
  return [...byKey.values()];
}

let onPending: (() => void) | null = null;

// sync.ts registers here to hear about new local changes.
export function setPendingListener(fn: (() => void) | null) {
  onPending = fn;
}

// Saves a change, and queues it for Supabase when signed in.
function commit(next: State, ops: Op[]) {
  if (!next.syncedUser || !ops.length) return set(next);
  set({ ...next, pending: coalesce([...next.pending, ...ops]) });
  onPending?.();
}

// Used by sync.ts only.
export function setSyncState(next: State) {
  set(next);
}

export function useStore(): State {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

// Hydration aware flag so pages can avoid flashing empty states.
export function useHydrated(): boolean {
  return useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
}

export function todayString(d = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`;
}

export function emptyExercise(sets: number): DraftExercise {
  return {
    sets: Array.from({ length: sets }, () => ({ weight: "", reps: "", done: false })),
    notes: "",
    useSub: false,
  };
}

export function updateDraftExercise(
  dayId: string,
  key: string,
  setCount: number,
  fn: (ex: DraftExercise) => DraftExercise,
) {
  const draft = state.drafts[dayId] ?? { startedAt: Date.now(), exercises: {} };
  const current = draft.exercises[key] ?? emptyExercise(setCount);
  set({
    ...state,
    drafts: {
      ...state.drafts,
      [dayId]: { ...draft, exercises: { ...draft.exercises, [key]: fn(current) } },
    },
  });
}

export function discardDraft(dayId: string) {
  const drafts = { ...state.drafts };
  delete drafts[dayId];
  set({ ...state, drafts });
}

export function finishDraft(dayId: string, entries: Omit<LogEntry, "id" | "savedAt">[]) {
  const now = Date.now();
  const drafts = { ...state.drafts };
  delete drafts[dayId];
  const saved = entries.map((e, i) => ({ ...e, id: `${now}-${i}`, savedAt: now }));
  commit(
    { ...state, log: [...state.log, ...saved], drafts },
    saved.map((entry) => ({ t: "log", entry })),
  );
}

export function setExerciseUnit(exercise: string, unit: Unit) {
  commit({ ...state, units: { ...state.units, [exercise]: unit } }, [{ t: "settings" }]);
}

export function unitFor(s: Pick<State, "defaultUnit" | "units">, exercise: string): Unit {
  return s.units[exercise] ?? s.defaultUnit;
}

export function saveBodyWeight(entry: BodyWeight) {
  const rest = state.bodyWeights.filter((b) => b.date !== entry.date);
  commit(
    { ...state, bodyWeights: [...rest, entry].sort((a, b) => a.date.localeCompare(b.date)) },
    [{ t: "weight", w: entry }],
  );
}

export function deleteBodyWeight(date: string) {
  commit(
    { ...state, bodyWeights: state.bodyWeights.filter((b) => b.date !== date) },
    [{ t: "delWeight", date }],
  );
}

export function setBodyUnit(unit: Unit) {
  commit({ ...state, bodyUnit: unit }, [{ t: "settings" }]);
}

const LB_PER_KG = 2.20462;

export function convertWeight(weight: number, from: Unit, to: Unit): number {
  if (from === to) return weight;
  return from === "kg" ? weight * LB_PER_KG : weight / LB_PER_KG;
}

export function deleteSession(date: string, dayId: string, savedAt: number) {
  const inSession = (e: LogEntry) => e.date === date && e.dayId === dayId && e.savedAt === savedAt;
  commit(
    { ...state, log: state.log.filter((e) => !inSession(e)) },
    state.log.filter(inSession).map((e) => ({ t: "delLog", id: e.id })),
  );
}

export function hasLoggedValue(s: { weight: string; reps: string }) {
  return s.weight.trim() !== "" || s.reps.trim() !== "";
}

// "60" → "60kg×10" (or "135lb×10"), "BW" → "BW×10", matching the sheet's log format.
export function formatSet(s: { weight: string; reps: string }, unit: Unit = "kg"): string {
  const w = s.weight.trim();
  const r = s.reps.trim();
  const weight = w === "" ? "" : /^\d+(\.\d+)?$/.test(w) ? `${w}${unit}` : w;
  if (!weight) return r;
  return r ? `${weight}×${r}` : weight;
}

export function lastEntryFor(log: LogEntry[], exercise: string): LogEntry | undefined {
  for (let i = log.length - 1; i >= 0; i--) {
    const e = log[i];
    if (e.exercise === exercise && !e.substitute) return e;
  }
  return undefined;
}

export type Session = {
  date: string;
  dayId: string;
  savedAt: number;
  entries: LogEntry[];
};

export function sessionsOf(log: LogEntry[]): Session[] {
  const map = new Map<string, Session>();
  for (const e of log) {
    const k = `${e.date}|${e.dayId}|${e.savedAt}`;
    let s = map.get(k);
    if (!s) {
      s = { date: e.date, dayId: e.dayId, savedAt: e.savedAt, entries: [] };
      map.set(k, s);
    }
    s.entries.push(e);
  }
  return [...map.values()].sort((a, b) => b.savedAt - a.savedAt);
}
