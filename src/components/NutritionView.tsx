"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ACTIVITY_LEVELS,
  NUTRITION_GOALS,
  NUTRITION_DAY_TYPES,
  WORKOUT_KCAL_PER_KG,
  calculateNutrition,
  type ActivityLevel,
  type NutritionGoal,
} from "@/lib/nutrition";
import { convertWeight, useHydrated, useStore, type BodyWeight, type Unit } from "@/lib/store";

const formatWeight = (weight: number) => weight.toLocaleString("en-US", { maximumFractionDigits: 1 });
const formatDate = (date: string) => {
  const [year, month, day] = date.split("/").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("en-US", { day: "numeric", month: "short", year: "numeric" });
};

export function NutritionView() {
  const { bodyWeights, bodyUnit } = useStore();
  const hydrated = useHydrated();

  if (!hydrated) return null;

  return <NutritionCalculator latest={bodyWeights.at(-1)} displayUnit={bodyUnit} />;
}

function NutritionCalculator({ latest, displayUnit }: { latest?: BodyWeight; displayUnit: Unit }) {
  const [activity, setActivity] = useState<ActivityLevel>("moderate");
  const [goal, setGoal] = useState<NutritionGoal>("maintain");
  // Read the live Weight-module record, never a copied or rounded input value.
  const estimates = NUTRITION_DAY_TYPES.map((day) => ({
    ...day,
    estimate: latest ? calculateNutrition(latest.weight, latest.unit, activity, goal, day.id) : null,
  }));
  const valid = estimates.every((day) => day.estimate !== null);

  if (!latest) {
    return (
      <section aria-labelledby="nutrition-empty" className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-5">
        <h2 id="nutrition-empty" className="text-lg font-semibold">Log your weight to get started</h2>
        <p className="mt-2 text-sm text-[var(--muted)]">Nutrition uses your latest entry from Weight to calculate workout-day and non-workout-day targets. No need to enter it twice.</p>
        <Link href="/weight" className="mt-4 inline-flex min-h-12 items-center justify-center rounded-xl bg-[var(--accent)] px-4 text-sm font-semibold text-[var(--accent-fg)]">Log weight →</Link>
      </section>
    );
  }

  return (
    <>
      <section aria-labelledby="nutrition-weight-title" className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="nutrition-weight-title" className="text-sm font-medium text-[var(--muted)]">Using your latest weight</h2>
          <Link href="/weight" className="inline-flex min-h-11 shrink-0 items-center text-sm font-semibold text-[var(--accent)]">Update weight →</Link>
        </div>
        <p className="text-3xl font-bold tabular-nums">{formatWeight(convertWeight(latest.weight, latest.unit, displayUnit))}<span className="ml-1 text-lg font-medium text-[var(--muted)]">{displayUnit}</span></p>
        <p className="mt-2 text-xs text-[var(--muted)]">Logged {formatDate(latest.date)} in Weight. Targets update automatically when your latest entry changes.</p>
      </section>

      {!valid ? (
        <section role="status" aria-labelledby="nutrition-weight-unavailable" className="mt-4 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
          <h2 id="nutrition-weight-unavailable" className="font-semibold">Unable to estimate from this weight</h2>
          <p className="mt-2 text-sm text-[var(--muted)]">This calculator supports adult weights from 35–300 kg (about 77–661 lb). Check your latest entry in Weight, or ask a registered dietitian for individual guidance.</p>
        </section>
      ) : (
        <>
          <section aria-labelledby="nutrition-inputs" className="mt-4 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
            <h2 id="nutrition-inputs" className="font-semibold">Personalize your targets</h2>
            <label htmlFor="nutrition-activity" className="mt-3 block text-sm font-medium">Activity outside workouts</label>
            <select
              id="nutrition-activity"
              value={activity}
              onChange={(event) => setActivity(event.target.value as ActivityLevel)}
              aria-describedby="nutrition-activity-help"
              className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none focus:border-[var(--accent)]"
            >
              {ACTIVITY_LEVELS.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
            </select>
            <p id="nutrition-activity-help" className="mt-2 text-xs text-[var(--muted)]">{ACTIVITY_LEVELS.find((option) => option.id === activity)?.description} Exclude your workout here so it is not counted twice.</p>

            <label htmlFor="nutrition-goal" className="mt-4 block text-sm font-medium">Your goal</label>
            <select
              id="nutrition-goal"
              value={goal}
              onChange={(event) => setGoal(event.target.value as NutritionGoal)}
              className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none focus:border-[var(--accent)]"
            >
              {NUTRITION_GOALS.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
            </select>
            <p className="mt-3 text-xs text-[var(--muted)]">Choose your activity and goal to update both day types. These choices apply only to this calculator.</p>
          </section>

          <section aria-labelledby="nutrition-results" aria-live="polite" aria-atomic="true" className="mt-4 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
            <h2 id="nutrition-results" className="font-semibold">Estimated daily targets</h2>
            <p className="mt-2 text-sm text-[var(--muted)]">Use the workout-day targets on days you train and the non-workout-day targets on days you rest. Protein stays the same for recovery; workout days get extra carbs.</p>
            <div className="mt-4 space-y-4">
              {estimates.map(({ id, label, estimate }) => estimate && (
                <section key={id} aria-labelledby={`nutrition-${id}-title`} className="rounded-2xl border border-[var(--line)] p-3">
                  <h3 id={`nutrition-${id}-title`} className="font-semibold">{label}</h3>
                  <p className="mt-1 text-xs text-[var(--muted)]">{id === "workout" ? "Extra fuel for your training session." : "Fuel for daily activity and recovery, without a workout allowance."}</p>
                  {estimate.belowMinimum ? (
                    <p className="mt-3 text-sm text-[var(--muted)]">This day’s estimate is below 1,500 kcal/day. Calorie and macro targets are withheld; speak with a registered dietitian for an individual plan. This cutoff is a calculator guardrail, not a universal minimum or a guarantee that higher targets suit you.</p>
                  ) : (
                    <>
                      <p className="mt-4 text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Calories / day</p>
                      <p className="mt-1 text-4xl font-bold tabular-nums text-[var(--accent)]">
                        {estimate.calories.toLocaleString("en-US")} <span className="text-lg font-medium text-[var(--muted)]">kcal</span>
                      </p>
                      <p className="mt-2 text-sm text-[var(--muted)]">Estimated maintenance: {estimate.maintenanceCalories.toLocaleString("en-US")} kcal/day. {goal === "lose" ? "Includes a 15% calorie deficit." : goal === "gain" ? "Includes a 10% calorie surplus." : "No calorie deficit or surplus."}</p>
                      <dl className="mt-4 grid grid-cols-3 gap-2">
                        {[
                          { label: "Protein", value: estimate.proteinGrams },
                          { label: "Carbs", value: estimate.carbGrams },
                          { label: "Fat", value: estimate.fatGrams },
                        ].map((macro) => (
                          <div key={macro.label} className="rounded-xl bg-[var(--soft)] px-1 py-3 text-center">
                            <dt className="text-xs font-medium text-[var(--muted)]">{macro.label}</dt>
                            <dd className="mt-1 text-xl font-bold tabular-nums">{macro.value}<span className="ml-1 text-sm font-normal text-[var(--muted)]">g</span></dd>
                          </div>
                        ))}
                      </dl>
                    </>
                  )}
                </section>
              ))}
              <p className="text-xs text-[var(--muted)]">Grams per day. Rounded macro values may not add up to the exact calorie estimate. Day types are not selected automatically from your workout log.</p>
            </div>
          </section>

          <details className="mt-4 rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4">
            <summary className="cursor-pointer text-sm font-semibold">How this is estimated</summary>
            <div className="mt-3 space-y-3 text-sm leading-relaxed text-[var(--muted)]">
              <p>This is a weight-based rule of thumb for generally healthy adults who exercise, not a measurement of your metabolism. It does not account for age, height, sex, body composition or medical history.</p>
              <ul className="list-disc space-y-2 pl-5">
                <li>Non-workout maintenance: body weight in kg × 28 (light), 33 (moderate) or 38 (very active), based only on activity outside workouts.</li>
                <li>Workout maintenance: add {WORKOUT_KCAL_PER_KG} kcal/kg for a typical training session before applying your goal. This is a rough allowance, not measured exercise calories; short, long or intense sessions can differ substantially.</li>
                <li>Goal: apply −15% for weight loss, no adjustment for maintenance, or +10% for muscle gain to each day’s maintenance estimate.</li>
                <li>Protein stays the same on both days: 2 g/kg for weight loss, or 1.8 g/kg for maintenance and muscle gain, to support recovery even on rest days.</li>
                <li>Fat stays the same on both days, using approximately 25% of non-workout target calories at 9 kcal/g. Carbs use each day’s remaining calories at 4 kcal/g, so the workout allowance goes to carbs; protein also provides 4 kcal/g.</li>
              </ul>
              <p>Actual needs can differ substantially. Follow your average weight, energy and training performance over 2–3 weeks and adjust with a qualified professional rather than treating these numbers as exact requirements.</p>
            </div>
          </details>
        </>
      )}
      <p className="mt-4 text-xs leading-relaxed text-[var(--muted)]">General guidance, not medical advice. Not intended for under-18s, pregnancy, breastfeeding, or people with an eating disorder or a medical condition that affects nutrition. A registered dietitian can help with a personalized plan.</p>
    </>
  );
}
