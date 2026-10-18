import type { Metadata } from "next";
import { LogView } from "@/components/LogView";

export const metadata: Metadata = { title: "Training Log" };

export default function LogPage() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Training Log</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">
          Saved on this phone. Copy it into the Training Log tab of your sheet anytime.
        </p>
      </header>
      <LogView />
    </>
  );
}
