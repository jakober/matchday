import SwiftUI
import UIKit
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .preferredColorScheme(.dark)
            // Einladungslink: die App merkt sich den Code und zeigt die Annahme.
            .onOpenURL { url in Reminders_iosKt.handleUrl(url: url.absoluteString) }
    }
}

/// Nimmt die Push-Kennung von Apple entgegen und reicht sie an den
/// gemeinsamen Code weiter. SwiftUI allein bietet dafuer keinen Haken, deshalb
/// der klassische App-Delegat.
class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Darf vor der Erlaubnisabfrage passieren: Die Kennung bekommt man
        // immer, die Erlaubnis entscheidet nur ueber die Anzeige.
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Apple liefert rohe Bytes; APNs erwartet sie spaeter als Hexfolge.
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        Push_iosKt.onApnsToken(token: hex)
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Waehrend die App weg war, koennen andere geantwortet haben.
        Reminders_iosKt.refreshGroupOnResume()
    }

    func applicationWillResignActive(_ application: UIApplication) {
        // Nutzungszeit festhalten.
        Reminders_iosKt.pauseUsage()
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Ohne Kennung laeuft die App weiter, nur eben ohne Push.
        print("Push-Registrierung fehlgeschlagen: \(error.localizedDescription)")
    }
}

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // Muss noch während des App-Starts passieren - später verweigert iOS
        // die Registrierung der Hintergrundaufgabe.
        BackgroundSync_iosKt.registerBackgroundRefresh()
        // Ohne diesen Delegaten zeigt iOS Benachrichtigungen nicht an,
        // solange die App im Vordergrund ist.
        Reminders_iosKt.configureNotifications()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
