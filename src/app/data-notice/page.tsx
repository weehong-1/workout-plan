import Link from "next/link";

export const metadata = { title: "Exercise data notice | Training Plan" };

export default function ExerciseDataNotice() {
  return <article className="space-y-4">
    <Link href="/" className="inline-flex min-h-10 items-center text-sm text-[var(--muted)]">← All days</Link>
    <h1 className="text-2xl font-bold">Exercise data notice</h1>
    <p>Exercise library text is provided by <a href="https://github.com/yuhonas/free-exercise-db" target="_blank" rel="noopener noreferrer" className="underline">Free Exercise DB</a>, published under the <a href="https://github.com/yuhonas/free-exercise-db/blob/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/LICENSE.md" target="_blank" rel="noopener noreferrer" className="underline">Unlicense</a>.</p>
    <p className="text-sm leading-relaxed text-[var(--muted)]">The app includes a pinned text-only catalog of 876 exercises. Searches run in your browser; search terms are not sent to an exercise provider. No exercise-provider API key is needed, and no library images or videos are downloaded. Existing authored tutorial links continue to use YouTube when opened.</p>
    <p className="text-sm leading-relaxed text-[var(--muted)]">When you add a library exercise, its name, equipment, muscles, instructions and your chosen working-set targets are saved to your signed-in database account. If you cannot find an exercise, you can enter your own. Custom exercise definitions are private account data, not submissions to a public library.</p>
    <p className="text-sm leading-relaxed text-[var(--muted)]">Legacy exercise references and saved logs are preserved without contacting the retired provider. You can remove and re-add those exercises from the current library or enter their details yourself.</p>
    <p className="text-sm leading-relaxed text-[var(--muted)]">Dataset content is supplied as-is. Choose movements appropriate for you and seek qualified instruction when needed. General exercise instructions are not personal medical advice.</p>
  </article>;
}
