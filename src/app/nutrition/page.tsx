import type { Metadata } from "next";
import { NutritionView } from "@/components/NutritionView";

export const metadata: Metadata = { title: "Nutrition Calculator" };

export default function NutritionPage() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Nutrition</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">A daily starting point for fueling your training, based on body weight.</p>
      </header>
      <NutritionView />
    </>
  );
}
