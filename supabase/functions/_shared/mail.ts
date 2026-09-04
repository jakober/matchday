// Gemeinsame Zustellschicht fuer alle Mails der App - ueber Brevo.
//
// Ein Modul fuer alle Functions, damit Anbieter, Absender und Fehlerbehandlung
// nur an einer Stelle leben. Die Absenderdomain (matchday.blockwerk-orange.de)
// ist bei Brevo mit DKIM bestaetigt; ohne das landet die Mail bei Gmail und
// Outlook im Spam - bei einem Einladungscode ein Totalausfall.
//
// Secrets: BREVO_API_KEY, MAIL_FROM, MAIL_FROM_NAME.

const BREVO_API_KEY = Deno.env.get("BREVO_API_KEY");
const MAIL_FROM = Deno.env.get("MAIL_FROM");
const MAIL_FROM_NAME = Deno.env.get("MAIL_FROM_NAME") ?? "Matchday";

export class MailError extends Error {}

export function mailConfigured(): boolean {
  return Boolean(BREVO_API_KEY && MAIL_FROM);
}

/** Grobe Plausibilitaet - der eigentliche Beweis ist die Zustellung. */
export function looksLikeEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim());
}

/**
 * Verschickt eine Mail. Wirft MailError mit einer Meldung, die so auf dem
 * Bildschirm landen darf.
 */
export async function sendMail(
  to: string,
  subject: string,
  text: string,
  html: string,
): Promise<void> {
  if (!mailConfigured()) {
    throw new MailError("Mailversand ist auf dem Server nicht eingerichtet");
  }
  const response = await fetch("https://api.brevo.com/v3/smtp/email", {
    method: "POST",
    headers: {
      "api-key": BREVO_API_KEY!,
      "content-type": "application/json",
      accept: "application/json",
    },
    body: JSON.stringify({
      sender: { name: MAIL_FROM_NAME, email: MAIL_FROM },
      to: [{ email: to.trim() }],
      subject,
      textContent: text,
      htmlContent: html,
    }),
  });
  if (!response.ok) {
    const detail = await response.text();
    console.error(`Brevo lehnte ab: ${response.status} ${detail}`);
    throw new MailError("Die Mail konnte nicht verschickt werden - bitte in einer Minute erneut versuchen");
  }
}

/** HTML-Sonderzeichen entschaerfen, damit ein Gruppenname keine Tags einschleust. */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
