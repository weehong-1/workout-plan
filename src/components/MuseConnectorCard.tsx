"use client";

import { useEffect, useState } from "react";
import { askConfirm } from "@/components/ConfirmDialog";
import { connectorTokenCreatedAt, generateConnectorToken, revokeConnectorToken } from "@/lib/connector-token";

const primary =
  "h-12 w-full rounded-xl bg-[var(--accent)] font-semibold text-[var(--accent-fg)] active:scale-[0.98] disabled:opacity-40";
const secondary =
  "min-h-11 rounded-xl border border-[var(--line)] px-4 text-sm font-medium text-[var(--fg)] transition-colors hover:bg-[var(--soft)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] disabled:opacity-40";
const code = "mt-1 block w-full break-all rounded-xl border border-[var(--line)] bg-[var(--bg)] px-3 py-2 font-mono text-xs select-all";

const formatDate = (d: Date) => d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

/** Generate, replace or revoke the Bearer token Muse AI uses for /api/muse/mcp. */
export function MuseConnectorCard({ userId }: { userId: string }) {
  // undefined while loading, null when the account has no token.
  const [createdAt, setCreatedAt] = useState<Date | null | undefined>(undefined);
  const [token, setToken] = useState("");
  const [copied, setCopied] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [origin, setOrigin] = useState("");

  useEffect(() => {
    let live = true;
    setOrigin(window.location.origin);
    connectorTokenCreatedAt(userId).then(
      (date) => live && setCreatedAt(date),
      (e) => { if (live) { setCreatedAt(null); setError(e instanceof Error ? e.message : String(e)); } },
    );
    return () => { live = false; };
  }, [userId]);

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

  const generate = async () => {
    if (createdAt && !(await askConfirm({
      title: "Replace the token?",
      body: "The current token stops working straight away. Paste the new one into Muse AI.",
      confirmLabel: "Replace token",
    }))) return;
    await attempt(async () => {
      const made = await generateConnectorToken(userId);
      setToken(made.token);
      setCreatedAt(made.createdAt);
      setCopied("");
    });
  };

  const revoke = async () => {
    if (!(await askConfirm({
      title: "Revoke the token?",
      body: "Muse AI loses access to your workouts and body weight until you generate a new token.",
      confirmLabel: "Revoke",
      danger: true,
    }))) return;
    await attempt(async () => {
      await revokeConnectorToken(userId);
      setToken("");
      setCreatedAt(null);
    });
  };

  const copy = async (label: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(label);
    } catch {
      setError("Couldn't copy. Select the text and copy it yourself.");
    }
  };

  const url = `${origin}/api/muse/mcp`;

  return (
    <section aria-labelledby="account-muse-title" className="mt-5 rounded-3xl border border-[var(--line)] bg-[var(--card)] p-5">
      <h2 id="account-muse-title" className="font-semibold">Muse AI connector</h2>
      <p className="mt-1 text-sm text-[var(--muted)]">
        Lets Muse AI read and log your workouts and body weight. Add a custom connector in Muse with this URL and token.
      </p>

      <p className="mt-3 text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Connector URL</p>
      <code className={code}>{url}</code>
      <button type="button" onClick={() => void copy("url", url)} className={`${secondary} mt-2`}>
        {copied === "url" ? "Copied" : "Copy URL"}
      </button>

      {token ? (
        <div role="status" className="mt-4">
          <p className="text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Bearer token</p>
          <code className={code}>{token}</code>
          <button type="button" onClick={() => void copy("token", token)} className={`${secondary} mt-2`}>
            {copied === "token" ? "Copied" : "Copy token"}
          </button>
          <p className="mt-2 text-sm text-[var(--muted)]">Copy it now. It won&apos;t be shown again. Anyone with it can change your data.</p>
        </div>
      ) : (
        <p className="mt-4 text-sm text-[var(--muted)]">
          {createdAt === undefined ? "Checking for a token…" : createdAt ? `Token created ${formatDate(createdAt)}. It can't be shown again.` : "No token yet."}
        </p>
      )}

      <button type="button" disabled={busy || createdAt === undefined} onClick={() => void generate()} className={`${primary} mt-3`}>
        {busy ? "Working…" : createdAt ? "Replace token" : "Generate token"}
      </button>
      {createdAt && (
        <button type="button" disabled={busy} onClick={() => void revoke()} className="mt-2 min-h-11 w-full rounded-lg text-sm font-medium text-red-600 disabled:opacity-40 dark:text-red-400">
          Revoke token
        </button>
      )}
      <p className="mt-3 text-xs text-[var(--muted)]">
        The connector acts as the account named by WORKOUT_EMAIL in the site&apos;s settings, so make the token while signed in as that account.
      </p>
      {error && <p role="alert" className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>}
    </section>
  );
}
