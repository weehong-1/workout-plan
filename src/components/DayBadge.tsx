export function DayBadge({ letter, size = "md" }: { letter: string; size?: "md" | "lg" }) {
  const color = `var(--day-${letter.toLowerCase()})`;
  return (
    <span
      className={`grid shrink-0 place-items-center rounded-xl font-bold text-[var(--day-fg)] ${
        size === "lg" ? "h-14 w-14 text-2xl" : "h-11 w-11 text-lg"
      }`}
      style={{ background: color }}
    >
      {letter}
    </span>
  );
}
