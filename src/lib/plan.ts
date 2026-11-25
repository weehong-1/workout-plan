// Snapshot of the "重新開始計劃" (restart plan) tab of the Google Sheet
// "Weekly Training Plan (Trainer's 3 Day Rotation)", with warm ups, form cues,
// substitutes and videos from its "Training Plan" tab.

export type Exercise = {
  order: string;
  name: string;
  muscles: string;
  warmUp: string;
  sets: number;
  reps: string;
  rest: string;
  cues: string;
  video: string;
  substitute: string;
  substituteVideo: string;
  // Restart weight (about 25% below the last trainer session).
  startWeight?: string;
  // Last working set with the trainer (June 2025).
  trainerLast?: string;
  // Extra instruction from the sheet's notes column.
  note?: string;
  // Add-on: do it last, only if there is time.
  optional?: boolean;
};

export type Block = {
  // "Straight Sets", "Optional" or "Superset A", "Superset B", ...
  format: string;
  exercises: Exercise[];
};

export type Day = {
  id: string;
  letter: string;
  name: string;
  optional: boolean;
  focus: string;
  duration: string;
  blocks: Block[];
};

const yt = (id: string) => `https://www.youtube.com/watch?v=${id}`;
// The sheet links YouTube searches where no specific video was picked yet.
const search = (q: string) => `https://www.youtube.com/results?search_query=${encodeURIComponent(q).replace(/%20/g, "+")}`;

export function youtubeId(url: string): string | null {
  try {
    return new URL(url).searchParams.get("v");
  } catch {
    return null;
  }
}

const COMPOUND = "8 / 5 / 3";
const ISOLATION = "8 / 5";
const BODYWEIGHT = "None";
const ONLY_IF_TIME = "Optional add-on: do it last, only if you have time.";
const BUILD_REPS = "Start with 8 reps; once they feel steady, build up to 10.";

// Every exercise is a straight set, done in the listed order.
const straight = (exercise: Exercise): Block => ({
  format: exercise.optional ? "Optional" : "Straight Sets",
  exercises: [exercise],
});

export const DAYS: Day[] = [
  {
    id: "a",
    letter: "A",
    name: "Hinge Day",
    optional: false,
    focus: "Adductors, wide grip pulldown, trap bar RDL, cable chest press, leg raises, triceps",
    duration: "45~60 min",
    blocks: [
      straight({
        order: "1",
        name: "Adductor Machine",
        muscles: "Inner thighs (adductors)",
        warmUp: ISOLATION,
        sets: 1,
        reps: "10",
        rest: "1~2 min",
        cues: "Sit tall with your back on the pad, squeeze the pads together and pause 1 sec, open slowly to the stretch",
        video: search("adductor machine tutorial"),
        substitute: "Cable Hip Adduction",
        substituteVideo: search("cable hip adduction tutorial"),
        startWeight: "50kg",
        trainerLast: "65kg × 9",
      }),
      straight({
        order: "2",
        name: "Wide Grip Lat Pulldown",
        muscles: "Lats, upper back",
        warmUp: COMPOUND,
        sets: 2,
        reps: "10",
        rest: "2~3 min",
        cues: "Chest up, pull the bar to your upper chest with your elbows driving down, control the bar back up. Use straps if grip gives out first",
        video: yt("AOpi-p0cJkc"),
        substitute: "Neutral Grip Lat Pulldown",
        substituteVideo: search("neutral grip lat pulldown tutorial"),
        startWeight: "40kg",
        trainerLast: "55kg × 10",
      }),
      straight({
        order: "3",
        name: "Trap Bar RDL",
        muscles: "Hamstrings, glutes, lower back",
        warmUp: COMPOUND,
        sets: 2,
        reps: "10",
        rest: "2~3 min",
        cues: "Push your hips back with a slight knee bend, keep your back flat and the handles close to your legs, stand up by squeezing your glutes",
        video: search("trap bar romanian deadlift tutorial"),
        substitute: "Dumbbell Romanian Deadlift",
        substituteVideo: yt("hQgFixeXdZo"),
        startWeight: "30kg",
        trainerLast: "40kg × 10",
      }),
      straight({
        order: "4",
        name: "Standing Cable Chest Press",
        muscles: "Chest, front delts, triceps",
        warmUp: COMPOUND,
        sets: 2,
        reps: "10",
        rest: "1.5~2 min",
        cues: "Staggered stance, brace your core, press the handles forward and together at chest height, don't let the cables pull your shoulders forward. Trainer's setting: C1H5",
        video: search("standing cable chest press tutorial"),
        substitute: "Dumbbell Press",
        substituteVideo: yt("5Y3VZsLb1Ys"),
        startWeight: "20kg",
        trainerLast: "27.5kg × 10",
        note: "Dumbbell Press substitute: bench setting S2.",
      }),
      straight({
        order: "5",
        name: "Flat Bench Leg Raise",
        muscles: "Lower abs",
        warmUp: BODYWEIGHT,
        sets: 3,
        reps: "8",
        rest: "1 min",
        cues: "Lie flat holding the bench behind your head, keep your lower back pressed down, lower your legs slowly without touching the bench",
        video: search("flat bench leg raise tutorial"),
        substitute: "Hanging Leg Raise",
        substituteVideo: yt("Nw0LOKe3_l8"),
        startWeight: "BW",
        trainerLast: "BW 3 × 8",
      }),
      straight({
        order: "6",
        name: "Cable Triceps Extension",
        muscles: "Triceps",
        warmUp: ISOLATION,
        sets: 2,
        reps: "10",
        rest: "1~1.5 min",
        cues: "Elbows pinned at your sides, extend fully and squeeze, return slowly without letting the elbows drift forward",
        video: search("cable triceps extension tutorial"),
        substitute: "Overhead Cable Triceps Extension",
        substituteVideo: yt("AUsSlsBu5eg"),
        startWeight: "12.5kg",
        trainerLast: "17.5kg × 10",
      }),
      straight({
        order: "Extra",
        name: "Dumbbell Lateral Raise",
        muscles: "Side delts",
        warmUp: BODYWEIGHT,
        sets: 2,
        reps: "12~15",
        rest: "1 min",
        cues: "Lead with the elbows, raise to shoulder height, lower slowly",
        video: yt("4hTUCDUQaNA"),
        substitute: "Cable Lateral Raise",
        substituteVideo: yt("Z5FA9aq3L6A"),
        startWeight: "3kg",
        trainerLast: "4kg × 15",
        note: ONLY_IF_TIME,
        optional: true,
      }),
    ],
  },
  {
    id: "b",
    letter: "B",
    name: "Squat Day",
    optional: false,
    focus: "Safety bar squat, Smith machine incline press, chest fly, sissy squat, preacher curl, ball rollout",
    duration: "45~60 min",
    blocks: [
      straight({
        order: "1",
        name: "Safety Bar Squat (Heels Elevated)",
        muscles: "Quads, glutes",
        warmUp: COMPOUND,
        sets: 2,
        reps: "10",
        rest: "2~3 min",
        cues: "Heels on a plate or wedge, chest up, sit straight down between your heels as deep as you can control, drive up through the whole foot",
        video: search("safety bar squat heels elevated tutorial"),
        substitute: "Smith Machine Squat (Heels Elevated)",
        substituteVideo: search("smith machine squat heels elevated tutorial"),
        startWeight: "22.5kg",
        trainerLast: "30kg × 10",
      }),
      straight({
        order: "2",
        name: "Smith Machine Incline Press",
        muscles: "Upper chest, front delts, triceps",
        warmUp: COMPOUND,
        sets: 2,
        reps: "10",
        rest: "2~3 min",
        cues: "Bench on incline (trainer's settings S4, S7), shoulder blades back and down, lower the bar to your upper chest, press without locking out hard",
        video: search("smith machine incline press tutorial"),
        substitute: "Incline Dumbbell Press",
        substituteVideo: yt("OC7Qs_SAO2E"),
        startWeight: "10kg",
        trainerLast: "15kg × 10",
      }),
      straight({
        order: "3",
        name: "Machine Chest Fly",
        muscles: "Chest",
        warmUp: ISOLATION,
        sets: 2,
        reps: "10",
        rest: "1.5~2 min",
        cues: "Slight bend in the elbows, bring the handles together in front of your chest and squeeze, open slowly to a comfortable stretch",
        video: search("machine chest fly tutorial"),
        substitute: "Cable Fly",
        substituteVideo: yt("PLS8SpaHlnI"),
        startWeight: "37.5kg",
        trainerLast: "50kg × 10",
      }),
      straight({
        order: "4",
        name: "Supported Sissy Squat",
        muscles: "Quads",
        warmUp: BODYWEIGHT,
        sets: 3,
        reps: "8",
        rest: "1~1.5 min",
        cues: "Hold a rail or post, rise onto your toes and let your knees travel forward as you lean back, keep hips and torso in one line",
        video: search("supported sissy squat tutorial"),
        substitute: "Leg Extension",
        substituteVideo: search("leg extension machine tutorial"),
        startWeight: "BW",
        trainerLast: "BW 3 × 10",
        note: BUILD_REPS,
      }),
      straight({
        order: "5",
        name: "Cable Preacher Curl",
        muscles: "Biceps",
        warmUp: ISOLATION,
        sets: 2,
        reps: "10",
        rest: "1~1.5 min",
        cues: "Upper arms flat on the pad, curl up and squeeze, lower all the way down under control",
        video: search("cable preacher curl tutorial"),
        substitute: "Preacher Curl Machine",
        substituteVideo: search("preacher curl machine tutorial"),
        startWeight: "12.5kg",
        trainerLast: "17.5kg × 9",
      }),
      straight({
        order: "6",
        name: "Stability Ball Rollout",
        muscles: "Abs, core",
        warmUp: BODYWEIGHT,
        sets: 3,
        reps: "8",
        rest: "1 min",
        cues: "Forearms on the ball, brace your abs and squeeze your glutes, roll out until your body is straight, pull back without arching your lower back",
        video: search("stability ball rollout tutorial"),
        substitute: "Ball Plank",
        substituteVideo: search("stability ball plank tutorial"),
        startWeight: "BW",
        trainerLast: "BW 3 × 10",
        note: BUILD_REPS,
      }),
    ],
  },
  {
    id: "c",
    letter: "C",
    name: "Back Day",
    optional: false,
    focus: "Leg curl, single arm pulldown, split squat, wide grip row, rear delts, side plank",
    duration: "45~60 min",
    blocks: [
      straight({
        order: "1",
        name: "Lying Leg Curl",
        muscles: "Hamstrings",
        warmUp: ISOLATION,
        sets: 2,
        reps: "10",
        rest: "1.5~2 min",
        cues: "Hips pressed into the pad, curl your heels toward your glutes and pause, lower slowly",
        video: yt("SbSNUXPRkc8"),
        substitute: "Seated Leg Curl",
        substituteVideo: search("seated leg curl tutorial"),
        startWeight: "17.5kg",
        trainerLast: "25kg × 10",
      }),
      straight({
        order: "2",
        name: "Single Arm Machine Pulldown",
        muscles: "Lats",
        warmUp: ISOLATION,
        sets: 2,
        reps: "10 per arm",
        rest: "1.5~2 min",
        cues: "Sit tall, pull your elbow down toward your hip, pause, and let your arm stretch fully overhead",
        video: search("single arm machine pulldown tutorial"),
        substitute: "Single Arm Cable Pulldown",
        substituteVideo: search("single arm cable lat pulldown tutorial"),
        startWeight: "22.5kg",
        trainerLast: "30kg × 10",
      }),
      straight({
        order: "3",
        name: "Split Squat",
        muscles: "Quads, glutes",
        warmUp: "8 / 5 per leg",
        sets: 2,
        reps: "10 per leg",
        rest: "2 min",
        cues: "Long stance, drop your back knee straight down, keep your front heel planted and your torso upright",
        video: search("split squat tutorial"),
        substitute: "Leg Press",
        substituteVideo: yt("K5n2vg3oZa4"),
        startWeight: "BW",
        trainerLast: "+8kg × 10",
        note: "If your ankle hurts, stop right away and switch to Leg Extension or Leg Press. Don't push through it.",
      }),
      straight({
        order: "4",
        name: "Wide Grip Seated Row",
        muscles: "Upper back, rear delts",
        warmUp: ISOLATION,
        sets: 2,
        reps: "10",
        rest: "1.5~2 min",
        cues: "Chest up, pull the bar to your lower chest with elbows flared out, squeeze your shoulder blades together, return slowly",
        video: search("wide grip seated cable row tutorial"),
        substitute: "Seated Cable Row",
        substituteVideo: yt("f_r95UajQcg"),
        startWeight: "40kg",
        trainerLast: "55kg × 10",
      }),
      straight({
        order: "5",
        name: "Single Arm Reverse Fly",
        muscles: "Rear delts",
        warmUp: "8",
        sets: 2,
        reps: "10 per arm",
        rest: "1 min",
        cues: "Lead with your hand out to the side at shoulder height, keep a soft elbow, don't swing your torso",
        video: search("single arm cable reverse fly tutorial"),
        substitute: "Reverse Pec Deck Fly",
        substituteVideo: yt("e4dJ1QoxUtI"),
        startWeight: "10kg",
        trainerLast: "12.5kg × 10",
      }),
      straight({
        order: "6",
        name: "Side Plank",
        muscles: "Obliques, core",
        warmUp: BODYWEIGHT,
        sets: 2,
        reps: "30 sec per side",
        rest: "1 min",
        cues: "Elbow under your shoulder, hips up so your body forms a straight line, don't let your hips sag",
        video: search("side plank tutorial"),
        substitute: "Ball Plank",
        substituteVideo: search("stability ball plank tutorial"),
        startWeight: "BW",
        trainerLast: "2 × 50 sec",
      }),
      straight({
        order: "Extra",
        name: "Standing Calf Raise",
        muscles: "Calves",
        warmUp: BODYWEIGHT,
        sets: 2,
        reps: "12~15",
        rest: "1 min",
        cues: "Balls of your feet on the edge of a step, lower your heels for a full stretch, rise as high as you can and pause 1 sec, no bouncing",
        video: yt("KRJIKsY02nE"),
        substitute: "Single Leg Dumbbell Calf Raise",
        substituteVideo: yt("1D7s-VyLq0Y"),
        startWeight: "BW",
        trainerLast: "Not done with the trainer",
        note: ONLY_IF_TIME,
        optional: true,
      }),
    ],
  },
];

// Progression rules from the sheet.
export const PROGRESSION_RULES = [
  "When you hit 10 clean reps on both sets, add the smallest step next time (1.25~2.5 kg).",
  "If you can't reach 8 reps after adding weight, go back to the previous weight.",
  "Rotate A → B → C each week, training Monday, Wednesday and Friday.",
  "If your ankle hurts on split squats, stop right away and switch to Leg Extension or Leg Press.",
  "Reassess after 4~6 weeks. Don't rush back to your June 2025 weights.",
];

// Below this many reps after a weight increase, the sheet says to go back.
export const BACK_OFF_REPS = 8;

export function getDay(id: string): Day | undefined {
  return DAYS.find((d) => d.id === id);
}

export function dayLabel(day: Day): string {
  return `${day.letter} ${day.name}${day.optional ? " (Optional)" : ""}`;
}

// Rotation from the sheet: A → B → C. Anything else (including sessions from
// the old optional D day) starts again at A.
export function nextDayId(lastDayId: string | undefined): string {
  switch (lastDayId) {
    case "a":
      return "b";
    case "b":
      return "c";
    default:
      return "a";
  }
}

// Seconds to start the rest timer with, using the lower bound of the range
// in the sheet (e.g. "1.5~2 min" → 90). The timer has +15s for when you need
// the longer end.
export function restSeconds(rest: string): number | null {
  const m = rest.match(/(\d+(?:\.\d+)?)(?:~\d+(?:\.\d+)?)?\s*(min|sec)/);
  if (!m) return null;
  const value = parseFloat(m[1]);
  return Math.round(m[2] === "min" ? value * 60 : value);
}

// Numeric rep target ("10", "10 per leg", "12~15" → 10, 10, 12). Timed sets return null.
export function targetReps(reps: string): number | null {
  if (/sec|min/.test(reps)) return null;
  const m = reps.match(/^\d+/);
  return m ? parseInt(m[0], 10) : null;
}

// "10 per leg" → { count: "10", per: "per leg" }.
export function splitReps(reps: string): { count: string; per: string } {
  const m = reps.match(/^(.*?)\s*(per (?:leg|arm|side))$/);
  return m ? { count: m[1], per: m[2] } : { count: reps, per: "" };
}

// Numeric part of a restart weight ("22.5kg" → "22.5"); "" for bodyweight.
export function startWeightValue(weight: string | undefined): string {
  const m = weight?.match(/^(\d+(?:\.\d+)?)kg$/);
  return m ? m[1] : "";
}

type LoggedSets = { sets: { weight: string; reps: string }[] };

const topWeight = (entry: LoggedSets) =>
  Math.max(-Infinity, ...entry.sets.map((s) => parseFloat(s.weight)).filter(Number.isFinite));

export type Advice =
  | { kind: "increase"; target: number; bodyweight: boolean }
  | { kind: "backOff"; weight: string };

// What to do this session, from the sheet's progression rules, given the
// exercise's history (oldest first, original exercise only).
export function progressionAdvice(history: LoggedSets[], ex: Pick<Exercise, "reps" | "sets">): Advice | null {
  const target = targetReps(ex.reps);
  const last = history.at(-1);
  if (!last || target === null) return null;
  if (last.sets.length >= ex.sets && last.sets.every((s) => parseInt(s.reps, 10) >= target)) {
    return { kind: "increase", target, bodyweight: !Number.isFinite(topWeight(last)) };
  }
  if (target < 10 || !last.sets.some((s) => parseInt(s.reps, 10) < BACK_OFF_REPS)) return null;
  const lastTop = topWeight(last);
  if (!Number.isFinite(lastTop)) return null;
  // Only after an increase: find the most recent lighter session.
  for (let i = history.length - 2; i >= 0; i--) {
    const top = topWeight(history[i]);
    if (!Number.isFinite(top)) continue;
    if (top < lastTop) return { kind: "backOff", weight: String(top) };
    if (top > lastTop) return null;
  }
  return null;
}

export function isLowerBody(muscles: string): boolean {
  return /quad|glute|hamstring|calves|lower|adductor/i.test(muscles);
}

// ---------------------------------------------------------------------------
// Editable plan. The account stores its own days (A, B, C and any it adds);
// until then the built-in restart plan above is used.

export type PlanSettings = { days: Day[]; weekdays: number[] };

// 0 = Sunday … 6 = Saturday, as in Date#getDay. The sheet trains Mon/Wed/Fri.
export const DEFAULT_WEEKDAYS = [1, 3, 5];
export const DAY_LETTERS = "ABCDEFGHIJKL";
export const MAX_DAYS = 12;
export const MAX_DAY_EXERCISES = 30;

export function defaultPlan(): PlanSettings {
  return { days: structuredClone(DAYS), weekdays: [...DEFAULT_WEEKDAYS] };
}

export function dayExercises(day: Day): Exercise[] {
  return day.blocks.flatMap((block) => block.exercises);
}

export function exerciseCount(n: number): string {
  return `${n} ${n === 1 ? "exercise" : "exercises"}`;
}

// Letters follow the order of the days, so moving a day relabels it.
export function relabel(days: Day[]): Day[] {
  return days.map((day, i) => ({ ...day, letter: DAY_LETTERS[i] ?? String(i + 1) }));
}

// The next day in the rotation after the most recent rotation day you logged.
// Optional days never move the rotation on.
export function nextPlanDay(days: Day[], log: { dayId: string }[]): Day {
  const rotation = days.filter((day) => !day.optional);
  if (!rotation.length) return days[0];
  for (let i = log.length - 1; i >= 0; i--) {
    const index = rotation.findIndex((day) => day.id === log[i].dayId);
    if (index >= 0) return rotation[(index + 1) % rotation.length];
  }
  return rotation[0];
}

export const WARM_UP_OPTIONS = ["None", "8 / 5", "8 / 5 / 3"] as const;
const WARM_UP_PERCENT: Record<number, number[]> = { 1: [0.5], 2: [0.5, 0.75], 3: [0.5, 0.7, 0.85] };

// Warm-up sets from the sheet's notation and today's working weight:
// "8 / 5 / 3" at 60 kg → 8 × 30, 5 × 42.5, 3 × 50 (rounded down to the step).
export function warmUpSets(warmUp: string, workWeight: number | null, step = 1.25): { weight: string; reps: string }[] {
  const reps = warmUp.match(/\d+/g) ?? [];
  if (!reps.length || /^none/i.test(warmUp)) return [];
  const percents = WARM_UP_PERCENT[reps.length] ?? reps.map(() => 0.5);
  return reps.map((r, i) => ({
    reps: r,
    weight: workWeight && workWeight > 0
      ? String(Math.max(step, Math.floor((workWeight * percents[i]) / step) * step))
      : "BW",
  }));
}

export type Verdict = "up" | "hold" | "back" | "first";
export type Suggestion = {
  weight: string; // numeric string, or "BW" / "" when there is no load
  verdict: Verdict;
  label: string;
};

type History = { sets: { weight: string; reps: string }[]; unit?: string };

const fmt = (n: number) => String(Number(n.toFixed(2)));

// Today's starting weight and what changed since last time, from the
// sheet's progression rules (see progressionAdvice).
export function suggestFor(ex: Pick<Exercise, "reps" | "sets" | "startWeight">, history: History[]): Suggestion {
  const last = history.at(-1);
  const start = startWeightValue(ex.startWeight);
  if (!last) {
    return { weight: start || (ex.startWeight === "BW" ? "BW" : ""), verdict: "first", label: "First time" };
  }
  const step = last.unit === "lb" ? 2.5 : 1.25;
  const unit = last.unit === "lb" ? "lb" : "kg";
  const top = topWeight(last);
  const advice = progressionAdvice(history, ex);
  if (!Number.isFinite(top)) {
    return advice?.kind === "increase"
      ? { weight: "BW", verdict: "up", label: "↑ add reps" }
      : { weight: "BW", verdict: "hold", label: "Hold" };
  }
  if (advice?.kind === "increase") return { weight: fmt(top + step), verdict: "up", label: `↑ +${step} ${unit}` };
  if (advice?.kind === "backOff") return { weight: advice.weight, verdict: "back", label: `↓ back to ${advice.weight}` };
  return { weight: fmt(top), verdict: "hold", label: "Hold" };
}

// Heaviest numeric weight in a session (NaN-safe), for charts and summaries.
export function sessionTop(entry: { sets: { weight: string }[] }): number | null {
  const top = topWeight(entry as LoggedSets);
  return Number.isFinite(top) ? top : null;
}

export function sessionVolume(entry: { sets: { weight: string; reps: string }[] }): number {
  return entry.sets.reduce((sum, s) => sum + (parseFloat(s.weight) || 0) * (parseInt(s.reps, 10) || 0), 0);
}

// "65kg × 9" → 65, for the trainer reference line.
export function trainerWeight(ex: Pick<Exercise, "trainerLast">): number | null {
  const m = ex.trainerLast?.match(/^(\d+(?:\.\d+)?)kg/);
  return m ? parseFloat(m[1]) : null;
}
