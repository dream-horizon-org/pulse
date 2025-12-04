import UIKit
import PulseKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Initialize with instrumentation configuration using DSL syntax
        PulseSDK.shared.initialize(endpointBaseUrl: "http://127.0.0.1:4318") { config in
            config.urlSession { urlSessionConfig in
                urlSessionConfig.enabled(false)
            }
        }
        
        // Create window and root view controller
        window = UIWindow(frame: UIScreen.main.bounds)
        let mainViewController = MainViewController()
        window?.rootViewController = UINavigationController(rootViewController: mainViewController)
        window?.makeKeyAndVisible()
        print("SDK Initialised")
        return true
    }
}

