/*
 * Copyright The Pulse Authors
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
        let t0 = Date()
        PulseLogger.debug(
            "sdk.interaction.config_fetch phase=start endpoint=\(PulseRedaction.redactUrl(urlString))")
        guard let url = URL(string: urlString) else {
            PulseLogger.warn(
                "sdk.interaction.config_fetch success=false duration_ms=0 error_class=invalid_url")
            return nil
        }

        var request = URLRequest(url: url)
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        let (data, response) = try await urlSession.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            let ms = Int(Date().timeIntervalSince(t0) * 1000)
            PulseLogger.warn(
                "sdk.interaction.config_fetch success=false duration_ms=\(ms) error_class=non_http_response")
            return nil
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let ms = Int(Date().timeIntervalSince(t0) * 1000)
            PulseLogger.warn(
                "sdk.interaction.config_fetch success=false duration_ms=\(ms) http_status=\(httpResponse.statusCode)")
            return nil
        }

        do {
            let configs = try JSONDecoder().decode([InteractionConfig].self, from: data)
            let ms = Int(Date().timeIntervalSince(t0) * 1000)
            PulseLogger.info(
                "sdk.interaction.config_fetch success=true duration_ms=\(ms) interactions_count=\(configs.count)")
            return configs
        } catch {
            let ms = Int(Date().timeIntervalSince(t0) * 1000)
            let decodeDetail = (error as? DecodingError).map { describe($0) } ?? error.localizedDescription
            PulseLogger.warn(
                "sdk.interaction.parse_failure duration_ms=\(ms) http_status=\(httpResponse.statusCode) error_detail=\(decodeDetail)"
            )
            let responsePreview = String(data: data.prefix(500), encoding: .utf8) ?? "<unable to decode as UTF-8>"
            PulseLogger.verbose("Interaction: decode failure response body (first 500 chars): \(responsePreview)")
            throw DecodingError.dataCorrupted(
                DecodingError.Context(
                    codingPath: [],
                    debugDescription: "Failed to decode JSON response. Original error: \(error.localizedDescription). This might indicate the endpoint URL is incorrect or the server returned an error page."
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
