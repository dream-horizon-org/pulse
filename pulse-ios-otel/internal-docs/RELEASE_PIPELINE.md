# PulseKit Release Pipeline

End-to-end pipeline from source code change to published SDK.

---

## Overview

```
pulse-ios-sdk (source)                          pulse-ios (release)
════════════════════                             ═══════════════════

  PR to main
      │
      ▼
┌─────────────────────┐
│  BuildAndTest.yml   │
│  • SwiftLint        │
│  • Unit tests       │
│  • iOS build & test │
│  • Example app      │
│  • XCFramework      │
└────────┬────────────┘
         │ (Manual trigger)
         ▼
┌─────────────────────┐
│  release.yml        │
│  • Read version     │
│  • Build XCFramework│
│  • Copy PulseKit +  │                     ┌─────────────────────────┐
│    peer xcframeworks│                     │  validate-pr.yml        │
│    (from podspec)   │                     │  • Branch vs podspec    │
│  • Bump podspec ver │                     │  • SPM build (sim+dev)  │
│  • Push release/*   │ ──── creates PR ──► │  • Example app build    │
│  • Create PR        │     in release repo │  • CocoaPods lint       │
└─────────────────────┘                     └────────────┬────────────┘
                                                         │ On merge
                                                         ▼
                                            ┌─────────────────────────┐
                                            │  publish.yml            │
                                            │  • Create git tag       │
                                            │  • GitHub Release       │
                                            │  • CocoaPods trunk push │
                                            └─────────────────────────┘
```

---

## Stage 1: PR Validation (source repo)

**Workflow:** `BuildAndTest.yml`
**Trigger:** PR opened/updated against `main`

| Job | What it does |
|-----|-------------|
| SwiftLint | Lints only changed files |
| UnitTests | `swift test` |
| iOS | Build & test on iOS Simulator |
| ExampleApp | `pod install` + build `PulseIOSExample` via CocoaPods |
| XCFramework | Full xcframework build to verify it produces a valid artifact |

All jobs must pass before merge is allowed.

---

## Stage 2: Release (source repo → release repo)

**Workflow:** `release.yml`
**Trigger:** See the workflow file (typically **`workflow_dispatch`**; additional triggers may be listed under `on:`).

Peer frameworks are derived from **`PulseKit.podspec`** `spec.dependency` lines; **`Scripts/print-peer-xcframework-entries.rb`** resolves each pod’s **Xcode scheme** and **`PRODUCT_MODULE_NAME`** via `xcodebuild -showBuildSettings` (after **`pod install`**). The same script feeds `build-xcframework.sh` and the release job (which copies every built peer into the release repo).

Steps:
1. Read version from `PulseKit.podspec`
2. Verify that version does not already exist as a **git tag** in the release repo
3. `pod install` in `Examples/PulseIOSExample/`
4. Run `Scripts/build-xcframework.sh` — builds PulseKit + all peers under `build/`
5. Checkout the release repo (`dream-horizon-org/pulse-ios`)
6. Create a `release/{version}` branch
7. Copy **`PulseKit.xcframework`** and **every peer** `*.xcframework` from `build/` (same list as `print-peer-xcframework-entries.rb`). **No zip** is produced or committed; CocoaPods and SPM consumers use the **xcframework directories** at the release tag.
8. Set **`spec.version`** in the release repo **`PulseKit.podspec`** with **`sed`** to match the source podspec. **`Package.swift`** (binary targets, wrapper) and **`spec.dependency`** are **not** rewritten—update those on the release PR when peers or CocoaPods dependencies change.
9. Push the branch and open a PR in the release repo (body includes a short reviewer checklist)

---

## Stage 3: Release PR Validation (release repo)

**Workflow:** `validate-pr.yml`
**Trigger:** PR from `release/*` branch against `main`

| Check | What it validates |
|-------|-------------------|
| Version consistency | PR head ref is `release/{version}` and matches `PulseKit.podspec` `spec.version` |
| Example app (Simulator) | Framework links, compiles, and public API is intact (SPM) |
| Example app (Device) | Same validation for arm64 device architecture |
| CocoaPods lint | `pod lib lint` — podspec is valid and publishable |

**How the SPM build works:**
- `Package.swift` in the release repo must list **path** `.binaryTarget` entries for every `*.xcframework` at repo root (maintainers update when peers change).
- Validate-pr patches or validates as needed (e.g. local `PulseKit.xcframework`, injected `PulseKitValidation` target in `Example/Sources/`) so CI can import PulseKit and exercise the public API.

---

## Stage 4: Publish (release repo)

**Workflow:** `publish.yml`
**Trigger:** `release/*` PR merged to `main`, or manual dispatch

Steps:
1. Extract version from the podspec (and verify expected **`.xcframework`** folders exist at repo root)
2. Create git tag `{version}` on `main` (skip if tag already exists)
3. Create a **GitHub Release** for that tag with install notes (no required **zip** asset; binaries ship as the tagged tree)
4. Publish to CocoaPods trunk via `pod trunk push` (skip if that version is already on trunk)

Idempotent — skips tag/release/trunk steps when they already exist.

---

## Key Files

### Source repo (`pulse-ios-sdk`)

| File | Purpose |
|------|---------|
| `PulseKit.podspec` | Source of truth for version |
| `Scripts/build-xcframework.sh` | Builds xcframework from CocoaPods workspace |
| `Examples/PulseIOSExample/` | Example app used during xcframework build |
| `.github/workflows/BuildAndTest.yml` | PR validation |
| `.github/workflows/release.yml` | Build + push to release repo |

### Release repo (`pulse-ios`)

| File | Purpose |
|------|---------|
| `PulseKit.xcframework/` | Prebuilt binary framework (and peer `*.xcframework/` trees for SPM path targets) |
| `Package.swift` | SPM manifest (path `.binaryTarget` entries + wrapper) |
| `PulseKit.podspec` | CocoaPods spec (vendored framework) |
| `Sources/PulseKitWrapper/Exports.swift` | `@_exported import PulseKit` |
| `Example/Sources/PulseKitValidation.swift` | CI validation target |
| `.github/workflows/validate-pr.yml` | Release PR validation |
| `.github/workflows/publish.yml` | Tag + release + CocoaPods publish |

---

## Important Notes

### `@_implementationOnly import` for KSCrash

The xcframework is built with CocoaPods, where KSCrash exposes a single `KSCrash` module. In SPM, KSCrash exposes separate modules (`KSCrashRecording`, `KSCrashFilters`). To keep the xcframework compatible with both:

```swift
#if canImport(KSCrashRecording)
  @_implementationOnly import KSCrashRecording
#elseif canImport(KSCrash)
  @_implementationOnly import KSCrash
#endif
```

`@_implementationOnly` prevents the import from appearing in the `.swiftinterface`, so consumers don't need to resolve the KSCrash module regardless of which package manager they use.

### Secrets Required

| Secret | Repo | Purpose |
|--------|------|---------|
| `RELEASE_REPO_TOKEN` | Source | Push branches + create PRs in release repo |
| `COCOAPODS_TRUNK_EMAIL` | Release | CocoaPods trunk auth |
| `COCOAPODS_TRUNK_TOKEN` | Release | CocoaPods trunk auth |
