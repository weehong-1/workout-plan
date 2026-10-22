"use client";

import { Fragment, useEffect, useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { AccountView } from "@/components/AccountView";
import { AccountSessionProvider, signOut } from "@/lib/auth";
import { loadAccount, resetAccount, usePersistenceStatus } from "@/lib/store";
import { databaseConfigured, supabase } from "@/lib/supabase";

type AuthState = {
  checked: boolean;
  session: Session | null;
  revision: number;
  error?: string;
};
type AccountLoad = {
  userId: string;
  revision: number;
  phase: "loading" | "ready" | "error";
  error?: string;
};

export function AuthGate({ children }: { children: React.ReactNode }) {
  const [auth, setAuth] = useState<AuthState>({ checked: false, session: null, revision: 0 });
  const [accountLoad, setAccountLoad] = useState<AccountLoad | null>(null);
  const [retry, setRetry] = useState(0);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState("");
  const persistence = usePersistenceStatus();
  const userId = auth.session?.user.id ?? null;

  useEffect(() => {
    const sb = supabase();
    if (!sb) return;
    let active = true;
    let receivedEvent = false;
    const observe = (session: Session | null, error?: string) => {
      if (!active) return;
      setAuth((previous) => ({
        checked: true,
        session,
        // Even A → signed-out → A must invalidate the previous account load.
        revision: previous.revision + (previous.session?.user.id !== session?.user.id ? 1 : 0),
        error,
      }));
    };
    const { data: { subscription } } = sb.auth.onAuthStateChange((_event, session) => {
      receivedEvent = true;
      // Never await Supabase calls here: loading happens in the effect below.
      observe(session);
    });
    void sb.auth.getSession().then(({ data, error }) => {
      if (!active || receivedEvent) return;
      observe(error ? null : data.session, error?.message);
    }).catch((error: unknown) => {
      if (!active || receivedEvent) return;
      observe(null, error instanceof Error ? error.message : String(error));
    });
    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!auth.checked) return;
    setSignOutError("");
    if (!userId) {
      resetAccount();
      setAccountLoad(null);
      return;
    }
    let active = true;
    const identity = { userId, revision: auth.revision };
    setAccountLoad({ ...identity, phase: "loading" });
    void loadAccount(userId).then(() => {
      if (active) setAccountLoad({ ...identity, phase: "ready" });
    }).catch((error: unknown) => {
      if (active) {
        setAccountLoad({
          ...identity,
          phase: "error",
          error: error instanceof Error ? error.message : String(error),
        });
      }
    });
    return () => {
      active = false;
    };
    // Session/token refreshes for the same user must not reload account data.
  }, [auth.checked, auth.revision, userId, retry]);

  const currentLoad = accountLoad?.userId === userId && accountLoad.revision === auth.revision
    ? accountLoad
    : null;
  const ready = databaseConfigured && auth.checked && userId !== null && currentLoad?.phase === "ready"
    && persistence.userId === userId && persistence.ready;
  const loadError = currentLoad?.phase === "error"
    ? currentLoad.error ?? "Your account could not be loaded."
    : userId && currentLoad && !persistence.ready && persistence.userId === userId && persistence.phase === "error"
      ? persistence.error ?? "Your account could not be loaded."
      : null;

  const attemptSignOut = async () => {
    setSigningOut(true);
    setSignOutError("");
    try {
      await signOut();
    } catch (error) {
      setSignOutError(error instanceof Error ? error.message : String(error));
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <AccountSessionProvider value={auth.session}>
      {ready ? (
        // Remount account-scoped UI (including timers) when the identity changes.
        <Fragment key={auth.revision}>{children}</Fragment>
      ) : (
        <main className="mx-auto flex min-h-dvh max-w-xl flex-col justify-center px-4 py-[calc(env(safe-area-inset-top)+2rem)]">
          <header className="mb-6 px-1">
            <p className="text-sm font-semibold uppercase tracking-wider text-[var(--muted)]">Training Plan</p>
            <h1 className="mt-2 text-3xl font-bold">{userId ? "Opening your account" : "Sign in to get started"}</h1>
            <p className="mt-2 text-[var(--muted)]">
              {userId ? "Your training data must load before you can continue." : "Sign in to access your training plan, workouts and progress."}
            </p>
          </header>
          {!databaseConfigured ? (
            <p role="alert" className="rounded-2xl border border-[var(--line)] bg-[var(--card)] p-4 text-sm text-[var(--muted)]">
              Sign-in is not configured yet. The site owner must configure the Supabase environment variables and redeploy before this application can be used.
            </p>
          ) : !auth.checked ? (
            <p role="status" className="px-1 text-sm text-[var(--muted)]">Checking your session…</p>
          ) : userId ? (
            loadError ? (
              <section className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
                <h2 className="font-semibold">Unable to load your account</h2>
                <p role="alert" className="mt-2 text-sm text-red-600 dark:text-red-400">{loadError}</p>
                <button
                  type="button"
                  disabled={signingOut}
                  onClick={() => setRetry((value) => value + 1)}
                  className="mt-4 h-12 w-full rounded-xl bg-[var(--accent)] font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40"
                >
                  Retry loading
                </button>
                <button
                  type="button"
                  disabled={signingOut}
                  onClick={() => void attemptSignOut()}
                  className="mt-2 h-12 w-full rounded-xl text-sm font-semibold text-red-600 disabled:opacity-40 dark:text-red-400"
                >
                  {signingOut ? "Saving and signing out…" : "Sign out"}
                </button>
                {signOutError && <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">{signOutError}</p>}
              </section>
            ) : (
              <p role="status" className="px-1 text-sm text-[var(--muted)]">Loading your account…</p>
            )
          ) : (
            <>
              {auth.error && <p role="alert" className="mb-4 text-sm text-red-600 dark:text-red-400">We couldn&apos;t restore your session. Please sign in again.</p>}
              <AccountView />
            </>
          )}
        </main>
      )}
    </AccountSessionProvider>
  );
}
