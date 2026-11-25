"use client";

import Link from "next/link";
import { askConfirm } from "@/components/ConfirmDialog";
import { useState } from "react";
import { MIN_PASSWORD_LENGTH, sendCode, setPassword, signInWithPassword, signOut, useAccountSession, verifyCode } from "@/lib/auth";
import { databaseConfigured } from "@/lib/supabase";
import { DeleteAccountDialog } from "@/components/DeleteAccountDialog";
import { MuseConnectorCard } from "@/components/MuseConnectorCard";

const field =
  "h-14 w-full rounded-xl border border-[var(--line)] bg-[var(--bg)] px-4 outline-none placeholder:text-[var(--muted)]/60 focus:border-[var(--accent)]";
const primary =
  "h-12 w-full rounded-xl bg-[var(--accent)] font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40";

export function AccountView() {
  const session = useAccountSession();
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState<"password" | "email" | "code">("password");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [showDelete, setShowDelete] = useState(false);
  const [password, setPasswordValue] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordSaved, setPasswordSaved] = useState(false);

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
        {step === "password" ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void attempt(() => signInWithPassword(email.trim(), password));
            }}
          >
            <h2 className="font-semibold">Sign in</h2>
            <input
              type="email"
              name="email"
              autoComplete="username"
              inputMode="email"
              required
              disabled={busy}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              aria-label="Email"
              className={`${field} mt-3`}
            />
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              required
              disabled={busy}
              value={password}
              onChange={(e) => setPasswordValue(e.target.value)}
              placeholder="Password"
              aria-label="Password"
              className={`${field} mt-2`}
            />
            <button type="submit" disabled={busy || !email.includes("@") || !password} className={`${primary} mt-3`}>
              {busy ? "Signing in…" : "Sign in"}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setStep("email");
                setError("");
              }}
              className="mt-2 h-11 w-full text-sm font-medium text-[var(--muted)] disabled:opacity-40"
            >
              No password yet? Email me a code
            </button>
          </form>
        ) : step === "email" ? (
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
              We&apos;ll email you a sign in code. Once in, set a password on the Account page to skip this next time.
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
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setStep("password");
                setError("");
              }}
              className="mt-2 h-11 w-full text-sm font-medium text-[var(--muted)] disabled:opacity-40"
            >
              Sign in with a password
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
            onClick={async () => {
              if (await askConfirm({ title: "Sign out?", body: "Your changes will be saved first. Your data stays in your account.", confirmLabel: "Sign out" })) {
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

      <form
        aria-labelledby="account-password-title"
        className="mt-5 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-5"
        onSubmit={(e) => {
          e.preventDefault();
          setPasswordSaved(false);
          void attempt(async () => {
            await setPassword(newPassword);
            setNewPassword("");
            setPasswordSaved(true);
          });
        }}
      >
        <h2 id="account-password-title" className="font-semibold">Password</h2>
        <p className="mt-1 text-sm text-[var(--muted)]">Sign in instantly next time, without waiting for an email code.</p>
        {/* Lets password managers save the new password against this email. */}
        <input type="email" name="email" autoComplete="username" value={session.user.email ?? ""} readOnly hidden />
        <input
          type="password"
          name="new-password"
          autoComplete="new-password"
          minLength={MIN_PASSWORD_LENGTH}
          required
          disabled={busy}
          value={newPassword}
          onChange={(e) => {
            setNewPassword(e.target.value);
            setPasswordSaved(false);
          }}
          placeholder={`New password (${MIN_PASSWORD_LENGTH}+ characters)`}
          aria-label="New password"
          className={`${field} mt-3`}
        />
        <button type="submit" disabled={busy || newPassword.length < MIN_PASSWORD_LENGTH} className={`${primary} mt-3`}>
          {busy ? "Saving…" : "Save password"}
        </button>
        {passwordSaved && <p role="status" className="mt-3 text-sm text-[var(--up)]">Password saved. Use it with your email to sign in.</p>}
      </form>

      <MuseConnectorCard userId={session.user.id} />

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
