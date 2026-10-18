import type { Metadata } from "next";
import { NOTES } from "@/lib/notes";

export const metadata: Metadata = { title: "Training Notes" };

export default function NotesPage() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Training Notes</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">The rules behind the plan.</p>
      </header>

      <nav className="-mx-4 mb-4 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none]">
        {NOTES.map((s) => (
          <a
            key={s.title}
            href={`#${slug(s.title)}`}
            className="shrink-0 rounded-full border border-[var(--line)] bg-[var(--card)] px-3 py-1.5 text-sm font-medium"
          >
            {s.title}
          </a>
        ))}
      </nav>

      <div className="space-y-3">
        {NOTES.map((s) => (
          <section
            key={s.title}
            id={slug(s.title)}
            className="scroll-mt-4 rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4"
          >
            <h2 className="font-semibold">{s.title}</h2>
            <ul className="mt-2 list-disc space-y-1.5 pl-5 text-[15px] leading-relaxed text-[var(--muted)]">
              {s.lines.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </>
  );
}

function slug(s: string) {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, "-");
}
