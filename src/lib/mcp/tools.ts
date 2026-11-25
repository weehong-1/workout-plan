// The workout plan's MCP tools, served to Muse AI from /api/muse/mcp.
// They act as one signed-in account, so row-level security limits them to
// that account's data, and every write goes through apply_workout_change,
// the same database function the app uses.

import { randomUUID } from "node:crypto";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { SupabaseClient } from "@supabase/supabase-js";
import { z } from "zod";
import { type Day, type PlanSettings, dayExercises, defaultPlan, nextPlanDay, suggestFor } from "@/lib/plan";
import { groupByWeek, weeklyTrend } from "@/lib/weekly";

export type Account = { client: SupabaseClient; userId: string };
type Row = Record<string, unknown>;
type Unit = "kg" | "lb";
type Entry = {
  id: string; date: string; dayId: string; exercise: string; substitute?: string;
  sets: { weight: string; reps: string }[]; unit: Unit; notes: string; savedAt: number; photos: string[];
};

const LB_PER_KG = 2.2046226218;
const WEEK_DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const round1 = (n: number) => Math.round(n * 10) / 10;
const convert = (weight: number, from: string, to: string) =>
  from === to ? weight : from === "kg" ? weight * LB_PER_KG : weight / LB_PER_KG;
const pad = (n: number) => String(n).padStart(2, "0");
// Calendar dates only: built and read at UTC midnight so the server's own zone never shifts them.
const toTime = (date: string) => { const [y, m, d] = date.split("/").map(Number); return Date.UTC(y, m - 1, d); };
const dateOf = (t: number) => { const d = new Date(t); return `${d.getUTCFullYear()}/${pad(d.getUTCMonth() + 1)}/${pad(d.getUTCDate())}`; };
const photosOf = (value: unknown) => (Array.isArray(value) ? (value as string[]) : []);

// Mondays at UTC midnight, matching toTime, for the app's weekly grouping.
function weeksOf(days: { date: string; value: number }[]) {
  const local = days.map((d) => { const [y, m, day] = d.date.split("/").map(Number); return { ...d, t: new Date(y, m - 1, day).getTime() }; });
  return groupByWeek(local).map((w) => {
    const first = new Date(w.start);
    return { ...w, weekOf: `${first.getFullYear()}/${pad(first.getMonth() + 1)}/${pad(first.getDate())}` };
  });
}

export function createTools(account: () => Promise<Account>, timeZone: string) {
  /** Today in the person's own timezone, as YYYY/MM/DD. */
  const today = () => {
    const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" })
      .formatToParts(new Date()).map((p) => [p.type, p.value]));
    return `${parts.year}/${parts.month}/${parts.day}`;
  };

  /** Accepts 2026-11-25 or 2026/11/25, refusing impossible or future dates. */
  const appDate = (input?: string) => {
    if (input === undefined || input === "") return today();
    const match = /^(\d{4})[-/](\d{2})[-/](\d{2})$/.exec(input.trim());
    if (!match) throw new Error(`Use a date like 2026-11-25, not "${input}".`);
    const date = `${match[1]}/${match[2]}/${match[3]}`;
    if (dateOf(toTime(date)) !== date) throw new Error(`${input} is not a real date.`);
    if (date > today()) throw new Error(`${input} is in the future.`);
    return date;
  };

  async function rows(table: string, order: string[] = []): Promise<Row[]> {
    const { client, userId } = await account();
    const out: Row[] = [];
    for (let start = 0; ; start += 1000) {
      let query = client.from(table).select("*").eq("user_id", userId);
      for (const column of order) query = query.order(column);
      const { data, error } = await query.range(start, start + 999);
      if (error) throw new Error(error.message);
      out.push(...data);
      if (data.length < 1000) return out;
    }
  }

  async function change(type: string, payload: Row) {
    const { client, userId } = await account();
    const { error } = await client.rpc("apply_workout_change", {
      account_id: userId, change_type: type, payload: { ...payload, _mutationId: randomUUID() },
    });
    if (error) throw new Error(error.message);
  }

  // Photo files are removed only after the database no longer refers to them.
  async function removePhotos(paths: string[]) {
    if (!paths.length) return;
    const { client } = await account();
    await client.storage.from("photos").remove(paths);
  }

  async function settings(): Promise<{ plan: PlanSettings; bodyUnit: Unit }> {
    const s = (await rows("settings"))[0] ?? {};
    return { plan: (s.plan as PlanSettings | null) ?? defaultPlan(), bodyUnit: (s.body_unit as Unit) ?? "kg" };
  }

  async function log(): Promise<Entry[]> {
    return (await rows("log_entries", ["saved_at", "id"])).map((r) => ({
      id: String(r.id), date: String(r.date), dayId: String(r.day_id), exercise: String(r.exercise),
      ...(r.substitute ? { substitute: String(r.substitute) } : {}),
      sets: r.sets as Entry["sets"], unit: (r.unit as Unit) ?? "kg", notes: String(r.notes ?? ""),
      savedAt: Number(r.saved_at), photos: photosOf(r.photos),
    }));
  }

  const setsText = (sets: Entry["sets"]) => sets.map((s) => `${s.weight}×${s.reps}`).join(", ");

  function sessionsOf(entries: Entry[], meta: Row[], days: Day[]) {
    const byKey = new Map<string, { date: string; dayId: string; savedAt: number; exercises: Row[] }>();
    for (const e of entries) {
      const key = `${e.date}|${e.dayId}|${e.savedAt}`;
      if (!byKey.has(key)) byKey.set(key, { date: e.date, dayId: e.dayId, savedAt: e.savedAt, exercises: [] });
      byKey.get(key)!.exercises.push({
        exercise: e.substitute ?? e.exercise, ...(e.substitute ? { insteadOf: e.exercise } : {}),
        unit: e.unit, sets: setsText(e.sets), ...(e.notes ? { notes: e.notes } : {}),
      });
    }
    return [...byKey.values()].map((s) => {
      const m = meta.find((r) => r.day_id === s.dayId && Number(r.saved_at) === s.savedAt);
      return {
        ...s, day: days.find((d) => d.id === s.dayId)?.name ?? s.dayId,
        ...(m?.duration_min != null ? { durationMin: Number(m.duration_min) } : {}),
        ...(m?.notes ? { notes: String(m.notes) } : {}),
      };
    });
  }

  const range = (from?: string, to?: string) => ({ lo: from ? appDate(from) : "0000/00/00", hi: to ? appDate(to) : today() });

  return {
    async get_body_weight({ from, to }: { from?: string; to?: string }) {
      const [{ bodyUnit }, weights] = await Promise.all([settings(), rows("body_weights", ["date"])]);
      const { lo, hi } = range(from, to);
      const days = weights.filter((w) => String(w.date) >= lo && String(w.date) <= hi)
        .map((w) => ({ date: String(w.date), value: convert(Number(w.weight), String(w.unit), bodyUnit) }));
      const weeks = weeksOf(days);
      const trend = weeklyTrend(weeks);
      const latest = days.at(-1);
      return {
        unit: bodyUnit,
        latest: latest ? { date: latest.date, weight: round1(latest.value) } : null,
        trend: trend && {
          perWeek: round1(trend.perWeek), overWeeks: trend.weeks,
          verdict: Math.abs(trend.perWeek) < 0.1 ? "steady" : trend.perWeek < 0 ? "dropping" : "rising",
        },
        weeks: weeks.map((w) => ({
          weekOf: w.weekOf, average: round1(w.average), change: w.change === null ? null : round1(w.change),
          days: w.days.map((d) => ({ date: d.date, weight: round1(d.value) })),
        })),
      };
    },

    async log_body_weight({ weight, date, unit }: { weight: number; date?: string; unit?: Unit }) {
      const day = appDate(date);
      const chosen = unit ?? (await settings()).bodyUnit;
      await change("weight", { date: day, weight, unit: chosen });
      return { saved: { date: day, weight, unit: chosen } };
    },

    async delete_body_weight({ date }: { date: string }) {
      const day = appDate(date);
      const entry = (await rows("body_weights")).find((w) => w.date === day);
      if (!entry) throw new Error(`There is no weigh-in on ${day}.`);
      await change("deleteWeight", { date: day });
      await removePhotos(photosOf(entry.photos));
      return { deleted: { date: day, weight: Number(entry.weight), unit: entry.unit } };
    },

    async get_plan() {
      const [{ plan }, history] = await Promise.all([settings(), log()]);
      const next = nextPlanDay(plan.days, history);
      return {
        trainingWeekdays: plan.weekdays.map((d) => WEEK_DAYS[d]),
        nextDay: { id: next.id, name: next.name },
        days: plan.days.map((day) => ({
          id: day.id, letter: day.letter, name: day.name, optional: Boolean(day.optional),
          exercises: dayExercises(day).map((ex) => {
            const s = suggestFor(ex, history.filter((e) => e.exercise === ex.name && !e.substitute));
            return {
              name: ex.name, sets: ex.sets, reps: ex.reps, rest: ex.rest, ...(ex.substitute ? { substitute: ex.substitute } : {}),
              optional: Boolean(ex.optional), next: { weight: s.weight, why: s.label },
            };
          }),
        })),
      };
    },

    async get_workouts({ from, to, limit = 20 }: { from?: string; to?: string; limit?: number }) {
      const [{ plan }, entries, meta] = await Promise.all([settings(), log(), rows("workout_sessions")]);
      const { lo, hi } = range(from, to);
      return sessionsOf(entries.filter((e) => e.date >= lo && e.date <= hi), meta, plan.days).reverse().slice(0, limit);
    },

    async get_exercise_history({ exercise }: { exercise: string }) {
      const [{ plan }, entries] = await Promise.all([settings(), log()]);
      const ex = plan.days.flatMap(dayExercises).find((e) => e.name.toLowerCase() === exercise.trim().toLowerCase());
      const name = ex?.name ?? exercise.trim();
      const past = entries.filter((e) => e.exercise === name && !e.substitute);
      if (!ex && !past.length) throw new Error(`No exercise called "${exercise}". Use a name from get_plan.`);
      const next = ex && suggestFor(ex, past);
      return {
        exercise: name,
        next: next ? { weight: next.weight, why: next.label } : null,
        sessions: past.map((e) => ({
          date: e.date, unit: e.unit, sets: setsText(e.sets),
          heaviest: Math.max(0, ...e.sets.map((s) => parseFloat(s.weight)).filter(Number.isFinite)) || null,
          ...(e.notes ? { notes: e.notes } : {}),
        })),
      };
    },

    async log_workout({ dayId, date, exercises, notes, durationMin }: {
      dayId: string; date?: string; notes?: string; durationMin?: number;
      exercises: { exercise: string; substitute?: boolean; sets: { weight: number | "BW"; reps: number }[]; unit?: Unit; notes?: string }[];
    }) {
      const day = appDate(date);
      const { plan } = await settings();
      const planDay = plan.days.find((d) => d.id === dayId);
      if (!planDay) throw new Error(`No day "${dayId}". Days: ${plan.days.map((d) => `${d.id} (${d.name})`).join(", ")}.`);
      const planned = dayExercises(planDay);
      const savedAt = Date.now();
      const entries = exercises.map((e, i) => {
        const ex = planned.find((x) => x.name.toLowerCase() === e.exercise.trim().toLowerCase());
        if (!ex) throw new Error(`"${e.exercise}" is not on ${planDay.name}. Its exercises: ${planned.map((x) => x.name).join(", ")}.`);
        if (e.substitute && !ex.substitute) throw new Error(`${ex.name} has no substitute in the plan.`);
        return {
          id: `${randomUUID()}-${i}`, date: day, dayId, exercise: ex.name,
          ...(e.substitute ? { substitute: ex.substitute } : {}),
          sets: e.sets.map((s) => ({ weight: String(s.weight), reps: String(s.reps) })),
          unit: e.unit ?? "kg", notes: e.notes ?? "", savedAt,
        };
      });
      await change("finish", {
        dayId, entries,
        session: { dayId, date: day, savedAt, notes: notes ?? "", photos: [], durationMin: durationMin ?? null },
      });
      return { saved: { date: day, day: planDay.name, savedAt, exercises: entries.map((e) => e.substitute ?? e.exercise) } };
    },

    async delete_workout({ date, dayId, savedAt }: { date: string; dayId: string; savedAt: number }) {
      const day = appDate(date);
      const [entries, meta] = await Promise.all([log(), rows("workout_sessions")]);
      const mine = entries.filter((e) => e.date === day && e.dayId === dayId && e.savedAt === savedAt);
      if (!mine.length) throw new Error("No workout matches that date, dayId and savedAt. Use get_workouts.");
      const sessionPhotos = photosOf(meta.find((r) => r.day_id === dayId && Number(r.saved_at) === savedAt)?.photos);
      await change("deleteSession", { date: day, dayId, savedAt });
      await removePhotos([...new Set([...mine.flatMap((e) => e.photos), ...sessionPhotos])]);
      return { deleted: { date: day, dayId, exercises: mine.map((e) => e.substitute ?? e.exercise) } };
    },
  };
}

const read = { readOnlyHint: true, openWorldHint: false };
const write = (destructive: boolean, idempotent: boolean) =>
  ({ readOnlyHint: false, destructiveHint: destructive, idempotentHint: idempotent, openWorldHint: false });
const setSchema = z.object({
  weight: z.union([z.number().nonnegative(), z.literal("BW")]).describe("Weight, or \"BW\" for bodyweight."),
  reps: z.number().int().positive().max(1000).describe("Reps, or seconds for timed exercises."),
});

export function registerTools(server: McpServer, tools: ReturnType<typeof createTools>) {
  const run = <A,>(fn: (args: A) => Promise<unknown>) => async (args: A) => {
    try {
      return { content: [{ type: "text" as const, text: JSON.stringify(await fn(args ?? ({} as A)), null, 2) }] };
    } catch (error) {
      return { isError: true, content: [{ type: "text" as const, text: error instanceof Error ? error.message : String(error) }] };
    }
  };
  const dateArg = (what: string) => z.string().optional().describe(`${what}, e.g. 2026-11-25.`);

  server.registerTool("get_body_weight", {
    title: "Body weight by week",
    description: "Body weight grouped into Monday-to-Sunday weeks, with each week's average, the change from the week before, and whether weight is dropping (the weekly slope over the last 4 weeks). Values are in the account's body-weight unit.",
    inputSchema: { from: dateArg("Earliest date. Defaults to all time"), to: dateArg("Latest date. Defaults to today") },
    annotations: read,
  }, run(tools.get_body_weight));

  server.registerTool("log_body_weight", {
    title: "Log a weigh-in",
    description: "Saves a body weight for a day. One weigh-in per day: logging the same date again replaces it.",
    inputSchema: {
      weight: z.number().positive().lt(1000),
      date: dateArg("Defaults to today"),
      unit: z.enum(["kg", "lb"]).optional().describe("Defaults to the account's body-weight unit."),
    },
    annotations: write(false, true),
  }, run(tools.log_body_weight));

  server.registerTool("delete_body_weight", {
    title: "Delete a weigh-in",
    description: "Deletes the weigh-in for one date, and its progress photos. Confirm with the person first.",
    inputSchema: { date: z.string().describe("e.g. 2026-11-25") },
    annotations: write(true, true),
  }, run(tools.delete_body_weight));

  server.registerTool("get_plan", {
    title: "Training plan and next workout",
    description: "The training plan: each day's exercises with sets, reps, rest and the suggested weight for next time (from the app's progression rules), plus which day is next in the rotation.",
    inputSchema: {},
    annotations: read,
  }, run(tools.get_plan));

  server.registerTool("get_workouts", {
    title: "Saved workouts",
    description: "Saved workouts, newest first: date, day, duration, notes and every exercise's sets (weight×reps). savedAt identifies a workout for delete_workout.",
    inputSchema: {
      from: dateArg("Earliest date"), to: dateArg("Latest date"),
      limit: z.number().int().min(1).max(200).optional().describe("Defaults to 20."),
    },
    annotations: read,
  }, run(tools.get_workouts));

  server.registerTool("get_exercise_history", {
    title: "One exercise's history",
    description: "Every logged session of one exercise, with the heaviest set each time and the suggested weight for next time.",
    inputSchema: { exercise: z.string().describe("Exercise name as in get_plan, e.g. \"Lat Pulldown\". Case-insensitive.") },
    annotations: read,
  }, run(tools.get_exercise_history));

  server.registerTool("log_workout", {
    title: "Log a workout",
    description: "Saves a finished workout for one plan day, as the app's Save button does. Exercise names must match that day's exercises in get_plan; set substitute when the plan's substitute was done instead. Confirm the sets with the person before saving.",
    inputSchema: {
      dayId: z.string().describe("Day id from get_plan, e.g. \"a\"."),
      date: dateArg("Defaults to today"),
      exercises: z.array(z.object({
        exercise: z.string(),
        substitute: z.boolean().optional().describe("True if the plan's substitute was done instead."),
        sets: z.array(setSchema).min(1).max(20),
        unit: z.enum(["kg", "lb"]).optional().describe("Defaults to kg."),
        notes: z.string().max(500).optional(),
      })).min(1).max(30),
      notes: z.string().max(1000).optional().describe("How the workout went."),
      durationMin: z.number().int().min(1).max(600).optional(),
    },
    annotations: write(false, false),
  }, run(tools.log_workout));

  server.registerTool("delete_workout", {
    title: "Delete a workout",
    description: "Deletes one saved workout (every exercise in it) and its photos. Take date, dayId and savedAt from get_workouts. Confirm with the person first: this can't be undone.",
    inputSchema: { date: z.string(), dayId: z.string(), savedAt: z.number().int() },
    annotations: write(true, true),
  }, run(tools.delete_workout));
}
