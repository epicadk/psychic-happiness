// ============================================================================
// food-lookup — resolve a barcode or text search to nutrition, with caching.
//
//   barcode -> Open Food Facts (free, global, ODbL — attribution required)
//   search  -> Nutritionix (fallback)
//
// Every resolved item is cached into the global `foods` table so repeat lookups
// are instant and offline-friendly. Cache hits skip the external call entirely.
// ============================================================================
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient, userClient } from "../_shared/supabase.ts";

interface FoodRow {
  barcode: string | null;
  name: string;
  brand: string | null;
  serving_qty: number | null;
  serving_unit: string | null;
  calories: number | null;
  protein_g: number | null;
  carbs_g: number | null;
  fat_g: number | null;
  source: string;
  external_id: string | null;
}

// ---------------------------------------------------------------------------
// Open Food Facts: barcode -> nutrition (per 100 g/ml).
// ---------------------------------------------------------------------------
async function lookupBarcode(barcode: string): Promise<FoodRow | null> {
  const resp = await fetch(
    `https://world.openfoodfacts.org/api/v2/product/${barcode}.json`,
    { headers: { "User-Agent": "FitNutri/0.1 (contact@fitnutri.app)" } },
  );
  if (!resp.ok) return null;
  const data = await resp.json();
  if (data.status !== 1 || !data.product) return null;

  const p = data.product;
  const n = p.nutriments ?? {};
  return {
    barcode,
    name: p.product_name ?? "Unknown product",
    brand: p.brands ?? null,
    serving_qty: 100,
    serving_unit: "g",
    calories: n["energy-kcal_100g"] ?? null,
    protein_g: n["proteins_100g"] ?? null,
    carbs_g: n["carbohydrates_100g"] ?? null,
    fat_g: n["fat_100g"] ?? null,
    source: "off",
    external_id: barcode,
  };
}

// ---------------------------------------------------------------------------
// Nutritionix: free-text search -> nutrition.
// ---------------------------------------------------------------------------
async function lookupSearch(query: string): Promise<FoodRow[]> {
  const appId = Deno.env.get("NUTRITIONIX_APP_ID");
  const appKey = Deno.env.get("NUTRITIONIX_APP_KEY");
  if (!appId || !appKey) return [];

  const resp = await fetch(
    "https://trackapi.nutritionix.com/v2/natural/nutrients",
    {
      method: "POST",
      headers: {
        "x-app-id": appId,
        "x-app-key": appKey,
        "content-type": "application/json",
      },
      body: JSON.stringify({ query }),
    },
  );
  if (!resp.ok) return [];
  const data = await resp.json();
  return (data.foods ?? []).map((f: Record<string, number | string>): FoodRow => ({
    barcode: null,
    name: String(f.food_name ?? query),
    brand: (f.brand_name as string) ?? null,
    serving_qty: (f.serving_weight_grams as number) ?? null,
    serving_unit: "g",
    calories: (f.nf_calories as number) ?? null,
    protein_g: (f.nf_protein as number) ?? null,
    carbs_g: (f.nf_total_carbohydrate as number) ?? null,
    fat_g: (f.nf_total_fat as number) ?? null,
    source: "nutritionix",
    external_id: `nix:${String(f.food_name ?? query).toLowerCase()}`,
  }));
}

/** Upsert a resolved item into the shared cache; return the persisted row id. */
async function cache(rows: FoodRow[]): Promise<Array<FoodRow & { id: string }>> {
  if (rows.length === 0) return [];
  const admin = serviceClient();
  const { data, error } = await admin
    .from("foods")
    .upsert(rows, { onConflict: "source,external_id", ignoreDuplicates: false })
    .select("*");
  if (error) {
    console.error("foods upsert failed", error);
    return [];
  }
  return data as Array<FoodRow & { id: string }>;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  const supa = userClient(req);
  if (!supa) return json({ error: "unauthorized" }, 401);
  const { data: { user } } = await supa.auth.getUser();
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: { barcode?: string; query?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid JSON body" }, 400);
  }

  const admin = serviceClient();

  // ---- Barcode path -------------------------------------------------------
  if (body.barcode) {
    // Cache hit first.
    const { data: cached } = await admin
      .from("foods")
      .select("*")
      .eq("barcode", body.barcode)
      .limit(1)
      .maybeSingle();
    if (cached) return json({ items: [cached], cached: true });

    const row = await lookupBarcode(body.barcode);
    if (!row) return json({ items: [], cached: false });
    const saved = await cache([row]);
    return json({ items: saved, cached: false, attribution: "Open Food Facts (ODbL)" });
  }

  // ---- Search path --------------------------------------------------------
  if (body.query) {
    const rows = await lookupSearch(body.query);
    const saved = await cache(rows);
    return json({ items: saved, cached: false });
  }

  return json({ error: "provide a barcode or query" }, 400);
});
