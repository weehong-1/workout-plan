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

-- Safe to run on existing installations: preserve all stored account data.
alter table public.settings add column if not exists rest_timer jsonb;
alter table public.settings add column if not exists keep_screen_on boolean not null default false;
-- Database-backed exercise snapshots and prescriptions; keep legacy references intact.
alter table public.settings add column if not exists day_exercises jsonb not null default '{}'::jsonb;

create table if not exists public.workout_drafts (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day_id text not null,
  draft jsonb not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, day_id)
);
alter table public.workout_drafts enable row level security;
drop policy if exists "Own rows" on public.workout_drafts;
create policy "Own rows" on public.workout_drafts for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Additive, idempotent upgrade: existing workout/account rows are untouched.
-- Validate the complete Block/Exercise shape even for direct table writes.
create or replace function public.valid_training_plan_blocks(blocks jsonb)
returns boolean language plpgsql immutable set search_path = '' as $$
declare
  block jsonb;
  exercise jsonb;
  field text;
begin
  if jsonb_typeof(blocks) is distinct from 'array' then return false; end if;
  for block in select value from jsonb_array_elements(blocks) loop
    if jsonb_typeof(block) is distinct from 'object'
      or jsonb_typeof(block->'format') is distinct from 'string'
      or jsonb_typeof(block->'exercises') is distinct from 'array' then return false; end if;
    for exercise in select value from jsonb_array_elements(block->'exercises') loop
      if jsonb_typeof(exercise) is distinct from 'object' then return false; end if;
      foreach field in array array['order', 'name', 'muscles', 'warmUp', 'reps', 'rest', 'cues', 'video', 'substitute', 'substituteVideo'] loop
        if jsonb_typeof(exercise->field) is distinct from 'string' then return false; end if;
      end loop;
      if jsonb_typeof(exercise->'sets') is distinct from 'number' then return false; end if;
      if (exercise->>'sets')::numeric < 1
        or trunc((exercise->>'sets')::numeric) <> (exercise->>'sets')::numeric then return false; end if;
    end loop;
  end loop;
  return true;
end;
$$;

create table if not exists public.training_plans (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id text not null check (id ~ '^plan-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
  name text not null check (char_length(btrim(name)) between 1 and 80),
  description text not null default '' check (char_length(btrim(description)) <= 500),
  blocks jsonb not null default '[]'::jsonb check (public.valid_training_plan_blocks(blocks)),
  created_at bigint not null check (created_at between 0 and 9007199254740991), -- milliseconds
  primary key (user_id, id)
);
alter table public.training_plans enable row level security;
drop policy if exists "Own rows" on public.training_plans;
create policy "Own rows" on public.training_plans for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.log_entries, public.body_weights, public.settings, public.workout_drafts, public.training_plans to authenticated;

-- Photos: rows store only paths of files in the private "photos" storage
-- bucket (bottom of this file). A path must sit in the row owner's folder.
create or replace function public.valid_photo_paths(photos jsonb, owner uuid)
returns boolean language plpgsql immutable set search_path = '' as $$
declare
  path jsonb;
begin
  if jsonb_typeof(photos) is distinct from 'array' or jsonb_array_length(photos) > 6 then return false; end if;
  for path in select value from jsonb_array_elements(photos) loop
    if jsonb_typeof(path) is distinct from 'string'
      or (path #>> '{}') !~ ('^' || owner::text || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$') then
      return false;
    end if;
  end loop;
  return true;
end;
$$;
alter table public.log_entries add column if not exists photos jsonb not null default '[]'::jsonb;
alter table public.body_weights add column if not exists photos jsonb not null default '[]'::jsonb;
alter table public.log_entries drop constraint if exists log_entries_photos_valid;
alter table public.log_entries add constraint log_entries_photos_valid check (public.valid_photo_paths(photos, user_id));
alter table public.body_weights drop constraint if exists body_weights_photos_valid;
alter table public.body_weights add constraint body_weights_photos_valid check (public.valid_photo_paths(photos, user_id));

-- One row per finished workout: the Save screen's notes, photos and duration.
create table if not exists public.workout_sessions (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day_id text not null,
  saved_at bigint not null,    -- matches log_entries.saved_at of the same workout
  date text not null,          -- YYYY/MM/DD
  notes text not null default '' check (char_length(notes) <= 2000),
  photos jsonb not null default '[]'::jsonb,
  duration_min integer check (duration_min between 0 and 1440),
  primary key (user_id, day_id, saved_at),
  constraint workout_sessions_photos_valid check (public.valid_photo_paths(photos, user_id))
);
alter table public.workout_sessions enable row level security;
drop policy if exists "Own rows" on public.workout_sessions;
create policy "Own rows" on public.workout_sessions for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
grant select, insert, update, delete on public.workout_sessions to authenticated;

-- The account's own training plan (days, exercises, rotation, weekdays).
-- Null means the built-in restart plan. Exercises use the same shape as
-- training_plans blocks.
create or replace function public.valid_plan(plan jsonb)
returns boolean language plpgsql immutable set search_path = '' as $$
declare
  day jsonb;
  weekday jsonb;
  ids text[] := '{}';
begin
  if jsonb_typeof(plan) is distinct from 'object'
    or jsonb_typeof(plan->'days') is distinct from 'array'
    or jsonb_array_length(plan->'days') not between 1 and 12
    or jsonb_typeof(plan->'weekdays') is distinct from 'array'
    or jsonb_array_length(plan->'weekdays') > 7 then
    return false;
  end if;
  for weekday in select value from jsonb_array_elements(plan->'weekdays') loop
    if jsonb_typeof(weekday) is distinct from 'number' or (weekday #>> '{}') not in ('0', '1', '2', '3', '4', '5', '6') then
      return false;
    end if;
  end loop;
  for day in select value from jsonb_array_elements(plan->'days') loop
    if jsonb_typeof(day) is distinct from 'object'
      or jsonb_typeof(day->'id') is distinct from 'string' or (day->>'id') !~ '^[a-z0-9-]{1,48}$'
      or (day->>'id') = any(ids)
      or jsonb_typeof(day->'letter') is distinct from 'string' or char_length(day->>'letter') not between 1 and 2
      or jsonb_typeof(day->'name') is distinct from 'string' or char_length(btrim(day->>'name')) not between 1 and 40
      or jsonb_typeof(day->'optional') is distinct from 'boolean'
      or jsonb_typeof(day->'focus') is distinct from 'string' or char_length(day->>'focus') > 300
      or jsonb_typeof(day->'duration') is distinct from 'string' or char_length(day->>'duration') > 40
      or not public.valid_training_plan_blocks(day->'blocks')
      or (select count(*) from jsonb_array_elements(day->'blocks') as b, jsonb_array_elements(b.value->'exercises')) > 30 then
      return false;
    end if;
    ids := ids || (day->>'id');
  end loop;
  return true;
end;
$$;
alter table public.settings add column if not exists plan jsonb;
alter table public.settings drop constraint if exists settings_plan_valid;
alter table public.settings add constraint settings_plan_valid check (plan is null or public.valid_plan(plan));

-- Keep a transaction receipt so retries after a lost response cannot replay an
-- old draft or delete a newer draft. Receipt and edit commit/roll back together.
create table if not exists public.workout_mutations (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id uuid not null,
  created_at timestamptz not null default now(),
  primary key (user_id, id)
);
alter table public.workout_mutations enable row level security;
drop policy if exists "Own receipts" on public.workout_mutations;
create policy "Own receipts" on public.workout_mutations for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
grant select, insert on public.workout_mutations to authenticated;

-- Each edit is one transaction. In particular, finishing inserts all log rows
-- and removes the draft atomically. SECURITY INVOKER preserves RLS checks.
create or replace function public.apply_workout_change(account_id uuid, change_type text, payload jsonb)
returns void language plpgsql security invoker set search_path = '' as $$
declare
  training_plan jsonb;
  plan_name text;
  plan_description text;
  added_exercise jsonb;
  added_exercises jsonb;
  added_name text;
  added_item jsonb;
  added_instruction_chars integer;
  added_identity text;
  added_keys text[];
  added_ids text[];
begin
  if auth.uid() is null or auth.uid() <> account_id then
    raise exception 'Not authorized' using errcode = '42501';
  end if;
  if payload->>'_mutationId' is null then
    raise exception 'Missing mutation ID';
  end if;
  insert into public.workout_mutations (user_id, id)
  values (account_id, (payload->>'_mutationId')::uuid)
  on conflict (user_id, id) do nothing;
  if not found then return; end if;
  case change_type
    when 'trainingPlan' then
      training_plan := payload->'plan';
      if jsonb_typeof(training_plan) is distinct from 'object'
        or jsonb_typeof(training_plan->'id') is distinct from 'string'
        or (training_plan->>'id') !~ '^plan-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        or jsonb_typeof(training_plan->'name') is distinct from 'string'
        or jsonb_typeof(training_plan->'description') is distinct from 'string' then
        raise exception 'Invalid training plan metadata';
      end if;
      -- Match JavaScript trim() for spaces, tabs, newlines and Unicode whitespace.
      plan_name := regexp_replace(training_plan->>'name', '^[[:space:]﻿ ]+|[[:space:]﻿ ]+$', '', 'g');
      plan_description := regexp_replace(training_plan->>'description', '^[[:space:]﻿ ]+|[[:space:]﻿ ]+$', '', 'g');
      if char_length(plan_name) not between 1 and 80 or char_length(plan_description) > 500 then
        raise exception 'Invalid training plan name or description';
      end if;
      if not public.valid_training_plan_blocks(training_plan->'blocks') then
        raise exception 'Invalid training plan blocks';
      end if;
      if jsonb_typeof(training_plan->'createdAt') is distinct from 'number' then
        raise exception 'Invalid training plan creation time';
      end if;
      if (training_plan->>'createdAt')::numeric not between 0 and 9007199254740991
        or trunc((training_plan->>'createdAt')::numeric) <> (training_plan->>'createdAt')::numeric then
        raise exception 'Invalid training plan creation time';
      end if;
      insert into public.training_plans (user_id, id, name, description, blocks, created_at)
      values (account_id, training_plan->>'id', plan_name, plan_description,
        training_plan->'blocks', (training_plan->>'createdAt')::bigint)
      on conflict (user_id, id) do update set name = excluded.name, description = excluded.description;
    when 'dayExercises' then
      if jsonb_typeof(payload->'dayId') is distinct from 'string'
        or (payload->>'dayId') !~ '^[a-d]$'
        or jsonb_typeof(payload->'exercises') is distinct from 'array' then
        raise exception 'Invalid added exercise day or list';
      end if;
      if jsonb_array_length(payload->'exercises') > 30 then
        raise exception 'Too many added exercises';
      end if;
      added_keys := array[]::text[];
      added_ids := array[]::text[];
      added_exercises := '[]'::jsonb;
      for added_exercise in select value from jsonb_array_elements(payload->'exercises') loop
        if jsonb_typeof(added_exercise) is distinct from 'object'
          or jsonb_typeof(added_exercise->'key') is distinct from 'string'
          or jsonb_typeof(added_exercise->'sets') is distinct from 'number'
          or jsonb_typeof(added_exercise->'reps') is distinct from 'string'
          or (added_exercise->>'reps') !~ '^([1-9][0-9]?|100)$'
          or jsonb_typeof(added_exercise->'restSeconds') is distinct from 'number' then
          raise exception 'Invalid added exercise shape';
        end if;
        if (added_exercise->>'sets')::numeric not between 1 and 10
          or trunc((added_exercise->>'sets')::numeric) <> (added_exercise->>'sets')::numeric
          or (added_exercise->>'restSeconds')::numeric not between 0 and 600
          or trunc((added_exercise->>'restSeconds')::numeric) <> (added_exercise->>'restSeconds')::numeric then
          raise exception 'Invalid added exercise prescription';
        end if;
        if (added_exercise->>'key') ~ '^mw-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
          -- Existing MuscleWiki references remain readable/removable without fetching metadata.
          if (added_exercise - array['key', 'musclewikiId', 'sets', 'reps', 'restSeconds']) <> '{}'::jsonb
            or jsonb_typeof(added_exercise->'musclewikiId') is distinct from 'number' then
            raise exception 'Invalid legacy added exercise reference';
          end if;
          if (added_exercise->>'musclewikiId')::numeric not between 0 and 2147483647
            or trunc((added_exercise->>'musclewikiId')::numeric) <> (added_exercise->>'musclewikiId')::numeric then
            raise exception 'Invalid legacy added exercise reference';
          end if;
          added_identity := 'musclewiki:' || (added_exercise->>'musclewikiId')::bigint::text;
        else
          -- Persist the bounded snapshot; no remote provider lookup or new table is needed.
          if (added_exercise - array['key', 'source', 'exerciseId', 'name', 'equipment', 'primaryMuscles', 'instructions', 'sets', 'reps', 'restSeconds']) <> '{}'::jsonb
            or (added_exercise->>'key') !~ '^ex-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or jsonb_typeof(added_exercise->'source') is distinct from 'string'
            or (added_exercise->>'source') not in ('free-exercise-db', 'custom')
            or jsonb_typeof(added_exercise->'exerciseId') is distinct from 'string'
            or jsonb_typeof(added_exercise->'name') is distinct from 'string'
            or jsonb_typeof(added_exercise->'equipment') is distinct from 'string'
            or jsonb_typeof(added_exercise->'primaryMuscles') is distinct from 'array'
            or jsonb_typeof(added_exercise->'instructions') is distinct from 'array' then
            raise exception 'Invalid added exercise snapshot';
          end if;
          added_name := regexp_replace(added_exercise->>'name', '^[[:space:]﻿ ]+|[[:space:]﻿ ]+$', '', 'g');
          if char_length(added_name) not between 1 and 120
            or char_length(added_exercise->>'exerciseId') not between 1 and 160
            or char_length(regexp_replace(added_exercise->>'exerciseId', '^[[:space:]﻿ ]+|[[:space:]﻿ ]+$', '', 'g')) = 0
            or ((added_exercise->>'source') = 'custom' and (added_exercise->>'exerciseId') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
            or char_length(added_exercise->>'equipment') > 100
            or jsonb_array_length(added_exercise->'primaryMuscles') > 10
            or jsonb_array_length(added_exercise->'instructions') > 30 then
            raise exception 'Invalid added exercise snapshot bounds';
          end if;
          for added_item in select value from jsonb_array_elements(added_exercise->'primaryMuscles') loop
            if jsonb_typeof(added_item) is distinct from 'string' then
              raise exception 'Invalid added exercise muscle';
            end if;
            if char_length(added_item #>> '{}') not between 1 and 100
              or char_length(regexp_replace(added_item #>> '{}', '^[[:space:]﻿ ]+|[[:space:]﻿ ]+$', '', 'g')) = 0 then
              raise exception 'Invalid added exercise muscle';
            end if;
          end loop;
          added_instruction_chars := 0;
          for added_item in select value from jsonb_array_elements(added_exercise->'instructions') loop
            if jsonb_typeof(added_item) is distinct from 'string' then
              raise exception 'Invalid added exercise instruction';
            end if;
            added_instruction_chars := added_instruction_chars + char_length(added_item #>> '{}');
            if char_length(added_item #>> '{}') > 3000 or added_instruction_chars > 6000 then
              raise exception 'Invalid added exercise instruction bounds';
            end if;
          end loop;
          added_exercise := jsonb_set(added_exercise, '{name}', to_jsonb(added_name));
          added_identity := (added_exercise->>'source') || ':' || (added_exercise->>'exerciseId');
        end if;
        if (added_exercise->>'key') = any(added_keys) or added_identity = any(added_ids) then
          raise exception 'Duplicate added exercise key or source ID';
        end if;
        added_keys := array_append(added_keys, added_exercise->>'key');
        added_ids := array_append(added_ids, added_identity);
        added_exercises := added_exercises || jsonb_build_array(added_exercise);
      end loop;
      if payload ? 'removedKey' then
        if jsonb_typeof(payload->'removedKey') is distinct from 'string'
          or (payload->>'removedKey') !~ '^(ex|mw)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          or (payload->>'removedKey') = any(added_keys) then
          raise exception 'Invalid removed exercise key';
        end if;
      end if;
      insert into public.settings (user_id, day_exercises)
      values (account_id, jsonb_build_object(payload->>'dayId', added_exercises))
      on conflict (user_id) do update set
        day_exercises = public.settings.day_exercises || excluded.day_exercises, updated_at = now();
      if payload ? 'removedKey' then
        update public.workout_drafts
        set draft = draft #- array['exercises', payload->>'removedKey'], updated_at = now()
        where user_id = account_id and day_id = payload->>'dayId'
          and (draft->'exercises') ? (payload->>'removedKey');
      end if;
    when 'draft' then
      if payload->'draft' = 'null'::jsonb then
        delete from public.workout_drafts where user_id = account_id and day_id = payload->>'dayId';
      else
        insert into public.workout_drafts (user_id, day_id, draft)
        values (account_id, payload->>'dayId', payload->'draft')
        on conflict (user_id, day_id) do update set draft = excluded.draft, updated_at = now();
      end if;
    when 'finish' then
      insert into public.log_entries (user_id, id, date, day_id, exercise, substitute, sets, unit, notes, saved_at, photos)
      select account_id, e->>'id', e->>'date', e->>'dayId', e->>'exercise', e->>'substitute',
        e->'sets', coalesce(e->>'unit', 'kg'), coalesce(e->>'notes', ''), (e->>'savedAt')::bigint,
        coalesce(e->'photos', '[]'::jsonb)
      from jsonb_array_elements(payload->'entries') as e
      on conflict (user_id, id) do nothing;
      -- Session-level photo and notes from the Save screen (optional).
      if jsonb_typeof(payload->'session') = 'object' then
        insert into public.workout_sessions (user_id, day_id, saved_at, date, notes, photos, duration_min)
        values (account_id, payload->>'dayId', (payload->'session'->>'savedAt')::bigint, payload->'session'->>'date',
          coalesce(payload->'session'->>'notes', ''), coalesce(payload->'session'->'photos', '[]'::jsonb),
          (payload->'session'->>'durationMin')::integer)
        on conflict (user_id, day_id, saved_at) do nothing;
      end if;
      delete from public.workout_drafts where user_id = account_id and day_id = payload->>'dayId';
    when 'plan' then
      if not public.valid_plan(payload->'plan') then raise exception 'Invalid plan'; end if;
      insert into public.settings (user_id, plan) values (account_id, payload->'plan')
      on conflict (user_id) do update set plan = excluded.plan, updated_at = now();
    when 'weight' then
      insert into public.body_weights (user_id, date, weight, unit)
      values (account_id, payload->>'date', (payload->>'weight')::double precision, payload->>'unit')
      on conflict (user_id, date) do update set weight = excluded.weight, unit = excluded.unit, updated_at = now();
    when 'deleteWeight' then
      delete from public.body_weights where user_id = account_id and date = payload->>'date';
    when 'weightPhotos' then
      if not public.valid_photo_paths(payload->'photos', account_id) then raise exception 'Invalid photos'; end if;
      -- A no-op if the entry was deleted meanwhile, so a retry cannot get stuck.
      update public.body_weights set photos = payload->'photos', updated_at = now()
      where user_id = account_id and date = payload->>'date';
    when 'deleteSession' then
      delete from public.log_entries where user_id = account_id and date = payload->>'date'
        and day_id = payload->>'dayId' and saved_at = (payload->>'savedAt')::bigint;
      delete from public.workout_sessions where user_id = account_id
        and day_id = payload->>'dayId' and saved_at = (payload->>'savedAt')::bigint;
    when 'exerciseUnit' then
      if payload->>'unit' not in ('kg', 'lb') then raise exception 'Invalid unit'; end if;
      insert into public.settings (user_id, units)
      values (account_id, jsonb_build_object(payload->>'exercise', payload->>'unit'))
      on conflict (user_id) do update set
        units = public.settings.units || excluded.units, updated_at = now();
    when 'bodyUnit' then
      insert into public.settings (user_id, body_unit) values (account_id, payload->>'unit')
      on conflict (user_id) do update set body_unit = excluded.body_unit, updated_at = now();
    when 'timer' then
      insert into public.settings (user_id, rest_timer) values (account_id, nullif(payload->'timer', 'null'::jsonb))
      on conflict (user_id) do update set rest_timer = excluded.rest_timer, updated_at = now();
    when 'keepScreenOn' then
      insert into public.settings (user_id, keep_screen_on) values (account_id, (payload->>'enabled')::boolean)
      on conflict (user_id) do update set keep_screen_on = excluded.keep_screen_on, updated_at = now();
    else
      raise exception 'Unknown change type: %', change_type;
  end case;
end;
$$;
revoke all on function public.apply_workout_change(uuid, text, jsonb) from public, anon;
grant execute on function public.apply_workout_change(uuid, text, jsonb) to authenticated;

-- SECURITY DEFINER is scoped to the authenticated caller's ownership only:
-- the expected ID is a guard, never a deletion target, and no caller-supplied
-- identifiers or dynamic SQL are used. No client admin key is required.
create or replace function public.delete_own_account(expected_account_id uuid, confirmation text)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null or expected_account_id is distinct from auth.uid() then
    raise exception 'Not authorized' using errcode = '42501';
  end if;
  if confirmation is distinct from 'DELETE' then
    raise exception 'Type DELETE to confirm account deletion' using errcode = '22023';
  end if;

  -- Legacy settings installations may have a foreign key without ON DELETE
  -- CASCADE. Remove only the caller's settings explicitly; all other app
  -- tables cascade from auth.users. Any failure rolls back the whole call.
  delete from public.settings where user_id = auth.uid();
  delete from auth.users where id = auth.uid();
  if not found then
    raise exception 'Account not found' using errcode = 'P0002';
  end if;
end;
$$;
revoke all on function public.delete_own_account(uuid, text) from public, anon;
grant execute on function public.delete_own_account(uuid, text) to authenticated;

-- Private photo storage: one folder per account (<user id>/<file>.jpg). The
-- app resizes photos to JPEG before upload. Skipped where Supabase Storage is
-- not installed (local tests).
do $$
begin
  if to_regclass('storage.buckets') is null then return; end if;
  insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
  values ('photos', 'photos', false, 5242880, array['image/jpeg'])
  on conflict (id) do update set public = false,
    file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;
  execute 'drop policy if exists "Own photos" on storage.objects';
  execute $p$create policy "Own photos" on storage.objects for all to authenticated
    using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text)$p$;
end;
$$;

-- Muse AI connector token, made on the Account page. Only its SHA-256 hash is
-- kept; the token itself is shown once. The connector signs in as the account
-- and compares the Bearer token it receives against this hash.
create table if not exists public.connector_tokens (
  user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  token_hash text not null check (token_hash ~ '^[0-9a-f]{64}$'),
  created_at timestamptz not null default now()
);
alter table public.connector_tokens enable row level security;
drop policy if exists "Own rows" on public.connector_tokens;
create policy "Own rows" on public.connector_tokens for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
grant select, insert, update, delete on public.connector_tokens to authenticated;

-- Trainer links from the iOS app (docs/ios spec section 7). Each row is a
-- frozen review of one month or year that the app uploads after the user's
-- consent. The link holds a random token; only its SHA-256 hash is stored.
-- Photos in a link are copies in the owner's photo folder, named
-- "<user id>/share-<link id>-<photo id>.jpg", so account deletion removes
-- them with the rest. Revoking a link deletes its row and its photos.
create or replace function public.valid_share_photo_paths(paths text[], owner uuid)
returns boolean language sql immutable set search_path = '' as $$
  select coalesce(bool_and(
    p like owner::text || '/share-%.jpg' and p !~ '\.\.' and p !~ '/.*/'
  ), true)
  from unnest(paths) as p
$$;

create table if not exists public.share_links (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  token_hash text not null unique check (token_hash ~ '^[0-9a-f]{64}$'),
  trainer_name text not null default '' check (char_length(trainer_name) <= 80),
  period_kind text not null check (period_kind in ('month', 'year')),
  period_start date not null,
  period_label text not null check (char_length(period_label) <= 40),
  includes jsonb not null default '{}'::jsonb,
  summary jsonb not null check (jsonb_typeof(summary) = 'object' and pg_column_size(summary) < 2000000),
  photo_paths text[] not null default '{}' check (cardinality(photo_paths) <= 2000),
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  constraint share_links_photo_paths_owned check (public.valid_share_photo_paths(photo_paths, user_id))
);
create index if not exists share_links_user_idx on public.share_links (user_id, created_at desc);
alter table public.share_links enable row level security;
drop policy if exists "Own rows" on public.share_links;
create policy "Own rows" on public.share_links for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
-- Links are created and revoked, never edited.
revoke all on public.share_links from anon, authenticated;
grant select, insert, delete on public.share_links to authenticated;

-- The trainer page reads a link by its token, without signing in. Returns
-- null for an unknown, revoked (deleted) or expired link. The trainer name
-- is for the owner's list only and is not returned.
create or replace function public.get_shared_review(link_token text)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'period_kind', l.period_kind,
    'period_label', l.period_label,
    'includes', l.includes,
    'summary', l.summary,
    'expires_at', l.expires_at,
    'created_at', l.created_at
  )
  from public.share_links as l
  where char_length(coalesce(link_token, '')) between 32 and 128
    and l.token_hash = encode(sha256(convert_to(link_token, 'UTF8')), 'hex')
    and (l.expires_at is null or l.expires_at > now())
  limit 1
$$;
revoke all on function public.get_shared_review(text) from public;
grant execute on function public.get_shared_review(text) to anon, authenticated;

-- True while a photo belongs to a link that still works. Lets the trainer
-- page create short-lived signed URLs for exactly those photos.
create or replace function public.is_shared_photo(object_name text)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.share_links as l
    where object_name = any (l.photo_paths)
      and (l.expires_at is null or l.expires_at > now())
  )
$$;
revoke all on function public.is_shared_photo(text) from public;
grant execute on function public.is_shared_photo(text) to anon, authenticated;

do $$
begin
  if to_regclass('storage.objects') is null then return; end if;
  execute 'drop policy if exists "Shared photos" on storage.objects';
  execute $p$create policy "Shared photos" on storage.objects for select to anon
    using (bucket_id = 'photos' and public.is_shared_photo(name))$p$;
end;
$$;
