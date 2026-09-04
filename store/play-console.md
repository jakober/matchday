# Play Console: Eintrag und geschlossener Test

Alle Angaben für die Play Console an einer Stelle. Reihenfolge wie in der
Console. Texte kommen aus `de.md` (Standardsprache) und `en.md` (Übersetzung
Englisch, en-US), Bilder aus `out/`.

## 1. App anlegen

| Feld | Wert |
|---|---|
| App-Name | Matchday - Wer kommt? |
| Standardsprache | Deutsch (de-DE) |
| App oder Spiel | App |
| Kostenlos oder kostenpflichtig | Kostenlos |
| Erklärungen | Richtlinien und US-Exportgesetze bestätigen |

## 2. Store-Eintrag (Haupteintrag)

| Feld | Wert |
|---|---|
| Kurzbeschreibung DE | aus `de.md`, Abschnitt „Kurzbeschreibung" |
| Vollständige Beschreibung DE | aus `de.md`, Abschnitt „Beschreibung" |
| Übersetzung en-US | Name „Matchday - Who's in?", Texte aus `en.md` |
| App-Symbol | `out/play-icon-512.png` |
| Funktionsgrafik | `out/feature-graphic-1024x500.png` |
| Smartphone-Screenshots DE | `out/android/1-de.png` … `5-de.png` |
| Smartphone-Screenshots EN | `out/android/1-en.png` … `5-en.png` |
| Tablet-Screenshots | keine (App ist für Smartphones) |
| Kategorie | Sport |
| E-Mail-Adresse (Kontakt) | mat.jakober@gmail.com |
| Website | https://jakober.github.io/matchday/ |

## 3. App-Inhalte (alle Erklärungen)

| Abschnitt | Antwort |
|---|---|
| Datenschutzerklärung | https://jakober.github.io/matchday/ |
| Werbung | Nein, enthält keine Werbung |
| Zugriff auf App | „Alle oder einige Funktionen sind eingeschränkt" → Anmeldedaten des Demo-Kontos (siehe unten) und Hinweis: „Nach der Anmeldung ist das Konto bereits Mitglied einer Gruppe mit Spielen und Zusagen." |
| Einstufung von Inhalten (IARC) | Kategorie „Dienstprogramm, Produktivität, Kommunikation oder Sonstiges"; alle Fragen zu Gewalt, Sex, Drogen, Glücksspiel: Nein; nutzergenerierte Inhalte: Ja (Namen, kurze Absagegründe, nur innerhalb geschlossener Gruppen sichtbar); Standortweitergabe: Nein; Käufe: Nein |
| Zielgruppe | 18 und älter |
| Nachrichten-App | Nein |
| COVID-19 | Nein |
| Datensicherheit | Tabelle unten |
| Behördliche Apps | Nein |
| Finanzfunktionen | Keine |
| Gesundheit | Keine |

### Datensicherheit

- Erhebt oder teilt die App Nutzerdaten? **Ja**
- Alle Nutzerdaten werden bei der Übertragung verschlüsselt: **Ja**
- Möglichkeit, die Löschung der Daten zu beantragen: **Ja**
  - Löschung in der App: **Ja**
  - Web-Adresse zur Kontolöschung: **https://jakober.github.io/matchday/konto-loeschen.html**

| Datentyp | Erhoben | Weitergegeben | Erforderlich | Zweck |
|---|---|---|---|---|
| Persönliche Daten → Name | ja | nein | ja | App-Funktionen |
| Persönliche Daten → E-Mail-Adresse | ja | nein | ja | App-Funktionen, Kontoverwaltung |
| Nachrichten → Sonstige nutzergenerierte Inhalte (Zusagen, Absagegründe) | ja | nein | ja | App-Funktionen |
| Geräte- oder andere IDs (Push-Token) | ja | nein | ja | App-Funktionen |

Alles andere (Standort, Kontakte, Fotos, Finanzen, Gesundheit, App-Aktivität,
Absturzberichte): **nicht erhoben**.

## 4. Länder

**Alle Länder und Regionen** freigeben (Testen → Geschlossener Test → Länder
→ „Alle Länder hinzufügen"). Die Tester kommen aus aller Welt. Für die
spätere Produktion ebenfalls alle Länder.

## 5. Geschlossener Test

1. Testen → **Geschlossener Test** → Track anlegen, Name „Testercommunity".
2. **Tester**: E-Mail-Liste anlegen, Name „Testercommunity", Google-Gruppe
   `testers-community@googlegroups.com` eintragen.
3. **Feedback-Adresse**: mat.jakober@gmail.com
4. **Länder**: alle.
5. **Neuen Release erstellen** → App Bundle `out/release/matchday-0.37.aab`
   hochladen. Beim ersten Upload fragt Google nach Play App Signing:
   „Von Google generierten Schlüssel verwenden" bestätigen - unser Schlüssel
   ist dann automatisch der Upload-Schlüssel.
6. Release-Name „0.37", Versionshinweise DE/EN:
   - de-DE: „Erste Testversion."
   - en-US: „First test release."
7. Prüfen und veröffentlichen. Danach erscheint der **Opt-in-Link** für die
   Tester (Testen → Geschlossener Test → Tester → „Link kopieren"). Den Link
   bekommt die Testercommunity.

Google zählt ab dem Moment, in dem 12 Tester opt-in gemacht und die App
installiert haben, 14 Tage. Danach unter „Dashboard" den Produktionszugang
beantragen.

## Demo-Konto für die Prüfer (Google und Apple)

Ein echtes Konto in der App, das beide Prüfteams benutzen:

1. In der App registrieren mit einer Adresse, die dir gehört (z.B.
   `matchday-demo@…`), Name „Demo", Passwort merken.
2. Gruppe „Demo-Gruppe" anlegen, Bundesliga und Premier League hinzufügen,
   zwei bis drei Zusagen setzen.
3. Diese Zugangsdaten bei Google unter „Zugriff auf App" und bei Apple unter
   „Prüfinformationen → Anmeldung erforderlich" eintragen.

Das Konto darf nie gelöscht werden, solange die App in den Stores ist.
