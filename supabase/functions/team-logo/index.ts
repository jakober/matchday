// Loest Mannschaftsnamen in Wappen auf.
//
// Die App schickt die Namen, die sie aus den Kalendern gelesen hat; zurueck
// kommt je Name eine Bildadresse oder null. Gefragt wird zuerst der eigene
// Zwischenspeicher (team_logos), erst dann TheSportsDB - und jedes Ergebnis
// wird gespeichert, auch ein Fehlschlag. Sonst wuerde jedes Geraet bei jedem
// Start dieselben unbekannten Namen erneut anfragen.
//
// Der Trefferanteil haengt fast vollstaendig an der Normalisierung, nicht am
// Dienst: "FC Bayern München" und "Bayern Munich" muessen denselben
// Schluessel ergeben, und "Deutschland" muss als "Germany" gesucht werden.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

// Freier Testschluessel von TheSportsDB. Reicht fuer eine Handvoll Gruppen;
// bei mehr Nutzern gegen einen eigenen tauschen (Umgebungsvariable).
const SPORTSDB_KEY = Deno.env.get("SPORTSDB_KEY") ?? "3";
const MAX_NAMES = 50;
// Nach dieser Frist wird ein Fehlschlag erneut versucht - der Dienst waechst.
const RETRY_AFTER_DAYS = 90;

// Deutsche Laendernamen, wie sie in Spielplaenen stehen, auf die englischen
// des Dienstes. Vereinsnamen findet die Suche meist ueber Zweitnamen selbst.
const ALIASES: Record<string, string> = {
  "deutschland": "Germany",
  "oesterreich": "Austria",
  "schweiz": "Switzerland",
  "frankreich": "France",
  "spanien": "Spain",
  "italien": "Italy",
  "niederlande": "Netherlands",
  "belgien": "Belgium",
  "daenemark": "Denmark",
  "schweden": "Sweden",
  "norwegen": "Norway",
  "polen": "Poland",
  "tuerkei": "Turkey",
  "kroatien": "Croatia",
  "tschechien": "Czech Republic",
  "ungarn": "Hungary",
  "griechenland": "Greece",
  "irland": "Ireland",
  "schottland": "Scotland",
  "slowakei": "Slovakia",
  "slowenien": "Slovenia",
  "rumaenien": "Romania",
  "serbien": "Serbia",
  "usa": "United States",
  "vereinigte staaten": "United States",
  "japan": "Japan",
  "brasilien": "Brazil",
  "argentinien": "Argentina",
};

/** Umlaute falten, Kleinschreibung, Rechtsformen und Zusaetze entfernen. */
function normalize(name: string): string {
  let s = name.trim().toLowerCase()
    .replace(/ä/g, "ae").replace(/ö/g, "oe").replace(/ü/g, "ue").replace(/ß/g, "ss")
    .replace(/é|è|ê/g, "e").replace(/á|à|â/g, "a").replace(/ó|ò|ô/g, "o")
    .replace(/[().,'’"]/g, " ");
  // Zusaetze am Ende: zweite Mannschaft, Jugend, Neuling
  s = s.replace(/\b(ii|iii|u\d{2}|amateure|a-jugend|b-jugend|n)\b/g, " ");
  // Rechtsformen und Vereinskuerzel, nur als eigenes Wort
  s = s.replace(/\b(1\.\s*)?(fc|sv|tsg|vfl|vfb|sc|bsc|fsv|tsv|spvgg|sg|ssv|sf|tus|e\.?\s*v\.?)\b/g, " ");
  return s.replace(/\s+/g, " ").trim();
}

interface Team {
  strTeam: string;
  strTeamAlternate: string | null;
  strSport: string;
  strBadge: string | null;
}

/** Trifft der Name genau - als Hauptname oder als einer der Zweitnamen? */
function isExact(team: Team, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (team.strTeam.trim().toLowerCase() === q) return true;
  return (team.strTeamAlternate ?? "")
    .split(",")
    .some((alt) => alt.trim().toLowerCase() === q);
}

async function searchSportsDb(query: string): Promise<Team[]> {
  const url = `https://www.thesportsdb.com/api/v1/json/${SPORTSDB_KEY}/searchteams.php?t=${encodeURIComponent(query)}`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Wappendienst antwortete ${response.status}`);
  // Bei Drosselung kommt HTML statt JSON - das soll als Fehler gelten, nicht
  // als "nichts gefunden", sonst wuerde der Fehlschlag gespeichert.
  const json = await response.json();
  return (json?.teams ?? []) as Team[];
}

/**
 * Sucht ein Wappen.
 *
 * Der Dienst sucht nach Teilzeichenketten: "Deutschland" liefert
 * "Deutschlandsberger", einen oesterreichischen Regionalligisten. Deshalb
 * gilt die Rangfolge exakter Treffer vor Teiltreffer, und darin Fussball vor
 * anderen Sportarten - das ist der Hauptzweck der App, und "Germany" gibt es
 * auch im Handball. Die Uebersetzung ("Germany") wird zuerst gefragt, weil
 * sie den exakten Treffer am ehesten bringt.
 */
async function lookup(name: string, key: string): Promise<string | null> {
  const candidates: string[] = [];
  if (ALIASES[key]) candidates.push(ALIASES[key]);
  candidates.push(name.trim());
  if (key !== name.trim().toLowerCase()) candidates.push(key);

  let fallback: Team | undefined;
  for (const query of [...new Set(candidates)]) {
    const teams = (await searchSportsDb(query)).filter((t) => t.strBadge);
    const exact = teams.filter((t) => isExact(t, query));
    const hit = exact.find((t) => t.strSport === "Soccer") ?? exact[0];
    if (hit) return hit.strBadge;
    // Teiltreffer nur merken; vielleicht bringt die naechste Anfrage Besseres.
    fallback ??= teams.find((t) => t.strSport === "Soccer") ?? teams[0];
  }
  return fallback?.strBadge ?? null;
}

Deno.serve(async (request) => {
  try {
    if (!request.headers.get("Authorization")) {
      return new Response("nicht angemeldet", { status: 401 });
    }
    const body = await request.json();
    const names: string[] = Array.isArray(body?.names)
      ? body.names.filter((n: unknown) => typeof n === "string" && n.trim().length > 0)
        .slice(0, MAX_NAMES)
      : [];
    if (names.length === 0) {
      return Response.json({ logos: {} });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const keyOf = new Map(names.map((n) => [n, normalize(n)]));
    const keys = [...new Set(keyOf.values())];

    const { data: cached } = await supabase
      .from("team_logos")
      .select("key, badge_url, source, looked_up_at")
      .in("key", keys);

    const byKey = new Map(
      (cached ?? []).map((row: { key: string; badge_url: string | null; source: string; looked_up_at: string }) =>
        [row.key, row]
      ),
    );

    const retryBefore = Date.now() - RETRY_AFTER_DAYS * 24 * 3600 * 1000;
    const logos: Record<string, string | null> = {};

    for (const name of names) {
      const key = keyOf.get(name)!;
      const row = byKey.get(key);
      const fresh = row && (
        row.source === "manual" ||
        row.badge_url !== null ||
        new Date(row.looked_up_at).getTime() > retryBefore
      );
      if (fresh) {
        logos[name] = row.badge_url;
        continue;
      }

      // Ein einzelner Fehlschlag - etwa eine Drosselung des Wappendienstes -
      // darf nicht die ganze Anfrage kippen. Dann bleibt dieser Name offen
      // und wird beim naechsten Mal erneut versucht; gespeichert wird nichts.
      let badge: string | null;
      try {
        badge = await lookup(name, key);
      } catch (error) {
        console.error(`Wappen fuer "${name}": ${error}`);
        logos[name] = null;
        continue;
      }
      logos[name] = badge;
      await supabase.from("team_logos").upsert({
        key,
        display_name: name.trim(),
        badge_url: badge,
        source: "thesportsdb",
        looked_up_at: new Date().toISOString(),
      });
      // Zwei Namen mit demselben Schluessel in einer Anfrage: der zweite
      // bekommt das Ergebnis des ersten.
      byKey.set(key, { key, badge_url: badge, source: "thesportsdb", looked_up_at: new Date().toISOString() });
    }

    return Response.json({ logos });
  } catch (error) {
    console.error(error);
    return new Response(String(error), { status: 500 });
  }
});
