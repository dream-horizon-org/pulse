# Pulse iOS SDK

Production iOS instrumentation built on [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift). **PulseKit** is the public SDK surface that apps integrate. This directory (`pulse-ios-otel/`) is the source and test tree inside the monorepo.

---

## Contents

- [Integration (apps)](#integration-apps)
- [This directory](#this-directory-pulse-ios-otel)
- [Features](#features)
- [Run and debug from source](#run-and-debug-from-source)
- [Docs & links](#docs--links)
- [Contributing](#contributing)
- [License](#license)

---

## Integration (apps)

**Distribution repo:** [github.com/dream-horizon-org/pulse-ios](https://github.com/dream-horizon-org/pulse-ios)

Apps should integrate from the **distribution repo**, not from this monorepo. It holds the published **XCFrameworks**, consumer `PulseKit.podspec`, and SPM **binary targets** for each released version. Integrate via CocoaPods or the repo's `Package.swift` following the instructions there.

The monorepo is where we **build and verify** the SDK before opening release PRs into `pulse-ios`.

---

## This directory (`pulse-ios-otel/`)

Source of truth for all Swift code. Hosted in the **Pulse monorepo** ([view on GitHub](https://github.com/dream-horizon-org/pulse/tree/main/pulse-ios-otel)).

| What | Where |
| --- | --- |
| PulseKit + all instrumentations | `Sources/` |
| Development podspec | `PulseKit.podspec` |
| Unit / integration tests | `Tests/` |
| XCFramework build script | `Scripts/build-xcframework.sh` (run from `pulse-ios-otel/`) |
| CI workflow | `.github/workflows/ios-sdk-checks.yml` |
| Release pipeline docs | `internal-docs/RELEASE_PIPELINE.md` |

---

## Features

Everything below is configured through `PulseKit.shared.initialize` or via remote config where noted.

| Area | What you get |
| --- | --- |
| **Core** | One-line init, OTLP HTTP export, API key headers, optional remote Pulse SDK config (sampling, signal routing) |
| **Network** | `URLSession` auto-instrumentation, GraphQL helpers |
| **Sessions** | Session lifecycle, meters, persistence-friendly session store |
| **Screen / UI** | Screen lifecycle, UIKit tap / interaction capture |
| **Interactions** | Server-driven multi-step flows (interaction config API) |
| **Crashes** | KSCrash-backed crash reporting, aligned with OTel exception conventions |
| **MetricKit (WIP)** | CPU, disk, hangs, launches mapped to logs/metrics |
| **Session Replay** | UIKit screenshot pipeline, privacy levels, batched upload (opt-in, feature-flagged) |
| **Location** | Location attributes (privacy-sensitive; configure explicitly) |
| **App Lifecycle** | Foreground / background / launch signals |
| **Resource** | Device, app, OS resource attributes |
| **Consent** | `PulseDataCollectionConsent` gates init, replay, and exporters |

For API details and DSL examples, see [Sources/PulseKit/README.md](Sources/PulseKit/README.md). Per-instrumentation notes live under `Sources/Instrumentation/*/README.md`.

---

## Run and debug from source

Use one of the example apps — each has its own setup steps:

1. **[Examples/PulseIOSExample](Examples/PulseIOSExample/README.md)** — CocoaPods, integrates PulseKit via local path. Best default for SDK development; matches how `build-xcframework.sh` is exercised.
2. **[Examples/PulseSPMExample](Examples/PulseSPMExample/README.md)** — minimal Xcode + SPM against the same sources (or local XCFrameworks).

Start with an example app to see telemetry end-to-end before changing core code.

---

## Docs & links

| Resource | Description |
| --- | --- |
| [PulseKit README](Sources/PulseKit/README.md) | Initialization API, instrumentation DSL, tracking methods |
| [Release Pipeline](internal-docs/RELEASE_PIPELINE.md) | How versions are built and published to `pulse-ios` |
| [Contributing](CONTRIBUTING.md) | Fork, build, test, and submit PRs |
| [OpenTelemetry Swift docs](https://opentelemetry.io/docs/languages/swift/) | Upstream SDK documentation |
| [OpenTelemetry spec](https://opentelemetry.io/docs/specs/otel/) | Specification reference |

---

## Contributing

PRs go to [dream-horizon-org/pulse](https://github.com/dream-horizon-org/pulse) with changes scoped to `pulse-ios-otel/`. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup and workflow details.

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
