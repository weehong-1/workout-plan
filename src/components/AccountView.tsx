"use client";

import Link from "next/link";
import { useState } from "react";
import { sendCode, signOut, useAccountSession, verifyCode } from "@/lib/auth";
import { databaseConfigured } from "@/lib/supabase";
import { DeleteAccountDialog } from "@/components/DeleteAccountDialog";

const field =
  "h-14 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-4 outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]";
const primary =
  "h-12 w-full rounded-xl bg-[var(--accent)] font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40";

export function AccountView() {
  const session = useAccountSession();
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState<"email" | "code">("email");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [showDelete, setShowDelete] = useState(false);

  if (!databaseConfigured) {
    return (
      <p role="alert" className="rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 text-sm text-[var(--muted)]">
        Sign-in isn&apos;t set up on this site yet. The site owner must configure the Supabase environment variables and redeploy.
      </p>
    );
  }

  const attempt = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError("");
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  if (!session) {
    return (
      <section className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
        {step === "email" ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void attempt(async () => {
                await sendCode(email.trim());
                setStep("code");
              });
            }}
          >
            <h2 className="font-semibold">Sign in</h2>
            <p className="mt-1 text-sm text-[var(--muted)]">
              We&apos;ll email you a sign in code. No password needed.
            </p>
            <input
              type="email"
              autoComplete="email"
              inputMode="email"
              required
              disabled={busy}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className={`${field} mt-3`}
            />
            <button type="submit" disabled={busy || !email.includes("@")} className={`${primary} mt-3`}>
              {busy ? "Sending…" : "Email me a code"}
            </button>
          </form>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void attempt(() => verifyCode(email.trim(), code.trim()));
            }}
          >
            <h2 className="font-semibold">Enter your code</h2>
            <p className="mt-1 text-sm text-[var(--muted)]">Sent to {email}. Check spam if it isn&apos;t there.</p>
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              required
              disabled={busy}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 10))}
              placeholder="123456"
              className={`${field} mt-3 text-center text-2xl tracking-[0.3em] tabular-nums`}
            />
            <button type="submit" disabled={busy || code.length < 6} className={`${primary} mt-3`}>
              {busy ? "Checking…" : "Sign in"}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setStep("email");
                setCode("");
                setError("");
              }}
              className="mt-2 h-11 w-full text-sm font-medium text-[var(--muted)] disabled:opacity-40"
            >
              Use a different email
            </button>
          </form>
        )}
        {error && <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
      </section>
    );
  }

  return (
    <>
      <section aria-labelledby="account-profile-title" className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-5">
        <h2 id="account-profile-title" className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Signed in as</h2>
        <p className="mt-1 break-all text-lg font-semibold">{session.user.email ?? "Your account"}</p>
        <p className="mt-3 text-sm leading-relaxed text-[var(--muted)]">
          Your workouts, body weight and training preferences are saved to your account.
        </p>
        <div className="mt-5 border-t border-[var(--line)] pt-4">
          <button
            type="button"
            onClick={() => {
              if (confirm("Sign out? Your changes will be saved first. Your data stays in your account.")) {
                void attempt(signOut);
              }
            }}
            disabled={busy}
            className="min-h-11 rounded-xl border border-[var(--line)] px-4 text-sm font-medium text-[var(--fg)] transition-colors hover:bg-[var(--soft)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] disabled:opacity-40"
          >
            {busy ? "Saving and signing out…" : "Sign out"}
          </button>
          <p className="mt-2 text-xs text-[var(--muted)]">Signing out keeps your saved data.</p>
        </div>
      </section>
      {error && <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}

      <Link href="/" className="mt-5 flex min-h-12 items-center justify-center rounded-xl bg-[var(--accent)] px-4 text-sm font-semibold text-[var(--accent-fg)] active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]">
        Back to plan
      </Link>

      <details className="group mt-8 border-t border-[var(--line)]">
        <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 rounded-lg text-sm text-[var(--muted)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] [&::-webkit-details-marker]:hidden">
          Account removal
          <svg viewBox="0 0 24 24" className="h-4 w-4 shrink-0 transition-transform group-open:rotate-180 motion-reduce:transition-none" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
            <path d="m6 9 6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </summary>
        <div className="pb-2">
          <p id="account-delete-description" className="text-sm leading-relaxed text-[var(--muted)]">Permanently remove your sign-in account and all saved workouts, weights, plans and preferences. This cannot be undone.</p>
          <button type="button" disabled={busy} onClick={() => setShowDelete(true)} aria-haspopup="dialog" aria-describedby="account-delete-description" className="mt-2 min-h-11 rounded-lg px-1 text-sm font-medium text-red-600 underline decoration-red-600/30 underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 disabled:opacity-40 dark:text-red-400">Delete account…</button>
        </div>
      </details>
      {showDelete && <DeleteAccountDialog userId={session.user.id} email={session.user.email} onClose={() => setShowDelete(false)} />}
    </>
  );
}
