"use client";

import Link from "next/link";
import { askConfirm } from "@/components/ConfirmDialog";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ExerciseSearch } from "@/components/ExerciseSearch";
import { BackLink, DayLetter, btnPrimary, sectionLabel } from "@/components/ui";
import {
  type Day,
  type Exercise,
  type PlanSettings,
  DAY_LETTERS,
  MAX_DAYS,
  MAX_DAY_EXERCISES,
  WARM_UP_OPTIONS,
  dayExercises,
  exerciseCount,
  relabel,
  startWeightValue,
} from "@/lib/plan";
import type { ExerciseDefinition } from "@/lib/exercise-library";
import { flushChanges, getPersistenceStatus, savePlan, useHydrated, usePlan } from "@/lib/store";

const WEEKDAYS = [[1, "Mon"], [2, "Tue"], [3, "Wed"], [4, "Thu"], [5, "Fri"], [6, "Sat"], [0, "Sun"]] as const;

function move<T>(list: T[], from: number, to: number): T[] {
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

async function persist(plan: PlanSettings): Promise<string> {
  if (!savePlan(plan)) return "Your account isn't ready, or the plan is incomplete.";
  return (await flushChanges()) ? "" : getPersistenceStatus().error ?? "Not saved yet. Keep this page open and retry.";
}

// Days, rotation order and training weekdays. Changes save straight away.
export function PlanView() {
  const plan = usePlan();
  const hydrated = useHydrated();
  const [error, setError] = useState("");
  if (!hydrated) return null;
  const update = async (next: PlanSettings) => setError(await persist(next));

  return (
    <div className="flex flex-col gap-6 lg:mx-auto lg:max-w-xl">
      <header className="flex items-center gap-2">
        <BackLink href="/" label="Back to Today" />
        <h1 className="flex-1 text-[17px] font-bold">Your plan</h1>
      </header>

      <section aria-labelledby="rotation" className="flex flex-col gap-2.5">
        <div>
          <h2 id="rotation" className={sectionLabel}>Rotation</h2>
          <p className="mt-1 text-sm text-[var(--muted)]">Today picks the next day in this order. Optional days are only offered, never due.</p>
        </div>
        <ol className="flex flex-col gap-2">
          {plan.days.map((day, i) => (
            <li key={day.id} className="flex min-h-[68px] items-center gap-3 rounded-2xl bg-[var(--card)] pr-1 pl-3">
              <DayLetter letter={day.letter} />
              <Link href={`/plan/${day.id}`} className="min-w-0 flex-1 py-2">
                <span className="block font-bold">{day.name}</span>
                <span className="block text-[13px] text-[var(--muted)]">
                  {exerciseCount(dayExercises(day).length)}{day.optional ? " · optional" : ""}
                </span>
              </Link>
              <div className="flex">
                <button type="button" disabled={i === 0} aria-label={`Move ${day.name} earlier`}
                  onClick={() => void update({ ...plan, days: relabel(move(plan.days, i, i - 1)) })}
                  className="grid h-11 w-10 place-items-center text-[var(--muted)] disabled:opacity-25">
                  <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" aria-hidden><path d="m6 15 6-6 6 6" /></svg>
                </button>
                <button type="button" disabled={i === plan.days.length - 1} aria-label={`Move ${day.name} later`}
                  onClick={() => void update({ ...plan, days: relabel(move(plan.days, i, i + 1)) })}
                  className="grid h-11 w-10 place-items-center text-[var(--muted)] disabled:opacity-25">
                  <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" aria-hidden><path d="m6 9 6 6 6-6" /></svg>
                </button>
              </div>
            </li>
          ))}
        </ol>
        {plan.days.length < MAX_DAYS && (
          <Link href="/plan/new" className="flex h-14 items-center justify-center gap-2 rounded-2xl border border-dashed border-[#3a3a40] font-bold">
            <span aria-hidden className="text-xl">＋</span> New training day
          </Link>
        )}
      </section>

      <section aria-labelledby="weekdays" className="flex flex-col gap-2.5">
        <h2 id="weekdays" className={sectionLabel}>Training days</h2>
        <div role="group" aria-label="Weekdays" className="grid grid-cols-7 gap-1.5">
          {WEEKDAYS.map(([n, label]) => {
            const on = plan.weekdays.includes(n);
            return (
              <button key={n} type="button" aria-pressed={on}
                onClick={() => void update({ ...plan, weekdays: on ? plan.weekdays.filter((d) => d !== n) : [...plan.weekdays, n].sort() })}
                className={`h-11 rounded-xl text-[13px] ${on ? "bg-[var(--accent)] font-bold text-[var(--accent-fg)]" : "bg-[var(--card)] font-semibold text-[var(--muted)]"}`}>
                {label}
              </button>
            );
          })}
        </div>
        <p className="text-[13px] text-[var(--muted)]">Missed a day? The rotation waits for you; it never skips ahead.</p>
      </section>
      {error && <p role="alert" className="text-sm text-[var(--danger)]">{error}</p>}
    </div>
  );
}

type Draft = { name: string; sets: number; reps: string; startKg: number; warmUp: string; substitute: string; muscles: string; cues: string };

function draftFrom(ex: Exercise): Draft {
  return {
    name: ex.name, sets: ex.sets, reps: ex.reps, startKg: Number(startWeightValue(ex.startWeight)) || 0,
    warmUp: ex.warmUp || "None", substitute: ex.substitute, muscles: ex.muscles, cues: ex.cues,
  };
}

function applyDraft(ex: Exercise, d: Draft): Exercise {
  return {
    ...ex, name: d.name.trim(), sets: d.sets, reps: d.reps, warmUp: d.warmUp,
    startWeight: d.startKg > 0 ? `${d.startKg}kg` : "BW",
    substitute: d.substitute.trim(), substituteVideo: d.substitute.trim() === ex.substitute ? ex.substituteVideo : "",
  };
}

function newExercise(d: Draft): Exercise {
  return applyDraft({
    order: `ex-${crypto.randomUUID()}`, name: d.name, muscles: d.muscles, warmUp: d.warmUp, sets: d.sets, reps: d.reps,
    rest: "", cues: d.cues, video: "", substitute: "", substituteVideo: "",
  }, d);
}

// New or existing training day: name, place in the rotation, and exercises.
export function EditDayView({ dayId }: { dayId: string }) {
  const plan = usePlan();
  const hydrated = useHydrated();
  if (!hydrated) return null;
  const existing = plan.days.find((d) => d.id === dayId);
  if (dayId !== "new" && !existing) return <p className="py-16 text-center">This day no longer exists. <Link href="/plan" className="font-semibold text-[var(--accent)]">Back to your plan</Link></p>;
  return <DayForm key={dayId} plan={plan} existing={existing} />;
}

function DayForm({ plan, existing }: { plan: PlanSettings; existing?: Day }) {
  const router = useRouter();
  const [day, setDay] = useState<Day>(() => existing ?? {
    id: `day-${crypto.randomUUID().slice(0, 8)}`, letter: DAY_LETTERS[plan.days.length] ?? "?",
    name: "", optional: false, focus: "", duration: "", blocks: [],
  });
  const [sheet, setSheet] = useState<{ index: number | null } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const exercises = dayExercises(day);
  const setExercises = (list: Exercise[]) => setDay({ ...day, blocks: list.map((ex) => ({ format: ex.optional ? "Optional" : "Straight Sets", exercises: [ex] })) });

  const save = async () => {
    if (!day.name.trim()) { setError("Give this day a name."); return; }
    setSaving(true);
    const named = { ...day, name: day.name.trim() };
    const days = existing ? plan.days.map((d) => (d.id === day.id ? named : d)) : [...plan.days, named];
    const message = await persist({ ...plan, days: relabel(days) });
    setSaving(false);
    if (message) setError(message);
    else router.push("/plan");
  };

  const remove = async () => {
    if (plan.days.length < 2 || !existing) return;
    if (!(await askConfirm({ title: `Delete ${existing.name}?`, body: "Your logged history for its exercises stays.", confirmLabel: "Delete day", danger: true }))) return;
    const message = await persist({ ...plan, days: relabel(plan.days.filter((d) => d.id !== existing.id)) });
    if (message) setError(message);
    else router.push("/plan");
  };

  return (
    <div className="flex flex-col gap-6 lg:mx-auto lg:max-w-xl">
      <header className="flex items-center gap-2">
        <Link href="/plan" className="flex h-11 items-center px-1 font-semibold text-[var(--muted)]">Cancel</Link>
        <h1 className="flex-1 text-center text-[17px] font-bold">{existing ? `Edit ${existing.letter}` : "New day"}</h1>
        <button type="button" onClick={() => void save()} disabled={saving}
          className="flex h-10 items-center rounded-xl bg-[var(--accent)] px-[18px] text-sm font-bold text-[var(--accent-fg)] disabled:opacity-40">
          {saving ? "Saving…" : "Save"}
        </button>
      </header>

      <div className="flex items-end gap-2.5">
        <div className="flex flex-col gap-1.5">
          <span className="text-[13px] font-semibold text-[var(--muted)]">Letter</span>
          <DayLetter letter={day.letter} size="lg" />
        </div>
        <label className="flex flex-1 flex-col gap-1.5">
          <span className="text-[13px] font-semibold text-[var(--muted)]">Name</span>
          <input value={day.name} maxLength={40} placeholder="e.g. Arm Day" onChange={(e) => { setDay({ ...day, name: e.target.value }); setError(""); }}
            className="font-display h-14 rounded-2xl border-2 border-[var(--line)] bg-[var(--card)] px-3.5 text-xl font-bold outline-none focus:border-[var(--accent)]" />
        </label>
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-[13px] font-semibold text-[var(--muted)]">When does it come up?</legend>
        {([[false, "Part of the rotation", "It takes its turn after the day before it"], [true, "Optional extra", "Offered on Today, never due"]] as const).map(([value, title, hint]) => (
          <label key={title} className={`flex min-h-14 items-center gap-3 rounded-2xl bg-[var(--card)] px-3.5 ${day.optional === value ? "ring-1 ring-[var(--accent)]" : ""}`}>
            <input type="radio" name="when" checked={day.optional === value} onChange={() => setDay({ ...day, optional: value })} className="h-5 w-5 accent-[var(--accent)]" />
            <span><span className="block font-semibold">{title}</span><span className="block text-[13px] text-[var(--muted)]">{hint}</span></span>
          </label>
        ))}
      </fieldset>

      <section aria-labelledby="day-exercises" className="flex flex-col gap-2">
        <h2 id="day-exercises" className="text-[13px] font-semibold text-[var(--muted)]">Exercises, in order</h2>
        {exercises.length > 0 && (
          <ol className="overflow-hidden rounded-2xl bg-[var(--card)]">
            {exercises.map((ex, i) => (
              <li key={ex.order} className="flex min-h-16 items-center gap-1 border-b border-[var(--soft)] pr-1 pl-3.5 last:border-0">
                <button type="button" onClick={() => setSheet({ index: i })} className="min-w-0 flex-1 py-2 text-left">
                  <span className="block truncate font-semibold">{ex.name}</span>
                  <span className="block text-[13px] text-[var(--muted)]">
                    {ex.sets} × {ex.reps} · {ex.startWeight === "BW" || !ex.startWeight ? "bodyweight" : `start ${ex.startWeight}`} · warm-up {ex.warmUp === "None" ? "none" : ex.warmUp}
                  </span>
                </button>
                <button type="button" disabled={i === 0} aria-label={`Move ${ex.name} earlier`} onClick={() => setExercises(move(exercises, i, i - 1))}
                  className="grid h-11 w-9 place-items-center text-[var(--muted)] disabled:opacity-25">
                  <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" aria-hidden><path d="m6 15 6-6 6 6" /></svg>
                </button>
                <button type="button" aria-label={`Remove ${ex.name}`} onClick={() => setExercises(exercises.filter((_, j) => j !== i))}
                  className="grid h-11 w-9 place-items-center text-[var(--muted)]">
                  <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" aria-hidden><path d="M6 6l12 12M18 6 6 18" /></svg>
                </button>
              </li>
            ))}
          </ol>
        )}
        {exercises.length < MAX_DAY_EXERCISES && (
          <button type="button" onClick={() => setSheet({ index: null })}
            className="flex h-13 min-h-12 items-center justify-center gap-2 rounded-2xl border border-dashed border-[#3a3a40] font-bold">
            <span aria-hidden className="text-xl">＋</span> Add exercise
          </button>
        )}
        <p className="text-[13px] text-[var(--faint)]">Removing an exercise keeps its logged history.</p>
      </section>

      {error && <p role="alert" className="text-sm text-[var(--danger)]">{error}</p>}
      {existing && plan.days.length > 1 && (
        <button type="button" onClick={() => void remove()} className="h-11 text-[15px] font-semibold text-[var(--danger)]">Delete this day</button>
      )}

      {sheet && (
        <ExerciseSheet
          dayName={day.name.trim() || "this day"}
          initial={sheet.index !== null ? draftFrom(exercises[sheet.index]) : null}
          onClose={() => setSheet(null)}
          onSave={(d) => {
            if (sheet.index !== null) setExercises(exercises.map((ex, j) => (j === sheet.index ? applyDraft(ex, d) : ex)));
            else setExercises([...exercises, newExercise(d)]);
            setSheet(null);
          }}
        />
      )}
    </div>
  );
}

// Pick from the library (or create one), then set sets, reps, start weight
// and warm-up. Editing an existing exercise skips the search.
function ExerciseSheet({ dayName, initial, onClose, onSave }: {
  dayName: string;
  initial: Draft | null;
  onClose: () => void;
  onSave: (d: Draft) => void;
}) {
  const [query, setQuery] = useState("");
  const [d, setD] = useState<Draft | null>(initial);
  const dialog = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCloseRef.current();
    window.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog.current?.focus();
    return () => { window.removeEventListener("keydown", onKey); document.body.style.overflow = overflow; };
  }, []);

  const pick = (ex: ExerciseDefinition) => setD({
    name: ex.name, sets: 2, reps: "10", startKg: 0, warmUp: "8 / 5", substitute: "",
    muscles: ex.primaryMuscles.join(", "), cues: ex.instructions.slice(0, 3).join(" "),
  });
  const create = () => setD({ name: query.trim(), sets: 2, reps: "10", startKg: 0, warmUp: "None", substitute: "", muscles: "", cues: "" });

  const repCount = d ? parseInt(d.reps, 10) || 0 : 0;
  const repSuffix = d ? d.reps.replace(/^\d+/, "") : "";
  const steppers = d ? [
    { label: "Sets", value: String(d.sets), dec: () => setD({ ...d, sets: Math.max(1, d.sets - 1) }), inc: () => setD({ ...d, sets: Math.min(10, d.sets + 1) }) },
    { label: /sec|min/.test(d.reps) ? "Seconds" : "Reps", value: String(repCount), dec: () => setD({ ...d, reps: `${Math.max(1, repCount - 1)}${repSuffix}` }), inc: () => setD({ ...d, reps: `${Math.min(100, repCount + 1)}${repSuffix}` }) },
    { label: "Start kg", value: d.startKg > 0 ? String(d.startKg) : "BW", dec: () => setD({ ...d, startKg: Math.max(0, d.startKg - 1.25) }), inc: () => setD({ ...d, startKg: d.startKg + 1.25 }) },
  ] : [];

  return (
    <div className="fixed inset-0 z-50 flex items-end bg-black/60 lg:items-center lg:justify-center" onClick={onClose}>
      <div ref={dialog} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="sheet-title" onClick={(e) => e.stopPropagation()}
        className="flex max-h-[92dvh] w-full flex-col gap-4 overflow-y-auto rounded-t-3xl border-t border-[var(--line)] bg-[#17171a] px-4 pt-3 pb-[calc(env(safe-area-inset-bottom)+1.5rem)] outline-none lg:max-w-lg lg:rounded-3xl lg:border">
        <div className="flex items-center justify-between">
          <h2 id="sheet-title" className="text-[17px] font-bold">{initial ? "Edit exercise" : `Add to ${dayName}`}</h2>
          <button type="button" onClick={onClose} className="h-11 px-1 font-semibold text-[var(--muted)]">Cancel</button>
        </div>

        {!d ? (
          <ExerciseSearch query={query} onQueryChange={setQuery} added={[]} onSelect={pick} onCreate={create} />
        ) : (
          <>
            <label className="flex flex-col gap-1.5">
              <span className="text-[13px] font-semibold text-[var(--muted)]">Exercise</span>
              <input value={d.name} maxLength={120} onChange={(e) => setD({ ...d, name: e.target.value })} placeholder="Exercise name"
                className="font-display h-12 rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 text-lg font-bold outline-none focus:border-[var(--accent)]" />
              {d.muscles && <span className="text-[13px] text-[var(--muted)]">{d.muscles}</span>}
            </label>

            <div className="grid grid-cols-3 gap-2">
              {steppers.map((st) => (
                <div key={st.label} className="flex flex-col items-center gap-1.5 rounded-2xl bg-[var(--bg)] px-1 py-2.5">
                  <span className="text-xs text-[var(--muted)]">{st.label}</span>
                  <span className="font-display text-2xl font-bold" aria-live="polite">{st.value}</span>
                  <div className="flex gap-1.5">
                    <button type="button" onClick={st.dec} aria-label={`Less ${st.label.toLowerCase()}`} className="h-10 w-10 rounded-[10px] bg-[var(--soft)] text-lg font-bold">−</button>
                    <button type="button" onClick={st.inc} aria-label={`More ${st.label.toLowerCase()}`} className="h-10 w-10 rounded-[10px] bg-[var(--soft)] text-lg font-bold">+</button>
                  </div>
                </div>
              ))}
            </div>

            <div role="radiogroup" aria-label="Warm-up sets" className="flex flex-col gap-1.5">
              <span className="text-[13px] font-semibold text-[var(--muted)]">Warm-up sets</span>
              <div className="grid grid-cols-3 gap-1 rounded-xl bg-[var(--bg)] p-1">
                {WARM_UP_OPTIONS.map((w) => {
                  const on = d.warmUp.replace(/ per .*$/, "") === w;
                  return (
                    <button key={w} type="button" role="radio" aria-checked={on} onClick={() => setD({ ...d, warmUp: w })}
                      className={`h-10 rounded-[9px] text-sm font-bold ${on ? "bg-[var(--fg)] text-[var(--bg)]" : "text-[var(--muted)]"}`}>{w}</button>
                  );
                })}
              </div>
            </div>

            <label className="flex flex-col gap-1.5">
              <span className="text-[13px] font-semibold text-[var(--muted)]">If the equipment is taken</span>
              <input value={d.substitute} maxLength={120} onChange={(e) => setD({ ...d, substitute: e.target.value })} placeholder="Substitute exercise (optional)"
                className="h-11 rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none focus:border-[var(--accent)]" />
            </label>

            <button type="button" disabled={!d.name.trim()} onClick={() => onSave(d)} className={btnPrimary}>
              {initial ? "Done" : `Add · ${d.sets} × ${d.reps}${d.startKg > 0 ? ` at ${d.startKg} kg` : ""}`}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
