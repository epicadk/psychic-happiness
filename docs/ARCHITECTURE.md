# Architecture

## Shape

A multi-tenant mobile product for many solo users (no social or coaching layer in
v1). The differentiating wedge is **AI photo food logging** — the one feature with
no good competitor data source.

## Client — Kotlin Multiplatform + Compose Multiplatform

A shared KMP module holds domain models, the MVI reducers/stores, repositories, and
the Supabase data layer. Compose Multiplatform renders the UI for both platforms
from one codebase.

```
shared/
  domain/        pure models + repository contracts        (commonMain)
  data/          Supabase data layer + health bridge        (commonMain + actuals)
  presentation/  MVI core + feature stores                  (commonMain)
```

**MVI / UDF.** `Intent → reduce(State, Intent) → State`, with one-shot `Effect`s for
navigation/toasts. See `presentation/mvi/Mvi.kt` for the core and
`presentation/dashboard/DashboardStore.kt` for the pattern every feature follows.

**The native cost we sign up for.** KMP does *not* wrap the health APIs. We define
an `expect`/`actual` `HealthBridge` (`data/health/HealthBridge.kt`) and implement it
natively — Swift HealthKit on iOS, Kotlin Health Connect on Android. This is
isolated to a thin sync boundary; everything above it stays shared. It is budgeted
for Phase 2, not Phase 0.

**Compose MP on iOS** is production-usable but its iOS UI polish still trails
SwiftUI in places (scroll feel, native pickers, some accessibility). Plan for
SwiftUI interop on a handful of screens rather than 100% shared UI.

## Backend — Supabase

- **Postgres** — relational fits this data and its daily rollups cleanly.
- **Auth + Row-Level Security** — RLS *is* the multi-tenancy mechanism. Every
  user-owned row is gated by `auth.uid()`, so "many solo users" needs no
  app-server logic to isolate data.
- **Realtime** — drives cross-device sync for a user's own data (exposed as
  `Flow`s from the repositories).
- **Edge Functions (Deno/TS)** — host the Strava webhook receiver, the food-lookup
  resolver, and the AI food-logging endpoint, so model API keys live server-side
  and never ship in the app binary.

### The load-bearing data pattern

Every record carries a `source` and an `external_id`, with a uniqueness constraint
that makes re-imports idempotent: `unique (user_id, source, external_id)`. Pulling
the same HealthKit workout twice changes nothing.

`foods` is intentionally **global** — readable by all authenticated users, writable
only via Edge Functions (service role). That shares the cache across the whole user
base instead of re-fetching the same barcode for everyone. This is why
`exercise_sets` carries a denormalized `user_id`: it lets the RLS policy be a flat
`auth.uid() = user_id` check rather than a subquery into `workouts`.

### Keeping `daily_summaries` fresh

Recompute on write via triggers on `workouts`/`food_entries`
(`migrations/...daily_summaries_triggers.sql`). Triggers are simpler than a
scheduled job and the write volume per user is tiny. A single
`recompute_daily_summary(user, day)` rebuilds a rollup from source rows; thin
trigger functions call it for the affected day(s), including the *old* day when an
entry is moved or deleted.

## Food database (no single source covers everything)

| Need | Source | Notes |
|---|---|---|
| Barcode | Open Food Facts | free, global, ODbL — attribution required |
| Search | Nutritionix (or USDA FDC) | own terms; check before commercial launch |
| AI photo | Claude vision (Edge Function) | estimate only, user-confirmed |

Every resolved item is cached into the `foods` table so repeat lookups are instant
and offline-friendly.

## Sync & dedup

| Source | Mechanism | Trigger |
|---|---|---|
| Apple HealthKit | Query API (no push) | app open + background refresh |
| Health Connect | Changes API | app open + periodic worker |
| Strava | Webhook | push → Edge Function on new activity |

**Two dedup layers:**

1. **Re-import safety** — the `unique (user_id, source, external_id)` constraints
   make every sync an idempotent upsert.
2. **Cross-source overlap** — a hand-logged run and the same run from Strava are
   *different* `external_id`s, so the constraint won't catch them. v1 detects
   near-duplicates with a time-window heuristic (overlapping start/end, similar
   duration) and surfaces a "looks like a duplicate — keep both / merge?" prompt
   rather than silently merging. No auto-merge before there's real data.

## AI photo food logging (the wedge)

`photo → ai-food-log Edge Function → Claude vision (structured-output prompt) →
parsed JSON → user confirms/edits → write food_entries`.

- The model is prompted to return JSON only; the function parses defensively (strips
  code fences, validates shape).
- **The estimate is never auto-trusted.** The function returns items with
  confidence and caches them into `foods` (`source = 'ai_estimate'`), but does
  *not* write `food_entries`. The client writes only after the user confirms
  quantities. This protects accuracy and trust and sidesteps "precise nutrition
  advice" positioning. Framed in-app as an *estimate*, never a measurement.
