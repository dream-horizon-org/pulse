# PulseSPMExample

Minimal SPM based example app to test Pulse source code and framework with SPM as package manager. This doesn't consists exhaustive list of all features to test.

## How to build

### Option 1: Source code (default)

The checked-in **`Package.swift`** depends on the **repository root** (`../..`) and the **`PulseKit`** product from **`pulse-ios-sdk`**. No xcframeworks are required.

1. Open **`PulseSPMExample.xcodeproj`** in Xcode.
2. Select scheme **PulseSPMExample**, a simulator, then **Run** (⌘R).

### Option 2: xcframeworks

**Step 1: Create xcframeworks and verify outputs**

1. From **`Examples/PulseIOSExample/`**, run **`pod install`** (needed for the workspace the script archives).
2. From the **repository root**, run **`./Scripts/build-xcframework.sh`**.
3. Confirm all the necassary frameworks are build as per depdencies added in PulseKit.podspec.

**Step 2: Point the package at frameworks instead of the repo root**

Replace **`Package.swift`** with:

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
        .target(
            name: "PulseKitWrapper",
            dependencies: [
                "PulseKitBinary",
                "KSCrashBinary",
                "OpenTelemetryApiBinary",
                "OpenTelemetrySdkBinary",
                "SwiftProtobufBinary",
            ],
            path: "PulseKitWrapper"
        ),
    ]
)
```

**Step 3: Clean the build folder in Xcode**

**Product → Clean Build Folder** (⇧⌘K).

**Step 4: Build the app from Xcode**

Open **`PulseSPMExample.xcodeproj`**, choose scheme **PulseSPMExample**, then **Run** (⌘R).
