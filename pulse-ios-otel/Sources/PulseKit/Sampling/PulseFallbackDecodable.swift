/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Conform any `String` `RawRepresentable` enum that has an `.unknown` case.
/// Unrecognised or null values from the server decode to `.unknown` instead of throwing.
///
/// Usage — replace the hand-written `extension MyEnum: Codable { ... }` with:
///   `enum MyEnum: String, CaseIterable, PulseFallbackDecodable { case foo; case unknown }`
public protocol PulseFallbackDecodable: RawRepresentable, Codable where RawValue == String {
    static var unknown: Self { get }
}

public extension PulseFallbackDecodable {
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .unknown
            return
        }
        let raw = (try? container.decode(String.self)) ?? ""
        self = Self(rawValue: raw) ?? .unknown
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}
