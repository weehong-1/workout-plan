import { createHash, timingSafeEqual } from "node:crypto";

const digest = (value: string) => createHash("sha256").update(value).digest();

/** True when the request's "Authorization: Bearer …" header carries the expected token. */
export function authorized(header: string | null, expected: string | undefined): boolean {
  // A short or missing token would be guessable, so the endpoint stays closed.
  if (!expected || expected.length < 32) return false;
  const token = /^Bearer\s+(\S+)\s*$/i.exec(header ?? "")?.[1];
  // Comparing fixed-length digests keeps the check constant-time.
  return Boolean(token) && timingSafeEqual(digest(token!), digest(expected));
}
