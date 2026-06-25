// ============================================================================
// strava-webhook — receive Strava activity events and upsert workouts.
//
// Strava pushes new activities here. Two responsibilities:
//   GET  -> subscription validation handshake (echo hub.challenge)
//   POST -> activity event -> fetch detail -> idempotent upsert into workouts
//
// verify_jwt is false for this function (config.toml) because Strava cannot send
// a Supabase JWT. We instead validate Strava's own verify token on the handshake
// and rely on the event payload + our own token store for POSTs.
//
// NOTE: this is a Phase 4 stub. The athlete -> user_id mapping and OAuth token
// storage (a `strava_tokens` table) are intentionally left as TODOs; the dedup
// and upsert shape is what's load-bearing and is wired up here.
// ============================================================================
import { json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/supabase.ts";

const VERIFY_TOKEN = Deno.env.get("STRAVA_VERIFY_TOKEN") ?? "";

interface StravaEvent {
  object_type: "activity" | "athlete";
  object_id: number;
  aspect_type: "create" | "update" | "delete";
  owner_id: number; // Strava athlete id
}

/** Map a Strava sport_type to our workouts.type vocabulary. */
function mapType(sportType: string): string {
  const s = sportType.toLowerCase();
  if (s.includes("run")) return "run";
  if (s.includes("ride") || s.includes("cycl")) return "cycle";
  if (s.includes("weight") || s.includes("workout")) return "strength";
  return "cardio";
}

Deno.serve(async (req) => {
  const url = new URL(req.url);

  // ---- Subscription validation handshake ----------------------------------
  if (req.method === "GET") {
    const mode = url.searchParams.get("hub.mode");
    const token = url.searchParams.get("hub.verify_token");
    const challenge = url.searchParams.get("hub.challenge");
    if (mode === "subscribe" && token === VERIFY_TOKEN && challenge) {
      return json({ "hub.challenge": challenge }, 200);
    }
    return json({ error: "verification failed" }, 403);
  }

  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  let event: StravaEvent;
  try {
    event = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }

  // Acknowledge non-activity events immediately; Strava expects a fast 200.
  if (event.object_type !== "activity") return json({ ok: true }, 200);

  const admin = serviceClient();

  // TODO(phase4): resolve user_id from event.owner_id via a strava_tokens table,
  // then use the stored OAuth access token to fetch full activity detail from
  // https://www.strava.com/api/v3/activities/{id}. Until that mapping exists we
  // cannot attribute the activity to a user, so just acknowledge.
  const userId: string | null = null; // = await resolveUser(admin, event.owner_id);

  if (!userId) {
    console.warn("strava event for unmapped athlete", event.owner_id);
    return json({ ok: true, note: "athlete not linked" }, 200);
  }

  // Delete propagation.
  if (event.aspect_type === "delete") {
    await admin
      .from("workouts")
      .delete()
      .match({ user_id: userId, source: "strava", external_id: String(event.object_id) });
    return json({ ok: true }, 200);
  }

  // Create/update -> idempotent upsert. The unique (user_id, source, external_id)
  // constraint makes re-delivery a no-op.
  // (Activity detail fetch elided in this stub; shape shown for the real impl.)
  const activity = {
    sport_type: "Run",
    start_date: new Date().toISOString(),
    elapsed_time: 0,
    calories: null as number | null,
  };

  await admin.from("workouts").upsert(
    {
      user_id: userId,
      source: "strava",
      external_id: String(event.object_id),
      type: mapType(activity.sport_type),
      started_at: activity.start_date,
      ended_at: new Date(
        new Date(activity.start_date).getTime() + activity.elapsed_time * 1000,
      ).toISOString(),
      calories: activity.calories,
    },
    { onConflict: "user_id,source,external_id" },
  );

  return json({ ok: true }, 200);
});
