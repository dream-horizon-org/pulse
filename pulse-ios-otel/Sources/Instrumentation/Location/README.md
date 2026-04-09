# Location Instrumentation

The Location instrumentation adds geo attributes to spans and log records using the device’s location.

## Attributes

When location is available and permission is granted, the following attributes are added to spans and log records:

| Attribute | Description |
|-----------|-------------|
| `geo.location.lat` | Latitude (WGS84) |
| `geo.location.lon` | Longitude (WGS84) |
| `geo.country.iso_code` | ISO 3166-1 alpha-2 country code |
| `geo.region.iso_code` | ISO 3166-2 region code |
| `geo.locality.name` | Locality (e.g. city, town) |
| `geo.postal_code` | Postal code |

These follow the [OpenTelemetry Geo semantic conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/).

## Behavior

- If location permission is not granted or location is unavailable, no geo attributes are added.
- Location is cached (default: 1 hour) to limit geocoder and location requests.
- When the app is in the foreground a periodic refresh updates the cache; when the app goes to the background, refresh is paused to save battery. Span and log processors read from this cache.

## Setup

Location is an **optional** dependency — PulseKit builds and runs without it. You can add it if you want geo attributes.

### 1. Add the Location dependency

#### Swift Package Manager (SPM)

Add **Location** to your target:

```swift
dependencies: [
    .package(url: "https://github.com/dream-horizon-org/pulse-ios-sdk.git", from: "0.0.1")
],
targets: [
    .target(
        name: "YourApp",
        dependencies: [
            .product(name: "PulseKit", package: "pulse-ios-sdk"),
            .product(name: "Location", package: "pulse-ios-sdk")  // optional: only if you want location
        ]
    )
]
```

To use the Location module alone (e.g. custom pipeline without PulseKit):

```swift
.product(name: "Location", package: "pulse-ios-sdk")
```

#### CocoaPods

```ruby
pod 'PulseKit', '~> 0.0.1'
# Optional: if you want location attributes
pod 'Pulse-Swift-Instrumentation-Location', '~> 0.0.1'
```

Then run:

```bash
pod install
```

### 2. Add Info.plist location permission keys

Your app must declare a location usage description in its `Info.plist`, otherwise iOS will silently deny location access (or crash on older versions). Add the key that matches the authorization level you request:

#### When In Use (recommended)

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app uses your location to attach geo attributes to telemetry data.</string>
```

#### Always (only if your app needs background location)

If your app also needs location updates while in the background, add both keys:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app uses your location to attach geo attributes to telemetry data.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>This app uses your location in the background to keep telemetry geo attributes up to date.</string>
```

> **Note:** The Location instrumentation uses `requestWhenInUseAuthorization()` by default, so `NSLocationWhenInUseUsageDescription` is sufficient for most apps. The string value is shown to the user in the system permission dialog — customize it to explain why your app needs location.

### 3. Enable in PulseKit

Location is **off** by default. Enable it during initialization:

```swift
PulseKit.shared.initialize(
    endpointBaseUrl: "https://your-backend.com",
    tenantId: "your-tenant-id",
    instrumentations: { config in
        config.location { location in
            location.enabled(true)
        }
    }
)
```

- **Without** the `Location` product linked: this block has no effect and PulseKit runs without location attributes.
- **With** the `Location` product linked: geo attributes are added to all spans and log records.


## Advanced Guide

### Location tracking

Location is obtained via Apple's **CoreLocation** framework (`CLLocationManager`). The provider uses `requestLocation()` for one-shot fixes (not continuous GPS tracking) with `kCLLocationAccuracyHundredMeters` to balance accuracy and battery. A `DispatchSourceTimer` fires every cache-invalidation interval (default 1 hour) to re-request location in the background queue.

### Reverse geocoding

Reverse geocoding is performed by Apple's **CoreLocation** `CLGeocoder.reverseGeocodeLocation(_:)` — no third-party service is involved. The first `CLPlacemark` from the response is used to extract `isoCountryCode`, `administrativeArea`, `locality`, and `postalCode`. The region ISO code is formatted as `{country}-{region}` (e.g. `US-CA`).

### Caching

Location data is cached in two layers: an in-memory singleton (`CachedLocationSaver`) for fast reads by span/log processors, and `UserDefaults` for persistence across app launches. Both are updated together on every successful location fix. The cache model (`CachedLocation`) is `Codable` and includes a timestamp so expiry can be checked against the configurable TTL.
