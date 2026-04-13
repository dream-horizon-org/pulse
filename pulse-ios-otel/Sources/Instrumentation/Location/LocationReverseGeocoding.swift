import Foundation
import CoreLocation

/// Handles reverse geocoding using CLGeocoder to resolve coordinates into geo attributes.
final class LocationReverseGeocoding {

    private let geocoder = CLGeocoder()

    /// Reverse geocodes a CLLocation into geo attributes and calls the completion handler with the result.
    /// - Parameters:
    ///   - location: The CLLocation to reverse geocode.
    ///   - completion: Called with the resolved placemark details, or nil if geocoding fails.
    func resolve(
        location: CLLocation,
        completion: @escaping (GeoResult?) -> Void
    ) {
        geocoder.reverseGeocodeLocation(location) { placemarks, error in
            if error != nil {
                completion(nil)
                return
            }

            guard let placemark = placemarks?.first else {
                completion(nil)
                return
            }

            let result = GeoResult(
                countryIsoCode: placemark.isoCountryCode,
                administrativeArea: placemark.administrativeArea,
                locality: placemark.locality,
                postalCode: placemark.postalCode
            )
            completion(result)
        }
    }
    func cancel() {
        geocoder.cancelGeocode()
    }
}

/// Result of a reverse geocoding operation.
struct GeoResult {
    let countryIsoCode: String?
    let administrativeArea: String?
    let locality: String?
    let postalCode: String?
}
