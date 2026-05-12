# pulse-ios-otel — Component Brief

## What

Production iOS RUM SDK built on OpenTelemetry-Swift. The public surface is the **PulseKit** module exporting a `Pulse` singleton (`Pulse.shared.initialize(...)`) plus instrumentation toggles, attribute helpers and consent APIs. The monorepo path holds source + tests; release artifacts (XCFrameworks) are published into the separate `pulse-ios` distribution repo.

## Path & tech stack

- Path: `/Users/ujjwal.bagrania/Desktop/pulse/pulse-ios-otel/`
- Stack: Swift 5+, OpenTelemetry-Swift, Xcode + Makefile + SPM (`Package.swift`) and CocoaPods (`PulseKit.podspec`).
- Public surface module: **PulseKit** (`Sources/PulseKit/`).
- Crash backend: KSCrash. Network: `URLSession` swizzling. Replay: UIKit screenshot pipeline.

## Build commands

From `pulse-ios-otel/`:

```bash
make build-ios                            # SPM build, iOS simulator
make test-ios                             # unit/integration tests
Scripts/build-xcframework.sh              # produce XCFrameworks for release
pod install                               # in Examples/PulseIOSExample
```

See `pulse-ios-otel/README.md`, `pulse-ios-otel/Makefile`, and `internal-docs/RELEASE_PIPELINE.md`.

## Module / namespace conventions

iOS does not have the Android-style two-namespace split. Everything ships inside the **PulseKit** module:

- `Sources/PulseKit/` — public surface, processors, sampling, consent, redaction.
- `Sources/Instrumentation/<feature>/` — per-feature instrumentations (auto-loaded via `PulseKit.initialize`).
- `Sources/Exporters/` — OTLP HTTP/GRPC + Persistence helpers.
- `Sources/Bridges/`, `Sources/Contrib/`, `Sources/Importers/` — adapter / interop targets.

Auto-discovery is explicit: `PulseKit.swift` wires each enabled instrumentation through the DSL closure passed to `initialize`. There is no ServiceLoader equivalent — every instrumentation is opt-in through `InstrumentationConfiguration`.

## Key files

- `Sources/PulseKit/PulseKit.swift` — `Pulse` singleton, init/shutdown, consent state.
- `Sources/PulseKit/PulseKitConfiguration.swift`, `PulseHostConfiguration.swift`.
- `Sources/PulseKit/PulseAttributes.swift` — attribute keys + `PulseTypeValues`.
- `Sources/PulseKit/Sampling/` — `PulseSamplingSignalProcessors`, `PulseSignalSelectExporter`, `PulseSdkConfigCoordinator`, `PulseSdkConfigRestProvider`, storage.
- `Sources/PulseKit/Consent/` — `ConsentSpanProcessor`, `ConsentLogProcessor`, `ConsentMetricExporter`, `PulseDataCollectionConsent`.
- `Sources/PulseKit/BeforeSend/` — `BeforeSendSpanExporter`, log/metric variants.
- `Sources/PulseKit/Instrumentation/`, `Sources/Instrumentation/{Crashes,URLSession,Sessions,UIKitTap,Interaction,AppLifecycle,MetricKit,SessionReplay,NetworkStatus,Location,SDKResourceExtension,SignPostIntegration}/`.
- `Sources/Exporters/OpenTelemetryProtocolHttp/`, `Persistence/`.
- `internal-docs/IOS_LIFECYCLE_SIGNALS.md` — span definitions for screen lifecycle.

## Cross-SDK parity contract

Every signal carries `platform` resource attribute (`ios`) and a `pulse.type` from `PulseAttributes.PulseTypeValues` (`Sources/PulseKit/PulseAttributes.swift`):

| `pulse.type` | Source |
|---|---|
| `session.start` / `session.end` | `Instrumentation/Sessions` + `PulseUserSessionEmitter` |
| `device.crash` | `Instrumentation/Crashes` (KSCrash) |
| `anr` | `Instrumentation/MetricKit` (hang reports — closest iOS analog) |
| `non_fatal` | `Pulse.trackNonFatal` |
| `network` | `Instrumentation/URLSession` |
| `app.click` / `touch` | `Instrumentation/UIKitTap` |
| `screen_load` | UIViewController swizzling (`UIViewControllerSwizzler`, `VisibleScreenTracker`) |
| `screen_session` | `ViewControllerSession` span (see `IOS_LIFECYCLE_SIGNALS.md`) |
| `app_start` | `AppStartupTimer` |
| `custom_event` | `Pulse.trackEvent` |

Note: Android-only types (`device.anr` as a top-level enum, `app.jank.*`) are not emitted; MetricKit hang detection plus `Instrumentation/SignPostIntegration` are the closest equivalents.

## Plan handbook

See `/Users/ujjwal.bagrania/Desktop/pulse/docs/plans/pulse-ios-otel/index.md`.
