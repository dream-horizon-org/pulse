import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import PulseReactNativeOtel
import OpenTelemetryApi
import OpenTelemetrySdk

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
  var window: UIWindow?

  var reactNativeDelegate: ReactNativeDelegate?
  var reactNativeFactory: RCTReactNativeFactory?

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    let delegate = ReactNativeDelegate()
    let factory = RCTReactNativeFactory(delegate: delegate)
    delegate.dependencyProvider = RCTAppDependencyProvider()

    reactNativeDelegate = delegate
    reactNativeFactory = factory

    let globalAttributes: [String: AttributeValue] = [
      "global.string": AttributeValue.string("test_string_value"),
      "global.number": AttributeValue.int(42),
      "global.bool": AttributeValue.bool(true),
    ]

    // Collector URLs are derived from `apiKey` (same as Android); RN merges screen processors into OTEL.
    PulseSDK.initialize(
      apiKey: "default-project_devkey01",
      dataCollectionState: .allowed,
      globalAttributes: globalAttributes,
      resource: { attributes in
        attributes["app.rn_example.resource"] = AttributeValue.string("PulseReactNativeOtelExample")
      },
      configuration: { kit in
        kit.includeScreenAttributes = true
        kit.includeNetworkAttributes = true
        kit.includeGlobalAttributes = true
      },
      instrumentations: { config in
        config.screenLifecycle { screenLifecycleConfig in
          screenLifecycleConfig.enabled(false)
        }
      },
      logLevel: .debug
    )

    window = UIWindow(frame: UIScreen.main.bounds)

    factory.startReactNative(
      withModuleName: "PulseReactNativeOtelExample",
      in: window,
      launchOptions: launchOptions
    )

    // Test: Track an event after 10 seconds to verify screen name is attached
    DispatchQueue.main.asyncAfter(deadline: .now() + 10.0) {
      let currentTimeMs = Date().timeIntervalSince1970 * 1000
      PulseSDK.trackEvent(
        name: "test_event_from_app_delegate",
        observedTimeStampInMs: currentTimeMs,
        params: [
          "test_param": AttributeValue.string("test_value"),
          "source": AttributeValue.string("app_delegate")
        ]
      )

      let span = PulseSDK.startSpan(
        name: "test_span_from_app_delegate",
        params: [
          "span_param": AttributeValue.string("span_value")
        ]
      )
      span.end()
    }

    return true
  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    self.bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}
