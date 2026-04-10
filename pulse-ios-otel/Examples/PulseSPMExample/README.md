# PulseSPMExample

Minimal Xcode + SwiftPM example to run **PulseKit from source** (same layout as the CocoaPods example, but through SPM). It does not exhaust every SDK feature—use **PulseIOSExample** for broader coverage.

## Option 1: Source (default)

The checked-in **`Package.swift`** points at **`../../`** (the **`pulse-ios-otel/`** root) and depends on the **`PulseKit`** product from package **`pulse-ios-sdk`**.

1. Open **`PulseSPMExample.xcodeproj`** in Xcode.
2. Select scheme **PulseSPMExample**, an iOS Simulator, then **Run** (⌘R).

## Option 2: Local XCFrameworks

Use this to validate linking against **`build/*.xcframework`** (same set as **`PulseKit.podspec`** peers).

### Step 1: Build XCFrameworks

From the **`pulse-ios-otel`** root (in a full monorepo clone that is **`pulse/pulse-ios-otel/`**):

```bash
cd Examples/PulseIOSExample
pod install
cd ../..
./Scripts/build-xcframework.sh
```

The second **`cd`** leaves you in **`pulse-ios-otel/`**, where **`./Scripts/build-xcframework.sh`** must run.

Confirm **`build/`** contains every peer listed in **`PulseKit.podspec`** (PulseKit, KSCrash, OpenTelemetryApi, OpenTelemetrySdk, SwiftProtobuf, libwebp, etc.).

### Step 2: Point SPM at `build/`

Replace **`Package.swift`** with a manifest that uses **`.binaryTarget`** entries. Paths below are relative to **`Examples/PulseSPMExample/`**; **`../../build/`** is **`pulse-ios-otel/build/`**.

```swift
// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "PulseSPMExample",
    platforms: [
        .iOS(.v15),
    ],
    products: [
        .library(name: "PulseSPMExampleSupport", targets: ["PulseKitWrapper"]),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "PulseKitBinary",
            path: "../../build/PulseKit.xcframework"
        ),
        .binaryTarget(
            name: "KSCrashBinary",
            path: "../../build/KSCrash.xcframework"
        ),
        .binaryTarget(
            name: "OpenTelemetryApiBinary",
            path: "../../build/OpenTelemetryApi.xcframework"
        ),
        .binaryTarget(
            name: "OpenTelemetrySdkBinary",
            path: "../../build/OpenTelemetrySdk.xcframework"
        ),
        .binaryTarget(
            name: "SwiftProtobufBinary",
            path: "../../build/SwiftProtobuf.xcframework"
        ),
        .binaryTarget(
            name: "libwebpBinary",
            path: "../../build/libwebp.xcframework"
        ),
        .target(
            name: "PulseKitWrapper",
            dependencies: [
                "PulseKitBinary",
                "KSCrashBinary",
                "OpenTelemetryApiBinary",
                "OpenTelemetrySdkBinary",
                "SwiftProtobufBinary",
                "libwebpBinary",
            ],
            path: "PulseKitWrapper"
        ),
    ]
)
```

Add additional **`.binaryTarget`** lines if **`print-peer-xcframework-entries.rb`** / **`build/`** includes more frameworks (e.g. **libwebp**).

### Step 3: Clean and run

**Product → Clean Build Folder** (⇧⌘K), then **Run** (⌘R) on **PulseSPMExample**.

---

## Production apps

Ship with **[dream-horizon-org/pulse-ios](https://github.com/dream-horizon-org/pulse-ios)** (CocoaPods or that repo’s **`Package.swift`**).
