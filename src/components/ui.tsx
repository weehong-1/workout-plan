import Link from "next/link";
import type { Verdict } from "@/lib/plan";

// Green = go up, grey = hold, amber = first time or back off.
export const VERDICT_TEXT: Record<Verdict, string> = {
  up: "text-[var(--up)]",
  hold: "text-[var(--muted)]",
  back: "text-[var(--warn)]",
  first: "text-[var(--warn)]",
};
const VERDICT_PILL: Record<Verdict, string> = {
  up: "bg-[var(--up-bg)] text-[var(--up)]",
  hold: "bg-[var(--soft)] text-[#d4d4d8]",
  back: "bg-[var(--warn-bg)] text-[var(--warn)]",
  first: "bg-[var(--warn-bg)] text-[var(--warn)]",
};

export function VerdictPill({ verdict, children }: { verdict: Verdict | "done"; children: React.ReactNode }) {
  const cls = verdict === "done" ? "bg-[var(--up-bg)] text-[var(--up)]" : VERDICT_PILL[verdict];
  return <span className={`shrink-0 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold ${cls}`}>{children}</span>;
}

export function DayLetter({ letter, size = "md" }: { letter: string; size?: "sm" | "md" | "lg" }) {
  const index = Math.max(0, "ABCDEFGHIJKL".indexOf(letter));
  const color = ["--day-a", "--day-b", "--day-c", "--day-d"][index % 4];
  const box = size === "lg" ? "h-14 w-14 text-2xl rounded-2xl" : size === "sm" ? "h-8 w-8 text-base rounded-lg" : "h-10 w-10 text-lg rounded-xl";
  return (
    <span aria-hidden className={`font-display grid shrink-0 place-items-center font-bold ${box}`}
      style={{ background: "var(--card)", color: `var(${color})`, boxShadow: `inset 0 0 0 1px var(${color})` }}>
      {letter}
    </span>
  );
}

export function Check({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </svg>
  );
}

export function BackLink({ href, label }: { href: string; label: string }) {
  return (
    <Link href={href} aria-label={label} className="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-xl text-[var(--fg)] focus-visible:outline-2 focus-visible:outline-[var(--accent)]">
      <svg viewBox="0 0 24 24" className="h-[22px] w-[22px]" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
        <path d="m15 6-6 6 6 6" />
      </svg>
    </Link>
  );
}

export const btnPrimary = "flex h-14 items-center justify-center rounded-2xl bg-[var(--accent)] px-6 text-[17px] font-bold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]";
export const btnGhost = "flex h-11 items-center justify-center rounded-xl px-3 text-sm font-semibold text-[var(--muted)] focus-visible:outline-2 focus-visible:outline-[var(--accent)]";
export const sectionLabel = "text-xs font-bold uppercase tracking-[0.08em] text-[var(--muted)]";
