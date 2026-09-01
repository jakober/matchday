import SwiftUI
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
    }
}

@main
struct iOSApp: App {

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
