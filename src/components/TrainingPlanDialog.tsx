"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { flushChanges, getPersistenceStatus, saveTrainingPlan, usePersistenceStatus } from "@/lib/store";
import type { TrainingPlan } from "@/lib/training-plan";

export function TrainingPlanDialog({
  plan,
  onClose,
  onSaved,
}: {
  plan?: TrainingPlan;
  onClose: () => void;
  onSaved: (plan: TrainingPlan, created: boolean) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const planId = useRef(plan?.id);
  const submitting = useRef(false);
  const mounted = useRef(false);
  const [name, setName] = useState(plan?.name ?? "");
  const [description, setDescription] = useState(plan?.description ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const { ready, locked } = usePersistenceStatus();
  const count = plan?.blocks.reduce((total, block) => total + block.exercises.length, 0) ?? 0;

  useEffect(() => {
    mounted.current = true;
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog?.showModal();
    nameRef.current?.focus();
    return () => {
      mounted.current = false;
      dialog?.close();
      document.body.style.overflow = overflow;
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    if (!name.trim()) {
      setError("Give your training plan a name.");
      nameRef.current?.focus();
      return;
    }
    submitting.current = true;
    setSaving(true);
    setError("");
    try {
      const saved = saveTrainingPlan(name, description, planId.current);
      if (!saved) throw new Error("Your plan could not be saved. Check the name and description, then try again.");
      // Reuse the optimistic record on retry rather than creating a second plan.
      planId.current = saved.id;
      const confirmed = await flushChanges();
      if (!confirmed) throw new Error(getPersistenceStatus().error ?? "Unable to save. Keep this page open and try again.");
      if (mounted.current) onSaved(saved, !plan);
    } catch (cause) {
      if (mounted.current) setError(cause instanceof Error ? cause.message : "Unable to save your plan. Please try again.");
    } finally {
      submitting.current = false;
      if (mounted.current) setSaving(false);
    }
  }

  return createPortal(
    <dialog
      ref={dialogRef}
      aria-labelledby="training-plan-title"
      aria-describedby="training-plan-hint"
      aria-modal="true"
      onCancel={(event) => { event.preventDefault(); if (!submitting.current) onClose(); }}
      onClick={(event) => { if (event.target === event.currentTarget && !submitting.current) onClose(); }}
      className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-3xl border border-[var(--line)] bg-[var(--card)] p-0 text-[var(--fg)] shadow-2xl backdrop:bg-black/50 backdrop:backdrop-blur-sm"
    >
      <div className="p-5 sm:p-6">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="training-plan-title" className="text-xl font-bold tracking-tight">{plan ? "Edit Training Plan" : "Add Training Plan"}</h2>
            <p id="training-plan-hint" className="mt-1 text-sm text-[var(--muted)]">
              {plan ? "Update your plan’s name and description." : "Start with a name and a little about your goals."}
            </p>
          </div>
          <button type="button" onClick={onClose} disabled={saving} aria-label="Close training plan" className="-mt-1 -mr-1 grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[var(--soft)] text-[var(--muted)] disabled:opacity-40">
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
              <path d="m6 6 12 12M18 6 6 18" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        <form onSubmit={submit} className="mt-6 space-y-5" aria-busy={saving}>
          <div>
            <label htmlFor="training-plan-name" className="block text-sm font-semibold">Plan name <span className="text-[var(--accent)]" aria-hidden="true">*</span></label>
            <input
              ref={nameRef}
              id="training-plan-name"
              name="name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
              maxLength={80}
              disabled={saving}
              placeholder="e.g. Strength & mobility"
              autoComplete="off"
              className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)] disabled:opacity-60"
            />
          </div>
          <div>
            <div className="flex items-baseline justify-between gap-2">
              <label htmlFor="training-plan-description" className="block text-sm font-semibold">Description <span className="font-normal text-[var(--muted)]">(optional)</span></label>
              <span className="text-xs tabular-nums text-[var(--muted)]" aria-hidden="true">{description.length}/500</span>
            </div>
            <textarea
              id="training-plan-description"
              name="description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
              rows={4}
              disabled={saving}
              placeholder="What would you like to work on?"
              className="mt-2 w-full resize-y rounded-xl border border-[var(--line)] bg-[var(--bg)] p-3 outline-none focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)] disabled:opacity-60"
            />
          </div>
          <div className="rounded-xl bg-[var(--soft)] px-4 py-3 text-sm text-[var(--muted)]">
            <span className="font-semibold text-[var(--fg)]">{count} {count === 1 ? "exercise" : "exercises"}</span>
            {count === 0 && <p className="mt-0.5 text-xs">{plan ? "This plan has no exercises yet." : "New plans start with no exercises."}</p>}
          </div>
          {error && <p role="alert" className="break-words text-sm text-red-600 dark:text-red-400">Couldn’t save your plan: {error}</p>}
          <div className="flex gap-3">
            <button type="button" onClick={onClose} disabled={saving} className="h-12 flex-1 rounded-xl border border-[var(--line)] text-sm font-semibold disabled:opacity-40">Cancel</button>
            <button type="submit" disabled={saving || !ready || locked || !name.trim()} className="h-12 flex-[2] rounded-xl bg-[var(--accent)] px-3 text-sm font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40">
              {saving ? "Saving…" : error ? "Retry saving" : plan ? "Save changes" : "Create plan"}
            </button>
          </div>
        </form>
      </div>
    </dialog>,
    document.body,
  );
}
