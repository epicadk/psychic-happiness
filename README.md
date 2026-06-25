# Fitness & Nutrition Tracker

A multi-tenant mobile product for solo users to log workouts, nutrition, and body
metrics — with **AI photo food logging** as the differentiating wedge.

- **Client:** Kotlin Multiplatform + Compose Multiplatform (shared domain, data, and
  UI across Android + iOS), MVI/UDF architecture.
- **Backend:** Supabase (Postgres + Auth + Row-Level Security + Realtime + Edge
  Functions).
- **Data:** Both manual logging and synced sources (HealthKit, Health Connect, Strava).

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design and
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the phased plan.

---

## Repository layout

```
.
├── supabase/                  # Backend: Postgres schema, RLS, Edge Functions
│   ├── config.toml            # Local dev config (supabase CLI)
│   ├── migrations/            # Versioned SQL migrations
│   │   ├── 20260624000001_initial_schema.sql
│   │   ├── 20260624000002_row_level_security.sql
│   │   └── 20260624000003_daily_summaries_triggers.sql
│   ├── functions/             # Deno/TypeScript Edge Functions
│   │   ├── _shared/           # Shared helpers (CORS, auth, json)
│   │   ├── ai-food-log/       # Vision model → structured nutrition JSON
│   │   ├── food-lookup/       # Barcode / search resolution + caching
│   │   └── strava-webhook/    # Strava activity webhook receiver
│   └── seed.sql               # Optional local seed data
├── shared/                    # KMP shared module (domain, MVI, data)
│   └── src/commonMain/kotlin/com/fitnutri/
│       ├── domain/            # Pure domain models + repository contracts
│       └── presentation/      # MVI core (Store, State, Intent, Effect)
├── gradle/                    # Version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── docs/
```

> **Status:** Phase 0 (Foundation). The Supabase backend is complete and
> migration-ready. The KMP module is a compiling-shape scaffold for the shared
> domain + MVI core; platform UI targets and the native health bridges
> (`expect`/`actual`) land in later phases.

---

## Getting started — backend

Prereqs: [Supabase CLI](https://supabase.com/docs/guides/cli).

```bash
# Start a local Supabase stack (Postgres, Auth, Studio, Edge runtime)
supabase start

# Apply migrations to the local database
supabase db reset            # drops + re-applies all migrations + seed

# Serve Edge Functions locally
supabase functions serve
```

Linking and deploying to a hosted project:

```bash
supabase link --project-ref <your-project-ref>
supabase db push                          # apply migrations
supabase functions deploy ai-food-log     # deploy a function
```

### Required secrets (Edge Functions)

Set these on the hosted project (`supabase secrets set KEY=value`). They are read
server-side only and never ship in the app binary.

| Secret | Used by | Purpose |
|---|---|---|
| `ANTHROPIC_API_KEY` | `ai-food-log` | Vision model for photo → nutrition JSON |
| `NUTRITIONIX_APP_ID` / `NUTRITIONIX_APP_KEY` | `food-lookup` | Search fallback |
| `STRAVA_VERIFY_TOKEN` | `strava-webhook` | Webhook subscription handshake |
| `STRAVA_CLIENT_ID` / `STRAVA_CLIENT_SECRET` | `strava-webhook` | Token exchange / activity fetch |

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically by the
Edge runtime.

---

## Getting started — client (KMP)

Prereqs: JDK 17+, Android SDK, (Xcode for iOS targets). The current scaffold
builds the shared module; app targets are added in Phase 1.

Generate the Gradle wrapper once (it isn't committed), then build:

```bash
gradle wrapper --gradle-version 8.11.1
./gradlew :shared:jvmTest      # runs the shared MVI tests on the JVM target
./gradlew :shared:build        # iOS targets require macOS + Xcode
```

---

## Multi-tenancy

There is **no app server**. Every user-owned row is gated by Postgres Row-Level
Security on `auth.uid()`, so "many solo users" need no application logic to isolate
data. The `foods` table is intentionally global (a shared cache) — readable by all
authenticated users, writable only via the service role inside Edge Functions.

## Licensing / attribution notes

- **Open Food Facts** (barcode source) is ODbL — attribution required.
- **Nutritionix** / **USDA FoodData Central** carry their own terms — review before
  commercial launch.
