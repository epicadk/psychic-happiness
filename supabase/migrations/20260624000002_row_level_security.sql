-- ============================================================================
-- Row-Level Security — the multi-tenancy guarantee
-- ============================================================================
-- Every user-owned row is gated by auth.uid(). With RLS on, "many solo users"
-- need no app-server logic to isolate data.
--
-- `foods` is intentionally global: readable by every authenticated user,
-- writable only via the service role (Edge Functions). The service role bypasses
-- RLS entirely, so we add NO insert/update policy here — that's deliberate.
-- ============================================================================

alter table profiles        enable row level security;
alter table workouts        enable row level security;
alter table exercise_sets   enable row level security;
alter table foods           enable row level security;
alter table food_entries    enable row level security;
alter table body_metrics    enable row level security;
alter table daily_summaries enable row level security;

-- ----------------------------------------------------------------------------
-- profiles — owner is the row's primary key (id == auth.uid())
-- ----------------------------------------------------------------------------
create policy "own profile" on profiles
  for all
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- ----------------------------------------------------------------------------
-- User-owned tables — flat auth.uid() = user_id check
-- ----------------------------------------------------------------------------
create policy "own rows" on workouts
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "own rows" on exercise_sets
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "own rows" on food_entries
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "own rows" on body_metrics
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- daily_summaries are written by triggers (which run as the table owner and
-- bypass RLS), but users still read their own rows directly for the dashboard.
create policy "own rows" on daily_summaries
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- foods — shared global cache: read for everyone authenticated, no client writes
-- ----------------------------------------------------------------------------
create policy "read all foods" on foods
  for select
  to authenticated
  using (true);

-- No insert/update/delete policy on purpose: only the service role (Edge
-- Functions) writes to foods, and the service role bypasses RLS.
