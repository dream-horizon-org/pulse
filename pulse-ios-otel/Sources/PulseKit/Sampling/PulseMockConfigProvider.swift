/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provides hardcoded PulseSdkConfig for dev/testing. Use with PulseSdkConfigCoordinator(useLocalMockConfig: true).
 * Do NOT commit useLocalMockConfig = true in production code.
 */

import Foundation

/// Which metricsToAdd scenario to include. Use one at a time for focused testing.
public enum PulseMockMetricsTestCase: CaseIterable {
    case counterTargetNameSpan
    case counterTargetNameLog
    case counterAttrSuffixFalse
    case counterAttrSuffixFalseButMutlipleAttr
    case counterAttrSuffixTrue
    case gaugeAttrSuffixFalse
    case gaugeAttrSuffixTrue
    case histogramAttrSuffixFalse
    case histogramAttrSuffixTrue
    case sumAttrSuffixFalse
    case sumAttrSuffixTrue
}

/// Static factory for a complete PulseSdkConfig with realistic metricsToAdd entries for dev/testing.
public enum PulseMockConfigProvider {
    /// Change this to test one scenario at a time. Nil = all scenarios (default for fullMockConfig).
    public static var activeMetricsTestCase: PulseMockMetricsTestCase? = nil

    /// Returns a full PulseSdkConfig with metricsToAdd. Uses activeMetricsTestCase when case is nil.
    /// Used when useLocalMockConfig is true in PulseSdkConfigCoordinator.
    public static func fullMockConfig(metricsCase: PulseMockMetricsTestCase? = nil) -> PulseSdkConfig {
        // metricsCase nil (default) → all scenarios. metricsCase set → single case for focused testing.
        let active = metricsCase
        return PulseSdkConfig(
            version: 999,
            description: "Local mock config for dev/testing",
            sampling: PulseSamplingConfig(
                default: PulseDefaultSamplingConfig(sessionSampleRate: 1.0),
                rules: [],
                signalsToSample: []
            ),
            signals: PulseSignalConfig(
                scheduleDurationMs: 60_000,
                logsCollectorUrl: "http://127.0.0.1:4318/v1/logs",
                metricCollectorUrl: "http://127.0.0.1:4318/v1/metrics",
                spanCollectorUrl: "http://127.0.0.1:4318/v1/traces",
                customEventCollectorUrl: "http://127.0.0.1:4318/v1/logs",
                attributesToDrop: [],
                attributesToAdd: [],
                metricsToAdd: makeMockMetricsToAdd(activeCase: active),
            ),
            interaction: PulseInteractionConfig(
                collectorUrl: "http://127.0.0.1:8080/v1/interaction-configs/",
                configUrl: "http://127.0.0.1:8080/v1/interaction-configs/",
                beforeInitQueueSize: 100
            ),
            features: makeMockFeaturesAllEnabled()
        )
    }

    /// All features enabled (sessionSampleRate 1) for pulse_ios_swift and pulse_ios_rn.
    private static func makeMockFeaturesAllEnabled() -> [PulseFeatureConfig] {
        let sdks: [PulseSdkName] = [.pulse_ios_swift, .pulse_ios_rn]
        return PulseFeatureName.allCases
            .filter { $0 != .unknown }
            .map { PulseFeatureConfig(featureName: $0, sessionSampleRate: 1, sdks: sdks) }
    }

    /// Returns metricsToAdd for the given case, or all when activeCase is nil.
    static func makeMockMetricsToAdd(activeCase: PulseMockMetricsTestCase?) -> [PulseMetricsToAddEntry] {
        let baseCondition = PulseSignalMatchCondition(
            name: ".*",
            props: [],
            scopes: [.traces],
            sdks: [.pulse_ios_swift, .pulse_ios_rn]
        )
        let logCondition = PulseSignalMatchCondition(
            name: ".*",
            props: [],
            scopes: [.logs],
            sdks: [.pulse_ios_swift, .pulse_ios_rn]
        )

        func entries(for case: PulseMockMetricsTestCase) -> [PulseMetricsToAddEntry] {
            switch `case` {
            case .counterTargetNameSpan:
                return [
                    PulseMetricsToAddEntry(name: "span_count", target: .name, condition: baseCondition, type: .counter)
                ]
            case .counterTargetNameLog:
                return [
                    PulseMetricsToAddEntry(name: "log_count", target: .name, condition: logCondition, type: .counter)
                ]
            case .counterAttrSuffixFalse:
                // Uses http.status_code – span must have this attribute (e.g. example spans with status 200)
                return [
                    PulseMetricsToAddEntry(
                        name: "http_req_count",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [PulseProp(name: "http\\.status_code", value: ".*")],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: false
                        ),
                        condition: baseCondition,
                        type: .counter
                    )
                ]
            case .counterAttrSuffixTrue:
                return [
                    PulseMetricsToAddEntry(
                        name: "http_count",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [
                                    PulseProp(name: "http\\.method", value: ".*"),
                                    PulseProp(name: "http\\.status_code", value: ".*")
                                ],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: true
                        ),
                        condition: baseCondition,
                        type: .counter
                    )
                ]
            case .counterAttrSuffixFalseButMutlipleAttr:
                return [
                    PulseMetricsToAddEntry(
                        name: "http_count",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [
                                    PulseProp(name: "http\\.method", value: ".*"),
                                    PulseProp(name: "http\\.status_code", value: ".*")
                                ],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: false
                        ),
                        condition: baseCondition,
                        type: .counter
                    )
                ]
            case .gaugeAttrSuffixFalse:
                return [
                    PulseMetricsToAddEntry(
                        name: "http_status_code_gauge",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [PulseProp(name: "http\\.status_code", value: ".*")],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: false
                        ),
                        condition: baseCondition,
                        type: .gauge(isFraction: true)
                    )
                ]
            case .gaugeAttrSuffixTrue:
                return [
                    PulseMetricsToAddEntry(
                        name: "latency_gauge",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [
                                    PulseProp(name: "http\\.status_code", value: ".*"),
                                    PulseProp(name: "http\\.method", value: ".*")
                                ],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: true
                        ),
                        condition: baseCondition,
                        type: .gauge(isFraction: true)
                    )
                ]
            case .histogramAttrSuffixFalse:
                // Uses http.duration – "Span with Events + Attributes" button sets this (150.0)
                return [
                    PulseMetricsToAddEntry(
                        name: "http_duration",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [PulseProp(name: "http\\.status_code", value: ".*")],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: false
                        ),
                        condition: baseCondition,
                        type: .histogram(bucket: [50, 100, 250, 500, 1000], isFraction: true)
                    )
                ]
            case .histogramAttrSuffixTrue:
                // http.duration from "Span with Events + Attributes", db.duration from "Nested Spans" child.db_query
                return [
                    PulseMetricsToAddEntry(
                        name: "latency_histogram",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [
                                    PulseProp(name: "http\\.status_code", value: ".*")
                                ],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: true
                        ),
                        condition: baseCondition,
                        type: .histogram(bucket: [10, 50, 100], isFraction: true)
                    )
                ]
            case .sumAttrSuffixFalse:
                // Uses http.response_content_length – "Span with Events + Attributes" sets 4096
                return [
                    PulseMetricsToAddEntry(
                        name: "bytes_sent",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [PulseProp(name: "http\\.response_content_length", value: ".*")],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: false
                        ),
                        condition: baseCondition,
                        type: .sum(isFraction: false, isMonotonic: true)
                    )
                ]
            case .sumAttrSuffixTrue:
                // Both from "Span with Events + Attributes" (0 and 4096) → bytes.http.request_content_length, bytes.http.response_content_length
                return [
                    PulseMetricsToAddEntry(
                        name: "bytes",
                        target: .attribute(
                            condition: PulseSignalMatchCondition(
                                name: ".*",
                                props: [
                                    PulseProp(name: "http\\.request_content_length", value: ".*"),
                                    PulseProp(name: "http\\.response_content_length", value: ".*")
                                ],
                                scopes: [.traces],
                                sdks: [.pulse_ios_swift, .pulse_ios_rn]
                            ),
                            addPropNameAsSuffix: true
                        ),
                        condition: baseCondition,
                        type: .sum(isFraction: false, isMonotonic: true)
                    )
                ]
            }
        }

        if let active = activeCase {
            return entries(for: active)
        }
        return PulseMockMetricsTestCase.allCases.flatMap { entries(for: $0) }
    }
}
