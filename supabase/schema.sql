-- Run once in Supabase: SQL Editor → New query → paste this → Run.
-- Every row belongs to the signed in user, and row level security makes
-- sure nobody can read or change anyone else's rows.

create table if not exists public.log_entries (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null,
  date text not null,          -- YYYY/MM/DD
  day_id text not null,        -- a, b, c, d
  exercise text not null,
  substitute text,
  sets jsonb not null,         -- [{ "weight": "60", "reps": "10" }, ...]
  unit text not null default 'kg' check (unit in ('kg', 'lb')),
  notes text not null default '',
  saved_at bigint not null,    -- ms since epoch, groups entries into sessions
  created_at timestamptz not null default now(),
  primary key (user_id, id)
);

create table if not exists public.body_weights (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  date text not null,          -- YYYY/MM/DD, one per day
  weight double precision not null check (weight > 0),
  unit text not null check (unit in ('kg', 'lb')),
  updated_at timestamptz not null default now(),
  primary key (user_id, date)
);

create table if not exists public.settings (
  user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  units jsonb not null default '{}',   -- per exercise kg/lb choice
  default_unit text not null default 'kg' check (default_unit in ('kg', 'lb')),
  body_unit text not null default 'kg' check (body_unit in ('kg', 'lb')),
  updated_at timestamptz not null default now()
);

alter table public.log_entries enable row level security;
alter table public.body_weights enable row level security;
alter table public.settings enable row level security;

drop policy if exists "Own rows" on public.log_entries;
create policy "Own rows" on public.log_entries for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "Own rows" on public.body_weights;
create policy "Own rows" on public.body_weights for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "Own rows" on public.settings;
create policy "Own rows" on public.settings for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.log_entries, public.body_weights, public.settings to authenticated;
