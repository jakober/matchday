// Erzeugt eine Einladung und schickt den Code per Mail.
//
// Wer einladen darf, entscheidet die Datenbank: Die Einladung entsteht ueber
// create_invite - aufgerufen mit dem Token des Anrufers, nicht mit dem
// Dienstschluessel -, und die Funktion weist jeden ab, der nicht Admin ist.
// Diese Function fuegt nur den Versand hinzu.
//
// Die Mail enthaelt ausschliesslich Gruppenname und Code, keinen frei
// eingegebenen Text. Sonst waere die bestaetigte Absenderdomain binnen
// Wochen ein Spamversender.
//
// Antwort immer als JSON mit Status 200: { ok, code } oder { ok, error }.
// Erwartbare Fehler (keine Adresse, kein Admin, Grenze erreicht) sind keine
// Serverfehler; die App zeigt die Meldung so an, wie sie hier steht.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";
import { escapeHtml, looksLikeEmail, MailError, sendMail } from "../_shared/mail.ts";

// Hoechstens so viele Einladungsmails je Gruppe und Tag. Eine Freundesgruppe
// braucht eine Handvoll; alles darueber ist ein Fehler oder Missbrauch.
const MAX_PER_GROUP_PER_DAY = 20;

function reply(body: Record<string, unknown>): Response {
  return Response.json({ ok: !body.error, ...body });
}

Deno.serve(async (request) => {
  try {
    const authorization = request.headers.get("Authorization");
    if (!authorization) return new Response("nicht angemeldet", { status: 401 });

    const { group_id, scope, email } = await request.json();
    if (!group_id || !scope) return reply({ error: "Angaben unvollständig" });
    if (typeof email !== "string" || !looksLikeEmail(email)) {
      return reply({ error: "Das ist keine gültige E-Mail-Adresse" });
    }

    // Als der Anrufer: Die Datenbank prueft, ob er Admin dieser Gruppe ist.
    const asUser = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authorization } } },
    );
    // Mit dem Dienstschluessel nur fuer das, was der Anrufer nicht darf:
    // die Zaehlung und den Versandvermerk.
    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
    const { count } = await admin
      .from("invites")
      .select("code", { count: "exact", head: true })
      .eq("group_id", group_id)
      .gte("sent_at", since);
    if ((count ?? 0) >= MAX_PER_GROUP_PER_DAY) {
      return reply({ error: "Für heute sind genug Einladungen verschickt - morgen geht es weiter" });
    }

    const { data: group } = await asUser
      .from("groups")
      .select("name")
      .eq("id", group_id)
      .maybeSingle();
    if (!group) return reply({ error: "Gruppe nicht gefunden" });

    const { data: code, error: rpcError } = await asUser.rpc("create_invite", {
      p_group_id: group_id,
      p_scope: scope,
    });
    if (rpcError || typeof code !== "string") {
      return reply({ error: rpcError?.message ?? "Einladung konnte nicht angelegt werden" });
    }

    const to = email.trim();
    const groupName = String(group.name);
    const scopeText = scope === "important"
      ? "Du siehst die hervorgehobenen Spiele."
      : "Du siehst alle Spiele.";
    const text = [
      `Du bist eingeladen, bei „${groupName}“ in Matchday mitzumachen.`,
      "",
      `Dein Einladungscode: ${code}`,
      "",
      "So geht es: Matchday öffnen, unter Gruppe „Beitreten“ wählen und den Code eingeben.",
      scopeText,
      "",
      "Der Code gilt einmal.",
    ].join("\n");
    const html = `
      <p>Du bist eingeladen, bei <strong>${escapeHtml(groupName)}</strong> in Matchday mitzumachen.</p>
      <p style="font-size:28px;letter-spacing:6px;font-weight:bold">${escapeHtml(code)}</p>
      <p>So geht es: Matchday öffnen, unter <em>Gruppe</em> „Beitreten“ wählen und den Code eingeben.<br>${scopeText}</p>
      <p style="color:#666">Der Code gilt einmal.</p>`;

    try {
      await sendMail(to, `Einladung zu „${groupName}“`, text, html);
    } catch (error) {
      // Die Einladung bleibt gueltig - der Code wird in der App angezeigt und
      // laesst sich anders weitergeben.
      const message = error instanceof MailError ? error.message : "Versand fehlgeschlagen";
      return reply({ code, error: message });
    }

    await admin
      .from("invites")
      .update({ sent_to: to, sent_at: new Date().toISOString() })
      .eq("code", code);

    return reply({ code, sent_to: to });
  } catch (error) {
    console.error(error);
    return new Response(String(error), { status: 500 });
  }
});
