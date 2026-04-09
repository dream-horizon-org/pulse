/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

// MARK: - Metrics To Add Entry

/// One `signals.metricsToAdd` rule. `type` matches server `MetricsToAddEntry.type` (JSON key `"type"`).
public struct PulseMetricsToAddEntry: Codable, Equatable {
    public let name: String
    public let target: PulseMetricsToAddTarget
    public let condition: PulseSignalMatchCondition
    public let type: PulseMetricsData
    public let attributesToPick: [PulseSignalMatchCondition]

    enum CodingKeys: String, CodingKey {
        case name
        case target
        case condition
        case type
        case attributesToPick
    }

    public init(
        name: String,
        target: PulseMetricsToAddTarget,
        condition: PulseSignalMatchCondition,
        type: PulseMetricsData,
        attributesToPick: [PulseSignalMatchCondition] = []
    ) {
        self.name = name
        self.target = target
        self.condition = condition
        self.type = type
        self.attributesToPick = attributesToPick
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        target = try c.decode(PulseMetricsToAddTarget.self, forKey: .target)
        condition = try c.decode(PulseSignalMatchCondition.self, forKey: .condition)
        type = try c.decode(PulseMetricsData.self, forKey: .type)
        attributesToPick = (try? c.decode([PulseSignalMatchCondition].self, forKey: .attributesToPick)) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(name, forKey: .name)
        try c.encode(target, forKey: .target)
        try c.encode(condition, forKey: .condition)
        try c.encode(type, forKey: .type)
        if !attributesToPick.isEmpty {
            try c.encode(attributesToPick, forKey: .attributesToPick)
        }
    }
}

// MARK: - Metrics To Add Target

/// Jackson-style polymorphic `target`: discriminator JSON key `type` (`name` | `attribute`).
/// Attribute variant is flat: `condition` and `shouldAddPropNameAsSuffix` sit beside `type`, not nested under `attribute`.
public enum PulseMetricsToAddTarget: Codable, Equatable {
    case name
    case attribute(condition: PulseSignalMatchCondition, addPropNameAsSuffix: Bool)

    private enum TargetKeys: String, CodingKey {
        case type
        case condition
        case shouldAddPropNameAsSuffix
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: TargetKeys.self)
        let typeStr = try c.decode(String.self, forKey: .type)
        switch typeStr {
        case "name":
            self = .name
        case "attribute":
            let condition = try c.decode(PulseSignalMatchCondition.self, forKey: .condition)
            let addPropNameAsSuffix = try c.decodeIfPresent(Bool.self, forKey: .shouldAddPropNameAsSuffix) ?? false
            self = .attribute(condition: condition, addPropNameAsSuffix: addPropNameAsSuffix)
        default:
            throw DecodingError.dataCorrupted(
                DecodingError.Context(
                    codingPath: decoder.codingPath,
                    debugDescription: "target.type must be \"name\" or \"attribute\", got \"\(typeStr)\""
                )
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: TargetKeys.self)
        switch self {
        case .name:
            try c.encode("name", forKey: .type)
        case .attribute(let condition, let addPropNameAsSuffix):
            try c.encode("attribute", forKey: .type)
            try c.encode(condition, forKey: .condition)
            try c.encode(addPropNameAsSuffix, forKey: .shouldAddPropNameAsSuffix)
        }
    }
}

// MARK: - Metrics Data (Instrument Type)

/// Polymorphic instrument: discriminator `type` = counter | gauge | histogram | sum.
/// Omitted `sum.isMonotonic` decodes as false (matches server `MetricsType.Sum`).
public enum PulseMetricsData: Codable, Equatable {
    case counter
    case gauge(isFraction: Bool)
    case histogram(bucket: [Double]?, isFraction: Bool)
    case sum(isFraction: Bool, isMonotonic: Bool)

    private enum CodingKeys: String, CodingKey {
        case type
        case isFraction
        case bucket
        case isMonotonic
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let kind = try c.decode(String.self, forKey: .type)
        switch kind {
        case "counter":
            self = .counter
        case "gauge":
            let isFraction = try c.decodeIfPresent(Bool.self, forKey: .isFraction) ?? false
            self = .gauge(isFraction: isFraction)
        case "histogram":
            let bucket = try c.decodeIfPresent([Double].self, forKey: .bucket)
            let isFraction = try c.decodeIfPresent(Bool.self, forKey: .isFraction) ?? false
            self = .histogram(bucket: bucket, isFraction: isFraction)
        case "sum":
            let isFraction = try c.decodeIfPresent(Bool.self, forKey: .isFraction) ?? false
            let isMonotonic = try c.decodeIfPresent(Bool.self, forKey: .isMonotonic) ?? false
            self = .sum(isFraction: isFraction, isMonotonic: isMonotonic)
        default:
            throw DecodingError.dataCorrupted(
                DecodingError.Context(
                    codingPath: decoder.codingPath,
                    debugDescription: "PulseMetricsData.type must be counter, gauge, histogram, or sum, got \"\(kind)\""
                )
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .counter:
            try c.encode("counter", forKey: .type)
        case .gauge(let isFraction):
            try c.encode("gauge", forKey: .type)
            try c.encode(isFraction, forKey: .isFraction)
        case .histogram(let bucket, let isFraction):
            try c.encode("histogram", forKey: .type)
            try c.encodeIfPresent(bucket, forKey: .bucket)
            try c.encode(isFraction, forKey: .isFraction)
        case .sum(let isFraction, let isMonotonic):
            try c.encode("sum", forKey: .type)
            try c.encode(isFraction, forKey: .isFraction)
            try c.encode(isMonotonic, forKey: .isMonotonic)
        }
    }
}
