import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import PulseReactNativeOtel
import PulseKit

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
    
    let globalAttributes: [String: PulseAttributeValue] = [
      "global.string": PulseAttributeValue.string("test_string_value"),
      "global.number": PulseAttributeValue.int(42),
      "global.bool": PulseAttributeValue.bool(true),
    ]
    
    PulseSDK.initialize(
      endpointBaseUrl: "http://127.0.0.1:4318",
      endpointHeaders: nil,
      globalAttributes: globalAttributes
    )

    window = UIWindow(frame: UIScreen.main.bounds)

    factory.startReactNative(
      withModuleName: "PulseReactNativeOtelExample",
      in: window,
      launchOptions: launchOptions
    )

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
