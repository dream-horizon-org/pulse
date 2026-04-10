# Pulse iOS SDK (PulseKit)

Production iOS instrumentation built on [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift). **`PulseKit`** is the SDK surface apps integrate; this directory is the source and test tree.

---

## Use released binaries (recommended for apps)

**Distribution repo:** **[github.com/dream-horizon-org/pulse-ios](https://github.com/dream-horizon-org/pulse-ios)**

That repository holds **XCFrameworks**, the consumer **`PulseKit.podspec`**, and SPM **binary targets** for published versions. Integrate your app the same way you would any closed-source binary SDK: CocoaPods or the release repo’s `Package.swift`, following instructions there.

The monorepo does **not** replace that flow for production apps; it is where we **build and verify** the SDK before opening release PRs into `pulse-ios`.

---

## This directory (`pulse-ios-otel/`)

Source of truth for Swift code lives in the **Pulse monorepo** under **`pulse-ios-otel/`** ([tree on GitHub](https://github.com/dream-horizon-org/pulse/tree/main/pulse-ios-otel)).

| What                            | Where                                                                                                                                  |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| PulseKit + all instrumentations | `Sources/`                                                                                                                             |
| Development podspec             | `PulseKit.podspec`                                                                                                                     |
| Unit / integration tests        | `Tests/`                                                                                                                               |
| XCFramework build script        | `Scripts/build-xcframework.sh` (cwd = **`pulse/pulse-ios-otel/`** — folder with **Package.swift**, **Scripts/**, **PulseKit.podspec**) |
| CI                              | Monorepo `.github/workflows/ios-sdk-checks.yml`                                                                                        |

---

## Run and debug from source

Use the example apps—each has its own setup steps:

1. **[Examples/PulseIOSExample](Examples/PulseIOSExample/README.md)** — CocoaPods, `pod 'PulseKit', path: '../../'`. Best default for SDK development (matches how `build-xcframework.sh` is exercised).
2. **[Examples/PulseSPMExample](Examples/PulseSPMExample/README.md)** — minimal Xcode + SPM against the same sources (or local XCFrameworks).

Contributors should start there to see telemetry end-to-end before changing core code. See also [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Features (PulseKit)

Everything below is configured through **`Pulse.shared.initialize`** and **`InstrumentationConfiguration`** (or remote config where noted).

| Area               | What you get                                                                                                                              |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **Core**           | One-line init, OTLP HTTP export, project/api key headers, optional remote **Pulse SDK config** (sampling, metrics-to-add, signal routing) |
| **Network**        | `URLSession` auto-instrumentation, GraphQL helpers                                                                                        |
| **Sessions**       | Session lifecycle, meters, persistence-friendly session store                                                                             |
| **Screen / UI**    | Screen lifecycle, **UIKit** tap / interaction capture (see UIKitTap README for SwiftUI limits)                                            |
| **Interactions**   | Server-driven multi-step flows (interaction config API)                                                                                   |
| **Crashes**        | KSCrash-backed crash reporting aligned with OTel exception conventions                                                                    |
| **MetricKit**      | CPU, disk, hangs, launches, etc., mapped to logs/metrics                                                                                  |
| **Session replay** | UIKit-first screenshot pipeline, privacy levels, batched upload (opt-in; feature-flagged via config)                                      |
| **Location**       | Optional location attributes (privacy-sensitive; configure explicitly)                                                                    |
| **App lifecycle**  | Foreground / background / launch signals                                                                                                  |
| **Resource**       | Device, app, OS resource attributes                                                                                                       |
| **Signpost**       | Integration with OSSignposter / points of interest                                                                                        |
| **Consent**        | `PulseDataCollectionConsent` gates init, replay, and exporters                                                                            |
| **OpenTelemetry**  | Full upstream exporters (OTLP gRPC/HTTP, Jaeger, Zipkin, Prometheus, persistence, etc.) available if you need lower-level APIs            |

For API details and DSL examples, see **[Sources/PulseKit/README.md](Sources/PulseKit/README.md)**. Per-instrumentation notes live under **`Sources/Instrumentation/*/README.md`**.

---

## Docs & links

- [PulseKit README](Sources/PulseKit/README.md) — initialization, configuration, persistence
- [Contributing](CONTRIBUTING.md) — fork monorepo, build, test, examples
- [Release pipeline (internal)](internal-docs/RELEASE_PIPELINE.md) — how versions reach `pulse-ios`
- [OpenTelemetry Swift docs](https://opentelemetry.io/docs/languages/swift/)
- [OpenTelemetry spec](https://opentelemetry.io/docs/specs/otel/)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). PRs go to **[dream-horizon-org/pulse](https://github.com/dream-horizon-org/pulse)** with changes under `pulse-ios-otel/`.

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
