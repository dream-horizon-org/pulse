# PulseIOSExample

Example iOS app demonstrating PulseKit integration.

- **Option 1** — Default: compile PulseKit **from source** in this repo (normal SDK development).
- **Option 2** — Run the app against **local xcframeworks** from `./Scripts/build-xcframework.sh`.

---

## Option 1: Build from source (default)

The **`Podfile`** uses the development podspec at the repo root:

```ruby
pod 'PulseKit', path: '../../'
```

PulseKit is built from Swift sources; OpenTelemetry, SwiftProtobuf, KSCrash, and libwebp are resolved from **CocoaPods trunk**. **`use_frameworks!`** is required so dynamic Swift frameworks link correctly.

```bash
cd Examples/PulseIOSExample
pod install
open PulseIOSExample.xcworkspace
```

Build and run (**⌘R**) on an iOS 15.1+ simulator.

---

## Option 2: Build with XCFrameworks

Use this to validate the **binary** layout: PulseKit **and** its peer frameworks from **`build/`**, with **no** trunk pods for those dependencies.

### Step 1: Produce xcframeworks

The archive script expects the Example workspace to exist first:

```bash
cd Examples/PulseIOSExample
pod install
cd ../..

# From pulse-ios-sdk repo root
./Scripts/build-xcframework.sh
```

Outputs land under **`build/`** (for example `PulseKit.xcframework`, `KSCrash.xcframework`, `OpenTelemetryApi.xcframework`, `OpenTelemetrySdk.xcframework`, `SwiftProtobuf.xcframework`, `libwebp.xcframework`).

### Step 2: Switch podspec to use the frameworks

Edit **`PulseKit.podspec`** at the **repo root** (`pulse-ios-sdk/PulseKit.podspec`):

1. **Comment out** `spec.source_files`, `spec.exclude_files`, and `spec.pod_target_xcconfig`.
2. **Comment out** every **`spec.dependency`** line (OpenTelemetry-Swift-Api, OpenTelemetry-Swift-Sdk, SwiftProtobuf, KSCrash, libwebp). Those libraries are already inside the xcframeworks; leaving the dependencies in place would pull duplicate trunk pods.
3. **Add** `spec.vendored_frameworks` pointing at **all** artifacts under `build/`, not only PulseKit — same set as the release podspec, for example:

```ruby
spec.vendored_frameworks = [
  "build/PulseKit.xcframework",
  "build/KSCrash.xcframework",
  "build/OpenTelemetryApi.xcframework",
  "build/OpenTelemetrySdk.xcframework",
  "build/SwiftProtobuf.xcframework",
  "build/libwebp.xcframework",
]
```

You can keep **`spec.resources`** / **`spec.preserve_paths`** (e.g. sourcemap upload scripts) if you still need them for the example.

### Step 3: Reinstall pods and build

Leave the **`Podfile`** on **`pod 'PulseKit', path: '../../'`** so it picks up the edited podspec.

```bash
cd Examples/PulseIOSExample
pod install
open PulseIOSExample.xcworkspace
```

Build and run (**⌘R**). You should see a single **PulseKit** pod with no separate OpenTelemetry / SwiftProtobuf / KSCrash / libwebp pods. No `post_install` hook is required.

### Step 4: Restore the development podspec

When you go back to **Option 1**, **revert** **`PulseKit.podspec`** (e.g. `git checkout -- PulseKit.podspec`) so source files and dependencies are active again.

---

## Configuration

Telemetry defaults to `http://127.0.0.1:4318`. Change the endpoint in **`AppDelegate.swift`** if needed.
