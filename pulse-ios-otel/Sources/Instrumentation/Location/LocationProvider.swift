import Foundation
import CoreLocation

/// Provides device location with caching and periodic refresh
/// Writes cached location to UserDefaults for use by LocationAttributesSpanAppender and LocationAttributesLogRecordProcessor.
public final class LocationProvider: NSObject {

    // MARK: - Core Services

    private let locationManager = CLLocationManager()
    private let reverseGeocoder = LocationReverseGeocoding()
    private let userDefaults: UserDefaults
    private let cacheKey: String
    private let cacheInvalidationTime: TimeInterval

    // MARK: - Timer

    private var refreshTimer: DispatchSourceTimer?

    // MARK: - Init

    public init(
        userDefaults: UserDefaults = .standard,
        cacheKey: String = LocationConstants.locationCacheKey,
        cacheInvalidationTime: TimeInterval = LocationConstants.defaultCacheInvalidationTime
    ) {
        self.userDefaults = userDefaults
        self.cacheKey = cacheKey
        self.cacheInvalidationTime = cacheInvalidationTime
        super.init()

        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    // MARK: - Public API

    public func startPeriodicRefresh() {
        stopPeriodicRefresh()

        let cached = loadCachedLocation()
        let isExpired = cached == nil || cached!.isExpired(cacheInvalidationTime)
        let isMissingGeoAttributes = cached != nil && cached!.countryIsoCode == nil
        let shouldFetchNow = isExpired || isMissingGeoAttributes

        if shouldFetchNow {
            requestLocation()
        }

        let timer = DispatchSource.makeTimerSource(queue: .global(qos: .background))
        timer.schedule(deadline: .now() + cacheInvalidationTime, repeating: cacheInvalidationTime)
        timer.setEventHandler { [weak self] in
            self?.requestLocation()
        }
        timer.resume()
        refreshTimer = timer
    }

    public func stopPeriodicRefresh() {
        refreshTimer?.cancel()
        refreshTimer = nil
        reverseGeocoder.cancel()
    }

    // MARK: - Location

    private func requestLocation() {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, macOS 11.0, *) {
            status = locationManager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }

        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
            return
        }

        #if os(iOS) || os(watchOS) || os(tvOS)
        guard status == .authorizedWhenInUse || status == .authorizedAlways else {
            return
        }
        #elseif os(macOS)
        guard status == .authorizedAlways else {
            return
        }
        #endif

        locationManager.requestLocation()
    }

    // MARK: - Caching

    private func saveLocation(_ cached: CachedLocation) {
        CachedLocationSaver.shared.cachedLocation = cached

        if let data = try? JSONEncoder().encode(cached) {
            userDefaults.set(data, forKey: cacheKey)
        }
    }

    private func loadCachedLocation() -> CachedLocation? {
        if let memCached = CachedLocationSaver.shared.cachedLocation,
           !memCached.isExpired(cacheInvalidationTime) {
            return memCached
        }
        guard let data = userDefaults.data(forKey: cacheKey),
              let cached = try? JSONDecoder().decode(CachedLocation.self, from: data) else {
            return nil
        }

        // Update in-memory cache from UserDefaults
        CachedLocationSaver.shared.cachedLocation = cached
        return cached
    }

    // MARK: - Reverse Geocoding

    private func resolveGeoAttributes(for location: CLLocation) {
        reverseGeocoder.resolve(location: location) { [weak self] result in
            guard let self = self, let result = result else { return }

            let regionIsoCode = self.formatRegion(
                country: result.countryIsoCode,
                region: result.administrativeArea
            )

            let updated = CachedLocation(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                timestamp: Date().timeIntervalSince1970,
                countryIsoCode: result.countryIsoCode,
                regionIsoCode: regionIsoCode,
                localityName: result.locality,
                postalCode: result.postalCode
            )

            self.saveLocation(updated)
        }
    }

    private func formatRegion(country: String?, region: String?) -> String? {
        guard let c = country, let r = region else { return nil }
        return "\(c)-\(r.uppercased())"
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationProvider: CLLocationManagerDelegate {

    public func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let location = locations.first else { return }

        let cached = CachedLocation(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            timestamp: Date().timeIntervalSince1970,
            countryIsoCode: nil,
            regionIsoCode: nil,
            localityName: nil,
            postalCode: nil
        )

        saveLocation(cached)
        resolveGeoAttributes(for: location)
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didFailWithError error: Error
    ) {
        // Silently handle location errors
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, macOS 11.0, *) {
            status = manager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }

        // Retry requesting location if authorized
        #if os(iOS) || os(watchOS) || os(tvOS)
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            requestLocation()
        }
        #elseif os(macOS)
        if status == .authorizedAlways {
            requestLocation()
        }
        #endif
    }
}
