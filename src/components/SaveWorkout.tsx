"use client";

import Link from "next/link";
import { askConfirm } from "@/components/ConfirmDialog";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { PhotoStrip } from "@/components/PhotoStrip";
import { VERDICT_TEXT, btnPrimary } from "@/components/ui";
import { useNow, workoutStats } from "@/components/Workout";
import { type Day, dayExercises, suggestFor } from "@/lib/plan";
import { removePhotos } from "@/lib/photos";
import {
  type LogEntry,
  discardDraft,
  draftPhotos,
  finishDraft,
  flushChanges,
  getPersistenceStatus,
  hasLoggedValue,
  historyFor,
  todayString,
  unitFor,
  updateDraftMeta,
  useHydrated,
  usePlan,
  useStore,
} from "@/lib/store";

export function SaveWorkout({ dayId }: { dayId: string }) {
  const plan = usePlan();
  const hydrated = useHydrated();
  if (!hydrated) return null;
  const day = plan.days.find((d) => d.id === dayId);
  if (!day) return <p className="py-16 text-center">This training day no longer exists. <Link href="/" className="font-semibold text-[var(--accent)]">Back to Today</Link></p>;
  return <SaveForm day={day} />;
}

export function SaveForm({ day }: { day: Day }) {
  const store = useStore();
  const router = useRouter();
  const now = useNow();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const draft = store.drafts[day.id];
  const stats = workoutStats(day, draft, now);
  const date = todayString();

  // What gets saved: every exercise with logged sets or a note.
  const entries: Omit<LogEntry, "id" | "savedAt">[] = [];
  for (const ex of dayExercises(day)) {
    const d = draft?.exercises[ex.order];
    if (!d) continue;
    const sets = d.sets.filter(hasLoggedValue).map(({ weight, reps }) => ({ weight, reps }));
    const photos = d.photos ?? [];
    if (!sets.length && !d.notes.trim() && !photos.length) continue;
    const name = d.useSub && ex.substitute ? ex.substitute : ex.name;
    entries.push({
      ...(photos.length ? { photos } : {}),
      date, dayId: day.id, exercise: ex.name,
      substitute: d.useSub && ex.substitute ? ex.substitute : undefined,
      sets, unit: unitFor(store, name), notes: d.notes.trim(),
    });
  }

  const nextTime = dayExercises(day).flatMap((ex) => {
    const entry = entries.find((e) => e.exercise === ex.name && !e.substitute && e.sets.length);
    if (!entry) return [];
    const s = suggestFor(ex, [...historyFor(store.log, ex.name), entry]);
    const unit = entry.unit ?? "kg";
    return [{ name: ex.name, verdict: s.verdict, text: s.weight === "BW" ? `${s.label === "Hold" ? "=" : s.label} · bodyweight` : `${s.verdict === "up" ? "↑" : s.verdict === "back" ? "↓" : "="} ${s.weight} ${unit}` }];
  });

  const save = async () => {
    if (saving) return;
    if (!entries.length) { setError("Log at least one set before saving."); return; }
    setSaving(true);
    setError("");
    finishDraft(day.id, entries, { notes: draft?.notes ?? "", photos: draft?.photos ?? [], durationMin: draft ? stats.minutes : null });
    const ok = await flushChanges();
    setSaving(false);
    if (ok) router.push("/");
    else setError(getPersistenceStatus().error ?? "Not saved yet. Keep this page open and retry.");
  };

  const discard = async () => {
    if (!(await askConfirm({ title: "Discard this workout?", body: "Everything you've logged today, including photos, will be cleared.", confirmLabel: "Discard", danger: true }))) return;
    const photos = draftPhotos(draft);
    discardDraft(day.id);
    router.push("/");
    if (photos.length && await flushChanges()) void removePhotos(photos);
  };

  return (
    <div className="flex flex-col gap-6 lg:mx-auto lg:max-w-xl">
      <header className="flex items-center gap-2">
        <Link href={`/day/${day.id}`} aria-label="Back to workout" className="-ml-2 grid h-11 w-11 place-items-center rounded-xl">
          <svg viewBox="0 0 24 24" className="h-[22px] w-[22px]" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="m15 6-6 6 6 6" /></svg>
        </Link>
        <h1 className="flex-1 text-center text-[17px] font-bold">Save workout</h1>
        <span className="w-11" />
      </header>

      <div>
        <p className="font-display text-3xl font-bold">{day.name}</p>
        <p className="mt-1 text-sm text-[var(--muted)]">{new Date().toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" })}</p>
      </div>

      <dl className="grid grid-cols-3 gap-2">
        {[["Duration", `${stats.minutes} min`], ["Volume", `${Math.round(stats.volume).toLocaleString("en-US")} kg`], ["Sets", `${stats.done} / ${stats.total}`]].map(([label, value]) => (
          <div key={label} className="rounded-2xl bg-[var(--card)] p-3">
            <dt className="text-xs text-[var(--muted)]">{label}</dt>
            <dd className="font-display mt-1 text-xl font-bold">{value}</dd>
          </div>
        ))}
      </dl>

      <section aria-labelledby="save-photos" className="flex flex-col gap-2">
        <h2 id="save-photos" className="text-[13px] font-semibold text-[var(--muted)]">Photo · progress pic, machine settings, anything</h2>
        <PhotoStrip
          paths={draft?.photos ?? []}
          label="Workout photos"
          onAdd={(paths) => updateDraftMeta(day.id, (d) => ({ ...d, photos: [...(d.photos ?? []), ...paths] }))}
          onRemove={(path) => {
            updateDraftMeta(day.id, (d) => ({ ...d, photos: (d.photos ?? []).filter((p) => p !== path) }));
            void flushChanges().then((ok) => { if (ok) void removePhotos([path]); });
          }}
        />
      </section>

      <label className="flex flex-col gap-2">
        <span className="text-[13px] font-semibold text-[var(--muted)]">How did it go?</span>
        <textarea
          value={draft?.notes ?? ""}
          maxLength={2000}
          onChange={(e) => updateDraftMeta(day.id, (d) => ({ ...d, notes: e.target.value }))}
          placeholder="Energy, sleep, anything that hurt…"
          className="h-24 resize-none rounded-2xl border border-[var(--line)] bg-[var(--card)] p-3 outline-none focus:border-[var(--accent)]"
        />
      </label>

      {nextTime.length > 0 && (
        <section aria-labelledby="next-title" className="flex flex-col gap-2">
          <h2 id="next-title" className="text-[13px] font-semibold text-[var(--muted)]">Next {day.name} starts at</h2>
          <ul className="overflow-hidden rounded-2xl bg-[var(--card)]">
            {nextTime.map((n) => (
              <li key={n.name} className="flex min-h-12 items-center justify-between gap-3 border-b border-[var(--soft)] px-3.5 last:border-0">
                <span className="min-w-0 truncate">{n.name}</span>
                <strong className={`shrink-0 ${VERDICT_TEXT[n.verdict]}`}>{n.text}</strong>
              </li>
            ))}
          </ul>
        </section>
      )}

      {error && <p role="alert" className="text-sm text-[var(--danger)]">{error}</p>}
      <button type="button" onClick={() => void save()} disabled={saving} className={btnPrimary}>{saving ? "Saving…" : "Save workout"}</button>
      <button type="button" onClick={() => void discard()} className="h-11 text-[15px] font-semibold text-[var(--danger)]">Discard workout</button>
    </div>
  );
}
