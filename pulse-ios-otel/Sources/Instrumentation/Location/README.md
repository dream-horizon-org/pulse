# Location Instrumentation

The Location instrumentation adds geo attributes to spans and log records using the device’s location.

## Attributes

When location is available and permission is granted, the following attributes are added to spans and log records:

| Attribute              | Description                     |
| ---------------------- | ------------------------------- |
| `geo.location.lat`     | Latitude (WGS84)                |
| `geo.location.lon`     | Longitude (WGS84)               |
| `geo.country.iso_code` | ISO 3166-1 alpha-2 country code |
| `geo.region.iso_code`  | ISO 3166-2 region code          |
| `geo.locality.name`    | Locality (e.g. city, town)      |
| `geo.postal_code`      | Postal code                     |

These follow the [OpenTelemetry Geo semantic conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/).

## Behavior

- If location permission is not granted or location is unavailable, no geo attributes are added.
- Location is cached (default: 1 hour) to limit geocoder and location requests.
- When the app is in the foreground a periodic refresh updates the cache; when the app goes to the background, refresh is paused to save battery. Span and log processors read from this cache.

## Configuration

It is **off** by default. Turn it on (or leave it off) in the `instrumentations` closure when you call **`Pulse.shared.initialize`**:

```swift
Pulse.shared.initialize(
    apiKey: "your-api-key",
    dataCollectionState: .allowed,
    instrumentations: { config in
        config.location { $0.enabled(true) }   // omit or use enabled(false) to keep geo attributes disabled
    }
)
```

When disabled or omitted, no location provider runs and no geo attributes are attached.

## Location permission (`Info.plist`)

If you enable location instrumentation, iOS still requires a usage description in **`Info.plist`** or the system will not show a permission prompt (and fixes may be unavailable). **`NSLocationWhenInUseUsageDescription`** is enough for the default **`requestWhenInUseAuthorization()`** flow:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app uses your location to attach geo attributes to telemetry.</string>
```

For background location, add **`NSLocationAlwaysAndWhenInUseUsageDescription`** (and only if your product actually needs always-on location). Customize the strings for your app.

## Advanced Guide

### Location tracking

Location is obtained via Apple's **CoreLocation** framework (`CLLocationManager`). The provider uses `requestLocation()` for one-shot fixes (not continuous GPS tracking) with `kCLLocationAccuracyHundredMeters` to balance accuracy and battery. A `DispatchSourceTimer` fires every cache-invalidation interval (default 1 hour) to re-request location in the background queue.

### Reverse geocoding

Reverse geocoding is performed by Apple's **CoreLocation** `CLGeocoder.reverseGeocodeLocation(_:)` — no third-party service is involved. The first `CLPlacemark` from the response is used to extract `isoCountryCode`, `administrativeArea`, `locality`, and `postalCode`. The region ISO code is formatted as `{country}-{region}` (e.g. `US-CA`).

### Caching

Location data is cached in two layers: an in-memory singleton (`CachedLocationSaver`) for fast reads by span/log processors, and `UserDefaults` for persistence across app launches. Both are updated together on every successful location fix. The cache model (`CachedLocation`) is `Codable` and includes a timestamp so expiry can be checked against the configurable TTL.
