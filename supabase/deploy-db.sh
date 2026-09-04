#!/usr/bin/env bash
# Erzeugt das SQL-Modul fuer db-setup aus den Migrationsdateien und deployt
# die Function. Der einzige vorgesehene Weg, das Schema zu aendern:
#
#   1. Neue Datei unter supabase/migrations/ anlegen (Nummer vorn, aufsteigend)
#   2. ./supabase/deploy-db.sh
#   3. db-setup aufrufen (siehe README)
#
# Das erzeugte Modul ist nicht eingecheckt - es waere nur eine Kopie.
set -euo pipefail

cd "$(dirname "$0")"

OUT="functions/db-setup/migrations.generated.ts"
{
  echo "// ERZEUGT von deploy-db.sh aus supabase/migrations/*.sql - nicht bearbeiten."
  echo "export const MIGRATIONS: { name: string; text: string }[] = ["
  for f in $(ls migrations/*.sql | sort); do
    name="$(basename "$f")"
    # Als JSON-String, damit Backticks, Backslashes und Dollarzeichen im SQL
    # keine Rolle spielen.
    printf '  { name: %s, text: ' "$(python -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$name")"
    python -c 'import json,sys; sys.stdout.write(json.dumps(open(sys.argv[1], encoding="utf-8").read()))' "$f"
    echo " },"
  done
  echo "];"
} > "$OUT"

echo "Erzeugt: $OUT ($(grep -c '^  { name:' "$OUT") Migrationen)"

CLI="../.tools/supabase.exe"
[ -x "$CLI" ] || CLI="supabase"
"$CLI" functions deploy db-setup --project-ref "$(cat .temp/project-ref)" 2>&1 | grep -v "Docker is not running"
