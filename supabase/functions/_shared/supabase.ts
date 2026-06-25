// Supabase client factories for Edge Functions.
import { createClient, type SupabaseClient } from "jsr:@supabase/supabase-js@2";

/**
 * A service-role client. Bypasses RLS — use only for trusted server-side writes
 * (e.g. populating the global `foods` cache). Never return its results to a
 * client without re-scoping by user.
 */
export function serviceClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );
}

/**
 * A client scoped to the caller's JWT. RLS applies, so reads/writes are limited
 * to the authenticated user's own rows. Returns null if no bearer token.
 */
export function userClient(req: Request): SupabaseClient | null {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return null;
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false },
    },
  );
}
