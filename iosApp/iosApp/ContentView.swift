import SwiftUI
import OltreClient

// Thin wrapper: the entire UI is Compose, exposed by the OltreClient framework.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
