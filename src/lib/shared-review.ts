// Trainer links from the iOS app (docs/ios spec section 7). The app uploads a
// frozen review; this reads it by token, without an account, on the server.
// Only the public Supabase key is used: the database function returns a link
// only for its token, and storage lets anyone sign URLs only for the photos of
// links that still work (supabase/schema.sql).

import { createClient } from "@supabase/supabase-js";

export type SharedReview = {
  version: number;
  period: { kind: "month" | "year"; label: string; start: string; end: string; days: number };
  /** Last day the link works, as a calendar day; null for no expiry. */
  expiresOn: string | null;
  activity: {
    averageSteps: number | null;
    averageSleepMinutes: number | null;
    averageMoveKcal: number | null;
    ringsClosedDays: number;
    daily: { date: string; steps: number; moveKcal: number; sleepMinutes: number | null; ringsClosed: boolean }[];
  } | null;
  weight: {
    unit: "kg" | "lb";
    count: number;
    first: { date: string; value: number };
    last: { date: string; value: number };
    change: number;
    entries: { date: string; value: number }[];
    firstPhoto: { path: string; date: string } | null;
    lastPhoto: { path: string; date: string } | null;
  } | null;
  food: {
    daysWithPhotos: number;
    photos: { path: string; date: string; time: string; label: string | null }[];
  } | null;
};

export type SharedLink = {
  review: SharedReview;
  expiresAt: string | null;
  createdAt: string;
  /** Signed photo URLs by storage path, valid for `PHOTO_URL_SECONDS`. */
  photoUrls: Record<string, string>;
};

export const PHOTO_URL_SECONDS = 15 * 60;

/** Tokens are 43 base64url characters; anything else is not a link. */
export function isWellFormedToken(token: string): boolean {
  return /^[A-Za-z0-9_-]{32,128}$/.test(token);
}

export function photoPaths(review: SharedReview): string[] {
  const paths = [
    review.weight?.firstPhoto?.path,
    review.weight?.lastPhoto?.path,
    ...(review.food?.photos.map((photo) => photo.path) ?? []),
  ];
  return [...new Set(paths.filter((path): path is string => Boolean(path)))];
}

/** The link's review, or null when the token is unknown, revoked or expired. */
export async function loadSharedLink(token: string): Promise<SharedLink | null> {
  if (!isWellFormedToken(token)) return null;
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !key) throw new Error("Supabase is not configured on this site.");

  const client = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await client.rpc("get_shared_review", { link_token: token });
  if (error) throw new Error(error.message);
  if (!data) return null;

  const row = data as { summary: SharedReview; expires_at: string | null; created_at: string };
  const review = row.summary;
  const photoUrls: Record<string, string> = {};
  const paths = photoPaths(review);
  if (paths.length) {
    const { data: signed } = await client.storage.from("photos").createSignedUrls(paths, PHOTO_URL_SECONDS);
    for (const item of signed ?? []) {
      if (item.path && item.signedUrl) photoUrls[item.path] = item.signedUrl;
    }
  }
  return { review, expiresAt: row.expires_at, createdAt: row.created_at, photoUrls };
}
