import Foundation

#if canImport(UIKit)
import UIKit
#endif

/// Location instrumentation: holds and starts LocationProvider when enabled.
/// provides singleton-style static APIs with install/uninstall lifecycle.
public final class LocationInstrumentation {
    // MARK: - Singleton/static API surface expected by tests and config

    private static let shared = LocationInstrumentation()

    /// The currently initialized LocationProvider, if any.
    public static var provider: LocationProvider? {
        return shared.initializedLocationProvider
    }

    /// Installs location instrumentation: creates LocationProvider and registers app lifecycle listeners.

    public static func install(
        userDefaults: UserDefaults = .standard,
        cacheInvalidationTime: TimeInterval = LocationConstants.defaultCacheInvalidationTime
    ) {
        shared.install(userDefaults: userDefaults, cacheInvalidationTime: cacheInvalidationTime)
    }

    /// Stops periodic refresh and clears the provider (e.g. for tests or shutdown).

    public static func uninstall() {
        shared.uninstall()
    }

    // MARK: - Instance-backed implementation

    private var initializedLocationProvider: LocationProvider?

    #if canImport(UIKit)
    private var lifecycleObservers: [NSObjectProtocol] = []
    #endif

    public init() {}

    // Instance install used by static forwarder
    public func install(
        userDefaults: UserDefaults = .standard,
        cacheInvalidationTime: TimeInterval = LocationConstants.defaultCacheInvalidationTime
    ) {
        // If already installed, reuse existing provider (idempotent)
        if let existing = initializedLocationProvider {
            #if canImport(UIKit)
            DispatchQueue.main.async { [weak self] in
                if UIApplication.shared.applicationState == .active {
                    existing.startPeriodicRefresh()
                } else {
                    existing.stopPeriodicRefresh()
                }
                self?.ensureLifecycleListenersRegistered()
            }
            #else
            existing.startPeriodicRefresh()
            #endif
            return
        }

        let locationProvider = LocationProvider(
            userDefaults: userDefaults,
            cacheInvalidationTime: cacheInvalidationTime
        )
        initializedLocationProvider = locationProvider

        #if canImport(UIKit)
        registerLifecycleListeners()

        // Start immediately if app is already in foreground
        DispatchQueue.main.async { [weak self] in
            if UIApplication.shared.applicationState == .active {
                self?.initializedLocationProvider?.startPeriodicRefresh()
            }
        }
        #else
        // On non-UIKit platforms (macOS without UIKit), start immediately
        locationProvider.startPeriodicRefresh()
        #endif
    }

    #if canImport(UIKit)
    private func ensureLifecycleListenersRegistered() {
        if lifecycleObservers.isEmpty {
            registerLifecycleListeners()
        }
    }

    private func registerLifecycleListeners() {
        // Listen for app becoming active - start location refresh
        let activeObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.initializedLocationProvider?.startPeriodicRefresh()
        }

        // Listen for app entering background - pause location refresh (battery saving)
        let backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.initializedLocationProvider?.stopPeriodicRefresh()
        }

        // Listen for app entering foreground - resume location refresh
        let foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.initializedLocationProvider?.startPeriodicRefresh()
        }

        lifecycleObservers = [activeObserver, backgroundObserver, foregroundObserver]
    }
    #endif

    // Instance uninstall used by static forwarder
    public func uninstall() {
        initializedLocationProvider?.stopPeriodicRefresh()
        initializedLocationProvider = nil

        UserDefaults.standard.removeObject(forKey: LocationConstants.locationCacheKey)

        #if canImport(UIKit)
        for observer in lifecycleObservers {
            NotificationCenter.default.removeObserver(observer)
        }
        lifecycleObservers.removeAll()
        #endif
    }
}
     
