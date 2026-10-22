"use client";

import { supabase } from "@/lib/supabase";
import type { State, LogEntry } from "@/lib/store";
import type { TrainingPlan } from "@/lib/training-plan";
import type { AddedExercise } from "@/lib/added-exercise";

export type Change = (
  | { type: "trainingPlan"; payload: { plan: TrainingPlan } }
  | { type: "dayExercises"; payload: { dayId: string; exercises: AddedExercise[]; removedKey?: string } }
  | { type: "draft"; payload: { dayId: string; draft: State["drafts"][string] | null } }
  | { type: "finish"; payload: { dayId: string; entries: LogEntry[] } }
  | { type: "exerciseUnit"; payload: { exercise: string; unit: string } }
  | { type: "bodyUnit"; payload: { unit: string } }
  | { type: "weight"; payload: State["bodyWeights"][number] }
  | { type: "deleteWeight"; payload: { date: string } }
  | { type: "deleteSession"; payload: { date: string; dayId: string; savedAt: number } }
  | { type: "timer"; payload: { timer: State["timer"] } }
  | { type: "keepScreenOn"; payload: { enabled: boolean } }
) & { mutationId?: string };

function client() {
  const sb = supabase();
  if (!sb) throw new Error("Database is not configured.");
  return sb;
}

async function rows(table: string, userId: string, order: string[]) {
  const result: Record<string, unknown>[] = [];
  const pageSize = 1000;
  for (let start = 0; ; start += pageSize) {
    let query = client().from(table).select("*").eq("user_id", userId);
    for (const key of order) query = query.order(key);
    const { data, error } = await query.range(start, start + pageSize - 1);
    if (error) throw new Error(error.message);
    result.push(...data);
    if (data.length < pageSize) return result;
  }
}

export async function readAccount(userId: string): Promise<State> {
  const [logs, weights, settings, drafts, plans] = await Promise.all([
    rows("log_entries", userId, ["saved_at", "id"]),
    rows("body_weights", userId, ["date"]),
    rows("settings", userId, []),
    rows("workout_drafts", userId, ["day_id"]),
    rows("training_plans", userId, ["created_at", "id"]),
  ]);
  const s = settings[0];
  return {
    trainingPlans: plans.map((r) => ({
      id: r.id, name: r.name, description: r.description, blocks: r.blocks, createdAt: Number(r.created_at),
    })) as TrainingPlan[],
    log: logs.map((r) => ({
      id: r.id, date: r.date, dayId: r.day_id, exercise: r.exercise,
      ...(r.substitute ? { substitute: r.substitute } : {}),
      sets: r.sets, unit: r.unit, notes: r.notes, savedAt: Number(r.saved_at),
    })) as State["log"],
    bodyWeights: weights.map((r) => ({ date: r.date, weight: Number(r.weight), unit: r.unit })) as State["bodyWeights"],
    drafts: Object.fromEntries(drafts.map((r) => [r.day_id, r.draft])) as State["drafts"],
    dayExercises: (s?.day_exercises ?? {}) as State["dayExercises"],
    units: (s?.units ?? {}) as State["units"],
    defaultUnit: (s?.default_unit ?? "kg") as State["defaultUnit"],
    bodyUnit: (s?.body_unit ?? "kg") as State["bodyUnit"],
    timer: (s?.rest_timer ?? null) as State["timer"],
    keepScreenOn: Boolean(s?.keep_screen_on),
  };
}

export async function writeChange(userId: string, change: Change): Promise<void> {
  // auth.uid() is checked inside the transaction as well as by row-level security.
  const { error } = await client().rpc("apply_workout_change", {
    account_id: userId, change_type: change.type, payload: { ...change.payload, _mutationId: change.mutationId },
  });
  if (error) throw new Error(error.message);
}
