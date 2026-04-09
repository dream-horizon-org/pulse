/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import OpenTelemetryApi
@testable import PulseKit
import XCTest

final class GraphQLHelperTests: XCTestCase {

    // MARK: - isGraphQLRequest

    func test_isGraphQLRequest_returnsTrue_forGraphQLPath() {
        XCTAssertTrue(GraphQLHelper.isGraphQLRequest(url: URL(string: "https://a.com/graphql")))
        XCTAssertTrue(GraphQLHelper.isGraphQLRequest(url: URL(string: "https://a.com/v1/graphql")))
        XCTAssertTrue(GraphQLHelper.isGraphQLRequest(url: URL(string: "https://graphql.example.com/api")))
    }

    func test_isGraphQLRequest_returnsTrue_forFalsePositiveURL() {
        XCTAssertTrue(GraphQLHelper.isGraphQLRequest(url: URL(string: "https://api.example.com/toggle-graphql-mode")))
    }

    func test_isGraphQLRequest_returnsFalse_forNonGraphQL() {
        XCTAssertFalse(GraphQLHelper.isGraphQLRequest(url: URL(string: "https://a.com/rest")))
        XCTAssertFalse(GraphQLHelper.isGraphQLRequest(url: URL(string: "https://a.com/rest/users")))
    }

    func test_isGraphQLRequest_returnsFalse_forNil() {
        XCTAssertFalse(GraphQLHelper.isGraphQLRequest(url: nil))
    }

    // MARK: - graphQLAttributes from body (operationName + operation)

    func test_graphQLAttributes_fromBody_operationNameAndOperation() {
        let body = #"{"operationName":"GetUser","operation":"query"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationName, expected: "GetUser")
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "query")
    }

    func test_graphQLAttributes_fromBody_operationOnly() {
        let body = #"{"operation":"mutation"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        XCTAssertNil(attrs[PulseAttributes.graphqlOperationName])
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "mutation")
    }

    func test_graphQLAttributes_fromBody_queryOnly_namedOperation() {
        let body = #"{"query":"query Foo { x }"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationName, expected: "Foo")
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "query")
    }

    func test_graphQLAttributes_fromBody_queryOnly_unnamedMutation() {
        let body = #"{"query":"mutation { deleteUser }"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        XCTAssertNil(attrs[PulseAttributes.graphqlOperationName])
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "mutation")
    }

    func test_graphQLAttributes_fromBody_queryOnly_unnamedQuery() {
        let body = #"{"query":"{ __typename }"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        XCTAssertNil(attrs[PulseAttributes.graphqlOperationName])
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "query")
    }

    func test_graphQLAttributes_fromBody_queryOnly_namedMutation() {
        let body = #"{"query":"mutation CreateUser($n: String!) { createUser(name: $n) { id } }"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationName, expected: "CreateUser")
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "mutation")
    }

    // MARK: - graphQLAttributes from URL query params

    func test_graphQLAttributes_fromQueryParams() {
        let url = URL(string: "https://a.com/graphql?operationName=Bar&operation=subscription")!
        let attrs = GraphQLHelper.graphQLAttributes(url: url, body: nil)
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationName, expected: "Bar")
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "subscription")
    }

    // MARK: - graphQLAttributes empty / safety

    func test_graphQLAttributes_returnsEmpty_whenBodyNilAndUrlNonGraphQL() {
        let url = URL(string: "https://a.com/rest")!
        let attrs = GraphQLHelper.graphQLAttributes(url: url, body: nil)
        XCTAssertTrue(attrs.isEmpty)
    }

    func test_graphQLAttributes_returnsEmptyOrPartial_forInvalidJSON() {
        let body = "not json".data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        XCTAssertTrue(attrs.isEmpty)
    }

    func test_graphQLAttributes_returnsEmpty_forEmptyBody() {
        let body = Data()
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        XCTAssertTrue(attrs.isEmpty)
    }

    // MARK: - Regex group behavior (direct query string semantics)

    func test_regex_namedQuery() {
        let body = #"{"query":"query Foo { }"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationName, expected: "Foo")
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "query")
    }

    func test_regex_unnamedMutation() {
        let body = #"{"query":"mutation { x }"}"#.data(using: .utf8)!
        let attrs = GraphQLHelper.graphQLAttributes(url: nil, body: body)
        XCTAssertNil(attrs[PulseAttributes.graphqlOperationName])
        assertAttribute(attrs, key: PulseAttributes.graphqlOperationType, expected: "mutation")
    }

    // MARK: - Helpers

    private func assertAttribute(_ attrs: [String: AttributeValue], key: String, expected: String) {
        guard let value = attrs[key] else {
            XCTFail("Missing attribute: \(key)")
            return
        }
        if case .string(let s) = value {
            XCTAssertEqual(s, expected, "\(key)")
        } else {
            XCTFail("Attribute \(key) is not a string")
        }
    }
}
