-- ============================================================================
-- daily_summaries freshness — recompute on write via triggers
-- ============================================================================
-- Per the spec: "Start with triggers — simpler, and the write volume per user
-- is tiny." A single recompute function rebuilds a (user, day) rollup from
-- source rows; thin trigger functions call it for the affected day(s).
--
-- Functions are SECURITY DEFINER so they can write daily_summaries regardless
-- of the caller's RLS context. They only ever touch the row keyed by the
-- triggering row's user_id, so this does not widen a user's reach.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Recompute one (user, day) rollup from food_entries + workouts.
-- ----------------------------------------------------------------------------
create or replace function recompute_daily_summary(p_user_id uuid, p_day date)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_calories_in  int;
  v_protein_g    int;
  v_carbs_g      int;
  v_fat_g        int;
  v_calories_out int;
begin
  -- Nutrition in: food_entries.quantity * the food's per-serving macros.
  select
    coalesce(round(sum(fe.quantity * f.calories)),  0),
    coalesce(round(sum(fe.quantity * f.protein_g)), 0),
    coalesce(round(sum(fe.quantity * f.carbs_g)),   0),
    coalesce(round(sum(fe.quantity * f.fat_g)),     0)
  into v_calories_in, v_protein_g, v_carbs_g, v_fat_g
  from food_entries fe
  join foods f on f.id = fe.food_id
  where fe.user_id = p_user_id
    and (fe.logged_at at time zone 'UTC')::date = p_day;

  -- Energy out: workout calories for the day.
  select coalesce(sum(w.calories), 0)
  into v_calories_out
  from workouts w
  where w.user_id = p_user_id
    and (w.started_at at time zone 'UTC')::date = p_day;

  -- If there is nothing left for the day, drop the rollup rather than leaving
  -- a stale zero row.
  if v_calories_in = 0 and v_calories_out = 0
     and v_protein_g = 0 and v_carbs_g = 0 and v_fat_g = 0
     and not exists (
       select 1 from food_entries fe
       where fe.user_id = p_user_id
         and (fe.logged_at at time zone 'UTC')::date = p_day
     )
     and not exists (
       select 1 from workouts w
       where w.user_id = p_user_id
         and (w.started_at at time zone 'UTC')::date = p_day
     )
  then
    delete from daily_summaries where user_id = p_user_id and day = p_day;
    return;
  end if;

  insert into daily_summaries
    (user_id, day, calories_in, calories_out, protein_g, carbs_g, fat_g, updated_at)
  values
    (p_user_id, p_day, v_calories_in, v_calories_out, v_protein_g, v_carbs_g, v_fat_g, now())
  on conflict (user_id, day) do update set
    calories_in  = excluded.calories_in,
    calories_out = excluded.calories_out,
    protein_g    = excluded.protein_g,
    carbs_g      = excluded.carbs_g,
    fat_g        = excluded.fat_g,
    updated_at   = now();
end;
$$;

-- ----------------------------------------------------------------------------
-- food_entries → recompute affected day(s)
-- ----------------------------------------------------------------------------
create or replace function tg_food_entries_recompute()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op in ('INSERT', 'UPDATE') then
    perform recompute_daily_summary(new.user_id, (new.logged_at at time zone 'UTC')::date);
  end if;
  -- On UPDATE that moves the entry to another day/user, also fix the old day.
  -- On DELETE, fix the day the entry used to belong to.
  if tg_op in ('UPDATE', 'DELETE') then
    if old.user_id <> new.user_id
       or (old.logged_at at time zone 'UTC')::date
            <> (new.logged_at at time zone 'UTC')::date
       or tg_op = 'DELETE'
    then
      perform recompute_daily_summary(old.user_id, (old.logged_at at time zone 'UTC')::date);
    end if;
  end if;
  return null;
end;
$$;

create trigger food_entries_recompute
  after insert or update or delete on food_entries
  for each row execute function tg_food_entries_recompute();

-- ----------------------------------------------------------------------------
-- workouts → recompute affected day(s)
-- ----------------------------------------------------------------------------
create or replace function tg_workouts_recompute()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op in ('INSERT', 'UPDATE') then
    perform recompute_daily_summary(new.user_id, (new.started_at at time zone 'UTC')::date);
  end if;
  if tg_op in ('UPDATE', 'DELETE') then
    if old.user_id <> new.user_id
       or (old.started_at at time zone 'UTC')::date
            <> (new.started_at at time zone 'UTC')::date
       or tg_op = 'DELETE'
    then
      perform recompute_daily_summary(old.user_id, (old.started_at at time zone 'UTC')::date);
    end if;
  end if;
  return null;
end;
$$;

create trigger workouts_recompute
  after insert or update or delete on workouts
  for each row execute function tg_workouts_recompute();

-- Note: editing a food_entry's macros indirectly (by changing the underlying
-- foods row) does not fire these triggers. The foods cache is treated as
-- immutable per (source, external_id); corrections create a new food row.
