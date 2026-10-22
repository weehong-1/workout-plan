"use client";

import { createContext, useContext } from "react";
import type { Session } from "@supabase/supabase-js";
import { flushChanges, getAccountGeneration, getPersistenceStatus, lockAccountEdits, resetAccount } from "@/lib/store";
import { supabase } from "@/lib/supabase";

// AuthGate owns the subscription so every account screen sees the same session.
const AccountSessionContext = createContext<Session | null>(null);
export const AccountSessionProvider = AccountSessionContext.Provider;

export function useAccountSession(): Session | null {
  return useContext(AccountSessionContext);
}

function authClient() {
  const client = supabase();
  if (!client) throw new Error("Sign-in is not configured on this site.");
  return client;
}

export async function sendCode(email: string): Promise<void> {
  const { error } = await authClient().auth.signInWithOtp({
    email,
    options: { shouldCreateUser: true, emailRedirectTo: `${window.location.origin}/account` },
  });
  if (error) throw new Error(error.message);
}

export async function verifyCode(email: string, token: string): Promise<void> {
  const { error } = await authClient().auth.verifyOtp({ email, token, type: "email" });
  if (error) throw new Error(error.message);
  // AuthGate loads this account after observing the authenticated session.
}

let deletionInProgress = false;

/** The database verifies identity again and deletes only auth.uid(), atomically. */
export async function deleteAccount(expectedUserId: string, confirmation: string): Promise<void> {
  if (confirmation !== "DELETE") throw new Error("Type DELETE to confirm account deletion.");
  if (deletionInProgress) throw new Error("Account deletion is already in progress.");
  const client = authClient();
  const generation = getAccountGeneration();
  const status = getPersistenceStatus();
  if (!expectedUserId || status.userId !== expectedUserId || !status.ready || status.locked) {
    throw new Error("Your account is not ready. Reload and try again.");
  }
  deletionInProgress = true;
  lockAccountEdits(true);
  try {
    // Drain in-flight writes before deletion. Failed writes need not be saved:
    // the user explicitly chose to remove this account and all its data.
    await flushChanges();
    const { data, error: sessionError } = await client.auth.getSession();
    if (getAccountGeneration() !== generation || sessionError || data.session?.user.id !== expectedUserId) {
      throw new Error("Your session changed. Sign in again before deleting your account.");
    }
    const { error } = await client.rpc("delete_own_account", {
      expected_account_id: expectedUserId,
      confirmation,
    });
    if (error) {
      throw new Error(error.code === "PGRST202" || error.code === "42883"
        ? "Account deletion is not set up yet. The site owner must apply the latest database schema."
        : "Could not confirm account deletion. Please retry. If your session has expired, sign in again.");
    }
    if (getAccountGeneration() !== generation) return;
    // Clear the deleted account's memory even if its now-invalid auth token
    // causes sign-out to fail. Never clear a newer account after an async wait.
    resetAccount();
    const clearedGeneration = getAccountGeneration();
    try {
      await client.auth.signOut({ scope: "local" });
    } catch {
      // The account is already deleted. Reload to the authentication gate.
    } finally {
      if (getAccountGeneration() === clearedGeneration) window.location.replace("/account");
    }
  } finally {
    deletionInProgress = false;
    if (getAccountGeneration() === generation) lockAccountEdits(false);
  }
}

export async function signOut(): Promise<void> {
  if (deletionInProgress) throw new Error("Please wait for account deletion to finish.");
  const client = authClient();
  const generation = getAccountGeneration();
  lockAccountEdits(true);
  try {
    if (!(await flushChanges())) {
      throw new Error("Your changes could not be saved. Please retry before signing out.");
    }
    const { error } = await client.auth.signOut({ scope: "local" });
    if (error) throw new Error(error.message);
    // No new edits can enter between flushing writes and ending the session.
    if (getAccountGeneration() === generation) resetAccount();
  } finally {
    if (getAccountGeneration() === generation) lockAccountEdits(false);
  }
}
