"use client";

import { type SupabaseClient, createClient } from "@supabase/supabase-js";

// Set these in Vercel (Project → Settings → Environment Variables).
// Without them, the authentication gate blocks access to the app.
const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
const key =
  process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

export const databaseConfigured = Boolean(url && key);

let client: SupabaseClient | null = null;

export function supabase(): SupabaseClient | null {
  if (!databaseConfigured || typeof window === "undefined") return null;
  client ??= createClient(url!, key!, {
    auth: { persistSession: true, autoRefreshToken: true, storageKey: "workout-plan:auth" },
  });
  return client;
}
