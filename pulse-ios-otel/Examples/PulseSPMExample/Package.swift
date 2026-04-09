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
    dependencies: [
        .package(path: "../.."),
    ],
    targets: [
        .target(
            name: "PulseKitWrapper",
            dependencies: [
                .product(name: "PulseKit", package: "pulse-ios-sdk"),
            ],
            path: "PulseKitWrapper"
        ),
    ]
)
