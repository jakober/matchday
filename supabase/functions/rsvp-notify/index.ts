// Benachrichtigt die uebrigen Gruppenmitglieder, wenn jemand zu- oder absagt.
//
// Wird von der App aufgerufen, direkt nachdem sie die eigene Antwort
// gespeichert hat. Bewusst nicht ueber einen Datenbank-Trigger: So laesst sich
// die Identitaet des Aufrufers aus seinem Anmeldetoken ableiten, statt einem
// uebergebenen Datensatz zu glauben. Der Aufrufer bestimmt nur, WELCHE seiner
// Antworten gemeldet wird - Inhalt und Absender liest die Function selbst aus
// der Datenbank.
//
// Der Weg zum Geraet unterscheidet sich je Plattform: Android ueber Firebase
// Cloud Messaging, iOS direkt ueber Apples Push-Dienst. Fuer iOS brauchen wir
// Firebase nicht - ein selbst signiertes Token genuegt und spart der App eine
// Abhaengigkeit.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

const APNS_KEY = Deno.env.get("APNS_KEY")!;
const APNS_KEY_ID = Deno.env.get("APNS_KEY_ID")!;
const APNS_TEAM_ID = Deno.env.get("APNS_TEAM_ID")!;
const FCM_SERVICE_ACCOUNT = Deno.env.get("FCM_SERVICE_ACCOUNT")!;
const BUNDLE_ID = "com.jakober.matchday";

// TestFlight- und App-Store-Installationen sprechen den Produktivdienst an.
// Ein Build direkt aus Xcode wuerde die Sandbox brauchen - deshalb der
// zweite Versuch, falls Apple die Kennung ablehnt.
const APNS_HOSTS = ["api.push.apple.com", "api.sandbox.push.apple.com"];

// -- Hilfen fuer JSON Web Tokens --------------------------------------------

function base64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string"
    ? new TextEncoder().encode(input)
    : new Uint8Array(input);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Wandelt einen PEM-Schluessel in die Rohbytes, die WebCrypto erwartet. */
function pemToBytes(pem: string): Uint8Array {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

/**
 * Liest die Nutzerkennung aus dem Anmeldetoken.
 *
 * Die Signatur ist bereits geprueft, bevor diese Function ueberhaupt startet -
 * Supabase laesst nur gueltige Token durch. Hier wird deshalb nur noch der
 * Inhalt gelesen.
 */
function userIdFromToken(authorization: string | null): string | null {
  const token = authorization?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json).sub ?? null;
  } catch {
    return null;
  }
}

/** Token fuer Apple: ES256, gueltig eine Stunde. */
async function appleToken(): Promise<string> {
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(APNS_KEY),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const header = base64url(JSON.stringify({ alg: "ES256", kid: APNS_KEY_ID }));
  const claims = base64url(JSON.stringify({
    iss: APNS_TEAM_ID,
    iat: Math.floor(Date.now() / 1000),
  }));
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    new TextEncoder().encode(`${header}.${claims}`),
  );
  return `${header}.${claims}.${base64url(signature)}`;
}

// Zugangstoken von Google gelten eine Stunde; erneut anzufordern kostet einen
// zusaetzlichen Rundlauf pro Benachrichtigung.
let googleToken: { value: string; expiresAt: number } | null = null;

async function googleAccessToken(): Promise<string> {
  if (googleToken && googleToken.expiresAt > Date.now() + 60_000) {
    return googleToken.value;
  }

  const account = JSON.parse(FCM_SERVICE_ACCOUNT);
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claims}`),
  );
  const assertion = `${header}.${claims}.${base64url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const json = await response.json();
  if (!json.access_token) {
    throw new Error(`Google lehnte die Anmeldung ab: ${JSON.stringify(json)}`);
  }
  googleToken = {
    value: json.access_token,
    expiresAt: Date.now() + (json.expires_in ?? 3600) * 1000,
  };
  return googleToken.value;
}

// -- Versand ----------------------------------------------------------------

async function sendApns(token: string, title: string, body: string): Promise<boolean> {
  const jwt = await appleToken();
  const payload = JSON.stringify({
    aps: { alert: { title, body }, sound: "default" },
  });

  for (const host of APNS_HOSTS) {
    const response = await fetch(`https://${host}/3/device/${token}`, {
      method: "POST",
      headers: {
        authorization: `bearer ${jwt}`,
        "apns-topic": BUNDLE_ID,
        "apns-push-type": "alert",
        "apns-priority": "10",
      },
      body: payload,
    });
    if (response.ok) return true;

    const reason = await response.text();
    // Nur bei falscher Umgebung den anderen Dienst versuchen; bei allen
    // anderen Fehlern waere ein zweiter Versuch sinnlos.
    if (!reason.includes("BadDeviceToken")) {
      console.error(`APNs ${host} lehnte ab: ${response.status} ${reason}`);
      return false;
    }
  }
  return false;
}

async function sendFcm(token: string, title: string, body: string): Promise<boolean> {
  const account = JSON.parse(FCM_SERVICE_ACCOUNT);
  const accessToken = await googleAccessToken();

  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          // Reine Datennachricht: Die App baut die Anzeige selbst und haelt
          // damit denselben Stil wie bei den uebrigen Erinnerungen.
          data: { title, body },
          android: { priority: "HIGH" },
        },
      }),
    },
  );
  if (!response.ok) {
    console.error(`FCM lehnte ab: ${response.status} ${await response.text()}`);
    return false;
  }
  return true;
}

// -- Einstiegspunkt ---------------------------------------------------------

Deno.serve(async (request) => {
  try {
    const userId = userIdFromToken(request.headers.get("Authorization"));
    if (!userId) return new Response("nicht angemeldet", { status: 401 });

    const { calendar_id, match_uid } = await request.json();
    if (!calendar_id || !match_uid) {
      return new Response("calendar_id und match_uid fehlen", { status: 400 });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      // Der Dienstschluessel umgeht die Zugriffsregeln - noetig, weil die
      // Push-Kennungen der anderen aus gutem Grund fuer niemanden lesbar sind.
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // Zu welcher Gruppe gehoert das Spiel? Ueber den Kalender, nicht ueber die
    // Mitgliedschaft des Aufrufers: Wer in mehreren Gruppen ist, haette sonst
    // mehrere Treffer - und maybeSingle() liefert dann gar keinen.
    const { data: calendar } = await supabase
      .from("calendars")
      .select("group_id")
      .eq("id", calendar_id)
      .maybeSingle();

    if (!calendar) return new Response("Kalender unbekannt", { status: 404 });

    // Wer ruft an? Aus dem Token abgeleitet, nicht aus dem Aufruf uebernommen.
    const { data: member } = await supabase
      .from("members")
      .select("id, group_id, display_name")
      .eq("user_id", userId)
      .eq("group_id", calendar.group_id)
      .maybeSingle();

    if (!member) return new Response("keine Mitgliedschaft", { status: 403 });

    // Inhalt ebenfalls aus der Datenbank, nicht aus dem Aufruf.
    const { data: rsvp } = await supabase
      .from("rsvps")
      .select("status, comment, match_title")
      .eq("member_id", member.id)
      .eq("calendar_id", calendar_id)
      .eq("match_uid", match_uid)
      .maybeSingle();

    // Fehlt sie, wurde die Antwort gerade zurueckgenommen - das ist keine
    // Nachricht wert, die alle aufschreckt.
    if (!rsvp) return new Response("keine Antwort vorhanden", { status: 200 });

    // Ist dieses Spiel hervorgehoben? Danach richtet sich, wer ueberhaupt
    // benachrichtigt wird.
    const { data: important } = await supabase
      .from("important_matches")
      .select("id")
      .eq("group_id", member.group_id)
      .eq("calendar_id", calendar_id)
      .eq("match_uid", match_uid)
      .maybeSingle();

    const { data: tokens } = await supabase
      .from("device_tokens")
      .select("platform, token, member_id")
      .eq("group_id", member.group_id)
      // Sich selbst benachrichtigen waere nur laestig.
      .neq("member_id", member.id);

    if (!tokens || tokens.length === 0) {
      return new Response("keine Empfaenger", { status: 200 });
    }

    // Wer nur die hervorgehobenen Spiele sieht, darf auch nur zu diesen
    // benachrichtigt werden - sonst kaeme eine Meldung zu einem Spiel, das in
    // seiner Liste gar nicht auftaucht. Diese Pruefung gehoert auf den Server:
    // In der App waere sie umgehbar.
    const { data: alle } = await supabase
      .from("members")
      .select("id, scope")
      .eq("group_id", member.group_id);

    const scopeById = new Map((alle ?? []).map((m: { id: string; scope: string }) => [m.id, m.scope]));
    const empfaenger = tokens.filter((entry: { member_id: string }) =>
      important !== null || scopeById.get(entry.member_id) !== "important"
    );

    if (empfaenger.length === 0) {
      return new Response("keine passenden Empfaenger", { status: 200 });
    }

    const name = member.display_name ?? "Jemand";
    const title = rsvp.status === "IN" ? `${name} ist dabei` : `${name} kann nicht`;
    const match = rsvp.match_title ?? "Ein Spiel";
    const body = rsvp.comment ? `${match} · ${rsvp.comment}` : match;

    const results = await Promise.all(
      empfaenger.map((entry: { platform: string; token: string }) =>
        entry.platform === "ios"
          ? sendApns(entry.token, title, body)
          : sendFcm(entry.token, title, body)
      ),
    );

    const sent = results.filter(Boolean).length;
    return new Response(`${sent} von ${empfaenger.length} zugestellt`, { status: 200 });
  } catch (error) {
    console.error(error);
    return new Response(String(error), { status: 500 });
  }
});
