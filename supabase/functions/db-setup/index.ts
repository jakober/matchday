// Spielt die Migrationen ein.
//
// Noetig, weil vom Rechner des Nutzers aus keine direkte Datenbankverbindung
// moeglich ist: Die Ports 5432 und 6543 sind in seinem Netz gesperrt, nur
// HTTPS kommt durch. "supabase db push" und "supabase db query" scheitern
// daran - auch letzteres versucht erst eine direkte Verbindung. Diese
// Function laeuft in Supabases eigenem Netz und erreicht die Datenbank von dort.
//
// Das SQL kommt aus migrations.generated.ts. Die Datei wird von
// supabase/deploy-db.sh aus supabase/migrations/*.sql erzeugt und ist nicht
// eingecheckt - die Laufzeit kann keine Verzeichnisse lesen, und von Hand
// gepflegt waere das SQL an zwei Stellen. Genau das war frueher so, und die
// eingebettete Fassung war bereits abgedriftet.
//
// Jede Datei laeuft genau einmal. Welche schon gelaufen sind, steht in
// schema_migrations. Damit duerfen Migrationen auch Dinge tun, die sich
// nicht wiederholen lassen - etwa Tabellen verwerfen.

import postgres from "https://deno.land/x/postgresjs@v3.4.5/mod.js";
import { MIGRATIONS } from "./migrations.generated.ts";

Deno.serve(async () => {
  const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false });
  const report: string[] = [];
  try {
    await sql.unsafe(`
      create table if not exists public.schema_migrations (
        version    text primary key,
        applied_at timestamptz not null default now()
      )`).simple();
    // Keine Zugriffsregel noetig: Mit RLS an und ohne Regel kommt von aussen
    // niemand dran.
    await sql.unsafe(
      "alter table public.schema_migrations enable row level security",
    ).simple();

    const applied = new Set(
      (await sql`select version from public.schema_migrations`).map(
        (row: { version: string }) => row.version,
      ),
    );

    for (const { name, text } of MIGRATIONS) {
      if (applied.has(name)) {
        report.push(`uebersprungen: ${name}`);
        continue;
      }
      // Alles oder nichts: Bleibt eine Migration auf halbem Weg stehen, soll
      // nichts davon zurueckbleiben.
      await sql.begin(async (tx) => {
        await tx.unsafe(text).simple();
        await tx`insert into public.schema_migrations (version) values (${name})`;
      });
      report.push(`eingespielt: ${name}`);
    }

    // Ohne diesen Anstoss kennt die REST-Schicht neue Tabellen und Funktionen
    // erst nach einigen Minuten - sie haelt einen eigenen Schema-Zwischenspeicher.
    await sql.unsafe("notify pgrst, 'reload schema'").simple();
    return new Response(report.join("\n") + "\n", { status: 200 });
  } catch (error) {
    console.error(error);
    const message = error instanceof Error ? error.message : String(error);
    return new Response(
      [...report, `FEHLGESCHLAGEN: ${message}`].join("\n") + "\n",
      { status: 500 },
    );
  } finally {
    await sql.end();
  }
});
