import { createHash, timingSafeEqual } from "node:crypto";

const digest = (value: string) => createHash("sha256").update(value).digest();

/** The token from an "Authorization: Bearer …" header, or null. */
export function bearerToken(header: string | null): string | null {
  return /^Bearer\s+(\S+)\s*$/i.exec(header ?? "")?.[1] ?? null;
}

/** True when the request's "Authorization: Bearer …" header carries the expected token. */
export function authorized(header: string | null, expected: string | undefined): boolean {
  // A short or missing token would be guessable, so the endpoint stays closed.
  if (!expected || expected.length < 32) return false;
  const token = bearerToken(header);
  // Comparing fixed-length digests keeps the check constant-time.
  return Boolean(token) && timingSafeEqual(digest(token!), digest(expected));
}

/** True when the token hashes to the SHA-256 hex stored for the app-generated token. */
export function matchesHash(token: string | null, storedHash: string | null | undefined): boolean {
  if (!token || token.length < 32 || !storedHash || !/^[0-9a-f]{64}$/.test(storedHash)) return false;
  return timingSafeEqual(digest(token), Buffer.from(storedHash, "hex"));
}
