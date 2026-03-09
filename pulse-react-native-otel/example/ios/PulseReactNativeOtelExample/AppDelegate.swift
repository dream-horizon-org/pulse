import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import PulseReactNativeOtel
import OpenTelemetryApi

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
    
    // Example: Initialize with instrumentations configuration
    // You can configure URLSession, Sessions, SignPost, and Interaction instrumentations
    PulseSDK.initialize(
      endpointBaseUrl: "http://127.0.0.1:4318",
      projectId: "default",
      globalAttributes: globalAttributes,
      instrumentations: { config in
        // Configure URLSession instrumentation
        config.urlSession { urlSessionConfig in
          // urlSessionConfig can be configured here
          urlSessionConfig.enabled(true)
        }
        // Configure Sessions instrumentation
        config.sessions { sessionsConfig in
          // sessionsConfig can be configured here
        }
        // Configure Interaction instrumentation
        config.interaction { interactionConfig in
          interactionConfig.enabled(true)
        }
      }
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
