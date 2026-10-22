"use client";

import { useSyncExternalStore } from "react";
import { readAccount, writeChange, type Change } from "@/lib/database";
import type { TrainingPlan } from "@/lib/training-plan";
import type { AddedExercise } from "@/lib/added-exercise";

// Account data is loaded from Supabase. Memory is only a live UI view;
// every edit starts a database transaction immediately. No local data cache.

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

export type State = {
  trainingPlans: TrainingPlan[];
  dayExercises: Record<string, AddedExercise[]>;
  log: LogEntry[];
  drafts: Record<string, Draft>;
  defaultUnit: Unit;
  // Per exercise choice, keyed by the name shown (substitutes have their own).
  units: Record<string, Unit>;
  bodyWeights: BodyWeight[]; // sorted by date
  bodyUnit: Unit;
  timer: { endAt: number; total: number; label: string } | null;
  keepScreenOn: boolean;
};

const EMPTY: State = {
  trainingPlans: [], dayExercises: {}, log: [], drafts: {}, defaultUnit: "kg", units: {}, bodyWeights: [], bodyUnit: "kg",
  timer: null, keepScreenOn: false,
};
export type PersistenceStatus = {
  phase: "idle" | "loading" | "saving" | "error";
  error?: string;
  unsaved: number;
  userId: string | null;
  ready: boolean;
  locked: boolean;
};
const INITIAL_STATUS: PersistenceStatus = { phase: "idle", unsaved: 0, userId: null, ready: false, locked: false };
let state = EMPTY;
let status = INITIAL_STATUS;
let generation = 0;
let queue: Change[] = [];
let running: Promise<boolean> | null = null;
const listeners = new Set<() => void>();
const statusListeners = new Set<() => void>();
const subscribe = (listener: () => void) => { listeners.add(listener); return () => listeners.delete(listener); };
const subscribeStatus = (listener: () => void) => { statusListeners.add(listener); return () => statusListeners.delete(listener); };
function set(next: State) { state = next; listeners.forEach((l) => l()); }
function setStatus(next: PersistenceStatus) { status = next; statusListeners.forEach((l) => l()); }
export const getState = () => state;
export const getPersistenceStatus = () => status;
export const getAccountGeneration = () => generation;
export function useStore(): State { return useSyncExternalStore(subscribe, () => state, () => EMPTY); }
export function usePersistenceStatus(): PersistenceStatus {
  return useSyncExternalStore(subscribeStatus, () => status, () => INITIAL_STATUS);
}
export function useHydrated(): boolean {
  return useSyncExternalStore(subscribeStatus, () => status.ready, () => false);
}
export function resetAccount() {
  generation++;
  queue = [];
  running = null;
  set(EMPTY);
  setStatus(INITIAL_STATUS);
}
export async function loadAccount(userId: string): Promise<void> {
  resetAccount();
  const current = generation;
  setStatus({ ...INITIAL_STATUS, userId, phase: "loading" });
  try {
    const data = await readAccount(userId);
    if (current !== generation) return;
    set(data);
    setStatus({ ...INITIAL_STATUS, userId, ready: true });
  } catch (e) {
    if (current !== generation) return;
    const error = e instanceof Error ? e.message : String(e);
    setStatus({ ...INITIAL_STATUS, userId, phase: "error", error });
    throw e;
  }
}

// Serialize requests so rapid edits cannot arrive out of order. Failed requests
// stay in memory with an explicit unsaved warning; they are never called saved.
// Each RPC is idempotent, including finishing a workout, so retry is safe.
export function flushChanges(): Promise<boolean> {
  if (running) return running;
  if (!queue.length) return Promise.resolve(true);
  if (!status.userId || !status.ready) return Promise.resolve(false);
  const current = generation;
  const userId = status.userId;
  setStatus({ ...status, phase: "saving", error: undefined });
  running = (async () => {
    while (queue.length && current === generation) {
      try {
        await writeChange(userId, queue[0]);
      } catch (e) {
        if (current === generation) setStatus({ ...status, phase: "error", error: e instanceof Error ? e.message : String(e) });
        return false;
      }
      if (current !== generation) return false;
      queue.shift();
      setStatus({ ...status, unsaved: queue.length });
    }
    if (current !== generation) return false;
    setStatus({ ...status, phase: "idle", error: undefined });
    return true;
  })().finally(() => { if (current === generation) running = null; });
  return running;
}
export function lockAccountEdits(locked: boolean) {
  setStatus({ ...status, locked });
}
function commit(next: State, change: Change) {
  if (!status.userId || !status.ready || status.locked) return;
  queue.push({ ...change, mutationId: crypto.randomUUID() });
  set(next);
  setStatus({ ...status, unsaved: queue.length });
  if (status.phase !== "error") void flushChanges();
}

// Metadata-only edits must not replace a plan's exercises or creation time.
// The returned plan is optimistic; callers await flushChanges() for confirmation.
export function saveTrainingPlan(name: string, description: string, id?: string): TrainingPlan | null {
  if (!status.userId || !status.ready || status.locked) return null;
  if (typeof name !== "string" || typeof description !== "string") return null;
  const trimmedName = name.trim();
  const trimmedDescription = description.trim();
  if (!trimmedName || trimmedName.length > 80 || trimmedDescription.length > 500) return null;
  const existing = id === undefined ? undefined : state.trainingPlans.find((plan) => plan.id === id);
  if (id !== undefined && !existing) return null;
  const plan: TrainingPlan = {
    id: existing?.id ?? `plan-${crypto.randomUUID()}`,
    name: trimmedName,
    description: trimmedDescription,
    blocks: existing?.blocks ?? [],
    createdAt: existing?.createdAt ?? Date.now(),
  };
  const trainingPlans = existing
    ? state.trainingPlans.map((entry) => entry.id === plan.id ? plan : entry)
    : [...state.trainingPlans, plan];
  commit({ ...state, trainingPlans }, { type: "trainingPlan", payload: { plan } });
  return plan;
}

// Added exercise definitions are independent of drafts: finishing/discarding a session
// must not erase the exercises the user has attached to this day.
export function addDayExercise(
  dayId: string,
  input: Omit<AddedExercise, "key">,
): AddedExercise | null {
  if (!status.userId || !status.ready || status.locked || !["a", "b", "c", "d"].includes(dayId)) return null;
  if (!input || !["free-exercise-db", "custom"].includes(input.source)
    || typeof input.exerciseId !== "string" || !input.exerciseId.trim() || input.exerciseId.length > 160
    || (input.source === "custom" && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(input.exerciseId))
    || typeof input.name !== "string" || !input.name.trim() || input.name.trim().length > 120
    || typeof input.equipment !== "string" || input.equipment.length > 100
    || !Array.isArray(input.primaryMuscles) || input.primaryMuscles.length > 10
    || input.primaryMuscles.some((muscle) => typeof muscle !== "string" || !muscle.trim() || muscle.length > 100)
    || !Array.isArray(input.instructions) || input.instructions.length > 30
    || input.instructions.some((step) => typeof step !== "string" || step.length > 3000)
    || input.instructions.reduce((length, step) => length + step.length, 0) > 6000
    || !Number.isInteger(input.sets) || input.sets < 1 || input.sets > 10
    || typeof input.reps !== "string" || !/^(?:[1-9][0-9]?|100)$/.test(input.reps)
    || !Number.isInteger(input.restSeconds) || input.restSeconds < 0 || input.restSeconds > 600) return null;
  const exercises = state.dayExercises[dayId] ?? [];
  const existing = exercises.find((exercise) => exercise.source === input.source && exercise.exerciseId === input.exerciseId);
  if (existing) return existing;
  if (exercises.length >= 30) return null;
  // The namespace cannot collide with authored numeric exercise orders. Also
  // check live keys so even a UUID collision cannot overwrite another draft.
  const usedKeys = new Set([
    ...Object.values(state.dayExercises).flat().map((exercise) => exercise.key),
    ...Object.values(state.drafts).flatMap((draft) => Object.keys(draft.exercises)),
    ...state.trainingPlans.flatMap((plan) => plan.blocks.flatMap((block) => block.exercises.map((exercise) => exercise.order))),
  ]);
  let key: string;
  do { key = `ex-${crypto.randomUUID()}`; } while (usedKeys.has(key));
  const exercise: AddedExercise = {
    key, source: input.source, exerciseId: input.exerciseId, name: input.name.trim(),
    equipment: input.equipment.trim(), primaryMuscles: input.primaryMuscles.map((muscle) => muscle.trim()),
    instructions: input.instructions.map((step) => step.trim()).filter(Boolean),
    sets: input.sets, reps: input.reps, restSeconds: input.restSeconds,
  };
  const next = [...exercises, exercise];
  commit({ ...state, dayExercises: { ...state.dayExercises, [dayId]: next } }, {
    type: "dayExercises", payload: { dayId, exercises: next },
  });
  return exercise;
}

export function removeDayExercise(dayId: string, key: string): void {
  if (!status.userId || !status.ready || status.locked || !["a", "b", "c", "d"].includes(dayId)) return;
  const current = state.dayExercises[dayId] ?? [];
  if (!current.some((exercise) => exercise.key === key)) return;
  const exercises = current.filter((exercise) => exercise.key !== key);
  const drafts = { ...state.drafts };
  const draft = drafts[dayId];
  if (draft && Object.hasOwn(draft.exercises, key)) {
    const remaining = { ...draft.exercises };
    delete remaining[key];
    drafts[dayId] = { ...draft, exercises: remaining };
  }
  // One RPC removes both the reference and only its draft exercise, leaving
  // other exercise sets intact even when the database has a newer draft.
  commit({ ...state, drafts, dayExercises: { ...state.dayExercises, [dayId]: exercises } }, {
    type: "dayExercises", payload: { dayId, exercises, removedKey: key },
  });
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
  const updated = { ...draft, exercises: { ...draft.exercises, [key]: fn(current) } };
  commit({
    ...state,
    drafts: {
      ...state.drafts,
      [dayId]: updated,
    },
  }, { type: "draft", payload: { dayId, draft: updated } });
}

export function discardDraft(dayId: string) {
  const drafts = { ...state.drafts };
  delete drafts[dayId];
  commit({ ...state, drafts }, { type: "draft", payload: { dayId, draft: null } });
}

export function finishDraft(dayId: string, entries: Omit<LogEntry, "id" | "savedAt">[]) {
  const now = Date.now();
  const drafts = { ...state.drafts };
  delete drafts[dayId];
  const saved = entries.map((e, i) => ({ ...e, id: `${crypto.randomUUID()}-${i}`, savedAt: now }));
  commit(
    { ...state, log: [...state.log, ...saved], drafts },
    { type: "finish", payload: { dayId, entries: saved } },
  );
}

export function setExerciseUnit(exercise: string, unit: Unit) {
  commit({ ...state, units: { ...state.units, [exercise]: unit } }, { type: "exerciseUnit", payload: { exercise, unit } });
}

export function unitFor(s: Pick<State, "defaultUnit" | "units">, exercise: string): Unit {
  return s.units[exercise] ?? s.defaultUnit;
}

export function saveBodyWeight(entry: BodyWeight): boolean {
  if (!status.userId || !status.ready || status.locked) return false;
  if (!entry || !Number.isFinite(entry.weight) || entry.weight <= 0 || entry.weight >= 1000
    || !["kg", "lb"].includes(entry.unit) || !/^\d{4}\/\d{2}\/\d{2}$/.test(entry.date)) return false;
  const [year, month, day] = entry.date.split("/").map(Number);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day
    || entry.date > todayString()) return false;
  const rest = state.bodyWeights.filter((b) => b.date !== entry.date);
  commit(
    { ...state, bodyWeights: [...rest, entry].sort((a, b) => a.date.localeCompare(b.date)) },
    { type: "weight", payload: entry },
  );
  return true;
}

export function deleteBodyWeight(date: string) {
  commit(
    { ...state, bodyWeights: state.bodyWeights.filter((b) => b.date !== date) },
    { type: "deleteWeight", payload: { date } },
  );
}

export function setBodyUnit(unit: Unit) {
  commit({ ...state, bodyUnit: unit }, { type: "bodyUnit", payload: { unit } });
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
    { type: "deleteSession", payload: { date, dayId, savedAt } },
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

export function setRestTimer(timer: State["timer"]) {
  commit({ ...state, timer }, { type: "timer", payload: { timer } });
}
export function setKeepScreenOn(enabled: boolean) {
  commit({ ...state, keepScreenOn: enabled }, { type: "keepScreenOn", payload: { enabled } });
}
