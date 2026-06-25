-- ============================================================================
-- Initial schema — Fitness & Nutrition Tracker
-- ============================================================================
-- The load-bearing pattern: every synced record carries a `source` and an
-- `external_id`, with a uniqueness constraint that makes re-imports idempotent.
-- ============================================================================

-- gen_random_uuid() lives in pgcrypto; present by default on Supabase, but be
-- explicit so the migration is portable.
create extension if not exists pgcrypto;

-- ----------------------------------------------------------------------------
-- profiles — one row per user, extends auth.users
-- ----------------------------------------------------------------------------
create table profiles (
  id                  uuid primary key references auth.users(id) on delete cascade,
  display_name        text,
  unit_system         text not null default 'metric'
                        check (unit_system in ('metric', 'imperial')),
  sex                 text check (sex in ('male', 'female', 'other')),
  birth_date          date,
  height_cm           numeric,
  goal                text,                 -- 'lose_fat','gain_muscle','maintain'
  daily_calorie_target int,
  protein_target_g    int,
  carb_target_g       int,
  fat_target_g        int,
  created_at          timestamptz not null default now()
);

comment on table profiles is 'Per-user profile, 1:1 with auth.users.';

-- ----------------------------------------------------------------------------
-- workouts
-- ----------------------------------------------------------------------------
create table workouts (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  type         text not null,               -- 'strength','run','cycle',...
  started_at   timestamptz not null,
  ended_at     timestamptz,
  calories     int,
  notes        text,
  source       text not null default 'manual'
                check (source in ('manual', 'healthkit', 'health_connect', 'strava')),
  external_id  text,                         -- null for manual entries
  created_at   timestamptz not null default now(),
  -- Re-importing the same activity is a no-op.
  unique (user_id, source, external_id)
);

create index workouts_user_started_idx on workouts (user_id, started_at desc);

comment on table workouts is
  'Workouts from manual entry or synced sources. Unique (user_id, source, external_id) makes re-imports idempotent.';

-- ----------------------------------------------------------------------------
-- exercise_sets
-- ----------------------------------------------------------------------------
create table exercise_sets (
  id          uuid primary key default gen_random_uuid(),
  workout_id  uuid not null references workouts(id) on delete cascade,
  -- Denormalized so the RLS policy is a flat auth.uid() = user_id check
  -- rather than a subquery into workouts.
  user_id     uuid not null references auth.users(id) on delete cascade,
  exercise    text not null,
  set_index   int not null,
  reps        int,
  weight_kg   numeric,
  rpe         numeric,
  unique (workout_id, set_index, exercise)
);

create index exercise_sets_workout_idx on exercise_sets (workout_id);

comment on column exercise_sets.user_id is
  'Denormalized from workouts so RLS stays a flat auth.uid() = user_id check.';

-- ----------------------------------------------------------------------------
-- foods — shared global cache, populated from barcode/search/AI lookups
-- ----------------------------------------------------------------------------
create table foods (
  id           uuid primary key default gen_random_uuid(),
  barcode      text,
  name         text not null,
  brand        text,
  serving_qty  numeric,                      -- e.g. 100
  serving_unit text,                          -- 'g','ml','serving'
  calories     numeric,                       -- per the serving above
  protein_g    numeric,
  carbs_g      numeric,
  fat_g        numeric,
  source       text not null
                check (source in ('off', 'nutritionix', 'usda', 'ai_estimate', 'user')),
  external_id  text,
  created_at   timestamptz not null default now(),
  -- Idempotent caching: same item from same source resolves to the same row.
  -- A full constraint (not a partial index) so PostgREST `ON CONFLICT` inference
  -- works for upserts. NULLs are distinct, so ai_estimate rows (no external_id)
  -- still insert freely instead of colliding.
  unique (source, external_id)
);

create index foods_barcode_idx on foods (barcode);

comment on table foods is
  'Global shared cache of food items. Readable by all authenticated users; writable only via service role (Edge Functions).';

-- ----------------------------------------------------------------------------
-- food_entries
-- ----------------------------------------------------------------------------
create table food_entries (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  food_id     uuid references foods(id),
  logged_at   timestamptz not null,
  meal        text check (meal in ('breakfast', 'lunch', 'dinner', 'snack')),
  quantity    numeric not null default 1,    -- multiples of the food's serving
  source      text not null default 'manual'
                check (source in ('manual', 'barcode', 'ai_photo')),
  external_id text,
  created_at  timestamptz not null default now()
);

create index food_entries_user_logged_idx on food_entries (user_id, logged_at desc);

-- ----------------------------------------------------------------------------
-- body_metrics
-- ----------------------------------------------------------------------------
create table body_metrics (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  measured_at timestamptz not null,
  type        text not null,                 -- 'weight','body_fat','resting_hr',...
  value       numeric not null,
  source      text not null default 'manual',
  external_id text,
  unique (user_id, type, source, external_id)
);

create index body_metrics_user_type_measured_idx
  on body_metrics (user_id, type, measured_at desc);

-- ----------------------------------------------------------------------------
-- daily_summaries — pre-aggregated rollups for a fast dashboard
-- ----------------------------------------------------------------------------
create table daily_summaries (
  user_id      uuid not null references auth.users(id) on delete cascade,
  day          date not null,
  calories_in  int,
  calories_out int,
  protein_g    int,
  carbs_g      int,
  fat_g        int,
  updated_at   timestamptz not null default now(),
  primary key (user_id, day)
);

comment on table daily_summaries is
  'Pre-aggregated daily rollups. Kept fresh by triggers on workouts/food_entries.';
