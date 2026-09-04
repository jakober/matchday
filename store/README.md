# Veröffentlichung in App Store und Play Store

Alles, was für die beiden Stores gebraucht wird, liegt hier:

| Datei | Inhalt |
|---|---|
| `de.md`, `en.md` | Name, Untertitel, Beschreibung, Schlüsselwörter, Kategorie, URLs |
| `icon.py` | erzeugt das Icon in allen Formaten (`python store/icon.py`) |
| `out/` | erzeugte Bilder: Play-Icon 512, Icon 1024, Feature-Grafik 1024×500 |
| `frame.py` | rahmt rohe Screenshots für beide Stores (`python store/frame.py`) |
| `../docs/index.html`, `../docs/privacy.html` | Datenschutzerklärung DE/EN, online unter jakober.github.io/matchday |

## Screenshots

Beide Stores wollen echte Screenshots mit echtem Inhalt. Die App auf dem
eigenen Gerät mit einer Gruppe, zwei Kalendern und ein paar Zusagen zeigt
mehr als jede Attrappe. Fünf Bilder reichen, in dieser Reihenfolge:

1. Liste mit Spielen, Wappen, grün umrandeter Zusage
2. Monatskalender
3. Spieldetail mit „Wer kommt mit" und Zusage-Knöpfen
4. Kalender hinzufügen mit Suche (z.B. „Bayern" eingetippt)
5. Gruppe mit Mitgliedern und Einladen

**iPhone:** Screenshots direkt vom Gerät (Seitentaste + Lauter). Apple verlangt
das 6,9"-Format (1320×2868) oder 6,7" (1290×2796); ein iPhone 15/16 Pro Max
liefert das. Kleinere iPhones lassen sich skalieren - dafür `frame.py`.
Da die App nur fürs iPhone eingereicht wird (kein iPad), entfallen
iPad-Screenshots.

**Android:** Screenshots vom Gerät genügen (mindestens 320 px, höchstens
3840 px Kante, Seitenverhältnis 16:9 bis 9:16).

`frame.py` legt die rohen Bilder auf dunklen Grund, skaliert auf die
Store-Größen und schreibt oben eine kurze Zeile dazu.

## App Store (Apple)

1. App Store Connect → App → **App-Informationen**: Name „Matchday - Wer kommt?",
   Untertitel, Kategorie Sport, Datenschutz-URL.
2. **Preise und Verfügbarkeit**: kostenlos, alle Länder oder nur DACH.
3. **App-Datenschutz** (Nutrition Label) - Angaben unter „Datenschutz-Angaben".
4. **Version 1.0 vorbereiten**: Screenshots hochladen, Beschreibung und
   Schlüsselwörter aus `de.md`/`en.md` (Deutsch als Primärsprache, Englisch
   als Lokalisierung), Build aus TestFlight wählen.
5. **Prüfinformationen**: Die App verlangt eine Anmeldung, also braucht Apple
   ein **Demo-Konto**: ein Konto mit E-Mail und Passwort, das bereits in einer
   Gruppe mit zwei Kalendern und ein paar Zusagen ist. Ohne das wird die App
   abgelehnt. Dazu eine Notiz: „Einladungscodes kommen per E-Mail; für die
   Prüfung ist das Demo-Konto bereits Mitglied einer Gruppe."
6. Einreichen. Prüfung dauert meist ein bis zwei Tage.

## Play Store (Google)

1. Play Console: Entwicklerkonto (einmalig 25 $).
   **Wichtig für neue Privatkonten:** Google verlangt vor der ersten
   Veröffentlichung einen geschlossenen Test mit mindestens 12 Testern über
   14 Tage am Stück. Das ist die längste Wartezeit im ganzen Ablauf - früh
   anfangen, Tester über eine E-Mail-Liste einladen.
2. App anlegen: Name, Standardsprache Deutsch, App, kostenlos.
3. **Store-Eintrag**: Kurz- und Vollbeschreibung, Icon 512 (`out/play-icon-512.png`),
   Feature-Grafik (`out/feature-graphic-1024x500.png`), Screenshots.
   Englische Übersetzung als zweite Sprache.
4. **App-Inhalte**: Datenschutz-URL, Werbung „Nein", Zugriff auf App
   („Alle Funktionen brauchen eine Anmeldung" → Demo-Konto wie bei Apple
   hinterlegen), Inhaltsfreigabe (IARC-Fragebogen), Zielgruppe (18+ genügt,
   dann entfällt der Familienteil), Datensicherheit - Angaben unten.
5. **Signierung**: Der Store-Build muss ein Release-Build mit eigenem
   Signierschlüssel sein, nicht die Debug-APK. Play App Signing übernimmt
   den Schlüssel; der Upload-Schlüssel wird einmal erzeugt und in GitHub als
   Secret hinterlegt (Keystore base64 + Passwort). Erst dann kann der
   Workflow ein signiertes App Bundle (`.aab`) bauen.
6. Geschlossener Test → nach 14 Tagen Produktionszugang beantragen → Produktion.

## Datenschutz-Angaben (für beide Stores gleich)

| Datenart | Erhoben | Zweck | Verknüpft mit Nutzer | Tracking |
|---|---|---|---|---|
| E-Mail-Adresse | ja | Konto, Anmeldung, Einladungen | ja | nein |
| Name (selbstgewählt) | ja | Anzeige in der Gruppe | ja | nein |
| Nutzerinhalte (Zusagen, Absagegründe, Gruppenname) | ja | Funktion der App | ja | nein |
| Geräte-Kennung für Push | ja | Benachrichtigungen | ja | nein |
| Standort, Kontakte, Fotos, Nutzungsdaten, Diagnosedaten | nein | – | – | – |

Verschlüsselung bei der Übertragung: ja (HTTPS). Löschung auf Anfrage: ja
(E-Mail an den Betreiber; Abmelden in der App entfernt lokale Daten).
Kein Verkauf, keine Weitergabe zu Werbezwecken.

## Was noch fehlt, bevor eingereicht werden kann

- [ ] Demo-Konto anlegen und in eine Gruppe mit Inhalt bringen
- [ ] Screenshots aufnehmen (5 je Plattform) und mit `frame.py` rahmen
- [ ] Play: Upload-Keystore erzeugen, als Secret hinterlegen, Release-Workflow
- [ ] Play: Entwicklerkonto, geschlossener Test (12 Tester, 14 Tage)
- [ ] Apple: Name im App-Eintrag ändern, Datenschutz-Angaben ausfüllen
