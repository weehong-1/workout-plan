"use client";

import Link from "next/link";
import { useState } from "react";
import { syncLine, useNow } from "@/components/SyncCard";
import { useHydrated, useStore } from "@/lib/store";
import { syncEnabled } from "@/lib/supabase";
import { requestSync, sendCode, signOut, useSyncStatus, verifyCode } from "@/lib/sync";

const field =
  "h-14 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-4 outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]";
const primary =
  "h-12 w-full rounded-xl bg-[var(--accent)] font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40";

export function AccountView() {
  const st = useSyncStatus();
  const { pending, log, bodyWeights } = useStore();
  const hydrated = useHydrated();
  const now = useNow(10_000);
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState<"email" | "code">("email");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (!hydrated) return null;

  if (!syncEnabled) {
    return (
      <p className="rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 text-sm text-[var(--muted)]">
        Sync isn&apos;t set up on this site yet. Add the Supabase keys in Vercel and redeploy (see the README).
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

  if (st.phase === "signed-out" || st.phase === "off") {
    const hasLocal = log.length > 0 || bodyWeights.length > 0;
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
              {hasLocal && " Everything already on this phone gets uploaded to your account."}
            </p>
            <input
              type="email"
              autoComplete="email"
              inputMode="email"
              required
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
              onClick={() => {
                setStep("email");
                setCode("");
                setError("");
              }}
              className="mt-2 h-11 w-full text-sm font-medium text-[var(--muted)]"
            >
              Use a different email
            </button>
          </form>
        )}
        {error && <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
      </section>
    );
  }

  return (
    <>
      <section className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Signed in as</p>
        <p className="mt-1 truncate text-lg font-semibold">{st.email}</p>
        <p className={`mt-2 text-sm ${st.phase === "error" ? "text-red-600 dark:text-red-400" : "text-[var(--muted)]"}`}>
          {syncLine(st, pending.length, now)}
        </p>
        <button
          onClick={() => void requestSync()}
          disabled={st.phase === "syncing"}
          className="mt-4 h-12 w-full rounded-xl border border-[var(--line)] font-semibold active:scale-[0.98] disabled:opacity-40"
        >
          Sync now
        </button>
      </section>

      <p className="mt-4 px-1 text-sm text-[var(--muted)]">
        Workouts, body weight and your kg/lb choices are saved to your account. A workout in progress stays on this
        phone until you finish it.
      </p>

      <button
        onClick={() => {
          const msg = pending.length
            ? `${pending.length} change${pending.length > 1 ? "s haven't" : " hasn't"} synced yet and will be lost. Sign out anyway?`
            : "Sign out? Your data stays in your account and comes back when you sign in again.";
          if (confirm(msg)) void signOut();
        }}
        className="mt-6 h-12 w-full rounded-xl text-sm font-semibold text-red-600 dark:text-red-400"
      >
        Sign out
      </button>
      <Link href="/" className="mt-2 block h-11 text-center text-sm font-medium leading-[2.75rem] text-[var(--muted)]">
        Back to plan
      </Link>
    </>
  );
}
