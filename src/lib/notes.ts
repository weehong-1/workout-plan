// Snapshot of the "Notes" tab of the Google Sheet.

export type NoteSection = { title: string; lines: string[] };

export const NOTES: NoteSection[] = [
  {
    title: "How to Schedule",
    lines: [
      "Train 3 days a week, rotating A Push Day → B Pull Day → C Mixed Day",
      "No fixed weekdays: train when you feel good, and pick up where you left off next time",
      "D Arm Day is optional: in weeks when you feel good and have more time, add it after C Mixed Day",
    ],
  },
  {
    title: "Split",
    lines: [
      "A Push Day: upper push (bench press, overhead press) + lower push (squat, split squat) + side delts + calves",
      "B Pull Day: upper pull (pull ups, rows) + lower pull (deadlift, leg curl) + biceps",
      "C Mixed Day: paired push and pull + hip thrust + chest fly + side delts + calves + triceps, so chest, back, legs and shoulders each get trained twice a week",
      "D Arm Day (Optional): biceps, triceps, forearms, grip and rear delts, topping up the smaller muscles trained less on other days",
      "After D Arm Day, ideally rest one day before A Push Day or B Pull Day so your arms can recover",
    ],
  },
  {
    title: "Straight Sets",
    lines: [
      "Squat, bench press, Romanian deadlift and pull ups are too taxing: always do them as straight sets, never paired with another exercise",
      "Do these first after warming up, resting 2~3 min between sets; for squats and deadlifts, rest up to 3~5 min if you're tired",
      "You can stretch or do mobility work while resting, but don't use the rest to train another exercise",
    ],
  },
  {
    title: "Superset",
    lines: [
      "Do A1, then go straight into A2: that's one round. Rest after each round, and repeat until all sets are done",
      "The paired exercises don't work the same muscles, so you don't need to wait for full recovery",
    ],
  },
  {
    title: "Substitutes",
    lines: [
      "If the equipment for a main exercise is taken or no dumbbells are free, switch to the substitute in the same row with the same sets and reps",
      "If equipment for one exercise in a superset is taken, use its substitute, or do that exercise as straight sets instead",
    ],
  },
  {
    title: "Training Principles",
    lines: [
      "Leave 1~2 reps in reserve on every set; don't train to failure, and never let your form break down",
    ],
  },
  {
    title: "Progression",
    lines: [
      "When you get 10 reps on both working sets with good form, increase the weight next session",
      "Add 1~2.5 kg for upper body and 2.5~5 kg for lower body",
      "It's normal for reps to drop to around 8 after a weight increase; build them back up to 10 gradually",
    ],
  },
  {
    title: "Session Length",
    lines: ["A, B and C take about 50~60 min; D Arm Day takes about 35~45 min"],
  },
  {
    title: "Training Log",
    lines: [
      "Log one row per exercise with your 2 working sets only, for example 60kg×10; if you used a substitute, write it in Notes",
    ],
  },
  {
    title: "Warm Up Sets",
    lines: [
      "Before every exercise, do 3 warm up sets of 8, 5 and 3 reps, getting heavier each set (roughly 50%, 70% and 85% of your working weight), then 2 working sets of 10 reps",
      "Rest 30~60 sec between warm up sets; for a superset, finish the warm ups for both exercises first, then start the paired working sets",
    ],
  },
];
