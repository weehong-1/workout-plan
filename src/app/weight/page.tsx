import type { Metadata } from "next";
import Link from "next/link";
import { BodyWeightView } from "@/components/BodyWeightView";

export const metadata: Metadata = { title: "Body" };

export default function WeightPage() {
  return (
    <div className="lg:mx-auto lg:max-w-2xl">
      <header className="mb-5 flex items-center justify-between">
        <h1 className="font-display text-[34px] font-bold tracking-tight">Body</h1>
        <Link href="/nutrition" className="flex min-h-11 items-center text-sm font-semibold text-[var(--accent)]">Nutrition targets</Link>
      </header>
      <BodyWeightView />
    </div>
  );
}
