# Matchday – Wer kommt?

Gemeinsam Fußball schauen: Eine Gruppe teilt sich Spielplan-Kalender, jeder
sagt zu oder ab, alle sehen, wer dabei ist. Eine Codebasis für Android und iOS
(Kotlin Multiplatform + Compose Multiplatform), Server bei Supabase.

## Funktionen

- Konto mit Name, E-Mail und Passwort; die Adresse wird bei der Registrierung
  mit einem sechsstelligen Code aus der Mail bestätigt
- Gruppe anlegen oder per Einladungscode beitreten; der Ersteller ist Admin
- Kalender gehören der Gruppe: Der Admin fügt sie hinzu - aus einer
  eingebauten Liste (Bundesliga, 2. Bundesliga, alle Vereine, Nationalmannschaft)
  oder über jede ICS-/webcal-Adresse, mit Vorschau vor dem Speichern
- Spiele als Liste oder Monatsraster, mit den Wappen beider Mannschaften
- Zusagen pro Spiel, Absage mit Kommentar; Push an die anderen, sobald jemand
  antwortet, und die offene Ansicht gleicht sich von selbst ab
- Admin hebt Spiele hervor; Einladungen legen fest, ob jemand alle oder nur
  die hervorgehobenen Spiele sieht
- Einladung per E-Mail oder als Code zum Weitergeben
- Erinnerung vor Anpfiff (Vorlauf einstellbar) und tägliche Nachfrage ab einer
  Woche vorher, solange die Antwort fehlt
- Deutsch und Englisch, nach Gerätesprache

## Aufbau

```
composeApp/src/commonMain   gemeinsamer Code: Oberfläche, Datenhaltung, ICS-Parser, Texte (i18n)
composeApp/src/androidMain  Android: Alarme, Benachrichtigungen, Push (FCM), Activity
composeApp/src/iosMain      iOS: lokale Benachrichtigungen, Push (APNs), Einstiegspunkt
iosApp/                     Xcode-Projekt (wird per XcodeGen erzeugt)
supabase/migrations/        das vollständige Datenbankschema, nummeriert
supabase/functions/         Edge Functions: db-setup, rsvp-notify, invite-send, team-logo
supabase/templates/         Mailvorlagen mit dem Bestätigungscode
```

Nennenswerte Entscheidungen:

- **Wer was darf, entscheidet die Datenbank.** Zeilenregeln (RLS) und
  `security definer`-Funktionen; die App kann nichts umgehen, auch wenn jemand
  den Schlüssel ausliest. Kalender anlegen und Spiele hervorheben darf nur der
  Admin - serverseitig erzwungen.
- **Die Kalender-Id kommt vom Server.** Spiel-Ids sind `calendars.id#UID`,
  damit Zusagen auf allen Geräten dasselbe Spiel meinen. Die lokale Abo-Liste
  bleibt als Betriebsgrundlage, der Server ist die Wahrheit.
- **Wappen werden auf dem Server nachgeschlagen** (TheSportsDB) und in
  `team_logos` zwischengespeichert - einmal je Name, nicht je Gerät, und
  korrigierbar mit `source = 'manual'`. Clients dürfen dort nicht schreiben.
- **Der ICS-Parser ist selbst geschrieben.** Die verbreiteten Bibliotheken sind
  JVM-only und auf iOS nicht verwendbar. Nicht unterstützt: RRULE.
- **Texte liegen in `i18n/Strings.kt`**, nicht in Ressourcendateien, damit
  auch Benachrichtigungen und Fehlermeldungen außerhalb von Composables
  übersetzt sind. Deutsch ist die Rückfallebene.
- **Die .xcodeproj liegt nicht im Repo.** Sie wird aus `iosApp/project.yml`
  erzeugt.

## Bauen

### Android

Voraussetzungen: JDK 17+, Android SDK (Platform 35).

```
./gradlew :composeApp:assembleDebug
```

APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`. Zum
Installieren auf demselben Gerät immer auf demselben Rechner bauen: Der
Debug-Schlüssel liegt unter `~/.android/debug.keystore`, und ein Build mit
anderem Schlüssel lässt sich nicht als Update installieren.

Tests: `./gradlew :composeApp:allTests`

### iOS

Braucht macOS mit Xcode. Lokal: `brew install xcodegen`, dann
`cd iosApp && xcodegen generate`. Der TestFlight-Workflow
(`.github/workflows/testflight.yml`) baut, signiert mit `fastlane match` und
lädt hoch - von Hand oder alle zwei Monate per Zeitplan, damit die Builds bei
den Testern nicht ablaufen. Ein Mac-Runner kostet das Zehnfache an
Actions-Minuten; deshalb baut `build.yml` iOS nur auf Handstart.

Benötigte Repository-Secrets: `APPSTORE_KEY_ID`, `APPSTORE_ISSUER_ID`,
`APPSTORE_PRIVATE_KEY`, `APPLE_TEAM_ID`, `MATCH_PASSWORD`.

## Server (Supabase)

### Schema ändern

Vom Rechner aus ist keine direkte Datenbankverbindung möglich (Ports gesperrt).
Deshalb:

1. Neue Datei unter `supabase/migrations/` anlegen, Nummer vorn, aufsteigend.
2. `bash supabase/deploy-db.sh` - erzeugt das SQL-Modul für die Function und
   deployt sie.
3. `db-setup` aufrufen:

```
curl -X POST https://<projekt>.supabase.co/functions/v1/db-setup \
  -H "apikey: <anon-key>" -H "Authorization: Bearer <anon-key>"
```

Jede Datei läuft genau einmal (`schema_migrations`). Nie von Hand im
SQL-Editor - der Zustand "angelegt, nirgends versioniert" darf nicht wieder
entstehen.

### Anmelde-Einstellungen

`supabase/config.toml` trägt die Auth-Konfiguration; `supabase config push`
überträgt sie. **Der Befehl schreibt ohne Rückfrage.** Die Mailvorlagen mit
dem Code (`supabase/templates/`) lassen sich erst setzen, wenn ein eigener
SMTP-Anbieter eingetragen ist - dann die Vorlagenzeilen in `config.toml`
einkommentieren und erneut pushen.

### Secrets der Edge Functions

| Secret | Zweck |
|---|---|
| `BREVO_API_KEY` | Einladungsmails über Brevo |
| `MAIL_FROM`, `MAIL_FROM_NAME` | Absender, Domain bei Brevo mit DKIM bestätigt |
| `APNS_KEY`, `APNS_KEY_ID`, `APNS_TEAM_ID` | Push an iOS |
| `FCM_SERVICE_ACCOUNT` | Push an Android |
| `SPORTSDB_KEY` | optional, eigener Schlüssel für den Wappendienst |

Dazu im Dashboard: Brevo als Custom SMTP für die Bestätigungsmails der
Anmeldung - sonst greift die Drossel von zwei Mails pro Stunde.
