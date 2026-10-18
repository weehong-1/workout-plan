// Snapshot of the "Training Plan" tab of the Google Sheet
// "Weekly Training Plan (Push/Pull + Supersets + Arm Day)".

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
};

export type Block = {
  // "Straight Sets" or "Superset A", "Superset B", ...
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
const search = (q: string) =>
  `https://www.youtube.com/results?search_query=${encodeURIComponent(q).replace(/%20/g, "+")}`;

const WARM = "8 / 5 / 3";

export const DAYS: Day[] = [
  {
    id: "a",
    letter: "A",
    name: "Push Day",
    optional: false,
    focus: "Upper push + lower push + side delts + calves",
    duration: "50~60 min",
    blocks: [
      {
        format: "Straight Sets",
        exercises: [
          {
            order: "1",
            name: "Barbell Back Squat",
            muscles: "Quads, glutes (lower push)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "3~5 min",
            cues: "Knees track over toes; squat until thighs are at least parallel to the floor",
            video: yt("irA7MTz96ho"),
            substitute: "Leg Press",
            substituteVideo: yt("K5n2vg3oZa4"),
          },
        ],
      },
      {
        format: "Straight Sets",
        exercises: [
          {
            order: "2",
            name: "Barbell Bench Press",
            muscles: "Chest, triceps, front delts (upper push)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "2~3 min",
            cues: "Pull shoulder blades back and down, feet planted, lower the bar to the lower chest",
            video: yt("4Y2ZdHCOXok"),
            substitute: "Dumbbell Bench Press",
            substituteVideo: yt("5Y3VZsLb1Ys"),
          },
        ],
      },
      {
        format: "Superset A",
        exercises: [
          {
            order: "A1",
            name: "Overhead Press",
            muscles: "Front delts, side delts, triceps (upper push)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to A2",
            cues: "Brace your core, squeeze your glutes, don't lean back too much",
            video: yt("nNMR9fRGRjQ"),
            substitute: "Seated Dumbbell Shoulder Press",
            substituteVideo: yt("WKEl69bIGus"),
          },
          {
            order: "A2",
            name: "Standing Calf Raise",
            muscles: "Calves",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Balls of your feet on the edge of a step, lower your heels for a full stretch, rise as high as you can and pause 1 sec, no bouncing",
            video: search("standing calf raise tutorial"),
            substitute: "Single Leg Dumbbell Calf Raise",
            substituteVideo: search("single leg dumbbell calf raise tutorial"),
          },
        ],
      },
      {
        format: "Superset B",
        exercises: [
          {
            order: "B1",
            name: "Bulgarian Split Squat",
            muscles: "Quads, glutes (lower push)",
            warmUp: "8 / 5 / 3 per leg",
            sets: 2,
            reps: "10 per leg",
            rest: "Go straight to B2",
            cues: "Plant the front foot firmly and lower straight down with an upright torso",
            video: yt("vLuhN_glFZ8"),
            substitute: "Smith Machine Split Squat",
            substituteVideo: yt("xGTQxrcCuEo"),
          },
          {
            order: "B2",
            name: "Dumbbell Lateral Raise",
            muscles: "Side delts",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Lead with the elbows, raise to shoulder height, lower slowly",
            video: yt("4hTUCDUQaNA"),
            substitute: "Cable Lateral Raise",
            substituteVideo: yt("Z5FA9aq3L6A"),
          },
        ],
      },
    ],
  },
  {
    id: "b",
    letter: "B",
    name: "Pull Day",
    optional: false,
    focus: "Upper pull + lower pull + biceps",
    duration: "50~60 min",
    blocks: [
      {
        format: "Straight Sets",
        exercises: [
          {
            order: "1",
            name: "Romanian Deadlift",
            muscles: "Hamstrings, glutes (lower pull)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "3~5 min",
            cues: "Keep the bar close to your legs, push hips back, knees slightly bent, back straight",
            video: yt("uhghy9pFIPY"),
            substitute: "Dumbbell Romanian Deadlift",
            substituteVideo: yt("hQgFixeXdZo"),
          },
        ],
      },
      {
        format: "Straight Sets",
        exercises: [
          {
            order: "2",
            name: "Pull Up",
            muscles: "Lats, biceps (upper pull)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "2~3 min",
            cues: "Set your shoulders down first, chin over the bar, fully extend your arms on every rep",
            video: yt("rmdn5X_KLkY"),
            substitute: "Lat Pulldown",
            substituteVideo: yt("AOpi-p0cJkc"),
          },
        ],
      },
      {
        format: "Superset A",
        exercises: [
          {
            order: "A1",
            name: "Barbell Bent Over Row",
            muscles: "Mid back, lats (upper pull)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to A2",
            cues: "Hinge at the hips, keep your back straight, pull the bar toward your navel",
            video: yt("FEFjR70BPt8"),
            substitute: "Seated Cable Row",
            substituteVideo: yt("f_r95UajQcg"),
          },
          {
            order: "A2",
            name: "Lying Leg Curl",
            muscles: "Hamstrings (lower pull)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Keep hips on the pad, pause at full contraction, lower slowly",
            video: yt("SbSNUXPRkc8"),
            substitute: "Stability Ball Leg Curl",
            substituteVideo: yt("8Wagn999nhA"),
          },
        ],
      },
      {
        format: "Superset B",
        exercises: [
          {
            order: "B1",
            name: "Face Pull",
            muscles: "Rear delts, upper back",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to B2",
            cues: "Pull the rope toward your forehead and spread your hands apart",
            video: yt("wnPX6Fwe-Fg"),
            substitute: "Reverse Pec Deck Fly",
            substituteVideo: yt("e4dJ1QoxUtI"),
          },
          {
            order: "B2",
            name: "Dumbbell Curl",
            muscles: "Biceps",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1~2 min after each round",
            cues: "Keep upper arms still, no swinging for momentum",
            video: yt("in7PaeYlhrM"),
            substitute: "Cable Curl",
            substituteVideo: yt("C68nhss07U8"),
          },
        ],
      },
    ],
  },
  {
    id: "c",
    letter: "C",
    name: "Mixed Day",
    optional: false,
    focus: "Paired push and pull + hip thrust + chest fly + side delts + calves + triceps",
    duration: "50~60 min",
    blocks: [
      {
        format: "Superset A",
        exercises: [
          {
            order: "A1",
            name: "Incline Dumbbell Press",
            muscles: "Upper chest, front delts (upper push)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to A2",
            cues: "Bench at 30~45 degrees, lower to the sides of the upper chest, don't lock out your elbows at the top",
            video: yt("OC7Qs_SAO2E"),
            substitute: "Machine Chest Press",
            substituteVideo: yt("n8TOta_pfr4"),
          },
          {
            order: "A2",
            name: "Seated Cable Row",
            muscles: "Mid back, lats (upper pull)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Sit tall with chest up, pull elbows back close to your body, squeeze shoulder blades",
            video: yt("f_r95UajQcg"),
            substitute: "Single Arm Dumbbell Row",
            substituteVideo: yt("ZRSGpBUVcNw"),
          },
        ],
      },
      {
        format: "Superset B",
        exercises: [
          {
            order: "B1",
            name: "Leg Press",
            muscles: "Quads, glutes (lower push)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to B2",
            cues: "Keep lower back against the pad, don't let knees cave in, don't lock knees at the top",
            video: yt("K5n2vg3oZa4"),
            substitute: "Goblet Squat",
            substituteVideo: yt("JO7D6GJ98wY"),
          },
          {
            order: "B2",
            name: "Dumbbell Lateral Raise",
            muscles: "Side delts",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Lead with the elbows, raise to shoulder height, lower slowly",
            video: yt("4hTUCDUQaNA"),
            substitute: "Cable Lateral Raise",
            substituteVideo: yt("Z5FA9aq3L6A"),
          },
        ],
      },
      {
        format: "Superset C",
        exercises: [
          {
            order: "C1",
            name: "Barbell Hip Thrust",
            muscles: "Glutes, hamstrings (lower pull)",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to C2",
            cues: "Upper back on the bench, drive up until your body is in a straight line, squeeze glutes and pause at the top",
            video: yt("S_uZP4UH6J0"),
            substitute: "Smith Machine Hip Thrust",
            substituteVideo: search("smith machine hip thrust tutorial"),
          },
          {
            order: "C2",
            name: "Overhead Dumbbell Triceps Extension",
            muscles: "Triceps",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Keep upper arms close to your ears and still, bend only at the elbows",
            video: yt("bVfZIlxBMqU"),
            substitute: "Rope Pushdown",
            substituteVideo: yt("vPeQu_L-1n0"),
          },
        ],
      },
      {
        format: "Superset D",
        exercises: [
          {
            order: "D1",
            name: "Cable Fly",
            muscles: "Chest",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to D2",
            cues: "Keep a slight fixed bend in the elbows, bring your hands together in front of your chest and squeeze, open slowly to feel the stretch",
            video: search("cable chest fly tutorial"),
            substitute: "Pec Deck Fly",
            substituteVideo: search("pec deck fly tutorial"),
          },
          {
            order: "D2",
            name: "Standing Calf Raise",
            muscles: "Calves",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1~1.5 min after each round",
            cues: "Balls of your feet on the edge of a step, lower your heels for a full stretch, rise as high as you can and pause 1 sec, no bouncing",
            video: search("standing calf raise tutorial"),
            substitute: "Single Leg Dumbbell Calf Raise",
            substituteVideo: search("single leg dumbbell calf raise tutorial"),
          },
        ],
      },
      {
        format: "Straight Sets",
        exercises: [
          {
            order: "Finisher",
            name: "Kneeling Cable Crunch",
            muscles: "Abs",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "1~2 min",
            cues: "Crunch with your abs and keep your hips still",
            video: yt("0KEP6A1deBE"),
            substitute: "Hanging Leg Raise",
            substituteVideo: yt("Nw0LOKe3_l8"),
          },
        ],
      },
    ],
  },
  {
    id: "d",
    letter: "D",
    name: "Arm Day",
    optional: true,
    focus: "Biceps, triceps, forearms, grip and rear delts",
    duration: "35~45 min",
    blocks: [
      {
        format: "Superset A",
        exercises: [
          {
            order: "A1",
            name: "EZ Bar Curl",
            muscles: "Biceps",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to A2",
            cues: "Keep upper arms at your sides, squeeze at the top, lower slowly",
            video: yt("rL_5SVxXRm4"),
            substitute: "Dumbbell Curl",
            substituteVideo: yt("in7PaeYlhrM"),
          },
          {
            order: "A2",
            name: "Close Grip Bench Press",
            muscles: "Triceps, chest",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1.5~2 min after each round",
            cues: "Shoulder width grip, lower with elbows close to your body",
            video: yt("UYJsFzqdgK4"),
            substitute: "Parallel Bar Dips",
            substituteVideo: yt("1eYIj_yplK0"),
          },
        ],
      },
      {
        format: "Superset B",
        exercises: [
          {
            order: "B1",
            name: "Incline Dumbbell Curl",
            muscles: "Biceps long head",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to B2",
            cues: "Bench at 45~60 degrees, let arms hang naturally, don't let upper arms drift forward",
            video: yt("t24mTZKfpGg"),
            substitute: "Cable Curl",
            substituteVideo: yt("C68nhss07U8"),
          },
          {
            order: "B2",
            name: "Overhead Cable Triceps Extension",
            muscles: "Triceps long head",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1~1.5 min after each round",
            cues: "Face away from the cable, keep upper arms close to your ears and still, pause at full extension",
            video: yt("AUsSlsBu5eg"),
            substitute: "Overhead Dumbbell Triceps Extension",
            substituteVideo: yt("bVfZIlxBMqU"),
          },
        ],
      },
      {
        format: "Superset C",
        exercises: [
          {
            order: "C1",
            name: "Reverse Grip Curl",
            muscles: "Brachioradialis, forearms",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Go straight to C2",
            cues: "Overhand grip, keep wrists straight",
            video: yt("ECvtLrj63Us"),
            substitute: "Hammer Curl",
            substituteVideo: yt("zC3nLlEvin4"),
          },
          {
            order: "C2",
            name: "Bent Over Reverse Dumbbell Fly",
            muscles: "Rear delts",
            warmUp: WARM,
            sets: 2,
            reps: "10",
            rest: "Rest 1~1.5 min after each round",
            cues: "Lean forward until your torso is nearly horizontal, slight bend in the elbows, raise arms out to the sides",
            video: yt("kFbx_KdzUKY"),
            substitute: "Reverse Pec Deck Fly",
            substituteVideo: yt("e4dJ1QoxUtI"),
          },
        ],
      },
      {
        format: "Straight Sets",
        exercises: [
          {
            order: "Finisher",
            name: "Farmer's Walk",
            muscles: "Grip, forearms, traps",
            warmUp: "1 light carry",
            sets: 2,
            reps: "30~40 sec",
            rest: "1~1.5 min",
            cues: "Grip heavy dumbbells tightly, chest up, core tight, take small steady steps",
            video: yt("VBobkldqqvk"),
            substitute: "Kettlebell Farmer's Walk",
            substituteVideo: search("kettlebell farmers walk tutorial"),
          },
        ],
      },
    ],
  },
];

export function getDay(id: string): Day | undefined {
  return DAYS.find((d) => d.id === id);
}

export function dayLabel(day: Day): string {
  return `${day.letter} ${day.name}${day.optional ? " (Optional)" : ""}`;
}

// Rotation from the Notes tab: A → B → C, with D as an optional extra.
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
// in the sheet (e.g. "Rest 1.5~2 min after each round" → 90). The timer has
// +15s for when you need the longer end.
export function restSeconds(rest: string): number | null {
  const m = rest.match(/(\d+(?:\.\d+)?)(?:~\d+(?:\.\d+)?)?\s*(min|sec)/);
  if (!m) return null;
  const value = parseFloat(m[1]);
  return Math.round(m[2] === "min" ? value * 60 : value);
}

// Numeric rep target ("10", "10 per leg" → 10). Timed sets return null.
export function targetReps(reps: string): number | null {
  if (/sec|min/.test(reps)) return null;
  const m = reps.match(/^\d+/);
  return m ? parseInt(m[0], 10) : null;
}

export function isLowerBody(muscles: string): boolean {
  return /quad|glute|hamstring|calves|lower/i.test(muscles);
}
