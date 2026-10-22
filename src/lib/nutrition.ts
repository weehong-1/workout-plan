export type NutritionUnit = "kg" | "lb";
export type ActivityLevel = "light" | "moderate" | "high";
export type NutritionGoal = "lose" | "maintain" | "gain";
export type NutritionDayType = "workout" | "rest";

export const NUTRITION_DAY_TYPES = [
  { id: "workout", label: "Workout day" },
  { id: "rest", label: "Non-workout day" },
] as const satisfies readonly { id: NutritionDayType; label: string }[];

export const WORKOUT_KCAL_PER_KG = 4;

// Non-workout activity baselines: broad heuristics, not measured energy needs.
export const ACTIVITY_LEVELS = [
  { id: "light", label: "Lightly active", description: "Mostly sitting with little walking.", kcalPerKg: 28 },
  { id: "moderate", label: "Moderately active", description: "Regular walking or on your feet for part of the day.", kcalPerKg: 33 },
  { id: "high", label: "Very active", description: "Physically demanding job or lots of daily movement.", kcalPerKg: 38 },
] as const satisfies readonly { id: ActivityLevel; label: string; description: string; kcalPerKg: number }[];

export const NUTRITION_GOALS = [
  { id: "lose", label: "Lose weight" },
  { id: "maintain", label: "Maintain weight" },
  { id: "gain", label: "Gain muscle" },
] as const satisfies readonly { id: NutritionGoal; label: string }[];

export type NutritionEstimate = {
  dayType: NutritionDayType;
  weightKg: number;
  maintenanceCalories: number;
  calories: number;
  proteinGrams: number;
  fatGrams: number;
  carbGrams: number;
  belowMinimum: boolean;
};

const LB_PER_KG = 2.20462;
// Permit floating-point noise when an equivalent pound value is at a boundary.
const WEIGHT_TOLERANCE_KG = 1e-8;

export function calculateNutrition(
  weight: number,
  unit: NutritionUnit,
  activity: ActivityLevel,
  goal: NutritionGoal,
  dayType: NutritionDayType,
): NutritionEstimate | null {
  if (!Number.isFinite(weight) || (unit !== "kg" && unit !== "lb")) return null;
  const activityLevel = ACTIVITY_LEVELS.find((level) => level.id === activity);
  if (!activityLevel || !NUTRITION_GOALS.some((option) => option.id === goal)) return null;
  if (!NUTRITION_DAY_TYPES.some((option) => option.id === dayType)) return null;

  const weightKg = unit === "lb" ? weight / LB_PER_KG : weight;
  if (weightKg < 35 - WEIGHT_TOLERANCE_KG || weightKg > 300 + WEIGHT_TOLERANCE_KG) return null;

  const restMaintenance = weightKg * activityLevel.kcalPerKg;
  const maintenance = weightKg * (activityLevel.kcalPerKg + (dayType === "workout" ? WORKOUT_KCAL_PER_KG : 0));
  // Modest goal adjustments and macro splits are starting-point heuristics.
  const goalMultiplier = goal === "lose" ? 0.85 : goal === "gain" ? 1.1 : 1;
  const calories = Math.round(maintenance * goalMultiplier);
  const restCalories = Math.round(restMaintenance * goalMultiplier);
  const proteinGrams = Math.round(weightKg * (goal === "lose" ? 2 : 1.8));
  const fatGrams = Math.round((restCalories * 0.25) / 9);
  // The workout allowance is not measured expenditure: extra energy is allocated
  // to carbs, keeping protein and rest-calorie-based fat the same on both days.
  const carbGrams = Math.round((calories - proteinGrams * 4 - fatGrams * 9) / 4);

  return {
    dayType,
    weightKg,
    maintenanceCalories: Math.round(maintenance),
    calories,
    proteinGrams,
    fatGrams,
    carbGrams,
    // Flag, rather than clamp: the UI must suppress targets below this threshold.
    belowMinimum: calories < 1500,
  };
}
