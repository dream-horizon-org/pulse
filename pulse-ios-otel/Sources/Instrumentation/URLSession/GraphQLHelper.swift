/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi

/// GraphQL detection and attribute extraction for URLSession network spans.
/// When httpBody is nil (e.g. request uses httpBodyStream), no GraphQL attributes are derived from body.
enum GraphQLHelper {
    /// Pattern: start of trimmed query string, then optional whitespace, then query|mutation|subscription, then optional name.
    /// Group 1 = operation type, Group 2 = operation name (optional).
    private static let queryRegexPattern = #"^\s*(query|mutation|subscription)\s+(\w+)?"#

    /// Returns true iff the URL's absolute string contains "graphql" (case-insensitive).
    static func isGraphQLRequest(url: URL?) -> Bool {
        guard let url else { return false }
        return url.absoluteString.lowercased().contains("graphql")
    }

    /// Returns 0–2 attributes (graphql.operation.name, graphql.operation.type) when derivable from body or URL query params.
    /// Uses only `body`; when body is nil (e.g. httpBodyStream), body parsing is skipped.
    static func graphQLAttributes(url: URL?, body: Data?) -> [String: AttributeValue] {
        var name: String?
        var type: String?

        if let body, !body.isEmpty {
            parseBody(body, name: &name, type: &type)
        }

        if name == nil || type == nil, let url {
            parseQueryParams(url: url, name: &name, type: &type)
        }

        var result = [String: AttributeValue]()
        if let n = name, !n.isEmpty {
            result[PulseAttributes.graphqlOperationName] = AttributeValue.string(n)
        }
        if let t = type, !t.isEmpty {
            result[PulseAttributes.graphqlOperationType] = AttributeValue.string(t)
        }
        return result
    }

    private static func parseBody(_ data: Data, name: inout String?, type: inout String?) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        if name == nil, let v = json["operationName"] as? String, !v.isEmpty {
            name = v
        }
        if type == nil, let v = json["operation"] as? String, !v.isEmpty {
            type = v.lowercased()
        }
        if (name == nil || type == nil), let query = json["query"] as? String, !query.isEmpty {
            parseQueryString(query, name: &name, type: &type)
        }
    }

    private static func parseQueryParams(url: URL, name: inout String?, type: inout String?) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let items = components.queryItems else { return }
        for item in items {
            if name == nil, item.name == "operationName", let v = item.value, !v.isEmpty {
                name = v
            }
            if type == nil, item.name == "operation", let v = item.value, !v.isEmpty {
                type = v.lowercased()
            }
        }
    }

    /// Regex: ^\s*(query|mutation|subscription)\s+(\w+)? — case-insensitive, run on trimmed string.
    /// Group 1 = operation type, Group 2 = operation name (optional).
    /// If regex does not match, GraphQL allows abbreviated form (e.g. "{ __typename }") which is implied query.
    private static func parseQueryString(_ query: String, name: inout String?, type: inout String?) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let regex = try? NSRegularExpression(pattern: queryRegexPattern, options: .caseInsensitive)
        let match = regex.flatMap { $0.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) }
        if let match {
            if type == nil, match.numberOfRanges > 1,
               let r = Range(match.range(at: 1), in: trimmed) {
                type = String(trimmed[r]).lowercased()
            }
            if name == nil, match.numberOfRanges > 2, match.range(at: 2).location != NSNotFound,
               let r = Range(match.range(at: 2), in: trimmed) {
                name = String(trimmed[r])
            }
        } else if type == nil, trimmed.first == "{" {
            type = "query"
        }
    }
}
