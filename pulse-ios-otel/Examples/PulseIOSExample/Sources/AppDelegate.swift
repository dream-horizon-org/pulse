import UIKit
import PulseKit
import OpenTelemetryApi

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let globalAttributes: [String: AttributeValue] = [
            "global.version": AttributeValue.string("1.0.0"),
            "global.environment": AttributeValue.string("development"),
            "global.build": AttributeValue.int(123),
            "global.is_debug": AttributeValue.bool(true),
            "global.version_code": AttributeValue.double(1.0),
            "global.features": AttributeValue.array(AttributeArray(values: [
                AttributeValue.string("feature1"),
                AttributeValue.string("feature2")
            ]))
        ]
        
        Pulse.shared.initialize(
            apiKey: "default-project_devkey01",
            dataCollectionState: .allowed,
            globalAttributes: globalAttributes,
            instrumentations: { config in
                // Enable UIKit tap instrumentation with context capture
                config.uiKitTap { tapConfig in
                    tapConfig.enabled(true)
                    tapConfig.captureContext(true)
                }

                // Enable Session Replay with code-level view masking configuration
                // Note: Privacy, quality, and flush settings are now controlled via backend remote config
                config.sessionReplay { replayConfig in
                    replayConfig.enabled(true)
                    
                    // Register custom classes for class-level masking rules
                    replayConfig.addMaskViewClass("PulseIOSExample.PrivateSecureView")
                    replayConfig.addMaskViewClass("PulseIOSExample.PrivateDataLabel")
                }
            },
            logLevel: .debug
        )
        window = UIWindow(frame: UIScreen.main.bounds)
        let mainViewController = MainViewController()
        window?.rootViewController = UINavigationController(rootViewController: mainViewController)
        window?.makeKeyAndVisible()
        // Demo only: not using PulseLogger (package-internal). Initialization logs come from SDK when logLevel != .none.
        print("SDK initialised (Pulse iOS example)")
        return true
    }
}
