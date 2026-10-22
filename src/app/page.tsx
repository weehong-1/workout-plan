import { NextUp } from "@/components/NextUp";
import { TrainingPlans } from "@/components/TrainingPlans";

export default function Home() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Training Plan</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">
          Push / Pull + Supersets + Arm Day · 3 days a week, A → B → C
        </p>
      </header>

      <NextUp />

      <TrainingPlans />

    </>
  );
}
