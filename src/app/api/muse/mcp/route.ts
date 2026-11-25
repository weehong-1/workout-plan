// MCP endpoint for Muse AI's custom connector (Streamable HTTP, stateless).
//
// Vercel environment variables:
//   MUSE_MCP_TOKEN    the Bearer token Muse sends (32+ random characters)
//   WORKOUT_EMAIL     the account the tools act as
//   WORKOUT_PASSWORD  that account's password (set on the app's Account page)
//   WORKOUT_TIMEZONE  optional, for "today"; defaults to Asia/Singapore

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { createClient } from "@supabase/supabase-js";
import { authorized } from "@/lib/mcp/auth";
import { type Account, createTools, registerTools } from "@/lib/mcp/tools";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// Reused while the function stays warm, and signed in again shortly before it expires.
let cached: { account: Account; expiresAt: number } | null = null;

async function account(): Promise<Account> {
  if (cached && cached.expiresAt - Date.now() > 60_000) return cached.account;
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  const email = process.env.WORKOUT_EMAIL;
  const password = process.env.WORKOUT_PASSWORD;
  if (!url || !key || !email || !password) {
    throw new Error("The connector is not set up: WORKOUT_EMAIL and WORKOUT_PASSWORD must be set in Vercel.");
  }
  const client = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await client.auth.signInWithPassword({ email, password });
  if (error || !data.session) throw new Error(`Sign-in failed: ${error?.message ?? "no session"}`);
  cached = { account: { client, userId: data.user.id }, expiresAt: (data.session.expires_at ?? 0) * 1000 };
  return cached.account;
}

async function handle(request: Request): Promise<Response> {
  if (!authorized(request.headers.get("authorization"), process.env.MUSE_MCP_TOKEN)) {
    return Response.json(
      { jsonrpc: "2.0", error: { code: -32001, message: "Unauthorized" }, id: null },
      { status: 401, headers: { "WWW-Authenticate": 'Bearer realm="workout-plan"' } },
    );
  }
  const server = new McpServer({ name: "workout-plan", version: "1.0.0" });
  registerTools(server, createTools(account, process.env.WORKOUT_TIMEZONE || "Asia/Singapore"));
  const transport = new WebStandardStreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
  await server.connect(transport);
  return transport.handleRequest(request);
}

export { handle as GET, handle as POST, handle as DELETE };
