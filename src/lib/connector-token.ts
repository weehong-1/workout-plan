"use client";

import { supabase } from "@/lib/supabase";

// The Muse AI connector's Bearer token. It is made in the browser and shown
// once; the database keeps only its SHA-256 hash (connector_tokens).

function client() {
  const sb = supabase();
  if (!sb) throw new Error("Sign-in is not configured on this site.");
  return sb;
}

const hex = (bytes: Uint8Array) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

export async function sha256Hex(value: string): Promise<string> {
  return hex(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))));
}

const notSetUp = (code?: string) => code === "42P01" || code === "PGRST205";
const SCHEMA_MESSAGE = "The connector token isn't set up yet. The site owner must apply the latest database schema.";

/** When the account's current token was made, or null if it has none. */
export async function connectorTokenCreatedAt(userId: string): Promise<Date | null> {
  const { data, error } = await client().from("connector_tokens").select("created_at").eq("user_id", userId).maybeSingle();
  if (error) throw new Error(notSetUp(error.code) ? SCHEMA_MESSAGE : error.message);
  return data ? new Date(data.created_at) : null;
}

/** Makes a new token, replacing any previous one, and returns it. It cannot be read back later. */
export async function generateConnectorToken(userId: string): Promise<{ token: string; createdAt: Date }> {
  const token = `wp_${hex(crypto.getRandomValues(new Uint8Array(32)))}`;
  const createdAt = new Date();
  const { error } = await client().from("connector_tokens").upsert(
    { user_id: userId, token_hash: await sha256Hex(token), created_at: createdAt.toISOString() },
    { onConflict: "user_id" },
  );
  if (error) throw new Error(notSetUp(error.code) ? SCHEMA_MESSAGE : error.message);
  return { token, createdAt };
}

/** Deletes the token, so the connector stops accepting it straight away. */
export async function revokeConnectorToken(userId: string): Promise<void> {
  const { error } = await client().from("connector_tokens").delete().eq("user_id", userId);
  if (error) throw new Error(notSetUp(error.code) ? SCHEMA_MESSAGE : error.message);
}
