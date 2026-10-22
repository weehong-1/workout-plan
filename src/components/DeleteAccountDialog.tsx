"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { deleteAccount } from "@/lib/auth";

export function DeleteAccountDialog({ userId, email, onClose }: {
  userId: string;
  email?: string;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const submitting = useRef(false);
  const mounted = useRef(false);
  const [confirmation, setConfirmation] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    mounted.current = true;
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog?.showModal();
    // Initial focus goes to the safe action, not the destructive button.
    cancelRef.current?.focus();
    return () => {
      mounted.current = false;
      dialog?.close();
      document.body.style.overflow = overflow;
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (confirmation !== "DELETE" || submitting.current) return;
    submitting.current = true;
    setDeleting(true);
    setError("");
    try {
      await deleteAccount(userId, confirmation);
    } catch (cause) {
      if (mounted.current) {
        setError(cause instanceof Error ? cause.message : "Could not confirm account deletion. Please try again.");
        setDeleting(false);
      }
    } finally {
      submitting.current = false;
    }
  }

  return createPortal(
    <dialog
      ref={dialogRef}
      aria-labelledby="delete-account-title"
      aria-describedby="delete-account-description"
      aria-modal="true"
      onCancel={(event) => { event.preventDefault(); if (!submitting.current) onClose(); }}
      onClick={(event) => { if (event.target === event.currentTarget && !submitting.current) onClose(); }}
      className="fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-3xl border border-[var(--line)] bg-[var(--card)] p-5 text-[var(--fg)] shadow-2xl backdrop:bg-black/50 backdrop:backdrop-blur-sm sm:p-6"
    >
      <h2 id="delete-account-title" className="text-xl font-bold">Permanently delete your account?</h2>
      <div id="delete-account-description" className="mt-3 space-y-3 text-sm text-[var(--muted)]">
        <p>This will delete {email ? <strong className="break-all text-[var(--fg)]">{email}</strong> : "your sign-in account"} and all its saved training data:</p>
        <ul className="list-disc space-y-1 pl-5">
          <li>Workout history and in-progress workouts</li>
          <li>Body-weight entries</li>
          <li>Custom training plans, added exercises and preferences</li>
        </ul>
        <p className="font-semibold text-red-600 dark:text-red-400">This cannot be undone. You will be signed out. Signing up again creates an empty account.</p>
      </div>
      <form onSubmit={submit} aria-busy={deleting} className="mt-5">
        <label htmlFor="delete-account-confirmation" className="block text-sm font-semibold">Type DELETE to confirm</label>
        <input
          id="delete-account-confirmation"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          disabled={deleting}
          aria-describedby="delete-account-description"
          className="mt-2 h-12 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 outline-none focus:border-red-600 disabled:opacity-60"
        />
        {error && <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
        <p role="status" className="mt-3 text-sm text-[var(--muted)]">{deleting ? "Deleting your account… Please keep this page open." : ""}</p>
        <div className="mt-4 flex flex-col gap-2 sm:flex-row">
          <button ref={cancelRef} type="button" disabled={deleting} onClick={onClose} className="h-12 flex-1 rounded-xl border border-[var(--line)] bg-[var(--soft)] text-sm font-semibold disabled:opacity-40">Keep my account</button>
          <button type="submit" disabled={deleting || confirmation !== "DELETE"} className="h-12 flex-1 rounded-xl bg-red-600 px-3 text-sm font-semibold text-white disabled:opacity-40">{deleting ? "Deleting…" : "Delete permanently"}</button>
        </div>
      </form>
    </dialog>,
    document.body,
  );
}
