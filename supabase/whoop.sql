-- WHOOP connection for the Slopevo Android app. Run once in Supabase ›
-- SQL Editor. Safe to run again.
--
-- Only the Edge Functions `whoop` and `whoop-callback` read or write these
-- tables, with the service role on Supabase's own servers. The app and
-- website keys cannot read them: row level security is on and no policies
-- or grants are given to anon or authenticated. Deleting an account
-- deletes its rows (on delete cascade).

create table if not exists public.whoop_connections (
  user_id uuid primary key references auth.users (id) on delete cascade,
  access_token text not null,
  refresh_token text not null,
  expires_at timestamptz not null,
  connected_at timestamptz not null default now()
);
alter table public.whoop_connections enable row level security;
revoke all on public.whoop_connections from anon, authenticated;

-- One row per "Connect WHOOP" tap, until WHOOP sends the person back.
create table if not exists public.whoop_oauth_states (
  state text primary key check (char_length(state) >= 32),
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.whoop_oauth_states enable row level security;
revoke all on public.whoop_oauth_states from anon, authenticated;
