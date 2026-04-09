/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// REST API implementation of InteractionConfigFetcher
/// Fetches interaction configurations from a remote server using URLSession
public class InteractionConfigRestFetcher: InteractionConfigFetcher {
    private let urlProvider: () -> String
    private let headers: [String: String]
    private let urlSession: URLSession

    public init(
        urlProvider: @escaping () -> String,
        headers: [String: String] = [:],
        urlSession: URLSession = .shared
    ) {
        self.urlProvider = urlProvider
        self.headers = headers
        self.urlSession = urlSession
    }

    public func getConfigs() async throws -> [InteractionConfig]? {
        let urlString = urlProvider()
        print("[Pulse] Interaction: requesting config from endpoint: \(urlString)")
        guard let url = URL(string: urlString) else {
            print("[Pulse] Interaction: invalid config URL (skipping fetch)")
            return nil
        }

        var request = URLRequest(url: url)
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        let (data, response) = try await urlSession.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            return nil
        }

        // Check HTTP status code
        guard (200...299).contains(httpResponse.statusCode) else {
            print("[Pulse] Interaction: config endpoint returned HTTP \(httpResponse.statusCode), response preview: \(String(data: data.prefix(500), encoding: .utf8) ?? "<unable to decode>")")
            return nil
        }

        // Check Content-Type header to ensure it's JSON
        if let contentType = httpResponse.value(forHTTPHeaderField: "Content-Type"),
           !contentType.contains("application/json") && !contentType.contains("text/json") {
            let preview = String(data: data.prefix(500), encoding: .utf8) ?? "<unable to decode>"
            print("[Pulse] Interaction: config endpoint returned non-JSON Content-Type: \(contentType). Endpoint: \(urlString). Response preview: \(preview)")
            throw DecodingError.dataCorrupted(
                DecodingError.Context(
                    codingPath: [],
                    debugDescription: "Expected JSON response but got Content-Type: \(contentType). URL might be incorrect or server returned error page."
                )
            )
        }

        // Try to decode JSON (API returns a raw array of interaction configs, no data/error wrapper)
        do {
            let configs = try JSONDecoder().decode([InteractionConfig].self, from: data)
            print("[Pulse] Interaction: config fetched successfully, \(configs.count) interaction(s) present")
            return configs
        } catch {
            // Log endpoint and raw response for debugging decode failures
            let responsePreview = String(data: data.prefix(500), encoding: .utf8) ?? "<unable to decode as UTF-8>"
            let decodeDetail = (error as? DecodingError).map { describe($0) } ?? error.localizedDescription
            print("[Pulse] Interaction: decode failed. Endpoint: \(urlString). HTTP status: \(httpResponse.statusCode). Decode error: \(decodeDetail). Response body (first 500 chars): \(responsePreview)")
            throw DecodingError.dataCorrupted(
                DecodingError.Context(
                    codingPath: [],
                    debugDescription: "Failed to decode JSON response. Response preview: \(responsePreview). Original error: \(error.localizedDescription). This might indicate the endpoint URL is incorrect or the server returned an error page."
                )
            )
        }
    }

    private func describe(_ error: DecodingError) -> String {
        switch error {
        case .keyNotFound(let key, let context):
            return "keyNotFound(\(key.stringValue)) at \(context.codingPath.map(\.stringValue).joined(separator: "."))"
        case .typeMismatch(let type, let context):
            return "typeMismatch(\(type)) at \(context.codingPath.map(\.stringValue).joined(separator: "."))"
        case .valueNotFound(let type, let context):
            return "valueNotFound(\(type)) at \(context.codingPath.map(\.stringValue).joined(separator: "."))"
        case .dataCorrupted(let context):
            return "dataCorrupted: \(context.debugDescription)"
        @unknown default:
            return error.localizedDescription
        }
    }
}

