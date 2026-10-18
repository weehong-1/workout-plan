"use client";

import { useSyncExternalStore } from "react";

// Everything lives in localStorage on the phone. Nothing is sent anywhere.

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

type State = {
  log: LogEntry[];
  drafts: Record<string, Draft>;
  defaultUnit: Unit;
  // Per exercise choice, keyed by the name shown (substitutes have their own).
  units: Record<string, Unit>;
};

const KEY = "workout-plan:v1";
const EMPTY: State = { log: [], drafts: {}, defaultUnit: "kg", units: {} };

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
  set({
    ...state,
    log: [
      ...state.log,
      ...entries.map((e, i) => ({ ...e, id: `${now}-${i}`, savedAt: now })),
    ],
    drafts,
  });
}

export function setDefaultUnit(unit: Unit) {
  set({ ...state, defaultUnit: unit });
}

export function setExerciseUnit(exercise: string, unit: Unit) {
  set({ ...state, units: { ...state.units, [exercise]: unit } });
}

export function unitFor(s: Pick<State, "defaultUnit" | "units">, exercise: string): Unit {
  return s.units[exercise] ?? s.defaultUnit;
}

export function deleteSession(date: string, dayId: string, savedAt: number) {
  set({
    ...state,
    log: state.log.filter(
      (e) => !(e.date === date && e.dayId === dayId && e.savedAt === savedAt),
    ),
  });
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
