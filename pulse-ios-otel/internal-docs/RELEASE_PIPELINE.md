# PulseKit release pipeline

How a new **PulseKit** version gets from **`pulse-ios-otel/`** (monorepo source) to **[dream-horizon-org/pulse-ios](https://github.com/dream-horizon-org/pulse-ios)** (binaries + CocoaPods + SPM consumers).

---

## Overview

1. **Develop and merge** in `dream-horizon-org/pulse` under `pulse-ios-otel/`. CI (**iOS SDK - Checks**) runs SwiftLint, `swift test`, iOS simulator builds, the CocoaPods example app, and a full XCFramework build to guard regressions.

2. **Cut a release** by bumping **`spec.version`** in **`pulse-ios-otel/PulseKit.podspec`**, then run the monorepo workflow **“iOS SDK — XCFramework & release PR”** (`.github/workflows/ios-sdk-release.yml`). That job builds XCFrameworks from the **PulseIOSExample** CocoaPods workspace, copies **`PulseKit.xcframework`** and every **peer** `*.xcframework` into the release repo, opens a PR on `pulse-ios`, and sets the release repo podspec version to match.

3. **Review and merge** the PR in **`pulse-ios`**. That repo’s own workflows (validate, then publish) create the git tag, GitHub Release notes, and **`pod trunk push`** when maintainers merge to `main`.

Peer frameworks are whatever **`Scripts/print-peer-xcframework-entries.rb`** derives from **`PulseKit.podspec`** after `pod install`—keep the release PR’s **`spec.vendored_frameworks`** and **`Package.swift`** binary targets in sync with the copied `*.xcframework` folders.

---

## Steps to release a new version

1. **Implement and merge** your changes to `main` (or your release branch policy) in the **pulse** monorepo; ensure **iOS SDK - Checks** is green.

2. **Bump the version** in **`pulse-ios-otel/PulseKit.podspec`** (`spec.version = "x.y.z"`). Commit and push to the branch the release workflow will run from (usually `main`).

3. **Run the release workflow** in **pulse**: _Actions → iOS SDK — XCFramework & release PR → Run workflow_.  
   Requires secret **`RELEASE_REPO_TOKEN`** (token able to push branches and open PRs on **`dream-horizon-org/pulse-ios`**).

4. **In `pulse-ios`**, review the opened **`release/x.y.z`** PR: vendored frameworks, `Package.swift` binary targets, and `PulseKit.podspec` match the artifacts. Merge per that repo’s process; publish/tag/trunk steps run there.

---

## Important notes

### Run `build-xcframework.sh`

Run **`./Scripts/build-xcframework.sh`** with current directory **`pulse/pulse-ios-otel/`** (contains **`Package.swift`**, **`Scripts/`**, **`PulseKit.podspec`**). Example: **`cd ~/src/pulse/pulse-ios-otel`** then **`./Scripts/build-xcframework.sh`**.

Prerequisite: **`pod install`** in **`Examples/PulseIOSExample/`** so **`Examples/PulseIOSExample/PulseIOSExample.xcworkspace`** exists.

### KSCrash and `@_implementationOnly`

The XCFramework is built via CocoaPods (single **`KSCrash`** module). SPM upstream may expose finer-grained modules. PulseKit uses conditional imports and **`@_implementationOnly`** so the built **`.swiftinterface`** does not force consumers to resolve different KSCrash module shapes. See source in PulseKit where KSCrash is imported.

### Release repo vs monorepo

- **`pulse` / `pulse-ios-otel`:** sources, tests, development podspec, scripts, example apps.
- **`pulse-ios`:** tagged **binaries** and consumer-facing **`PulseKit.podspec` + `Package.swift`**. App developers integrate from **`pulse-ios`** (CocoaPods or SPM binary targets in that repo).

---

## Secrets

| Secret                   | Where                        | Purpose                                                                                                               |
| ------------------------ | ---------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| **`RELEASE_REPO_TOKEN`** | **pulse** (monorepo)         | Used by **ios-sdk-release.yml** to push **`release/{version}`** and open the PR on **`dream-horizon-org/pulse-ios`**. |
| **`COCOAPODS_TRUNK_*`**  | **pulse-ios** (release repo) | Used by that repo’s publish workflow for **`pod trunk push`** (exact names as configured there).                      |

Do not commit tokens; configure them in GitHub **Settings → Secrets and variables**.
