import type { Metadata } from "next";
import { BodyWeightView } from "@/components/BodyWeightView";

export const metadata: Metadata = { title: "Body Weight" };

export default function WeightPage() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Body Weight</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">Saved on this phone, one entry per day.</p>
      </header>
      <BodyWeightView />
    </>
  );
}
