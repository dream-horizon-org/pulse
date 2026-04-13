// swift-tools-version:5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import Foundation
import PackageDescription

let package = Package(
  name: "pulse-ios-sdk",
  platforms: [
    .macOS(.v12),
    .iOS(.v13),
    .tvOS(.v13),
    .watchOS(.v6),
    .visionOS(.v1)
  ],
  products: [
    .library(name: "SwiftMetricsShim", targets: ["SwiftMetricsShim"]),
    .library(name: "PrometheusExporter", targets: ["PrometheusExporter"]),
    .library(name: "OpenTelemetryProtocolExporter", targets: ["OpenTelemetryProtocolExporterGrpc"]),
    .library(
      name: "OpenTelemetryProtocolExporterHTTP", targets: ["OpenTelemetryProtocolExporterHttp"]
    ),
    .library(name: "PersistenceExporter", targets: ["PersistenceExporter"]),
    .library(name: "InMemoryExporter", targets: ["InMemoryExporter"]),
    .library(name: "OTelSwiftLog", targets: ["OTelSwiftLog"]),
    .library(name: "BaggagePropagationProcessor", targets: ["BaggagePropagationProcessor"])
  ],
  dependencies: [
    .package(url: "https://github.com/open-telemetry/opentelemetry-swift-core.git", from: "2.3.0"),
    .package(url: "https://github.com/apple/swift-nio.git", from: "2.90.1"),
    .package(url: "https://github.com/grpc/grpc-swift.git", exact: "1.27.0"),
    .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.33.3"),
    .package(url: "https://github.com/apple/swift-log.git", from: "1.6.4"),
    .package(url: "https://github.com/apple/swift-metrics.git", from: "2.7.1"),
    .package(url: "https://github.com/kstenerud/KSCrash.git", .upToNextMajor(from: "2.5.1")),
    .package(url: "https://github.com/SDWebImage/libwebp-Xcode.git", from: "1.5.0"),
  ],
  targets: [
    .target(
      name: "SharedTestUtils",
      dependencies: [],
      path: "Tests/Shared/TestUtils"
    ),
    .target(
      name: "OTelSwiftLog",
      dependencies: [
        .product(name: "OpenTelemetryApi", package: "opentelemetry-swift-core"),
        .product(name: "Logging", package: "swift-log")
      ],
      path: "Sources/Bridges/OTelSwiftLog",
      exclude: ["README.md"]
    ),
    .target(
      name: "SwiftMetricsShim",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
        .product(name: "CoreMetrics", package: "swift-metrics")
      ],
      path: "Sources/Importers/SwiftMetricsShim",
      exclude: ["README.md"]
    ),
    .target(
      name: "PrometheusExporter",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
        .product(name: "NIO", package: "swift-nio"),
        .product(name: "NIOHTTP1", package: "swift-nio")
      ],
      path: "Sources/Exporters/Prometheus"
    ),
    .target(
      name: "OpenTelemetryProtocolExporterCommon",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
        .product(name: "Logging", package: "swift-log"),
        .product(name: "SwiftProtobuf", package: "swift-protobuf")
      ],
      path: "Sources/Exporters/OpenTelemetryProtocolCommon"
    ),
    .target(
      name: "OpenTelemetryProtocolExporterHttp",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
        "OpenTelemetryProtocolExporterCommon"
      ],
      path: "Sources/Exporters/OpenTelemetryProtocolHttp"
    ),
    .target(
      name: "OpenTelemetryProtocolExporterGrpc",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
        "OpenTelemetryProtocolExporterCommon",
        .product(name: "GRPC", package: "grpc-swift")
      ],
      path: "Sources/Exporters/OpenTelemetryProtocolGrpc"
    ),
    .target(
      name: "InMemoryExporter",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
      ],
      path: "Sources/Exporters/InMemory"
    ),
    .target(
      name: "PersistenceExporter",
      dependencies: [
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
      ],
      path: "Sources/Exporters/Persistence",
      exclude: ["README.md"]
    ),
    .target(
      name: "BaggagePropagationProcessor",
      dependencies: [
        .product(name: "OpenTelemetryApi", package: "opentelemetry-swift-core"),
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")

      ],
      path: "Sources/Contrib/Processors/BaggagePropagationProcessor"
    ),
    .testTarget(
      name: "OTelSwiftLogTests",
      dependencies: ["OTelSwiftLog"],
      path: "Tests/BridgesTests/OTelSwiftLog"
    ),
    .testTarget(
      name: "SwiftMetricsShimTests",
      dependencies: [
        "SwiftMetricsShim",
        .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
      ],
      path: "Tests/ImportersTests/SwiftMetricsShim"
    ),
    .testTarget(
      name: "PrometheusExporterTests",
      dependencies: ["PrometheusExporter"],
      path: "Tests/ExportersTests/Prometheus"
    ),
    .testTarget(
      name: "OpenTelemetryProtocolExporterTests",
      dependencies: [
        "OpenTelemetryProtocolExporterGrpc",
        "OpenTelemetryProtocolExporterHttp",
        "SharedTestUtils",
      ],
      path: "Tests/ExportersTests/OpenTelemetryProtocol"
    ),
    .testTarget(
      name: "InMemoryExporterTests",
      dependencies: ["InMemoryExporter"],
      path: "Tests/ExportersTests/InMemory"
    ),
    .testTarget(
      name: "PersistenceExporterTests",
      dependencies: ["PersistenceExporter"],
      path: "Tests/ExportersTests/PersistenceExporter"
    ),
    .testTarget(
      name: "ContribTests",
      dependencies: [
        "BaggagePropagationProcessor",
        "InMemoryExporter"
      ]
    )
  ]
).addPlatformSpecific()

extension Package {
  func addPlatformSpecific() -> Self {
    #if canImport(ObjectiveC)
      dependencies.append(
        .package(url: "https://github.com/undefinedlabs/opentracing-objc", from: "0.5.2")
      )
      products.append(
        .library(name: "OpenTracingShim-experimental", targets: ["OpenTracingShim"])
      )
      targets.append(contentsOf: [
        .target(
          name: "OpenTracingShim",
          dependencies: [
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
            .product(name: "Opentracing", package: "opentracing-objc")
          ],
          path: "Sources/Importers/OpenTracingShim",
          exclude: ["README.md"]
        ),
        .testTarget(
          name: "OpenTracingShimTests",
          dependencies: [
            "OpenTracingShim",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/ImportersTests/OpenTracingShim"
        )
      ])
    #endif

    #if canImport(Darwin)
      dependencies.append(
        .package(url: "https://github.com/undefinedlabs/Thrift-Swift", from: "1.1.1")
      )
      products.append(contentsOf: [
        .library(name: "JaegerExporter", targets: ["JaegerExporter"]),
        .library(name: "ZipkinExporter", targets: ["ZipkinExporter"]),
        .library(name: "MetricKitInstrumentation", targets: ["MetricKitInstrumentation"]),
        .library(name: "PulseKit", targets: ["PulseKit"])
      ])
      targets.append(contentsOf: [
        .target(
          name: "JaegerExporter",
          dependencies: [
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
            .product(
              name: "Thrift", package: "Thrift-Swift",
              condition: .when(platforms: [.iOS, .macOS, .tvOS, .macCatalyst, .linux])
            )
          ],
          path: "Sources/Exporters/Jaeger"
        ),
        .testTarget(
          name: "JaegerExporterTests",
          dependencies: ["JaegerExporter"],
          path: "Tests/ExportersTests/Jaeger"
        ),
        .target(
          name: "ZipkinExporter",
          dependencies: [
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Sources/Exporters/Zipkin"
        ),
        .testTarget(
          name: "ZipkinExporterTests",
          dependencies: ["ZipkinExporter"],
          path: "Tests/ExportersTests/Zipkin"
        ),
        .target(
          name: "MetricKitInstrumentation",
          dependencies: [
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Sources/Instrumentation/MetricKit",
          exclude: ["README.md"]
        ),
        .testTarget(
          name: "MetricKitInstrumentationTests",
          dependencies: [
            "MetricKitInstrumentation",
            "InMemoryExporter",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/MetricKitTests"
        ),
        .target(
          name: "PulseKit",
          dependencies: [
            .product(name: "OpenTelemetryApi", package: "opentelemetry-swift-core"),
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core"),
            .product(name: "StdoutExporter", package: "opentelemetry-swift-core"),
            .product(name: "Recording", package: "KSCrash"),
            .product(name: "Filters", package: "KSCrash"),
            "OpenTelemetryProtocolExporterHttp",
            "PersistenceExporter",
            .product(name: "libwebp", package: "libwebp-Xcode")
          ],
          path: "Sources",
          exclude: [
            "PulseKit/README.md",
            "PulseKit/Consent/README.md",
            "PulseKit/Sampling/README.md",
            "Instrumentation/Sessions/README.md",
            "Instrumentation/Crashes/README.md",
            "Instrumentation/URLSession/README.md",
            "Instrumentation/Interaction/README.md",
            "Instrumentation/Interaction/Internal_Interaction.md",
            "Instrumentation/SignPostIntegration/README.md",
            "Instrumentation/SDKResourceExtension/README.md",
            "Instrumentation/Location/README.md",
            "Instrumentation/AppLifecycle/README.md",
            "Instrumentation/UIKitTap/README.md",
            "Instrumentation/SessionReplay/README.md"
          ],
          sources: [
            "PulseKit",
            "Instrumentation/Sessions",
            "Instrumentation/Crashes",
            "Instrumentation/URLSession",
            "Instrumentation/Interaction",
            "Instrumentation/NetworkStatus",
            "Instrumentation/SignPostIntegration",
            "Instrumentation/SDKResourceExtension",
            "Instrumentation/Location",
            "Instrumentation/AppLifecycle",
            "Instrumentation/UIKitTap",
            "Instrumentation/SessionReplay"
          ],
          linkerSettings: [.linkedFramework("CoreTelephony", .when(platforms: [.iOS]))]
        ),
        .testTarget(
          name: "SessionTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/SessionTests"
        ),
        .testTarget(
          name: "NetworkStatusTests",
          dependencies: [
            "PulseKit"
          ],
          path: "Tests/InstrumentationTests/NetworkStatusTests"
        ),
        .testTarget(
          name: "URLSessionInstrumentationTests",
          dependencies: [
            "PulseKit",
            "SharedTestUtils",
            .product(name: "OpenTelemetryApi", package: "opentelemetry-swift-core"),
          ],
          path: "Tests/InstrumentationTests/URLSessionTests"
        ),
        .testTarget(
          name: "InteractionInstrumentationTests",
          dependencies: [
            "PulseKit",
            "InMemoryExporter",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/InteractionTests"
        ),
        .testTarget(
          name: "LocationTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetryApi", package: "opentelemetry-swift-core"),
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/LocationTests"
        ),
        .testTarget(
          name: "CrashInstrumentationTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/CrashTests"
        ),
        .testTarget(
          name: "AppLifecycleTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/AppLifecycleTests"
        ),
        .testTarget(
          name: "UIKitTapTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/UIKitTapTests"
        ),
        .testTarget(
          name: "SessionReplayTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/SessionReplayTests"
        ),
        .testTarget(
          name: "ResourceExtensionTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/InstrumentationTests/SDKResourceExtensionTests"
        ),
        .testTarget(
          name: "PulseKitTests",
          dependencies: [
            "PulseKit",
            .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift-core")
          ],
          path: "Tests/PulseKitTests"
        )
      ])
    #endif

    return self
  }
}

if ProcessInfo.processInfo.environment["OTEL_ENABLE_SWIFTLINT"] != nil {
  package.dependencies.append(contentsOf: [
    .package(url: "https://github.com/SimplyDanny/SwiftLintPlugins", from: "0.57.1")
  ])

  for target in package.targets {
    target.plugins = [
      .plugin(name: "SwiftLintBuildToolPlugin", package: "SwiftLintPlugins")
    ]
  }
}
