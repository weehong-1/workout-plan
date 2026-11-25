#!/usr/bin/env node
// Local MCP server for the workout plan app. It signs in as you with your
// email and password, so row-level security limits it to your own data, and it
// writes through the same database function the app uses.
//
// Setup: see mcp/README.md.

import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { createClient } from "@supabase/supabase-js";
import { z } from "zod";
// The app's own logic, so suggestions and weekly averages match what it shows.
import { dayExercises, defaultPlan, nextPlanDay, suggestFor } from "../src/lib/plan.ts";
import { groupByWeek, weeklyTrend } from "../src/lib/weekly.ts";

for (const file of ["../.env.local", ".env"]) {
  const path = fileURLToPath(new URL(file, import.meta.url));
  if (existsSync(path)) process.loadEnvFile(path);
}

const LB_PER_KG = 2.2046226218;
const round1 = (n) => Math.round(n * 10) / 10;
const convert = (weight, from, to) => (from === to ? weight : from === "kg" ? weight * LB_PER_KG : weight / LB_PER_KG);
const pad = (n) => String(n).padStart(2, "0");
const today = () => { const d = new Date(); return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`; };
const toTime = (date) => { const [y, m, d] = date.split("/").map(Number); return new Date(y, m - 1, d).getTime(); };
const dateOf = (t) => { const d = new Date(t); return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`; };

/** Accepts 2026-11-25 or 2026/11/25 and returns the app's YYYY/MM/DD, refusing impossible or future dates. */
function appDate(input) {
  if (input === undefined || input === "") return today();
  const match = /^(\d{4})[-/](\d{2})[-/](\d{2})$/.exec(input.trim());
  if (!match) throw new Error(`Use a date like 2026-11-25, not "${input}".`);
  const date = `${match[1]}/${match[2]}/${match[3]}`;
  if (dateOf(toTime(date)) !== date) throw new Error(`${input} is not a real date.`);
  if (date > today()) throw new Error(`${input} is in the future.`);
  return date;
}

// ---------------------------------------------------------------------------
// Session

let session = null; // { client, userId }

async function account() {
  if (session) return session;
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  const email = process.env.WORKOUT_EMAIL;
  const password = process.env.WORKOUT_PASSWORD;
  if (!url || !key) throw new Error("Supabase is not configured: NEXT_PUBLIC_SUPABASE_URL and the publishable key must be in .env.local or the MCP's env.");
  if (!email || !password) throw new Error("Set WORKOUT_EMAIL and WORKOUT_PASSWORD in mcp/.env (see mcp/.env.example).");
  const client = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: true } });
  const { data, error } = await client.auth.signInWithPassword({ email, password });
  if (error) throw new Error(`Sign-in failed: ${error.message}`);
  session = { client, userId: data.user.id };
  return session;
}

async function rows(table, order = []) {
  const { client, userId } = await account();
  const out = [];
  for (let start = 0; ; start += 1000) {
    let query = client.from(table).select("*").eq("user_id", userId);
    for (const column of order) query = query.order(column);
    const { data, error } = await query.range(start, start + 999);
    if (error) throw new Error(error.message);
    out.push(...data);
    if (data.length < 1000) return out;
  }
}

async function change(type, payload) {
  const { client, userId } = await account();
  const { error } = await client.rpc("apply_workout_change", {
    account_id: userId, change_type: type, payload: { ...payload, _mutationId: randomUUID() },
  });
  if (error) throw new Error(error.message);
}

async function settings() {
  return (await rows("settings"))[0] ?? {};
}

async function plan() {
  const s = await settings();
  return { plan: s.plan ?? defaultPlan(), bodyUnit: s.body_unit ?? "kg" };
}

async function log() {
  return (await rows("log_entries", ["saved_at", "id"])).map((r) => ({
    id: r.id, date: r.date, dayId: r.day_id, exercise: r.exercise, substitute: r.substitute ?? undefined,
    sets: r.sets, unit: r.unit ?? "kg", notes: r.notes ?? "", savedAt: Number(r.saved_at),
    photos: Array.isArray(r.photos) ? r.photos : [],
  }));
}

// ---------------------------------------------------------------------------
// Tools

const server = new McpServer({ name: "workout-plan", version: "0.1.0" });
const json = (value) => ({ content: [{ type: "text", text: JSON.stringify(value, null, 2) }] });
const tool = (name, config, handler) => server.registerTool(name, config, async (args) => {
  try {
    return json(await handler(args ?? {}));
  } catch (error) {
    return { isError: true, content: [{ type: "text", text: error instanceof Error ? error.message : String(error) }] };
  }
});
const read = { readOnlyHint: true, openWorldHint: false };

tool("get_body_weight", {
  title: "Body weight by week",
  description: "Body weight grouped into Monday-to-Sunday weeks, with each week's average, the change from the week before, and whether weight is dropping (the weekly slope over the last 4 weeks). Values are in the account's body-weight unit.",
  inputSchema: {
    from: z.string().optional().describe("Earliest date, e.g. 2026-09-01. Defaults to all time."),
    to: z.string().optional().describe("Latest date. Defaults to today."),
  },
  annotations: read,
}, async ({ from, to }) => {
  const [{ bodyUnit }, weights] = await Promise.all([plan(), rows("body_weights", ["date"])]);
  const lo = from ? appDate(from) : "0000/00/00";
  const hi = to ? appDate(to) : today();
  const days = weights.filter((w) => w.date >= lo && w.date <= hi).map((w) => ({
    date: w.date, t: toTime(w.date), value: convert(Number(w.weight), w.unit, bodyUnit),
  }));
  const weeks = groupByWeek(days);
  const trend = weeklyTrend(weeks);
  return {
    unit: bodyUnit,
    latest: days.at(-1) ? { date: days.at(-1).date, weight: round1(days.at(-1).value) } : null,
    trend: trend && {
      perWeek: round1(trend.perWeek),
      overWeeks: trend.weeks,
      verdict: Math.abs(trend.perWeek) < 0.1 ? "steady" : trend.perWeek < 0 ? "dropping" : "rising",
    },
    weeks: weeks.map((w) => ({
      weekOf: dateOf(w.start),
      average: round1(w.average),
      change: w.change === null ? null : round1(w.change),
      days: w.days.map((d) => ({ date: d.date, weight: round1(d.value) })),
    })),
  };
});

tool("log_body_weight", {
  title: "Log a weigh-in",
  description: "Saves a body weight for a day. One weigh-in per day: logging the same date again replaces it.",
  inputSchema: {
    weight: z.number().positive().lt(1000),
    date: z.string().optional().describe("e.g. 2026-11-25. Defaults to today."),
    unit: z.enum(["kg", "lb"]).optional().describe("Defaults to the account's body-weight unit."),
  },
  annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false },
}, async ({ weight, date, unit }) => {
  const day = appDate(date);
  const chosen = unit ?? (await plan()).bodyUnit;
  await change("weight", { date: day, weight, unit: chosen });
  return { saved: { date: day, weight, unit: chosen } };
});

tool("delete_body_weight", {
  title: "Delete a weigh-in",
  description: "Deletes the weigh-in for one date, and its progress photos.",
  inputSchema: { date: z.string().describe("e.g. 2026-11-25") },
  annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
}, async ({ date }) => {
  const day = appDate(date);
  const entry = (await rows("body_weights")).find((w) => w.date === day);
  if (!entry) throw new Error(`There is no weigh-in on ${day}.`);
  await change("deleteWeight", { date: day });
  await removePhotos(Array.isArray(entry.photos) ? entry.photos : []);
  return { deleted: { date: day, weight: Number(entry.weight), unit: entry.unit } };
});

tool("get_plan", {
  title: "Training plan and next workout",
  description: "The training plan: each day's exercises with sets, reps, rest and the suggested weight for next time (from the app's progression rules), plus which day is next in the rotation.",
  inputSchema: {},
  annotations: read,
}, async () => {
  const [{ plan: p }, history] = await Promise.all([plan(), log()]);
  const next = nextPlanDay(p.days, history);
  return {
    trainingWeekdays: p.weekdays.map((d) => ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][d]),
    nextDay: { id: next.id, name: next.name },
    days: p.days.map((day) => ({
      id: day.id, letter: day.letter, name: day.name, optional: Boolean(day.optional),
      exercises: dayExercises(day).map((ex) => {
        const past = history.filter((e) => e.exercise === ex.name && !e.substitute);
        const s = suggestFor(ex, past);
        return {
          name: ex.name, sets: ex.sets, reps: ex.reps, rest: ex.rest, substitute: ex.substitute || undefined,
          optional: Boolean(ex.optional), next: { weight: s.weight, why: s.label },
        };
      }),
    })),
  };
});

function sessionsOf(entries, sessionRows, days) {
  const byKey = new Map();
  for (const e of entries) {
    const key = `${e.date}|${e.dayId}|${e.savedAt}`;
    if (!byKey.has(key)) byKey.set(key, { date: e.date, dayId: e.dayId, savedAt: e.savedAt, exercises: [] });
    byKey.get(key).exercises.push({
      exercise: e.substitute ?? e.exercise, ...(e.substitute ? { insteadOf: e.exercise } : {}),
      unit: e.unit, sets: e.sets.map((s) => `${s.weight}×${s.reps}`).join(", "), ...(e.notes ? { notes: e.notes } : {}),
    });
  }
  return [...byKey.values()].map((s) => {
    const meta = sessionRows.find((r) => r.day_id === s.dayId && Number(r.saved_at) === s.savedAt);
    return {
      ...s, day: days.find((d) => d.id === s.dayId)?.name ?? s.dayId,
      ...(meta?.duration_min != null ? { durationMin: meta.duration_min } : {}),
      ...(meta?.notes ? { notes: meta.notes } : {}),
    };
  });
}

tool("get_workouts", {
  title: "Saved workouts",
  description: "Saved workouts, newest first: date, day, duration, notes and every exercise's sets (weight×reps). savedAt identifies a workout for delete_workout.",
  inputSchema: {
    from: z.string().optional().describe("Earliest date, e.g. 2026-11-01."),
    to: z.string().optional(),
    limit: z.number().int().min(1).max(200).optional().describe("Defaults to 20."),
  },
  annotations: read,
}, async ({ from, to, limit = 20 }) => {
  const [{ plan: p }, entries, meta] = await Promise.all([plan(), log(), rows("workout_sessions")]);
  const lo = from ? appDate(from) : "0000/00/00";
  const hi = to ? appDate(to) : today();
  return sessionsOf(entries.filter((e) => e.date >= lo && e.date <= hi), meta, p.days).reverse().slice(0, limit);
});

tool("get_exercise_history", {
  title: "One exercise's history",
  description: "Every logged session of one exercise (by its plan name), with the heaviest set each time and the suggested weight for next time.",
  inputSchema: { exercise: z.string().describe("Exercise name as in get_plan, e.g. \"Lat Pulldown\". Case-insensitive.") },
  annotations: read,
}, async ({ exercise }) => {
  const [{ plan: p }, entries] = await Promise.all([plan(), log()]);
  const all = p.days.flatMap((d) => dayExercises(d));
  const ex = all.find((e) => e.name.toLowerCase() === exercise.trim().toLowerCase());
  const name = ex?.name ?? exercise.trim();
  const past = entries.filter((e) => e.exercise === name && !e.substitute);
  if (!ex && !past.length) throw new Error(`No exercise called "${exercise}". Use a name from get_plan.`);
  return {
    exercise: name,
    next: ex ? (({ weight, label }) => ({ weight, why: label }))(suggestFor(ex, past)) : null,
    sessions: past.map((e) => ({
      date: e.date, unit: e.unit, sets: e.sets.map((s) => `${s.weight}×${s.reps}`).join(", "),
      heaviest: Math.max(0, ...e.sets.map((s) => parseFloat(s.weight)).filter(Number.isFinite)) || null,
      ...(e.notes ? { notes: e.notes } : {}),
    })),
  };
});

const setSchema = z.object({
  weight: z.union([z.number().nonnegative(), z.literal("BW")]).describe("Weight, or \"BW\" for bodyweight."),
  reps: z.number().int().positive().max(1000).describe("Reps, or seconds for timed exercises."),
});

tool("log_workout", {
  title: "Log a workout",
  description: "Saves a finished workout for one plan day, as the app's Save button does. Exercise names must match that day's exercises in get_plan; set substitute when the substitute was done instead. Ask the person to confirm the sets before saving.",
  inputSchema: {
    dayId: z.string().describe("Day id from get_plan, e.g. \"a\"."),
    date: z.string().optional().describe("Defaults to today."),
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
  annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false },
}, async ({ dayId, date, exercises, notes, durationMin }) => {
  const day = appDate(date);
  const { plan: p } = await plan();
  const planDay = p.days.find((d) => d.id === dayId);
  if (!planDay) throw new Error(`No day "${dayId}". Days: ${p.days.map((d) => `${d.id} (${d.name})`).join(", ")}.`);
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
});

tool("delete_workout", {
  title: "Delete a workout",
  description: "Deletes one saved workout (every exercise in it) and its photos. Take date, dayId and savedAt from get_workouts. Confirm with the person first: this can't be undone.",
  inputSchema: { date: z.string(), dayId: z.string(), savedAt: z.number().int() },
  annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true, openWorldHint: false },
}, async ({ date, dayId, savedAt }) => {
  const day = appDate(date);
  const [entries, meta] = await Promise.all([log(), rows("workout_sessions")]);
  const mine = entries.filter((e) => e.date === day && e.dayId === dayId && e.savedAt === savedAt);
  if (!mine.length) throw new Error("No workout matches that date, dayId and savedAt. Use get_workouts.");
  const sessionPhotos = meta.find((r) => r.day_id === dayId && Number(r.saved_at) === savedAt)?.photos ?? [];
  await change("deleteSession", { date: day, dayId, savedAt });
  await removePhotos([...new Set([...mine.flatMap((e) => e.photos), ...sessionPhotos])]);
  return { deleted: { date: day, dayId, exercises: mine.map((e) => e.substitute ?? e.exercise) } };
});

// Photo files are removed only after the database no longer refers to them.
async function removePhotos(paths) {
  if (!paths.length) return;
  const { client } = await account();
  await client.storage.from("photos").remove(paths);
}

await server.connect(new StdioServerTransport());
