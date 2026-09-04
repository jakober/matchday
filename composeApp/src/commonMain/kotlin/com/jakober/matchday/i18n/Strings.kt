package com.jakober.matchday.i18n

import kotlinx.datetime.LocalDateTime

/** Sprache des Geraets als ISO-Kuerzel, etwa "de" oder "en". */
expect fun deviceLanguage(): String

/**
 * Alle Texte der App, gewaehlt nach Gerätesprache. Ein Objekt statt
 * Ressourcendateien: So lassen sich die Texte auch dort verwenden, wo kein
 * Composable laeuft - in Benachrichtigungen, Fehlermeldungen des Abrufs und
 * im Container. Deutsch ist die Rueckfallebene, weil die Nutzer Deutsch lesen.
 */
val S: Strings by lazy { if (deviceLanguage().startsWith("en")) En else De }

/** Kuerzel fuer den Server, der Benachrichtigungen in der Sprache des Empfaengers schreibt. */
val currentLocale: String get() = if (S === En) "en" else "de"

@Suppress("PropertyName")
interface Strings {
    // -- Allgemein
    val appName: String
    val back: String
    val cancel: String
    val save: String
    val remove: String
    val copy: String
    val failed: String
    val clockSuffix: String

    // -- Anmeldung
    val authIntroRegister: String
    val authIntroSignIn: String
    val nameQuestion: String
    val emailLabel: String
    val passwordLabel: String
    val passwordLabelNew: String
    val showPassword: String
    val hidePassword: String
    val forgotPassword: String
    val createAccount: String
    val signIn: String
    val haveAccount: String
    val noAccount: String
    val haveInvite: String
    val acceptInviteIntro: String
    val acceptInvite: String
    val inviteCodeLabel: String
    val inviteeName: String
    val inviteNameHint: String
    val codeTitle: String
    val codeCancel: String
    fun codeIntro(email: String): String
    val codeLabel: String
    val codeResend: String
    val confirm: String
    val resetTitle: String
    val backToSignIn: String
    val newCodeSent: String
    val saveFailed: String
    val confirmFailed: String
    val sendFailed: String
    val newPasswordTitle: String
    val newPasswordIntro: String
    val newPasswordLabel: String
    val passwordChanged: String
    val errInvalidLogin: String
    val errAlreadyRegistered: String
    val errWeakPassword: String
    val errCodeInvalid: String
    val errRateLimit: String
    val errInvalidEmail: String
    fun errAuthGeneric(message: String?): String
    val errInviteFailed: String
    val errMembershipRead: String

    // -- Einstieg
    val onboardingIntro: String
    val color: String
    val letsGo: String

    // -- Start
    val refresh: String
    val calendars: String
    val emptyNoCalendarTitle: String
    val emptyNoCalendarBody: String
    val emptyNoCalendarAction: String
    val emptyNoMatchesTitle: String
    val emptyNoMatchesBody: String
    val emptyNoMatchesAction: String
    val list: String
    val month: String
    val prevMonth: String
    val nextMonth: String
    val monthHint: String
    val noMatchThatDay: String
    val importantMatch: String
    val statusIn: String
    val statusOut: String
    val statusOpen: String
    val allDay: String

    // -- Spieldetail
    val unmarkImportant: String
    val markImportant: String
    val whoComes: String
    val areYouIn: String
    val whyNotLabel: String
    val whyNotPlaceholder: String
    val declineWithoutReason: String
    val withdraw: String
    val nobodyAnswered: String
    val noAcceptYetDot: String
    fun declinesHeader(n: Int): String
    val youSuffix: String

    // -- Teilnehmer
    val noAcceptYet: String
    val youDeclined: String
    val oneDecline: String
    fun nDeclines(n: Int): String
    fun declinedSuffix(n: Int): String
    val youAreIn: String
    fun xIsIn(name: String): String
    fun youAndN(n: Int): String
    fun nIn(n: Int): String

    // -- Datum
    val weekdaysShort: List<String>
    val weekdaysLong: List<String>
    val months: List<String>
    val today: String
    val tomorrow: String
    val dayAfterTomorrow: String
    fun inDays(n: Int): String
    val past: String
    fun dateTime(dateTime: LocalDateTime, date: String, time: String): String

    // -- Erinnerungen
    val askAreYouIn: String
    val isTomorrow: String
    val isInAWeek: String
    fun isInDays(n: Int): String
    fun notAnswered(title: String, whenText: String): String
    val kickoffTomorrow: String
    fun kickoffInDays(n: Int): String
    val kickoffInHour: String
    fun kickoffInHours(n: Int): String
    fun kickoffInMinutes(n: Int): String

    // -- Kalenderabruf
    val errFetch: String
    val errServerNoAnswer: String
    val errInvalidAddress: String
    val errNotFound: String
    val errNotPublic: String
    fun errServerStatus(code: Int): String
    val errNoCalendar: String

    // -- Container
    val needGroup: String
    val calendarExists: String
    val onlyAdminCalendars: String
    fun addFailed(message: String): String
    val removeFailed: String
    val matchUnknown: String
    val changeFailed: String

    // -- Einstellungen
    val settings: String
    val profile: String
    val name: String
    val account: String
    val group: String
    val noGroup: String
    val groupSubtitleNone: String
    val groupSubtitleInvite: String
    fun membersN(n: Int): String
    val reminderSection: String
    val remindBefore: String
    val delivery: String
    val openRsvps: String
    val askWeekBefore: String
    val askWeekBeforeDesc: String
    val calendarSection: String
    fun calendarsN(n: Int): String
    fun deviceId(id: String?): String
    val checking: String
    val notifAllowed: String
    val notifDenied: String
    val exactOk: String
    val exactFail: String
    val allowExact: String
    val exactPromptTitle: String
    val exactPromptText: String
    val exactPromptAllow: String
    val exactPromptLater: String
    val exactPromptNever: String
    val pendingNone: String
    val pendingOne: String
    fun pendingN(n: Int): String
    val pushOk: String
    val pushNoGroup: String
    val pushFail: String
    val sendTest: String
    val testHint: String
    val changePassword: String
    val newPasswordMin: String
    val notSignedIn: String
    val signOut: String
    val signOutQuestion: String
    val signOutText: String
    fun daysShort(n: Int): String
    fun hoursShort(n: Int): String
    fun minutesShort(n: Int): String

    // -- Gruppe
    val yourGroup: String
    val groupTitle: String
    val groupIntro: String
    val newGroup: String
    val groupNameLabel: String
    val groupNamePlaceholder: String
    val adminHint: String
    val createGroup: String
    val orJoin: String
    val inviteCode: String
    val sixChars: String
    val join: String
    val admin: String
    val onlyImportantHint: String
    val invite: String
    val inviteIntro: String
    val emailOptional: String
    val allMatches: String
    val onlyImportant: String
    fun membersHeader(n: Int): String
    val leaveGroup: String
    fun removeMemberQ(name: String): String
    val removeMemberText: String
    val seesOnlyImportant: String
    val seesAll: String
    fun sentTo(email: String): String
    fun sendWarning(message: String): String
    val onlyImportantSmall: String
    val you: String
    fun removeMemberDesc(name: String): String
    val membershipLostGate: String
    val membershipLostSettings: String

    // -- Kalender hinzufuegen
    val addCalendar: String
    val searchLabel: String
    val searchHint: String
    val nothingFound: String
    val orEnterAddress: String
    val importIntro: String
    val hideHelp: String
    val whereAddress: String
    val addressLabel: String
    val check: String
    val nameInApp: String
    val groupWide: String
    val add: String
    val previewNone: String
    val previewOne: String
    fun previewN(n: Int): String
    val recurringWarning: String
    val help1: String
    val help2: String
    val help3: String
    val checkFailed: String
    val addFailedShort: String

    // -- Kalenderliste
    val noCalendarAdmin: String
    val noCalendarMember: String
    val autoUpdateHint: String
    val adminDecidesHint: String
    val removeCalendarQ: String
    fun removeCalendarText(name: String): String
    val hidden: String
    val noMatchesYet: String
    fun matchesN(n: Int): String
    fun matchesNStand(n: Int, stand: String): String
    val removeCalendar: String
}

object De : Strings {
    override val appName = "Matchday"
    override val back = "Zurück"
    override val cancel = "Abbrechen"
    override val save = "Speichern"
    override val remove = "Entfernen"
    override val copy = "Kopieren"
    override val failed = "Fehlgeschlagen"
    override val clockSuffix = " Uhr"

    override val authIntroRegister = "Leg dein Konto an. Die Adresse bestätigst du gleich mit einem Code aus der Mail."
    override val authIntroSignIn = "Melde dich mit deinem Konto an."
    override val nameQuestion = "Wie heißt du?"
    override val emailLabel = "E-Mail-Adresse"
    override val passwordLabel = "Passwort"
    override val passwordLabelNew = "Passwort (mindestens 8 Zeichen)"
    override val showPassword = "Passwort anzeigen"
    override val hidePassword = "Passwort verbergen"
    override val forgotPassword = "Passwort vergessen?"
    override val createAccount = "Konto anlegen"
    override val signIn = "Anmelden"
    override val haveAccount = "Ich habe schon ein Konto"
    override val noAccount = "Noch kein Konto? Registrieren"
    override val haveInvite = "Ich habe eine Einladung"
    override val acceptInviteIntro = "Gib den Code aus der Einladungsmail ein und wähle ein Passwort - mehr braucht es nicht."
    override val acceptInvite = "Einladung annehmen"
    override val inviteCodeLabel = "Einladungscode"
    override val inviteeName = "Name der Person"
    override val inviteNameHint = "Mit Name und Adresse muss sich die Person nicht registrieren - sie wählt nur ein Passwort."
    override val codeTitle = "Adresse bestätigen"
    override val codeCancel = "Andere Adresse verwenden"
    override fun codeIntro(email: String) = "Wir haben einen Code an $email geschickt. Schau auch im Spam-Ordner nach."
    override val codeLabel = "Code aus der Mail"
    override val codeResend = "Code erneut schicken"
    override val confirm = "Bestätigen"
    override val resetTitle = "Passwort zurücksetzen"
    override val backToSignIn = "Zurück zur Anmeldung"
    override val newCodeSent = "Ein neuer Code ist unterwegs."
    override val saveFailed = "Speichern fehlgeschlagen"
    override val confirmFailed = "Bestätigung fehlgeschlagen"
    override val sendFailed = "Senden fehlgeschlagen"
    override val newPasswordTitle = "Neues Passwort"
    override val newPasswordIntro = "Wähle ein neues Passwort mit mindestens 8 Zeichen."
    override val newPasswordLabel = "Neues Passwort"
    override val passwordChanged = "Passwort geändert."
    override val errInvalidLogin = "E-Mail oder Passwort stimmt nicht."
    override val errAlreadyRegistered = "Für diese Adresse gibt es schon ein Konto - melde dich an."
    override val errWeakPassword = "Das Passwort braucht mindestens 8 Zeichen."
    override val errCodeInvalid = "Der Code ist abgelaufen oder falsch. Fordere einen neuen an."
    override val errRateLimit = "Zu viele Mails in kurzer Zeit - bitte kurz warten."
    override val errInvalidEmail = "Das ist keine gültige E-Mail-Adresse."
    override fun errAuthGeneric(message: String?) = "Anmeldung fehlgeschlagen: $message"
    override val errInviteFailed = "Einladung fehlgeschlagen"
    override val errMembershipRead = "Mitgliedschaft konnte nicht gelesen werden"

    override val onboardingIntro = "Alle Spiele an einem Ort - und deine Truppe weiß, wer dabei ist."
    override val color = "FARBE"
    override val letsGo = "Los geht's"

    override val refresh = "Aktualisieren"
    override val calendars = "Kalender"
    override val emptyNoCalendarTitle = "Noch kein Kalender"
    override val emptyNoCalendarBody = "Der Admin eurer Gruppe fügt Kalender hinzu - zum Beispiel den Spielplan der Bundesliga. Danach stehen alle Termine automatisch hier."
    override val emptyNoCalendarAction = "Kalender ansehen"
    override val emptyNoMatchesTitle = "Keine Spiele gefunden"
    override val emptyNoMatchesBody = "Der Kalender ist abonniert, enthält aber keine anstehenden Termine."
    override val emptyNoMatchesAction = "Neu laden"
    override val list = "Liste"
    override val month = "Monat"
    override val prevMonth = "Voriger Monat"
    override val nextMonth = "Nächster Monat"
    override val monthHint = "Wische für den nächsten Monat. Tippe einen Spieltag an."
    override val noMatchThatDay = "Kein Spiel an diesem Tag."
    override val importantMatch = "Wichtiges Spiel"
    override val statusIn = "Dabei"
    override val statusOut = "Nicht dabei"
    override val statusOpen = "Offen"
    override val allDay = "Ganztägig"

    override val unmarkImportant = "Hervorhebung aufheben"
    override val markImportant = "Als wichtig hervorheben"
    override val whoComes = "WER KOMMT MIT"
    override val areYouIn = "BIST DU DABEI?"
    override val whyNotLabel = "Warum nicht? (optional)"
    override val whyNotPlaceholder = "z.B. bin im Urlaub"
    override val declineWithoutReason = "Ohne Grund absagen"
    override val withdraw = "Antwort zurücknehmen"
    override val nobodyAnswered = "Noch hat niemand geantwortet."
    override val noAcceptYetDot = "Noch keine Zusage."
    override fun declinesHeader(n: Int) = if (n == 1) "1 ABSAGE" else "$n ABSAGEN"
    override val youSuffix = " (du)"

    override val noAcceptYet = "Noch keine Zusage"
    override val youDeclined = "Du hast abgesagt"
    override val oneDecline = "1 Absage"
    override fun nDeclines(n: Int) = "$n Absagen"
    override fun declinedSuffix(n: Int) = " · $n abgesagt"
    override val youAreIn = "Du bist dabei"
    override fun xIsIn(name: String) = "$name ist dabei"
    override fun youAndN(n: Int) = "Du und $n weitere"
    override fun nIn(n: Int) = "$n dabei"

    override val weekdaysShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    override val weekdaysLong = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    override val months = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    override val today = "Heute"
    override val tomorrow = "Morgen"
    override val dayAfterTomorrow = "Übermorgen"
    override fun inDays(n: Int) = "In $n Tagen"
    override val past = "Vorbei"
    override fun dateTime(dateTime: LocalDateTime, date: String, time: String) = "$date, $time Uhr"

    override val askAreYouIn = "Bist du dabei?"
    override val isTomorrow = "ist morgen"
    override val isInAWeek = "ist in einer Woche"
    override fun isInDays(n: Int) = "ist in $n Tagen"
    override fun notAnswered(title: String, whenText: String) = "$title $whenText - du hast noch nicht geantwortet."
    override val kickoffTomorrow = "Morgen um diese Zeit ist Anpfiff"
    override fun kickoffInDays(n: Int) = "In $n Tagen ist Anpfiff"
    override val kickoffInHour = "Anpfiff in einer Stunde"
    override fun kickoffInHours(n: Int) = "Anpfiff in $n Stunden"
    override fun kickoffInMinutes(n: Int) = "Anpfiff in $n Minuten"

    override val errFetch = "Abruf fehlgeschlagen"
    override val errServerNoAnswer = "Der Server antwortet nicht. Bitte später erneut versuchen."
    override val errInvalidAddress = "Das ist keine gültige Adresse. Bitte auf Tippfehler prüfen."
    override val errNotFound = "Die Adresse gibt es nicht. Bitte auf Tippfehler prüfen."
    override val errNotPublic = "Der Kalender ist nicht öffentlich - diese Adresse verlangt eine Anmeldung."
    override fun errServerStatus(code: Int) = "Der Server antwortete mit Fehler $code."
    override val errNoCalendar = "Unter dieser Adresse liegt kein Kalender. Wahrscheinlich ist es die Adresse der Webseite statt die des Kalenders - suche dort nach „Kalender abonnieren“."

    override val needGroup = "Dafür brauchst du eine Gruppe"
    override val calendarExists = "Diesen Kalender habt ihr schon."
    override val onlyAdminCalendars = "Nur der Admin kann Kalender hinzufügen."
    override fun addFailed(message: String) = "Hinzufügen fehlgeschlagen: $message"
    override val removeFailed = "Entfernen nicht möglich"
    override val matchUnknown = "Spiel konnte nicht zugeordnet werden"
    override val changeFailed = "Ändern nicht möglich"

    override val settings = "Einstellungen"
    override val profile = "PROFIL"
    override val name = "Name"
    override val account = "KONTO"
    override val group = "GRUPPE"
    override val noGroup = "Keine Gruppe"
    override val groupSubtitleNone = "Zusagen bleiben auf diesem Gerät"
    override val groupSubtitleInvite = "Lade jemanden mit dem Einladungscode ein"
    override fun membersN(n: Int) = "$n Mitglieder"
    override val reminderSection = "ERINNERUNG VOR ANPFIFF"
    override val remindBefore = "Vor dem Spiel erinnern"
    override val delivery = "ZUSTELLUNG"
    override val openRsvps = "OFFENE ZUSAGEN"
    override val askWeekBefore = "Eine Woche vorher nachfragen"
    override val askWeekBeforeDesc = "Erinnert dich, wenn ein Spiel in sieben Tagen ansteht und du noch nicht geantwortet hast."
    override val calendarSection = "KALENDER"
    override fun calendarsN(n: Int) = if (n == 1) "1 Kalender" else "$n Kalender"
    override fun deviceId(id: String?) = "Gerätekennung: ${id ?: "nicht angemeldet"}"
    override val checking = "Wird geprüft ..."
    override val notifAllowed = "Benachrichtigungen erlaubt"
    override val notifDenied = "Benachrichtigungen nicht erlaubt - in den Systemeinstellungen freigeben"
    override val exactOk = "Erinnerungen kommen auf die Minute genau"
    override val exactFail = "Ohne exakte Alarme kann eine Erinnerung einige Minuten später kommen"
    override val allowExact = "Exakte Alarme erlauben"
    override val exactPromptTitle = "Erinnerungen auf die Minute?"
    override val exactPromptText = "Android darf Erinnerungen ohne diese Erlaubnis um einige Minuten verschieben. Für „Anpfiff in einer Stunde“ ist das ungünstig. Die Erlaubnis gibst du einmal in den Systemeinstellungen."
    override val exactPromptAllow = "Erlauben"
    override val exactPromptLater = "Später"
    override val exactPromptNever = "Nicht mehr fragen"
    override val pendingNone = "Zurzeit keine Erinnerung vorgemerkt"
    override val pendingOne = "1 Erinnerung vorgemerkt"
    override fun pendingN(n: Int) = "$n Erinnerungen vorgemerkt"
    override val pushOk = "Für Meldungen der Gruppe erreichbar"
    override val pushNoGroup = "Ohne Gruppe gibt es keine Meldungen der anderen."
    override val pushFail = "Nicht für Meldungen der Gruppe erreichbar"
    override val sendTest = "Testbenachrichtigung senden"
    override val testHint = "Kommt in etwa 10 Sekunden. Schließe die App kurz, dann siehst du sie wie im Alltag."
    override val changePassword = "Passwort ändern"
    override val newPasswordMin = "Neues Passwort (mindestens 8 Zeichen)"
    override val notSignedIn = "Nicht angemeldet"
    override val signOut = "Abmelden"
    override val signOutQuestion = "Abmelden?"
    override val signOutText = "Deine Gruppe und deine Zusagen bleiben in deinem Konto erhalten. Auf diesem Gerät wird alles entfernt, bis du dich wieder anmeldest."
    override fun daysShort(n: Int) = "$n Tag"
    override fun hoursShort(n: Int) = "$n Std"
    override fun minutesShort(n: Int) = "$n Min"

    override val yourGroup = "Deine Gruppe"
    override val groupTitle = "Gruppe"
    override val groupIntro = "Matchday lebt von der Gruppe: Ihr teilt die Kalender und seht, wer zu einem Spiel mitkommt. Lege eine Gruppe an oder tritt mit einem Einladungscode bei."
    override val newGroup = "NEUE GRUPPE"
    override val groupNameLabel = "Name der Gruppe"
    override val groupNamePlaceholder = "z.B. Stammtisch"
    override val adminHint = "Wer die Gruppe anlegt, verwaltet sie: einladen und Spiele hervorheben."
    override val createGroup = "Gruppe erstellen"
    override val orJoin = "ODER BEITRETEN"
    override val inviteCode = "Einladungscode"
    override val sixChars = "6 Zeichen"
    override val join = "Beitreten"
    override val admin = "ADMIN"
    override val onlyImportantHint = "Du siehst nur die hervorgehobenen Spiele."
    override val invite = "EINLADEN"
    override val inviteIntro = "Jede Einladung gilt einmal. Du legst dabei fest, was der Eingeladene zu sehen bekommt. Mit Adresse geht der Code direkt per E-Mail raus, ohne siehst du ihn hier zum Weitergeben."
    override val emailOptional = "E-Mail-Adresse (optional)"
    override val allMatches = "Alle Spiele"
    override val onlyImportant = "Nur wichtige"
    override fun membersHeader(n: Int) = if (n == 1) "1 MITGLIED" else "$n MITGLIEDER"
    override val leaveGroup = "Gruppe verlassen"
    override fun removeMemberQ(name: String) = "$name entfernen?"
    override val removeMemberText = "Alle Zu- und Absagen dieser Person verschwinden aus der Gruppe. Für eine Rückkehr braucht sie eine neue Einladung."
    override val seesOnlyImportant = "sieht nur wichtige Spiele"
    override val seesAll = "sieht alle Spiele"
    override fun sentTo(email: String) = "Per E-Mail an $email geschickt"
    override fun sendWarning(message: String) = "$message - gib den Code stattdessen so weiter."
    override val onlyImportantSmall = "nur wichtige Spiele"
    override val you = "Du"
    override fun removeMemberDesc(name: String) = "$name entfernen"
    override val membershipLostGate = "Du bist nicht mehr Mitglied deiner bisherigen Gruppe. Lege eine neue an oder lass dich neu einladen."
    override val membershipLostSettings = "Deine bisherige Gruppe gehört zu einer früheren Installation der App und ist nicht mehr erreichbar. Lege eine neue an oder lass dich neu einladen."

    override val addCalendar = "Kalender hinzufügen"
    override val searchLabel = "VEREIN ODER LIGA SUCHEN"
    override val searchHint = "z.B. Bayern, Dortmund, 2. Bundesliga"
    override val nothingFound = "Nichts gefunden - unten kannst du die Adresse selbst eingeben."
    override val orEnterAddress = "ODER ADRESSE EINGEBEN"
    override val importIntro = "Fast jeder Verein und jede Liga veröffentlicht den Spielplan als Kalender zum Abonnieren. Du brauchst dessen Adresse - eine Zeile, die mit https:// oder webcal:// beginnt und meist auf .ics endet."
    override val hideHelp = "Erklärung ausblenden"
    override val whereAddress = "Wo finde ich so eine Adresse?"
    override val addressLabel = "Adresse des Kalenders"
    override val check = "Prüfen"
    override val nameInApp = "Name in der App"
    override val groupWide = "Der Kalender gilt für die ganze Gruppe. Alle sehen die Spiele daraus und können zusagen."
    override val add = "Hinzufügen"
    override val previewNone = "Erreichbar, aber keine Termine gefunden"
    override val previewOne = "1 Termin gefunden"
    override fun previewN(n: Int) = "$n Termine gefunden"
    override val recurringWarning = "Möglich ist, dass der Kalender nur wiederkehrende Termine enthält - die kann Matchday nicht lesen."
    override val help1 = "Auf der Webseite des Vereins beim Spielplan nach „Kalender abonnieren“, „iCal“, „ICS“ oder einem Kalendersymbol suchen. Nicht darauf tippen, sondern lange gedrückt halten und „Link kopieren“ wählen. Die kopierte Adresse hier einfügen."
    override val help2 = "Klappt das nicht, hilft eine Suche nach dem Vereinsnamen zusammen mit „Spielplan ICS“."
    override val help3 = "Fertige Spielpläne für nahezu alle deutschen Vereine und Ligen gibt es bei calovo.de - dort den Verein wählen und die Abo-Adresse kopieren."
    override val checkFailed = "Prüfen fehlgeschlagen"
    override val addFailedShort = "Hinzufügen fehlgeschlagen"

    override val noCalendarAdmin = "Eure Gruppe hat noch keinen Kalender. Füge einen hinzu - zum Beispiel den Spielplan der Bundesliga."
    override val noCalendarMember = "Eure Gruppe hat noch keinen Kalender. Der Admin kann einen hinzufügen."
    override val autoUpdateHint = "Die Spielpläne werden automatisch im Hintergrund aktualisiert - auch verlegte Anstoßzeiten."
    override val adminDecidesHint = "Welche Kalender es gibt, legt der Admin fest. Der Schalter blendet einen Kalender nur auf diesem Gerät aus."
    override val removeCalendarQ = "Kalender entfernen?"
    override fun removeCalendarText(name: String) = "„$name“ verschwindet für alle in der Gruppe - samt aller Zusagen zu diesen Spielen."
    override val hidden = "Ausgeblendet"
    override val noMatchesYet = "Noch keine Spiele geladen"
    override fun matchesN(n: Int) = "$n Spiele"
    override fun matchesNStand(n: Int, stand: String) = "$n Spiele · Stand $stand"
    override val removeCalendar = "Kalender entfernen"
}

object En : Strings {
    override val appName = "Matchday"
    override val back = "Back"
    override val cancel = "Cancel"
    override val save = "Save"
    override val remove = "Remove"
    override val copy = "Copy"
    override val failed = "Failed"
    override val clockSuffix = ""

    override val authIntroRegister = "Create your account. You'll confirm the address with a code from the email in a moment."
    override val authIntroSignIn = "Sign in with your account."
    override val nameQuestion = "What's your name?"
    override val emailLabel = "Email address"
    override val passwordLabel = "Password"
    override val passwordLabelNew = "Password (at least 8 characters)"
    override val showPassword = "Show password"
    override val hidePassword = "Hide password"
    override val forgotPassword = "Forgot password?"
    override val createAccount = "Create account"
    override val signIn = "Sign in"
    override val haveAccount = "I already have an account"
    override val noAccount = "No account yet? Register"
    override val haveInvite = "I have an invitation"
    override val acceptInviteIntro = "Enter the code from the invitation email and choose a password - that's all it takes."
    override val acceptInvite = "Accept invitation"
    override val inviteCodeLabel = "Invitation code"
    override val inviteeName = "Person's name"
    override val inviteNameHint = "With name and address the person doesn't need to register - they just pick a password."
    override val codeTitle = "Confirm address"
    override val codeCancel = "Use a different address"
    override fun codeIntro(email: String) = "We sent a code to $email. Check your spam folder too."
    override val codeLabel = "Code from the email"
    override val codeResend = "Send code again"
    override val confirm = "Confirm"
    override val resetTitle = "Reset password"
    override val backToSignIn = "Back to sign-in"
    override val newCodeSent = "A new code is on its way."
    override val saveFailed = "Saving failed"
    override val confirmFailed = "Confirmation failed"
    override val sendFailed = "Sending failed"
    override val newPasswordTitle = "New password"
    override val newPasswordIntro = "Choose a new password with at least 8 characters."
    override val newPasswordLabel = "New password"
    override val passwordChanged = "Password changed."
    override val errInvalidLogin = "Email or password is wrong."
    override val errAlreadyRegistered = "There is already an account for this address - sign in instead."
    override val errWeakPassword = "The password needs at least 8 characters."
    override val errCodeInvalid = "The code has expired or is wrong. Request a new one."
    override val errRateLimit = "Too many emails in a short time - please wait a moment."
    override val errInvalidEmail = "That is not a valid email address."
    override fun errAuthGeneric(message: String?) = "Sign-in failed: $message"
    override val errInviteFailed = "Invitation failed"
    override val errMembershipRead = "Membership could not be read"

    override val onboardingIntro = "All matches in one place - and your crew knows who's in."
    override val color = "COLOUR"
    override val letsGo = "Let's go"

    override val refresh = "Refresh"
    override val calendars = "Calendars"
    override val emptyNoCalendarTitle = "No calendar yet"
    override val emptyNoCalendarBody = "Your group's admin adds calendars - the Bundesliga fixture list, for example. After that, every match shows up here automatically."
    override val emptyNoCalendarAction = "View calendars"
    override val emptyNoMatchesTitle = "No matches found"
    override val emptyNoMatchesBody = "The calendar is subscribed but has no upcoming fixtures."
    override val emptyNoMatchesAction = "Reload"
    override val list = "List"
    override val month = "Month"
    override val prevMonth = "Previous month"
    override val nextMonth = "Next month"
    override val monthHint = "Swipe for the next month. Tap a match day."
    override val noMatchThatDay = "No match on this day."
    override val importantMatch = "Important match"
    override val statusIn = "In"
    override val statusOut = "Out"
    override val statusOpen = "Open"
    override val allDay = "All day"

    override val unmarkImportant = "Remove highlight"
    override val markImportant = "Mark as important"
    override val whoComes = "WHO'S COMING"
    override val areYouIn = "ARE YOU IN?"
    override val whyNotLabel = "Why not? (optional)"
    override val whyNotPlaceholder = "e.g. on holiday"
    override val declineWithoutReason = "Decline without a reason"
    override val withdraw = "Withdraw answer"
    override val nobodyAnswered = "Nobody has answered yet."
    override val noAcceptYetDot = "No one is in yet."
    override fun declinesHeader(n: Int) = if (n == 1) "1 DECLINED" else "$n DECLINED"
    override val youSuffix = " (you)"

    override val noAcceptYet = "No one is in yet"
    override val youDeclined = "You declined"
    override val oneDecline = "1 declined"
    override fun nDeclines(n: Int) = "$n declined"
    override fun declinedSuffix(n: Int) = " · $n declined"
    override val youAreIn = "You're in"
    override fun xIsIn(name: String) = "$name is in"
    override fun youAndN(n: Int) = "You and $n more"
    override fun nIn(n: Int) = "$n in"

    override val weekdaysShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    override val weekdaysLong = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    override val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    override val today = "Today"
    override val tomorrow = "Tomorrow"
    override val dayAfterTomorrow = "Day after tomorrow"
    override fun inDays(n: Int) = "In $n days"
    override val past = "Past"
    override fun dateTime(dateTime: LocalDateTime, date: String, time: String) = "$date, $time"

    override val askAreYouIn = "Are you in?"
    override val isTomorrow = "is tomorrow"
    override val isInAWeek = "is in a week"
    override fun isInDays(n: Int) = "is in $n days"
    override fun notAnswered(title: String, whenText: String) = "$title $whenText - you haven't answered yet."
    override val kickoffTomorrow = "Kick-off is tomorrow at this time"
    override fun kickoffInDays(n: Int) = "Kick-off in $n days"
    override val kickoffInHour = "Kick-off in one hour"
    override fun kickoffInHours(n: Int) = "Kick-off in $n hours"
    override fun kickoffInMinutes(n: Int) = "Kick-off in $n minutes"

    override val errFetch = "Download failed"
    override val errServerNoAnswer = "The server isn't responding. Please try again later."
    override val errInvalidAddress = "That is not a valid address. Please check for typos."
    override val errNotFound = "That address doesn't exist. Please check for typos."
    override val errNotPublic = "The calendar isn't public - this address requires a login."
    override fun errServerStatus(code: Int) = "The server replied with error $code."
    override val errNoCalendar = "There is no calendar at this address. It's probably the website rather than the calendar - look for \"subscribe to calendar\" there."

    override val needGroup = "You need a group for that"
    override val calendarExists = "You already have this calendar."
    override val onlyAdminCalendars = "Only the admin can add calendars."
    override fun addFailed(message: String) = "Adding failed: $message"
    override val removeFailed = "Could not remove"
    override val matchUnknown = "Match could not be identified"
    override val changeFailed = "Could not change"

    override val settings = "Settings"
    override val profile = "PROFILE"
    override val name = "Name"
    override val account = "ACCOUNT"
    override val group = "GROUP"
    override val noGroup = "No group"
    override val groupSubtitleNone = "Answers stay on this device"
    override val groupSubtitleInvite = "Invite someone with the invitation code"
    override fun membersN(n: Int) = "$n members"
    override val reminderSection = "REMINDER BEFORE KICK-OFF"
    override val remindBefore = "Remind me before the match"
    override val delivery = "DELIVERY"
    override val openRsvps = "OPEN ANSWERS"
    override val askWeekBefore = "Ask a week ahead"
    override val askWeekBeforeDesc = "Reminds you when a match is seven days away and you haven't answered yet."
    override val calendarSection = "CALENDARS"
    override fun calendarsN(n: Int) = if (n == 1) "1 calendar" else "$n calendars"
    override fun deviceId(id: String?) = "Device ID: ${id ?: "not signed in"}"
    override val checking = "Checking ..."
    override val notifAllowed = "Notifications allowed"
    override val notifDenied = "Notifications not allowed - enable them in the system settings"
    override val exactOk = "Reminders arrive to the minute"
    override val exactFail = "Without exact alarms a reminder can arrive a few minutes late"
    override val allowExact = "Allow exact alarms"
    override val exactPromptTitle = "Reminders to the minute?"
    override val exactPromptText = "Without this permission Android may delay reminders by a few minutes - not ideal for \"kick-off in one hour\". You grant it once in the system settings."
    override val exactPromptAllow = "Allow"
    override val exactPromptLater = "Later"
    override val exactPromptNever = "Don't ask again"
    override val pendingNone = "No reminder scheduled right now"
    override val pendingOne = "1 reminder scheduled"
    override fun pendingN(n: Int) = "$n reminders scheduled"
    override val pushOk = "Reachable for group notifications"
    override val pushNoGroup = "Without a group there are no notifications from others."
    override val pushFail = "Not reachable for group notifications"
    override val sendTest = "Send test notification"
    override val testHint = "Arrives in about 10 seconds. Close the app briefly to see it as you would day to day."
    override val changePassword = "Change password"
    override val newPasswordMin = "New password (at least 8 characters)"
    override val notSignedIn = "Not signed in"
    override val signOut = "Sign out"
    override val signOutQuestion = "Sign out?"
    override val signOutText = "Your group and your answers stay in your account. Everything is removed from this device until you sign in again."
    override fun daysShort(n: Int) = "$n d"
    override fun hoursShort(n: Int) = "$n h"
    override fun minutesShort(n: Int) = "$n min"

    override val yourGroup = "Your group"
    override val groupTitle = "Group"
    override val groupIntro = "Matchday lives on the group: you share the calendars and see who's coming to a match. Create a group or join with an invitation code."
    override val newGroup = "NEW GROUP"
    override val groupNameLabel = "Group name"
    override val groupNamePlaceholder = "e.g. Regulars"
    override val adminHint = "Whoever creates the group runs it: inviting and highlighting matches."
    override val createGroup = "Create group"
    override val orJoin = "OR JOIN"
    override val inviteCode = "Invitation code"
    override val sixChars = "6 characters"
    override val join = "Join"
    override val admin = "ADMIN"
    override val onlyImportantHint = "You only see the highlighted matches."
    override val invite = "INVITE"
    override val inviteIntro = "Each invitation works once. You decide what the invitee gets to see. With an address the code goes out by email right away; without one you see it here to pass on."
    override val emailOptional = "Email address (optional)"
    override val allMatches = "All matches"
    override val onlyImportant = "Important only"
    override fun membersHeader(n: Int) = if (n == 1) "1 MEMBER" else "$n MEMBERS"
    override val leaveGroup = "Leave group"
    override fun removeMemberQ(name: String) = "Remove $name?"
    override val removeMemberText = "All of this person's answers disappear from the group. They need a new invitation to return."
    override val seesOnlyImportant = "sees important matches only"
    override val seesAll = "sees all matches"
    override fun sentTo(email: String) = "Sent by email to $email"
    override fun sendWarning(message: String) = "$message - pass the code on another way instead."
    override val onlyImportantSmall = "important matches only"
    override val you = "You"
    override fun removeMemberDesc(name: String) = "Remove $name"
    override val membershipLostGate = "You are no longer a member of your previous group. Create a new one or get invited again."
    override val membershipLostSettings = "Your previous group belongs to an earlier installation of the app and can't be reached any more. Create a new one or get invited again."

    override val addCalendar = "Add calendar"
    override val searchLabel = "SEARCH CLUB OR LEAGUE"
    override val searchHint = "e.g. Bayern, Dortmund, 2. Bundesliga"
    override val nothingFound = "Nothing found - you can enter the address yourself below."
    override val orEnterAddress = "OR ENTER AN ADDRESS"
    override val importIntro = "Almost every club and league publishes its fixtures as a calendar you can subscribe to. You need its address - a line starting with https:// or webcal://, usually ending in .ics."
    override val hideHelp = "Hide explanation"
    override val whereAddress = "Where do I find such an address?"
    override val addressLabel = "Calendar address"
    override val check = "Check"
    override val nameInApp = "Name in the app"
    override val groupWide = "The calendar applies to the whole group. Everyone sees its matches and can answer."
    override val add = "Add"
    override val previewNone = "Reachable, but no fixtures found"
    override val previewOne = "1 fixture found"
    override fun previewN(n: Int) = "$n fixtures found"
    override val recurringWarning = "The calendar may only contain recurring events - Matchday can't read those."
    override val help1 = "On the club's website, look near the fixture list for \"subscribe to calendar\", \"iCal\", \"ICS\" or a calendar icon. Don't tap it - press and hold and choose \"copy link\". Paste the copied address here."
    override val help2 = "If that doesn't work, search for the club's name together with \"fixtures ICS\"."
    override val help3 = "Ready-made fixture calendars for nearly all German clubs and leagues are at calovo.de - pick the club there and copy the subscription address."
    override val checkFailed = "Check failed"
    override val addFailedShort = "Adding failed"

    override val noCalendarAdmin = "Your group has no calendar yet. Add one - the Bundesliga fixture list, for example."
    override val noCalendarMember = "Your group has no calendar yet. The admin can add one."
    override val autoUpdateHint = "Fixture lists update automatically in the background - including rescheduled kick-offs."
    override val adminDecidesHint = "The admin decides which calendars exist. The switch only hides a calendar on this device."
    override val removeCalendarQ = "Remove calendar?"
    override fun removeCalendarText(name: String) = "\"$name\" disappears for everyone in the group - including all answers to those matches."
    override val hidden = "Hidden"
    override val noMatchesYet = "No matches loaded yet"
    override fun matchesN(n: Int) = "$n matches"
    override fun matchesNStand(n: Int, stand: String) = "$n matches · as of $stand"
    override val removeCalendar = "Remove calendar"
}
