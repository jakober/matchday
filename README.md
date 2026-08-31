# Matchday

Terminkalender für Fußballspiele. Eine Codebasis für Android und iOS
(Kotlin Multiplatform + Compose Multiplatform).

## Funktionen

- Kalender abonnieren über jede ICS- oder webcal-Adresse; der Feed wird vor dem
  Speichern geprüft und mit Namen und Terminanzahl angezeigt
- Spiele chronologisch als Liste (nach Monaten gruppiert) oder als Monatsraster
- Zusagen pro Spiel: dabei, nicht dabei, oder zurücknehmen
- Profil mit Name und Farbe beim ersten Start
- Erinnerung vor Anpfiff, Vorlaufzeit einstellbar (Standard: eine Stunde)
- Nachfrage, wenn ein Spiel in einer Woche ansteht und die Zusage noch fehlt

## Aufbau

```
composeApp/src/commonMain   gemeinsamer Code: Oberfläche, Datenhaltung, ICS-Parser
composeApp/src/androidMain  Android: Alarme, Benachrichtigungen, Activity
composeApp/src/iosMain      iOS: lokale Benachrichtigungen, Einstiegspunkt
iosApp/                     Xcode-Projekt (wird per XcodeGen erzeugt)
```

Nennenswerte Entscheidungen:

- **Der ICS-Parser ist selbst geschrieben.** Die verbreiteten Bibliotheken sind
  JVM-only und auf iOS nicht verwendbar. Er deckt gefaltete Zeilen, die drei
  Datumsformate und maskierten Text ab. Nicht unterstützt: RRULE.
- **Benachrichtigungen sind lokal, nicht per Push-Server.** Beide Anlässe stehen
  zum Planungszeitpunkt fest, damit braucht es weder Firebase noch APNs.
  Begrenzt auf 60 Vormerkungen, weil iOS bei 64 stillschweigend abschneidet.
- **Keine Datenbank.** Ein Spielplan sind ein paar hundert Einträge, die als
  JSON in SharedPreferences bzw. NSUserDefaults liegen.
- **Die .xcodeproj liegt nicht im Repo.** Sie wird aus `iosApp/project.yml`
  erzeugt — die Projektdatei ist binär und erzeugt sonst laufend Konflikte.

## Bauen

### Android

Voraussetzungen: JDK 17+, Android SDK (Platform 35). `local.properties` mit
`sdk.dir=` anlegen oder `ANDROID_HOME` setzen.

```
./gradlew :composeApp:assembleDebug
```

APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

Tests:

```
./gradlew :composeApp:testDebugUnitTest
```

### iOS

Braucht macOS mit Xcode — unter Windows nicht möglich. Der CI-Workflow baut
iOS deshalb auf einem macOS-Runner gegen den Simulator, ohne Signierung. Lokal
auf einem Mac:

```
brew install xcodegen
cd iosApp && xcodegen generate
open iosApp.xcodeproj
```

Für die Installation auf einem echten Gerät oder TestFlight wird das Apple
Developer Program benötigt.

## CI

`.github/workflows/build.yml` baut bei jedem Push beide Plattformen und legt
APK und iOS-App-Bundle als Artefakte ab.
