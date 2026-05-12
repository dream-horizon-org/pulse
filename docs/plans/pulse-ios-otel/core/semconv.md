# iOS — Core: Semantic conventions

## Purpose

Define every attribute key and `pulse.type` value emitted by PulseKit so wire format matches Android, RN and web.

## Source location

- `Sources/PulseKit/PulseAttributes.swift`

## Public surface

`public enum PulseAttributes` (namespace, not an enum case container):

- Core keys: `pulseType = "pulse.type"`, `pulseName = "pulse.name"`, `pulseSpanId = "pulse.span.id"`, `screenName = "screen.name"`, `lastScreenName = "last.screen.name"`, `userId = "user.id"`, `appInstallationId = "app.installation.id"`, `viewControllerName = "view_controller.name"`, `startType = "start.type"`.
- User scope: `pulseUserPrefix`, `pulseUserPreviousId`, event names `pulseUserSessionStartEventName` / `pulseUserSessionEndEventName`, helper `pulseUserParameter(_ key)`.
- Exception keys: `exceptionMessage`, `exceptionType`, `exceptionStacktrace`.
- GraphQL: `graphqlOperationName`, `graphqlOperationType` (set when URL contains "graphql").
- Project: `projectId = "project.id"`, internal `apiKeyHeaderKey = "X-API-KEY"`.
- Click: `clickType`, `clickIsRage`, `clickRageCount`, `deviceScreenWidth/Height/AspectRatio`, `appScreenCoordinateNx/Ny`.
- Nested: `PulseSdkNames`, `ClickTypeValues` (`good`, `dead`), `AppClickContext.buildContext(label:)`, `PulseTypeValues`.

## Canonical `pulse.type` values (`PulseTypeValues`)

- Custom: `customEvent = "custom_event"`, `nonFatal = "non_fatal"`.
- Spans: `network = "network"`, `screenLoad = "screen_load"`, `appStart = "app_start"`, `screenSession = "screen_session"`.
- Logs: `crash = "device.crash"`, `anr = "anr"`, `frozen = "frozen"`, `slow = "slow"`, `touch = "touch"`, `appClick = "app.click"`, `networkChange = "network_change"`.

## Canonical SDK names

`PulseSdkNames.iosSwift = "pulse_ios_swift"`, `PulseSdkNames.iosRn = "pulse_ios_rn"` — emitted as `telemetry.sdk.name`.

## Internal design

Pure Swift enum-namespace with `public static let`s. No runtime state. Mirrors `com.pulse.semconv.PulseAttributes` byte-for-byte where possible; iOS-specific differences (`appClick` separately from `touch`; `anr` without `device.` prefix) are intentional historical artifacts kept stable for dashboard compatibility.

## Dependencies

- `OpenTelemetryApi` (for `AttributeValue` used elsewhere in PulseKit).

## Data contracts

This file IS the iOS contract. New types must be added here before any instrumentation references them. When adding a value, cross-check against:

- Android: `pulse-semconv/.../PulseAttributes.kt::PulseTypeValues`
- Web SDK: `pulse-web-otel/.../semconv` (per the agent context).

## Tests

- `Tests/PulseKitTests/` exercises `AppClickContext.buildContext` and key string stability.

## History / decisions

- `appClick` (`app.click`) is the modern key matching Android; `touch` is retained for backwards compatibility with older dashboards.
- `apiKeyHeaderKey` is `internal` because customers should never override it.

## Rebuild recipe

1. Create `public enum PulseAttributes` with the keys above.
2. Add nested `PulseSdkNames`, `ClickTypeValues`, `AppClickContext`, `PulseTypeValues`.
3. Verify parity with Android + web before merging any new constant.
