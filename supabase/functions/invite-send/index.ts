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

    const { group_id, scope, email, locale, name } = await request.json();
    const inviteeName = typeof name === "string" ? name.trim() : "";
    const en = locale === "en";
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
    // Sprache des Einladenden - die des Empfaengers kennt niemand.
    const scopeText = scope === "important"
      ? (en ? "You'll see the highlighted matches." : "Du siehst die hervorgehobenen Spiele.")
      : (en ? "You'll see all matches." : "Du siehst alle Spiele.");
    const greeting = inviteeName ? (en ? `Hi ${inviteeName},` : `Hallo ${inviteeName},`) : (en ? "Hi," : "Hallo,");
    const intro = en
      ? `${greeting} you're invited to join “${groupName}” on Matchday.`
      : `${greeting} du bist eingeladen, bei „${groupName}“ in Matchday mitzumachen.`;
    // Der Link fuehrt auf eine kleine Seite, die den Code zeigt und die App
    // oeffnet - Mail-Programme machen App-Links selbst oft nicht anklickbar.
    const link = `https://jakober.github.io/matchday/einladung.html?code=${encodeURIComponent(code)}`;
    const codeLine = en ? `Your invitation code: ${code}` : `Dein Einladungscode: ${code}`;
    const howTo = inviteeName
      ? (en
        ? "Tap the link, choose a password, done - no registration needed."
        : "Tippe auf den Link, wähle ein Passwort, fertig - keine Registrierung nötig.")
      : (en
        ? "How it works: open Matchday, choose “Join” under Group and enter the code."
        : "So geht es: Matchday öffnen, unter Gruppe „Beitreten“ wählen und den Code eingeben.");
    const once = en ? "The code works once." : "Der Code gilt einmal.";
    const text = [intro, "", link, "", codeLine, "", howTo, scopeText, "", once].join("\n");
    const html = `
      <p>${escapeHtml(intro)}</p>
      <p><a href="${link}" style="display:inline-block;padding:14px 22px;background:#37e27a;color:#0f1115;border-radius:12px;text-decoration:none;font-weight:bold">${en ? "Open invitation" : "Einladung öffnen"}</a></p>
      <p>${en ? "Your code:" : "Dein Code:"}</p>
      <p style="font-size:28px;letter-spacing:6px;font-weight:bold">${escapeHtml(code)}</p>
      <p>${escapeHtml(howTo)}<br>${escapeHtml(scopeText)}</p>
      <p style="color:#666">${escapeHtml(once)}</p>`;
    const subject = en ? `Invitation to “${groupName}”` : `Einladung zu „${groupName}“`;

    try {
      await sendMail(to, subject, text, html);
    } catch (error) {
      // Die Einladung bleibt gueltig - der Code wird in der App angezeigt und
      // laesst sich anders weitergeben.
      const message = error instanceof MailError ? error.message : "Versand fehlgeschlagen";
      return reply({ code, error: message });
    }

    await admin
      .from("invites")
      .update({
        sent_to: to,
        sent_at: new Date().toISOString(),
        invitee_name: inviteeName || null,
      })
      .eq("code", code);

    return reply({ code, sent_to: to });
  } catch (error) {
    console.error(error);
    return new Response(String(error), { status: 500 });
  }
});
