// Administración de cuentas para el Pastor/Administrador.
//
// La creación de cuentas y el cambio de contraseña de terceros requieren la
// clave service_role, que nunca debe estar en la app. Esta función se ejecuta en
// Supabase, verifica que quien llama sea un Pastor activo (con su propio JWT y
// RLS) y solo entonces usa la clave de servicio.
//
// Acciones:
//   { action: "create", email, full_name, phone?, role, password, memberships? }
//   { action: "set_password", user_id, password }
import { createClient } from "jsr:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const ROLES = new Set(["pastor", "ministry_leader", "leader"]);
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

/** Política propuesta: al menos 8 caracteres, una letra y un número. */
function passwordError(password: unknown): string | null {
  if (typeof password !== "string" || password.length < 8) {
    return "La contraseña debe tener al menos 8 caracteres.";
  }
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    return "La contraseña debe incluir al menos una letra y un número.";
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Método no permitido." }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    return json({ error: "La función no está configurada en el servidor." }, 500);
  }

  // 1. Verificar a quien llama con su propio token y las funciones RLS existentes.
  const caller = createClient(url, anonKey, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: isPastor, error: pastorError } = await caller.rpc("is_pastor");
  if (pastorError || isPastor !== true) {
    return json({ error: "Solo un Pastor/Administrador activo puede administrar cuentas." }, 403);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Solicitud inválida." }, 400);
  }

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  if (body.action === "set_password") {
    const userId = String(body.user_id ?? "");
    if (!UUID_PATTERN.test(userId)) return json({ error: "Cuenta inválida." }, 400);
    const invalid = passwordError(body.password);
    if (invalid) return json({ error: invalid }, 400);
    const { error } = await admin.auth.admin.updateUserById(userId, { password: body.password as string });
    if (error) return json({ error: "No se pudo actualizar la contraseña." }, 400);
    return json({ ok: true });
  }

  if (body.action !== "create") return json({ error: "Acción desconocida." }, 400);

  const email = String(body.email ?? "").trim().toLowerCase();
  const fullName = String(body.full_name ?? "").trim();
  const phone = String(body.phone ?? "").trim();
  const role = String(body.role ?? "leader");
  const memberships = Array.isArray(body.memberships) ? body.memberships : [];

  if (!EMAIL_PATTERN.test(email)) return json({ error: "El correo no es válido." }, 400);
  if (!fullName) return json({ error: "El nombre es obligatorio." }, 400);
  if (!ROLES.has(role)) return json({ error: "El rol no es válido." }, 400);
  const invalid = passwordError(body.password);
  if (invalid) return json({ error: invalid }, 400);
  for (const membership of memberships) {
    if (!UUID_PATTERN.test(String((membership as Record<string, unknown>)?.ministry_id ?? ""))) {
      return json({ error: "Ministerio inválido." }, 400);
    }
  }

  // 2. Crear la cuenta confirmada; el trigger handle_new_user crea el perfil.
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password: body.password as string,
    email_confirm: true,
    user_metadata: { full_name: fullName },
  });
  if (createError || !created.user) {
    const duplicated = createError?.message?.toLowerCase().includes("already");
    return json(
      { error: duplicated ? "Ya existe una cuenta con ese correo." : "No se pudo crear la cuenta." },
      duplicated ? 409 : 400,
    );
  }
  const userId = created.user.id;

  // 3. Completar perfil y membresías; si algo falla, se revierte la cuenta.
  const { error: profileError } = await admin
    .from("profiles")
    .update({ full_name: fullName, phone: phone || null, role, is_active: true })
    .eq("id", userId);
  let membershipError = null;
  if (!profileError && memberships.length > 0) {
    const rows = memberships.map((membership) => {
      const value = membership as Record<string, unknown>;
      return { ministry_id: value.ministry_id, user_id: userId, is_leader: value.is_leader === true };
    });
    ({ error: membershipError } = await admin.from("ministry_memberships").insert(rows));
  }
  if (profileError || membershipError) {
    await admin.auth.admin.deleteUser(userId);
    return json({ error: "No se pudo completar el perfil; la cuenta no se creó." }, 400);
  }

  return json({ id: userId });
});
