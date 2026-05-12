# pulse-ios-otel — Plan Handbook

Rebuild guide for the Pulse iOS SDK (public surface: **PulseKit**). Each sub-file follows the standard nine-section template.

Component brief: `/Users/ujjwal.bagrania/Desktop/pulse/docs/components/pulse-ios-otel.md`.

## Module map

| Layer | Plan file |
|---|---|
| Core: PulseKit public surface (`Pulse.shared`, init, consent, shutdown) | [core/pulsekit-surface.md](core/pulsekit-surface.md) |
| Core: Semantic conventions (`PulseAttributes`) | [core/semconv.md](core/semconv.md) |
| Core: Sampling (`PulseKit/Sampling/*`) | [core/sampling.md](core/sampling.md) |
| Core: Exporter chain (BeforeSend → Consent → OTLP, persistence) | [core/exporter.md](core/exporter.md) |
| Instrumentation: Crashes (KSCrash) | [instrumentations/crash.md](instrumentations/crash.md) |
| Instrumentation: URLSession network | [instrumentations/network.md](instrumentations/network.md) |
| Instrumentation: Sessions + AppLifecycle | [instrumentations/session.md](instrumentations/session.md) |
| Instrumentation: Screen lifecycle (UIKit swizzling) | [instrumentations/screen.md](instrumentations/screen.md) |
| Instrumentation: Interaction (UIKitTap + server flows) | [instrumentations/interaction.md](instrumentations/interaction.md) |

## Cross-cutting invariants

- Everything ships inside the **PulseKit** module; there's no Android-style namespace split.
- Auto-discovery is **explicit**: `Pulse.shared.initialize` takes an `InstrumentationConfiguration` block — no ServiceLoader equivalent.
- Init/shutdown is thread-safe via `initializationQueue` (see `PulseKit.swift`); second `initialize` calls are ignored.
- Consent (`PulseDataCollectionConsent`) is enforced by `ConsentSpanProcessor` / `ConsentLogProcessor` / `ConsentMetricExporter` BEFORE BeforeSend — see `Sources/PulseKit/Consent/README.md`.
- Sampling uses `currentSdkConfig` loaded from disk at init; new config from the API is persisted for **next launch only** (see `Sources/PulseKit/Sampling/README.md`).
- Lifecycle span naming mirrors Android: `Created` / `Restarted` / `ViewControllerSession` / `Stopped` / `AppStart` (`internal-docs/IOS_LIFECYCLE_SIGNALS.md`).
