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
            endpointBaseUrl: "http://127.0.0.1:4318",
            apiKey: "default-project_devkey01",
            endpointHeaders: nil,
            globalAttributes: globalAttributes,
            instrumentations: { config in
                // Enable UIKit tap instrumentation with context capture
                config.uiKitTap { tapConfig in
                    tapConfig.enabled(true)
                    tapConfig.captureContext(true)
                }

                // Enable Session Replay
                config.sessionReplay { replayConfig in
                    replayConfig.enabled(true)
                    replayConfig.configure { localConfig in
                        localConfig.textAndInputPrivacy = .maskAllInputs
                        localConfig.imagePrivacy = .maskNone
                        
                        // Register custom classes for class-level overrides
                        localConfig.maskViewClasses = Set([
                            "PulseIOSExample.PrivateSecureView",
                            "PulseIOSExample.PrivateDataLabel",
                        ])
                        localConfig.replayEndpointBaseUrl = "http://127.0.0.1:3400"
                    }
                }
            },
            dataCollectionState: .allowed
        )
        window = UIWindow(frame: UIScreen.main.bounds)
        let mainViewController = MainViewController()
        window?.rootViewController = UINavigationController(rootViewController: mainViewController)
        window?.makeKeyAndVisible()
        print("SDK Initialised")
        return true
    }
}
