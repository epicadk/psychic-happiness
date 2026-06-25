// ============================================================================
// ai-food-log — the wedge.
//
// Flow: photo -> this function -> Claude vision (structured-output prompt) ->
// parsed JSON -> returned to client for user confirm/edit BEFORE any write to
// food_entries.
//
// This endpoint does NOT write food_entries. It returns estimates and caches the
// recognized items into the global `foods` table (source = 'ai_estimate') so
// frequent meals resolve faster next time. The client writes food_entries only
// after the user confirms quantities — protecting accuracy and user trust, and
// keeping us clear of "precise nutrition advice" positioning.
// ============================================================================
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient, userClient } from "../_shared/supabase.ts";

const ANTHROPIC_API_KEY = Deno.env.get("ANTHROPIC_API_KEY")!;
// Sonnet is the cost/quality sweet spot for per-photo vision; override via env.
const MODEL = Deno.env.get("AI_FOOD_MODEL") ?? "claude-sonnet-4-6";

interface EstimatedItem {
  name: string;
  est_serving: string;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  confidence: number;
}

const SYSTEM_PROMPT =
  `You are a nutrition estimation assistant. Given a photo of food, identify each
distinct item and estimate its nutrition for the portion visible.

Respond with JSON only — no prose, no markdown fences. Shape:
{"items":[{"name":string,"est_serving":string,"calories":number,
"protein_g":number,"carbs_g":number,"fat_g":number,"confidence":number}]}

- "est_serving" is a human-readable portion estimate, e.g. "1 cup", "approx 150 g".
- "confidence" is 0..1, your confidence in the identification + portion.
- These are estimates, not measurements. Do not include medical or clinical claims.
- If no food is visible, return {"items":[]}.`;

/** Strip code fences and parse defensively; validate shape. */
function parseModelJson(text: string): EstimatedItem[] {
  let t = text.trim();
  // Remove ```json ... ``` or ``` ... ``` fences if present.
  const fence = t.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/);
  if (fence) t = fence[1].trim();

  let parsed: unknown;
  try {
    parsed = JSON.parse(t);
  } catch {
    // Last resort: grab the first {...} block.
    const m = t.match(/\{[\s\S]*\}/);
    if (!m) throw new Error("model did not return JSON");
    parsed = JSON.parse(m[0]);
  }

  const items = (parsed as { items?: unknown }).items;
  if (!Array.isArray(items)) throw new Error("missing items array");

  return items.map((raw): EstimatedItem => {
    const it = raw as Record<string, unknown>;
    const num = (v: unknown) => (typeof v === "number" && isFinite(v) ? v : 0);
    return {
      name: String(it.name ?? "Unknown item"),
      est_serving: String(it.est_serving ?? ""),
      calories: num(it.calories),
      protein_g: num(it.protein_g),
      carbs_g: num(it.carbs_g),
      fat_g: num(it.fat_g),
      confidence: Math.min(1, Math.max(0, num(it.confidence))),
    };
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  // Require an authenticated user (verify_jwt = true also enforces this).
  const supa = userClient(req);
  if (!supa) return json({ error: "unauthorized" }, 401);
  const { data: { user } } = await supa.auth.getUser();
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: { image_base64?: string; media_type?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid JSON body" }, 400);
  }
  if (!body.image_base64) return json({ error: "image_base64 required" }, 400);
  const mediaType = body.media_type ?? "image/jpeg";

  // Call Claude vision.
  const aiResp = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: 1024,
      system: SYSTEM_PROMPT,
      messages: [{
        role: "user",
        content: [
          {
            type: "image",
            source: { type: "base64", media_type: mediaType, data: body.image_base64 },
          },
          { type: "text", text: "Identify the food and estimate nutrition as JSON." },
        ],
      }],
    }),
  });

  if (!aiResp.ok) {
    const detail = await aiResp.text();
    console.error("anthropic error", aiResp.status, detail);
    return json({ error: "vision model request failed" }, 502);
  }

  const aiJson = await aiResp.json();
  const text: string = aiJson?.content?.[0]?.text ?? "";

  let items: EstimatedItem[];
  try {
    items = parseModelJson(text);
  } catch (e) {
    console.error("parse failure", e, text);
    return json({ error: "could not parse model output" }, 502);
  }

  // Cache recognized items into the global foods table via the service role.
  // Returned food_id lets the client write food_entries after the user confirms.
  const admin = serviceClient();
  const enriched = await Promise.all(items.map(async (it) => {
    const { data, error } = await admin
      .from("foods")
      .insert({
        name: it.name,
        serving_qty: 1,
        serving_unit: "serving",
        calories: it.calories,
        protein_g: it.protein_g,
        carbs_g: it.carbs_g,
        fat_g: it.fat_g,
        source: "ai_estimate",
      })
      .select("id")
      .single();
    if (error) console.error("foods cache insert failed", error);
    return { ...it, food_id: data?.id ?? null };
  }));

  // The client shows these with confidence and lets the user adjust quantities
  // before writing to food_entries. We never auto-trust the estimate.
  return json({ items: enriched, model: MODEL, estimate: true });
});
