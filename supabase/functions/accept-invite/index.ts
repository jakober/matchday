// Nimmt eine Einladung an, fuer die Name und Adresse schon feststehen:
// legt das Konto an, traegt die Person in die Gruppe ein, verbraucht den Code.
//
// Laeuft ohne Anmeldung (verify_jwt aus), weil der Anrufer noch kein Konto
// hat. Was ihn ausweist, ist der Code aus der Mail an genau diese Adresse -
// deshalb gilt die Adresse danach als bestaetigt, ohne zweiten Code. Ein Code
// ist sechs Zeichen aus 36, einmal gueltig und zufaellig; Raten lohnt nicht.
//
// Antwort immer als JSON mit Status 200: { ok, email, name } oder
// { ok: false, error, ... }. Die App zeigt die Meldung so an, wie sie hier
// steht.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

// Dieselbe Palette wie in der App (AvatarColors), damit die Farbe passt.
const COLORS = [
  0xFF37E27A, 0xFF3FA9F5, 0xFFB06BFF, 0xFFFF6B6B,
  0xFFFFA23E, 0xFF17C3B2, 0xFFEE5D9C, 0xFF8A94A6,
];

function reply(body: Record<string, unknown>): Response {
  return Response.json({ ok: !body.error, ...body });
}

Deno.serve(async (request) => {
  try {
    const { code, password, locale } = await request.json();
    const en = locale === "en";
    const t = (de: string, enText: string) => (en ? enText : de);

    const normalized = typeof code === "string" ? code.trim().toUpperCase() : "";
    if (!/^[A-Z0-9]{6}$/.test(normalized)) {
      return reply({ error: t("Ungültiger Einladungscode", "Invalid invitation code") });
    }
    if (typeof password !== "string" || password.length < 8) {
      return reply({ error: t("Das Passwort braucht mindestens 8 Zeichen.", "The password needs at least 8 characters.") });
    }

    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: invite } = await admin
      .from("invites")
      .select("code, group_id, scope, used_by, sent_to, invitee_name")
      .eq("code", normalized)
      .maybeSingle();

    if (!invite) {
      return reply({ error: t("Ungültiger Einladungscode", "Invalid invitation code") });
    }
    if (invite.used_by) {
      return reply({ error: t("Diese Einladung wurde bereits verwendet", "This invitation has already been used") });
    }
    if (!invite.sent_to || !invite.invitee_name) {
      // Ein Code, der nur zum Weitergeben erzeugt wurde: Da fehlt die
      // Adresse, also braucht es die normale Registrierung.
      return reply({
        needs_signup: true,
        error: t(
          "Für diesen Code ist keine Adresse hinterlegt - bitte registrieren und den Code danach eingeben.",
          "No address is stored for this code - please register and enter the code afterwards.",
        ),
      });
    }

    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email: invite.sent_to,
      password,
      email_confirm: true,
      user_metadata: { display_name: invite.invitee_name },
    });
    if (createError || !created?.user) {
      const message = createError?.message ?? "";
      if (/already|exists|registered/i.test(message)) {
        return reply({
          exists: true,
          email: invite.sent_to,
          error: t(
            "Für diese Adresse gibt es schon ein Konto - melde dich an und gib den Code dort ein.",
            "There is already an account for this address - sign in and enter the code there.",
          ),
        });
      }
      return reply({ error: message || t("Konto konnte nicht angelegt werden", "Account could not be created") });
    }

    const color = COLORS[Math.floor(Math.random() * COLORS.length)];
    const { data: member, error: memberError } = await admin
      .from("members")
      .insert({
        group_id: invite.group_id,
        user_id: created.user.id,
        display_name: invite.invitee_name,
        color,
        scope: invite.scope,
      })
      .select("id")
      .single();
    if (memberError || !member) {
      return reply({ error: memberError?.message ?? t("Beitritt fehlgeschlagen", "Joining failed") });
    }

    await admin
      .from("invites")
      .update({ used_by: member.id, used_at: new Date().toISOString() })
      .eq("code", normalized);

    return reply({ email: invite.sent_to, name: invite.invitee_name });
  } catch (error) {
    console.error(error);
    return new Response(String(error), { status: 500 });
  }
});
