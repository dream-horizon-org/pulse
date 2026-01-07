
# Location Instrumentation

Status: development

The location instrumentation automatically adds geo attributes to spans and log records based on the device's location.

## Telemetry

This instrumentation adds the following geo attributes to spans and log records:

* [`geo.location.lat`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-location-lat): Latitude of the geo location in WGS84
* [`geo.location.lon`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-location-lon): Longitude of the geo location in WGS84
* [`geo.country.iso_code`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-country-iso-code): Two-letter ISO Country Code (ISO 3166-1 alpha2)
* [`geo.region.iso_code`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-region-iso-code): Region ISO code (ISO 3166-2)
* [`geo.locality.name`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-locality-name): Locality name (city, town, village, etc.)
* [`geo.postal_code`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-postal-code): Postal code associated with the location

All attributes follow the [OpenTelemetry Geo semantic conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/).

**Note:** [`geo.continent.code`](https://opentelemetry.io/docs/specs/semconv/registry/attributes/geo/#geo-continent-code) is not currently implemented.

## Behavior

* If location permission is not granted or location is not available, no geo attributes will be added.
* Location data is cached to avoid multiple geo coder calls and location fetches. Currently cache expires after 1 hour
* The instrumentation automatically refreshes location data periodically:
  * When the app starts, it checks if the cache is valid. If the cache is expired or missing, it fetches location immediately. Otherwise, it uses the cached data.
  * After the initial check, location is refreshed every cache invalidation period.
* The periodic refresh is automatically paused when the app is backgrounded and resumed when the app is foregrounded to conserve battery.
* If the cache is expired or location data is missing, then no geo attributes will be added.

## Installation

If you're using the [pulse-android-sdk](../../pulse-android-sdk), the location instrumentation can be included by adding the location library module to your dependencies:

```kotlin
dependencies {
    implementation("io.opentelemetry.android.instrumentation:location-library:0.15.0-alpha")
}
```

Once you initialize the SDK, the location instrumentation will automatically start collecting and adding geo attributes to your spans and log records. No additional configuration is required.

