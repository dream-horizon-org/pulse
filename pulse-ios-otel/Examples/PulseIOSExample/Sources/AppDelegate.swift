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
            apiKey: "Bharti11-Vhg9eEy9_foRWzkosKnAVhKwqeGyR19cO",
            dataCollectionState: .allowed,
            globalAttributes: globalAttributes,
            instrumentations: { config in
                config.uiKitTap { tapConfig in
                    tapConfig.captureContext(true)
                }
                config.sessionReplay { replayConfig in
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
