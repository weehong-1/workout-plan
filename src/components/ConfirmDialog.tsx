"use client";

import { useEffect, useRef, useSyncExternalStore } from "react";

// An in-app replacement for window.confirm(). Browsers and in-app web views may
// block native dialogs, and then confirm() silently answers "Cancel", which made
// deletes look broken. askConfirm() resolves true only when the person taps the
// confirm button in <ConfirmHost />.

export type ConfirmRequest = {
  title: string;
  body?: string;
  confirmLabel: string;
  danger?: boolean;
};

type Pending = ConfirmRequest & { resolve: (ok: boolean) => void };

let pending: Pending | null = null;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

export function askConfirm(request: ConfirmRequest): Promise<boolean> {
  // A new question answers the open one with "no" rather than leaving it hanging.
  pending?.resolve(false);
  return new Promise((resolve) => {
    pending = { ...request, resolve };
    emit();
  });
}

function answer(ok: boolean) {
  const current = pending;
  pending = null;
  emit();
  current?.resolve(ok);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function ConfirmHost() {
  const request = useSyncExternalStore(subscribe, () => pending, () => null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!request) return;
    // Focus the safe choice, so Enter never deletes by accident.
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") answer(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [request]);

  if (!request) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 p-3 sm:items-center" onClick={() => answer(false)}>
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby={request.body ? "confirm-body" : undefined}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-sm rounded-3xl border border-[var(--line)] bg-[var(--panel,var(--card))] p-5 shadow-2xl"
      >
        <h2 id="confirm-title" className="text-[17px] font-bold">{request.title}</h2>
        {request.body && <p id="confirm-body" className="mt-2 text-sm text-[var(--muted)]">{request.body}</p>}
        <div className="mt-5 grid grid-cols-2 gap-2">
          <button ref={cancelRef} type="button" onClick={() => answer(false)}
            className="h-12 rounded-2xl bg-[var(--card)] text-[15px] font-semibold">Cancel</button>
          <button type="button" onClick={() => answer(true)}
            className={`h-12 rounded-2xl text-[15px] font-bold ${request.danger ? "bg-[var(--danger)] text-white" : "bg-[var(--accent)] text-[var(--accent-fg)]"}`}>
            {request.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
