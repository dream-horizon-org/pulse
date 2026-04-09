/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Unit tests for PulseSdkConfigRestProvider (mock URLSession: no real network).
 */

import XCTest
@testable import PulseKit

// MARK: - Mock URLProtocol

/// Intercepts URLSession requests and returns stubbed response. Use with URLSessionConfiguration.protocolClasses.
private final class MockConfigURLProtocol: URLProtocol {

    static var stub: (data: Data?, statusCode: Int, error: Error?)?

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "config.pulse.test"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let stub = Self.stub else {
            client?.urlProtocol(self, didFailWithError: NSError(domain: "MockConfigURLProtocol", code: -1, userInfo: [NSLocalizedDescriptionKey: "No stub set"]))
            return
        }
        if let error = stub.error {
            client?.urlProtocol(self, didFailWithError: error)
            return
        }
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: stub.statusCode,
            httpVersion: "1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        if let data = stub.data {
            client?.urlProtocol(self, didLoad: data)
        }
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

// MARK: - Tests

final class PulseSdkConfigRestProviderTests: XCTestCase {

    private var session: URLSession!

    override func setUp() {
        super.setUp()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockConfigURLProtocol.self]
        session = URLSession(configuration: config)
    }

    override func tearDown() {
        MockConfigURLProtocol.stub = nil
        session = nil
        super.tearDown()
    }

    func testProvideReturnsNilWhenUrlProviderReturnsNil() async {
        let provider = PulseSdkConfigRestProvider(urlProvider: { nil }, urlSession: session)
        let result = await provider.provide()
        XCTAssertNil(result)
    }

    func testProvideReturnsConfigWhenHTTP200AndValidJSON() async {
        let json = minimalConfigJSON(version: 2)
        MockConfigURLProtocol.stub = (data: json.data(using: .utf8), statusCode: 200, error: nil)
        let provider = PulseSdkConfigRestProvider(
            urlProvider: { URL(string: "https://config.pulse.test/v1/configs/active/") },
            urlSession: session
        )
        let result = await provider.provide()
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.version, 2)
    }

    func testProvideReturnsNilWhenHTTP404() async {
        MockConfigURLProtocol.stub = (data: "Not Found".data(using: .utf8), statusCode: 404, error: nil)
        let provider = PulseSdkConfigRestProvider(
            urlProvider: { URL(string: "https://config.pulse.test/v1/configs/active/") },
            urlSession: session
        )
        let result = await provider.provide()
        XCTAssertNil(result)
    }

    func testProvideReturnsNilWhenHTTP500() async {
        MockConfigURLProtocol.stub = (data: "Server Error".data(using: .utf8), statusCode: 500, error: nil)
        let provider = PulseSdkConfigRestProvider(
            urlProvider: { URL(string: "https://config.pulse.test/v1/configs/active/") },
            urlSession: session
        )
        let result = await provider.provide()
        XCTAssertNil(result)
    }

    func testProvideReturnsNilWhenResponseBodyIsInvalidJSON() async {
        MockConfigURLProtocol.stub = (data: "not json".data(using: .utf8), statusCode: 200, error: nil)
        let provider = PulseSdkConfigRestProvider(
            urlProvider: { URL(string: "https://config.pulse.test/v1/configs/active/") },
            urlSession: session
        )
        let result = await provider.provide()
        XCTAssertNil(result)
    }

    func testProvideReturnsNilWhenNetworkError() async {
        MockConfigURLProtocol.stub = (data: nil, statusCode: 200, error: NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet, userInfo: nil))
        let provider = PulseSdkConfigRestProvider(
            urlProvider: { URL(string: "https://config.pulse.test/v1/configs/active/") },
            urlSession: session
        )
        let result = await provider.provide()
        XCTAssertNil(result)
    }

    private func minimalConfigJSON(version: Int) -> String {
        """
        {
            "version": \(version),
            "description": "test",
            "sampling": { "default": { "sessionSampleRate": 0.5 }, "rules": [] },
            "signals": {
                "scheduleDurationMs": 60000,
                "logsCollectorUrl": "https://logs",
                "metricCollectorUrl": "https://metrics",
                "spanCollectorUrl": "https://spans",
                "customEventCollectorUrl": "https://custom",
                "attributesToDrop": [],
                "attributesToAdd": []
            },
            "interaction": {
                "collectorUrl": "https://coll",
                "configUrl": "https://config",
                "beforeInitQueueSize": 100
            },
            "features": []
        }
        """
    }
}
