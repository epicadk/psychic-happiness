# Roadmap

## Phase 0 — Foundation  ✅ (this commit)

- [x] Supabase: full schema + RLS + `daily_summaries` triggers (migration-ready).
- [x] Edge Function scaffolds: `ai-food-log`, `food-lookup`, `strava-webhook`.
- [x] KMP project skeleton: shared module with MVI store/reducer scaffolding,
      domain models, repository contracts, and the `expect`/`actual` health bridge.
- [ ] Wire the Supabase Kotlin client into the data layer (repository impls).
- [ ] Auth + onboarding screens (units, profile, goal, optional macro targets).

## Phase 1 — Manual logging MVP (shippable)

- Strength logging (workout + sets/reps/weight), cardio, food via search + barcode
  scan, body weight.
- Dashboard reading `daily_summaries`.
- Ship to TestFlight + Play internal testing. *This alone is a usable product.*

## Phase 2 — One sync source

- Pick HealthKit **or** Health Connect (whichever platform holds the bigger early
  audience) and build the `expect`/`actual` bridge end to end.
- Goal: prove `sync → upsert → dedup → summary-recompute` on one platform before
  doubling it.

## Phase 3 — The wedge

- AI photo food logging end to end (the `ai-food-log` function + confirm/edit UI).

## Phase 4 — Breadth

- Strava webhooks, the second platform's health sync, richer analytics (PRs, weight
  trend, macro adherence over time).

## Deferred indefinitely

- Anything social/coaching. Solo-only keeps v1 lean. Revisit only when retention
  data says users want it.

---

## Decisions still open (not blocking the build)

- **Monetization** — freemium is the natural fit; AI photo logging is an obvious
  paywall line.
- **Which platform first** — drives the Phase 2 choice.
- **Goal model** — compute TDEE and suggest targets, or let users set their own
  calorie/macro goals manually?

## Risks & watch-items

- **Compose MP iOS polish** — budget SwiftUI interop for a few screens.
- **HealthKit** — needs a real device, entitlements, a clear data-usage
  justification; App Store review scrutinizes health data.
- **Health Connect** — built into Android 14+, installable below; requires a
  privacy policy and Google's health-data declaration.
- **Food DB licensing** — Open Food Facts is ODbL (attribution); Nutritionix/USDA
  carry their own terms.
- **Background sync** — both OSes throttle background work and watch battery; keep
  pulls cheap and infrequent.
- **AI estimates** — accuracy varies; keep the human-confirm step, avoid
  precise/medical framing.
