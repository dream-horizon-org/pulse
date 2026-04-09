import PulseKit
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        Pulse.shared.initialize(
            endpointBaseUrl: "http://127.0.0.1:4318",
            apiKey: "default-project"
        )

        let window = UIWindow(frame: UIScreen.main.bounds)
        let root = UINavigationController(rootViewController: RootViewController())
        window.rootViewController = root
        window.makeKeyAndVisible()
        self.window = window

        return true
    }
}
