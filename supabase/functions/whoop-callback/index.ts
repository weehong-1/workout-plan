// Slopevo's WHOOP connection: one Supabase Edge Function, deployed with
// "Verify JWT" OFF because WHOOP's redirect carries no Supabase sign-in.
// Requests from the app are checked here instead (signedInUser).
//
//   GET  ?code=…&state=…   WHOOP sends the person back after they allow
//                          access. Swaps the code for tokens, stores them and
//                          redirects to vitrack://whoop-callback?result=…
//   POST {"action":"status"}        → {"connected": bool}
//   POST {"action":"connect"}       → {"url": WHOOP consent page}
//   POST {"action":"disconnect"}    → {"connected": false}
//   POST {"action":"data","start","end"}
//        → {"cycles":[…],"recoveries":[…],"sleeps":[…],"workouts":[…]}
//          WHOOP's own v2 records between start and end (ISO date-times).
//
// Secrets: WHOOP_CLIENT_ID and WHOOP_CLIENT_SECRET (Edge Functions ›
// Secrets). SUPABASE_URL and the server key (SUPABASE_SECRET_KEYS, or the
// legacy SUPABASE_SERVICE_ROLE_KEY) are provided by Supabase; the server key
// never leaves Supabase's servers.

const WHOOP_AUTH = "https://api.prod.whoop.com/oauth/oauth2/auth";
const WHOOP_TOKEN = "https://api.prod.whoop.com/oauth/oauth2/token";
const WHOOP_API = "https://api.prod.whoop.com/developer/v2";
const SCOPES = "offline read:cycles read:recovery read:sleep read:workout read:profile read:body_measurement";
const APP_RETURN = "vitrack://whoop-callback";
/** How long a "Connect WHOOP" tap stays valid. */
const STATE_MINUTES = 15;
/** Longest range one "data" call may ask for, to stay inside WHOOP's rate limits. */
const MAX_DAYS = 400;

function env(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing secret ${name}`);
  return value;
}

const redirectUri = () => `${env("SUPABASE_URL")}/functions/v1/whoop-callback`;

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const backToApp = (result: string) =>
  new Response(null, { status: 302, headers: { Location: `${APP_RETURN}?result=${result}` } });

/**
 * The project's server key: the new secret key (sb_secret_…) when Supabase
 * provides one, else the legacy service_role key.
 */
function serverKey(): string {
  try {
    const secret = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}").default;
    if (typeof secret === "string" && secret) return secret;
  } catch {
    // Not set or not JSON: use the legacy key.
  }
  return env("SUPABASE_SERVICE_ROLE_KEY");
}

/** PostgREST with the server key. New keys go in apikey only; legacy ones are JWTs. */
async function db(method: string, path: string, body?: unknown, prefer?: string): Promise<any> {
  const key = serverKey();
  const res = await fetch(`${env("SUPABASE_URL")}/rest/v1/${path}`, {
    method,
    headers: {
      apikey: key,
      ...(key.startsWith("sb_") ? {} : { Authorization: `Bearer ${key}` }),
      "Content-Type": "application/json",
      ...(prefer ? { Prefer: prefer } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Database ${res.status}: ${await res.text()}`);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/** The signed-in Supabase user who sent this request, or null. */
async function signedInUser(req: Request): Promise<string | null> {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const res = await fetch(`${env("SUPABASE_URL")}/auth/v1/user`, {
    headers: { apikey: serverKey(), Authorization: auth },
  });
  if (!res.ok) return null;
  const user = await res.json();
  return typeof user?.id === "string" ? user.id : null;
}

type Tokens = { access_token: string; refresh_token: string; expires_in: number };

class TokenError extends Error {
  constructor(readonly status: number, body: string) {
    super(`WHOOP token ${status}: ${body}`);
  }
}

/** WHOOP's token endpoint takes a form body with the client credentials in it. */
async function tokenRequest(params: Record<string, string>): Promise<Tokens> {
  const res = await fetch(WHOOP_TOKEN, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      ...params,
      client_id: env("WHOOP_CLIENT_ID"),
      client_secret: env("WHOOP_CLIENT_SECRET"),
    }),
  });
  if (!res.ok) throw new TokenError(res.status, await res.text());
  return await res.json();
}

async function saveTokens(userId: string, tokens: Tokens) {
  await db("POST", "whoop_connections?on_conflict=user_id", {
    user_id: userId,
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    expires_at: new Date(Date.now() + tokens.expires_in * 1000).toISOString(),
  }, "resolution=merge-duplicates,return=minimal");
}

const forget = (userId: string) => db("DELETE", `whoop_connections?user_id=eq.${userId}`);

/**
 * A working WHOOP access token, refreshed first when it ends within two
 * minutes. Null when not connected, or when WHOOP refused the refresh (the
 * person revoked access): the stored connection is then removed.
 */
async function accessToken(userId: string): Promise<string | null> {
  const rows = await db("GET", `whoop_connections?user_id=eq.${userId}&select=access_token,refresh_token,expires_at`);
  const row = rows?.[0];
  if (!row) return null;
  if (new Date(row.expires_at).getTime() - Date.now() > 120_000) return row.access_token;
  try {
    const tokens = await tokenRequest({ grant_type: "refresh_token", refresh_token: row.refresh_token, scope: "offline" });
    await saveTokens(userId, tokens);
    return tokens.access_token;
  } catch (e) {
    if (e instanceof TokenError && e.status >= 400 && e.status < 500) {
      await forget(userId);
      return null;
    }
    throw e;
  }
}

/** Every record of one WHOOP collection between start and end. */
async function collection(token: string, path: string, start: string, end: string): Promise<unknown[]> {
  const records: unknown[] = [];
  let next: string | undefined;
  do {
    const query = new URLSearchParams({ start, end, limit: "25", ...(next ? { nextToken: next } : {}) });
    const res = await fetch(`${WHOOP_API}/${path}?${query}`, { headers: { Authorization: `Bearer ${token}` } });
    if (!res.ok) throw new Error(`WHOOP ${path} ${res.status}: ${await res.text()}`);
    const page = await res.json();
    records.push(...(page.records ?? []));
    next = page.next_token || undefined;
  } while (next);
  return records;
}

async function callback(url: URL): Promise<Response> {
  const state = url.searchParams.get("state") ?? "";
  const code = url.searchParams.get("code");
  if (!/^[0-9a-f]{64}$/.test(state)) return backToApp("failed");
  // One use only: the row is deleted as it is read.
  const rows = await db("DELETE", `whoop_oauth_states?state=eq.${state}`, undefined, "return=representation");
  const row = rows?.[0];
  if (!row || Date.now() - new Date(row.created_at).getTime() > STATE_MINUTES * 60_000) return backToApp("expired");
  if (!code) return backToApp(url.searchParams.get("error") === "access_denied" ? "denied" : "failed");
  await saveTokens(row.user_id, await tokenRequest({
    grant_type: "authorization_code",
    code,
    redirect_uri: redirectUri(),
  }));
  return backToApp("connected");
}

async function action(req: Request): Promise<Response> {
  const userId = await signedInUser(req);
  if (!userId) return json({ error: "not_signed_in" }, 401);
  const body = await req.json().catch(() => ({}));

  switch (body?.action) {
    case "status": {
      const rows = await db("GET", `whoop_connections?user_id=eq.${userId}&select=user_id`);
      return json({ connected: rows.length > 0 });
    }
    case "connect": {
      const bytes = crypto.getRandomValues(new Uint8Array(32));
      const state = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
      await db("DELETE", `whoop_oauth_states?created_at=lt.${new Date(Date.now() - STATE_MINUTES * 60_000).toISOString()}`);
      await db("POST", "whoop_oauth_states", { state, user_id: userId }, "return=minimal");
      const query = new URLSearchParams({
        response_type: "code",
        client_id: env("WHOOP_CLIENT_ID"),
        redirect_uri: redirectUri(),
        scope: SCOPES,
        state,
      });
      return json({ url: `${WHOOP_AUTH}?${query}` });
    }
    case "disconnect": {
      const token = await accessToken(userId).catch(() => null);
      if (token) {
        // Tell WHOOP too; the stored tokens go whether or not this succeeds.
        await fetch(`${WHOOP_API}/user/access`, { method: "DELETE", headers: { Authorization: `Bearer ${token}` } }).catch(() => {});
      }
      await forget(userId);
      return json({ connected: false });
    }
    case "data": {
      const start = new Date(body.start), end = new Date(body.end);
      if (isNaN(+start) || isNaN(+end) || end <= start || +end - +start > MAX_DAYS * 86_400_000) {
        return json({ error: "bad_range" }, 400);
      }
      const token = await accessToken(userId);
      if (!token) return json({ error: "not_connected" }, 409);
      const [s, e] = [start.toISOString(), end.toISOString()];
      // One after another: a single token, and gentle on the rate limit.
      const cycles = await collection(token, "cycle", s, e);
      const recoveries = await collection(token, "recovery", s, e);
      const sleeps = await collection(token, "activity/sleep", s, e);
      const workouts = await collection(token, "activity/workout", s, e);
      return json({ cycles, recoveries, sleeps, workouts });
    }
    default:
      return json({ error: "unknown_action" }, 400);
  }
}

Deno.serve(async (req) => {
  try {
    if (req.method === "GET") return await callback(new URL(req.url));
    if (req.method === "POST") return await action(req);
    return json({ error: "method_not_allowed" }, 405);
  } catch (e) {
    console.error(e);
    return req.method === "GET" ? backToApp("failed") : json({ error: "server_error" }, 500);
  }
});
