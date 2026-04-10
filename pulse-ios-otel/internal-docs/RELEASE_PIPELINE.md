# PulseKit Release Pipeline

## Overview

PulseKit source lives in **`pulse-ios-otel/`** on [dream-horizon-org/pulse](https://github.com/dream-horizon-org/pulse).

Releases ship from a separate repo, [dream-horizon-org/pulse-ios](https://github.com/dream-horizon-org/pulse-ios), which hosts the published **XCFrameworks**, consumer **CocoaPods podspec**, and **SPM binary targets**. The monorepo release workflow opens a PR there automatically.

---

## Repo Roles

| Repo | Role |
| --- | --- |
| **pulse** | Sources, tests, `PulseKit.podspec`, scripts, example apps |
| **pulse-ios** | Tagged binaries, consumer `PulseKit.podspec` + `Package.swift`. Apps integrate from here |

---

## Release Steps

1. **Land changes** in `dream-horizon-org/pulse` under `pulse-ios-otel/` and ensure **iOS SDK - Checks** is green.
2. **Bump the version** in `pulse-ios-otel/PulseKit.podspec` (`spec.version = "x.y.z"`). Commit and push to the branch the workflow runs from (usually `main`).
3. **Run the workflow** on `pulse`: _Actions → **iOS SDK — XCFramework & release PR** → Run workflow_. Requires the `RELEASE_REPO_TOKEN` secret (push branches + open PRs on `pulse-ios`).
4. **Review the PR** on `pulse-ios` (e.g. `release/x.y.z`): verify `PulseKit.xcframework`, every peer `*.xcframework`, `PulseKit.podspec`, and `Package.swift` binary targets all match. Peers are defined by `PulseKit.podspec` (see `Scripts/print-peer-xcframework-entries.rb` after `pod install` in the example app).
5. **Merge** in `pulse-ios` per that repo's process. Tagging, GitHub Release, and `pod trunk push` run from that repo's workflows.

---

## Secrets

| Secret | Repo | Purpose |
| --- | --- | --- |
| `RELEASE_REPO_TOKEN` | **pulse** (monorepo) | Used by `ios-sdk-release.yml` to push `release/{version}` and open the PR on `dream-horizon-org/pulse-ios` |
| `COCOAPODS_TRUNK_*` | **pulse-ios** (release repo) | Used by that repo's publish workflow for `pod trunk push` (exact names as configured there) |

Do not commit tokens. Configure them in GitHub **Settings → Secrets and variables**.

---

## Why We Ship Prebuilt XCFrameworks

PulseKit is released together with prebuilt XCFrameworks for every external dependency it links — OpenTelemetry, SwiftProtobuf, KSCrash, libwebp, and others listed in `PulseKit.podspec`. This is deliberate.

### Swift Package Manager

PulseKit is built as a **dynamic** framework. With SPM, the app's loader can resolve `@rpath` to dynamic libraries for peer dependencies, but SPM's build pipeline does not provide a reliable way to produce **matching dynamic frameworks** for every peer at the consumer's build time. That mismatch causes crashes when frameworks the main binary expects are never built correctly. Shipping **binary targets** (XCFrameworks) for PulseKit and each peer avoids this entirely.

### CocoaPods

`BUILD_LIBRARY_FOR_DISTRIBUTION = YES` is recommended for distributed Swift frameworks — it improves Swift version and toolchain compatibility across Xcode versions. If only PulseKit were built with this flag while Xcode rebuilt dependency pods from source without it, you get inconsistent module stability. Requiring every app to enable this flag on every transitive pod is hard to document, verify, and support. We already build one coherent set of frameworks in the release pipeline; `vendored_frameworks` in the podspec points at that set so consumers don't have to manage per-pod flags.

### Dynamic vs Static

We ship **dynamic** XCFrameworks to avoid **duplicate symbol** errors, which occur more often when the same code is linked statically in multiple places.

---

## Local Build Notes

### Running `build-xcframework.sh`

Run from `pulse/pulse-ios-otel/` (the folder containing `Package.swift`, `Scripts/`, and `PulseKit.podspec`):

```sh
cd pulse/pulse-ios-otel
./Scripts/build-xcframework.sh
```

Run `pod install` under `Examples/PulseIOSExample/` first so that `PulseIOSExample.xcworkspace` exists.
