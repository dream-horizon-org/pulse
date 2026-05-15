import { afterEach, describe, expect, it, vi } from "vitest";
import type { Span } from "@opentelemetry/api";
import { SpanStatusCode } from "@opentelemetry/api";

import { PulseWebSemconv } from "../semconv";
import {
  applyPulseHttpClientSpanAttributes,
  buildNetworkIgnoreUrls,
  extractGraphQlMeta,
  getOtelHttpRequestMethodFromSpan,
  getOtelHttpUrlFromSpan,
  isSensitiveCapturedHeaderName,
  isSensitiveQueryParamName,
  methodFromOtelClientSpanName,
  networkProtocolVersionFromNextHop,
  networkPulseType,
  normalizeUrlPath,
  requestHeaderGetter,
  resolveFetchMethod,
  resolveFetchStatus,
  resolveFetchUrl,
  resourceTimingProtocolVersion,
  sanitizeHttpUrl,
} from "../utils/network-http";

const DENY = PulseWebSemconv.SensitiveCapturedHeaderName;

afterEach(() => {
  vi.restoreAllMocks();
});

describe("resolveFetchMethod", () => {
  it("reads method from Request", () => {
    const req = new Request("https://a.test/", { method: "POST" });
    expect(resolveFetchMethod(req)).toBe("POST");
  });

  it("defaults to GET when RequestInit.method missing", () => {
    expect(resolveFetchMethod({})).toBe("GET");
  });
});

describe("resolveFetchStatus", () => {
  it("reads Response.status", () => {
    expect(resolveFetchStatus(new Response("", { status: 418 }))).toBe(418);
  });

  it("returns undefined for non-Response without status", () => {
    expect(resolveFetchStatus(undefined)).toBeUndefined();
  });
});

describe("resolveFetchUrl", () => {
  it("prefers Response.url when present", () => {
    const res = new Response("", {});
    Object.defineProperty(res, "url", {
      value: "https://final.example/api",
      configurable: true,
    });
    const span = {
      attributes: {},
    } as unknown as import("@opentelemetry/api").Span;
    expect(resolveFetchUrl(span, { method: "GET" } as RequestInit, res)).toBe(
      "https://final.example/api",
    );
  });

  it("prefers Response.url over Request.url after redirect", () => {
    const req = new Request("https://start.example/redirect");
    const res = new Response("", {});
    Object.defineProperty(res, "url", {
      value: "https://final.example/after-redirect",
      configurable: true,
    });
    const span = {
      attributes: {},
    } as unknown as import("@opentelemetry/api").Span;
    expect(resolveFetchUrl(span, req, res)).toBe(
      "https://final.example/after-redirect",
    );
  });
});

describe("requestHeaderGetter", () => {
  it("returns undefined when RequestInit has no headers", () => {
    expect(requestHeaderGetter({ method: "GET" })).toBeUndefined();
  });

  it("reads plain-object headers case-insensitively", () => {
    const get = requestHeaderGetter({
      method: "POST",
      headers: { "X-Custom": "a", "Content-Length": "256" },
    });
    expect(get?.("x-custom")).toBe("a");
    expect(get?.("CONTENT-LENGTH")).toBe("256");
  });

  it("reads HeadersInit tuple array", () => {
    const get = requestHeaderGetter({
      headers: [
        ["Content-Type", "application/json"],
        ["X-Foo", "bar"],
      ],
    });
    expect(get?.("content-type")).toBe("application/json");
    expect(get?.("X-FOO")).toBe("bar");
  });

  it("delegates to Request.headers for Request input", () => {
    const req = new Request("https://a.test/", {
      headers: { "X-Req": "1" },
    });
    const get = requestHeaderGetter(req);
    expect(get?.("x-req")).toBe("1");
  });
});

describe("isSensitiveQueryParamName", () => {
  it("flags common secret-like keys case-insensitively", () => {
    expect(isSensitiveQueryParamName("access_token")).toBe(true);
    expect(isSensitiveQueryParamName("refresh_token")).toBe(true);
    expect(isSensitiveQueryParamName("client_secret")).toBe(true);
    expect(isSensitiveQueryParamName("API_KEY")).toBe(true);
  });

  it("allows non-sensitive keys", () => {
    expect(isSensitiveQueryParamName("page")).toBe(false);
    expect(isSensitiveQueryParamName("sort")).toBe(false);
  });
});

describe("isSensitiveCapturedHeaderName", () => {
  it("flags auth and cookie headers case-insensitively", () => {
    expect(isSensitiveCapturedHeaderName("Authorization")).toBe(true);
    expect(isSensitiveCapturedHeaderName(DENY.AUTHORIZATION)).toBe(true);
    expect(isSensitiveCapturedHeaderName(DENY.COOKIE)).toBe(true);
    expect(isSensitiveCapturedHeaderName(DENY.SET_COOKIE)).toBe(true);
    expect(isSensitiveCapturedHeaderName("X-Api-Key")).toBe(true);
    expect(isSensitiveCapturedHeaderName(DENY.X_AUTH_TOKEN)).toBe(true);
    expect(isSensitiveCapturedHeaderName(DENY.PROXY_AUTHORIZATION)).toBe(true);
  });

  it("allows safe header names", () => {
    expect(isSensitiveCapturedHeaderName("content-type")).toBe(false);
    expect(isSensitiveCapturedHeaderName("x-request-id")).toBe(false);
  });
});

describe("networkPulseType", () => {
  it("maps status code to network.<code>", () => {
    expect(networkPulseType(200)).toBe("network.200");
    expect(networkPulseType(404)).toBe("network.404");
  });

  it("uses network.0 for missing or non-finite status (Android parity)", () => {
    expect(networkPulseType(undefined)).toBe("network.0");
    expect(networkPulseType(Number.NaN)).toBe("network.0");
  });

  it("truncates non-integer status", () => {
    expect(networkPulseType(201.7)).toBe("network.201");
  });
});

describe("methodFromOtelClientSpanName", () => {
  it("extracts verb from HTTP GET prefix (Fetch-style)", () => {
    expect(methodFromOtelClientSpanName("HTTP GET")).toBe("GET");
  });

  it("returns plain verb unchanged (XHR-style)", () => {
    expect(methodFromOtelClientSpanName("POST")).toBe("POST");
  });

  it("normalizes case for plain verb", () => {
    expect(methodFromOtelClientSpanName("patch")).toBe("PATCH");
  });

  it("defaults to GET for empty or unknown format", () => {
    expect(methodFromOtelClientSpanName(undefined)).toBe("GET");
    expect(methodFromOtelClientSpanName("")).toBe("GET");
    expect(methodFromOtelClientSpanName("GET /api")).toBe("GET");
  });

  it("trims whitespace before parsing", () => {
    expect(methodFromOtelClientSpanName("  HTTP PUT  ")).toBe("PUT");
  });
});

describe("network-http helpers", () => {
  it("sanitizeHttpUrl strips query when captureQueryParams is false", () => {
    expect(
      sanitizeHttpUrl("https://api.example.com/users?t=secret", {
        captureQueryParams: false,
      }),
    ).toBe("https://api.example.com/users");
  });

  it("sanitizeHttpUrl strips credentials (OTel url.full)", () => {
    expect(
      sanitizeHttpUrl("https://user:secret@api.example.com/path?q=1", {
        captureQueryParams: false,
      }),
    ).toBe("https://api.example.com/path");
  });

  it("sanitizeHttpUrl redacts sensitive query values when captureQueryParams is true", () => {
    const out = sanitizeHttpUrl(
      "https://api.example.com/search?token=secret&q=ok",
      { captureQueryParams: true },
    );
    expect(out).toContain("q=ok");
    expect(out).toContain("token=");
    expect(out).toContain("***");
    expect(out).not.toContain("secret");
  });

  it("buildNetworkIgnoreUrls matches OTLP prefix", () => {
    const ignored = buildNetworkIgnoreUrls("http://localhost:4318");
    const traces = "http://localhost:4318/v1/traces";
    expect(
      ignored.some((p) =>
        typeof p === "string" ? p === traces : p.test(traces),
      ),
    ).toBe(true);
  });

  it("buildNetworkIgnoreUrls ignores local pulse-server REST (:8080) when OTLP is :4318", () => {
    const ignored = buildNetworkIgnoreUrls("http://localhost:4318");
    const config = "http://localhost:8080/v1/configs/active/default-project/";
    const interaction = "http://localhost:8080/v1/interaction-configs/";
    expect(ignored.some((p) => typeof p !== "string" && p.test(config))).toBe(
      true,
    );
    expect(
      ignored.some((p) => typeof p !== "string" && p.test(interaction)),
    ).toBe(true);
  });

  it("buildNetworkIgnoreUrls does not duplicate pattern when endpoint is already :8080", () => {
    const ignored = buildNetworkIgnoreUrls("http://localhost:8080");
    const otlpish = "http://localhost:8080/v1/traces";
    expect(
      ignored.filter((p) => typeof p !== "string" && p.test(otlpish)).length,
    ).toBe(1);
  });

  it("extractGraphQlMeta reads named operation", () => {
    const body = JSON.stringify({
      query: "query GetProducts { products { id } }",
      operationName: "GetProducts",
    });
    const m = extractGraphQlMeta(body);
    expect(m.operationType).toBe("query");
    expect(m.operationName).toBe("GetProducts");
  });
});

// ISS-N04: http.response.body.size from Content-Length
describe("applyPulseHttpClientSpanAttributes — response body size", () => {
  it("sets http.response.body.size from Content-Length response header", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/items",
      method: "GET",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
      responseHeaderGet: (name) =>
        name.toLowerCase() === "content-length" ? "512" : null,
    });

    expect(attrs[PulseWebSemconv.AttributeKey.HTTP_RESPONSE_BODY_SIZE]).toBe(
      512,
    );
  });

  it("does not set http.response.body.size when Content-Length absent", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/items",
      method: "GET",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
      responseHeaderGet: () => null,
    });

    expect(
      attrs[PulseWebSemconv.AttributeKey.HTTP_RESPONSE_BODY_SIZE],
    ).toBeUndefined();
  });
});

// ISS-N05: extractGraphQlMeta missing cases
describe("extractGraphQlMeta — full coverage", () => {
  it("mutation with named operation", () => {
    const m = extractGraphQlMeta(
      JSON.stringify({ query: "mutation UpdateCart { updateCart { id } }" }),
    );
    expect(m.operationType).toBe("mutation");
    expect(m.operationName).toBe("UpdateCart");
  });

  it("subscription with named operation", () => {
    const m = extractGraphQlMeta(
      JSON.stringify({
        query: "subscription OnOrderUpdate { orderUpdate { id } }",
      }),
    );
    expect(m.operationType).toBe("subscription");
    expect(m.operationName).toBe("OnOrderUpdate");
  });

  it("anonymous query falls back to operationName JSON field", () => {
    const m = extractGraphQlMeta(
      JSON.stringify({
        query: "query { products { id } }",
        operationName: "MyAnonymousQuery",
      }),
    );
    expect(m.operationType).toBe("query");
    expect(m.operationName).toBe("MyAnonymousQuery");
  });

  it("body over 262144 bytes returns empty object", () => {
    const oversized = JSON.stringify({
      query: "query A { b }",
      padding: "x".repeat(262_145),
    });
    expect(oversized.length).toBeGreaterThan(262_144);
    const m = extractGraphQlMeta(oversized);
    expect(m.operationType).toBeUndefined();
    expect(m.operationName).toBeUndefined();
  });

  it("non-JSON string returns empty object", () => {
    const m = extractGraphQlMeta("not json at all");
    expect(m).toEqual({});
  });

  it("JSON without query key returns empty object", () => {
    const m = extractGraphQlMeta(JSON.stringify({ operationName: "Foo" }));
    expect(m).toEqual({});
  });
});

describe("getOtelHttpUrlFromSpan", () => {
  it("prefers url.full over deprecated http.url when both exist", () => {
    const span = {
      attributes: {
        [PulseWebSemconv.AttributeKey.URL_FULL]: "https://stable.example/a",
        "http.url": "http://legacy.example/b",
      },
    } as unknown as import("@opentelemetry/api").Span;
    expect(getOtelHttpUrlFromSpan(span)).toBe("https://stable.example/a");
  });

  it("reads deprecated http.url when url.full absent", () => {
    const span = {
      attributes: { "http.url": "http://localhost/foo/bar" },
    } as unknown as import("@opentelemetry/api").Span;
    expect(getOtelHttpUrlFromSpan(span)).toBe("http://localhost/foo/bar");
  });

  it("returns empty when absent", () => {
    const span = {
      attributes: {},
    } as unknown as import("@opentelemetry/api").Span;
    expect(getOtelHttpUrlFromSpan(span)).toBe("");
  });
});

describe("getOtelHttpRequestMethodFromSpan", () => {
  it("reads http.request.method from span attributes", () => {
    const span = {
      attributes: {
        [PulseWebSemconv.AttributeKey.HTTP_REQUEST_METHOD]: "post",
      },
    } as unknown as import("@opentelemetry/api").Span;
    expect(getOtelHttpRequestMethodFromSpan(span)).toBe("POST");
  });

  it("accepts legacy http.request.method string key", () => {
    const span = {
      attributes: { "http.request.method": "PATCH" },
    } as unknown as import("@opentelemetry/api").Span;
    expect(getOtelHttpRequestMethodFromSpan(span)).toBe("PATCH");
  });

  it("returns undefined when missing", () => {
    const span = {
      attributes: {},
    } as unknown as import("@opentelemetry/api").Span;
    expect(getOtelHttpRequestMethodFromSpan(span)).toBeUndefined();
  });
});

describe("networkProtocolVersionFromNextHop", () => {
  it("maps common PerformanceResourceTiming protocol strings", () => {
    expect(networkProtocolVersionFromNextHop("http/1.1")).toBe("1.1");
    expect(networkProtocolVersionFromNextHop("h2")).toBe("2");
    expect(networkProtocolVersionFromNextHop("h3")).toBe("3");
    expect(networkProtocolVersionFromNextHop("  HTTP/1.1  ")).toBe("1.1");
  });

  it("returns undefined for unknown protocol", () => {
    expect(networkProtocolVersionFromNextHop("quic")).toBeUndefined();
  });
});

describe("resourceTimingProtocolVersion", () => {
  it("reads nextHopProtocol from last PerformanceResourceTiming entry", () => {
    if (typeof PerformanceResourceTiming === "undefined") {
      return;
    }
    const entry = Object.create(PerformanceResourceTiming.prototype);
    Object.defineProperty(entry, "duration", {
      value: 12,
      configurable: true,
      writable: true,
    });
    Object.defineProperty(entry, "nextHopProtocol", {
      value: "h2",
      configurable: true,
      writable: true,
    });
    vi.spyOn(performance, "getEntriesByName").mockReturnValue([
      entry,
    ] as unknown as PerformanceResourceTiming[]);

    expect(resourceTimingProtocolVersion("https://api.example.com/z")).toBe(
      "2",
    );
  });
});

describe("applyPulseHttpClientSpanAttributes", () => {
  it("sets network pulse.type and stable keys for success response", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/items",
      method: "get",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
      perfLookupUrl: "https://api.example.com/items",
    });

    expect(attrs["pulse.type"]).toBe("network.200");
    expect(attrs["http.request.method"]).toBe("GET");
    expect(attrs["url.full"]).toBe("https://api.example.com/items");
    expect(attrs["http.response.status_code"]).toBe(200);
    expect(attrs["server.address"]).toBe("api.example.com");
    expect(attrs["server.port"]).toBe(443);
  });

  it("sets server.port for explicit non-default port", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "http://api.example.com:8080/items",
      method: "GET",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
      perfLookupUrl: "http://api.example.com:8080/items",
    });

    expect(attrs["server.port"]).toBe(8080);
  });

  it("sets http.request.body.size from plain-object Content-Length via requestHeaderGetter", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/items",
      method: "POST",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
      requestHeaderGet: requestHeaderGetter({
        method: "POST",
        headers: { "Content-Length": "256" },
      }),
      perfLookupUrl: "https://api.example.com/items",
    });

    expect(attrs[PulseWebSemconv.AttributeKey.HTTP_REQUEST_BODY_SIZE]).toBe(
      256,
    );
  });

  it("returns early with error when resolvedUrl empty", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "   ",
      method: "GET",
      statusCode: undefined,
      privacy: { captureQueryParams: false },
      optional: undefined,
    });

    expect(attrs["pulse.type"]).toBe("network.0");
    expect(attrs["url.full"]).toBeUndefined();
    expect(span.setStatus).toHaveBeenCalled();
  });

  it("404 maps to network.404, error.type 4xx, span ERROR", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/missing",
      method: "GET",
      statusCode: 404,
      privacy: { captureQueryParams: false },
      optional: undefined,
      perfLookupUrl: "https://api.example.com/missing",
    });

    expect(attrs["pulse.type"]).toBe("network.404");
    expect(attrs["http.response.status_code"]).toBe(404);
    expect(attrs["error.type"]).toBe("4xx");
    expect(span.setStatus).toHaveBeenCalledWith({
      code: SpanStatusCode.ERROR,
    });
  });

  it("500 maps to network.500, error.type 5xx", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/boom",
      method: "GET",
      statusCode: 500,
      privacy: { captureQueryParams: false },
      optional: undefined,
      perfLookupUrl: "https://api.example.com/boom",
    });

    expect(attrs["pulse.type"]).toBe("network.500");
    expect(attrs["error.type"]).toBe("5xx");
  });

  it("undefined status with valid URL → network.0 and network_error", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/timeout",
      method: "GET",
      statusCode: undefined,
      privacy: { captureQueryParams: false },
      optional: undefined,
      perfLookupUrl: "https://api.example.com/timeout",
    });

    expect(attrs["pulse.type"]).toBe("network.0");
    expect(attrs["error.type"]).toBe("network_error");
    expect(attrs["http.response.status_code"]).toBeUndefined();
  });

  it("does not copy denylisted headers even when optional capture lists them", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/items",
      method: "GET",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: {
        capturedRequestHeaders: [DENY.AUTHORIZATION, "X-Request-Id"],
        capturedResponseHeaders: [DENY.SET_COOKIE, "Content-Type"],
      },
      perfLookupUrl: "https://api.example.com/items",
      requestHeaderGet: (name) => {
        if (name.toLowerCase() === DENY.AUTHORIZATION) {
          return "Bearer secret";
        }
        if (name.toLowerCase() === "x-request-id") {
          return "req-123";
        }
        return null;
      },
      responseHeaderGet: (name) => {
        if (name.toLowerCase() === DENY.SET_COOKIE) {
          return "session=abc";
        }
        if (name.toLowerCase() === "content-type") {
          return "application/json";
        }
        return null;
      },
    });

    expect(attrs["http.request.header.authorization"]).toBeUndefined();
    expect(attrs["http.request.header.x-request-id"]).toBe("req-123");
    expect(attrs["http.response.header.set-cookie"]).toBeUndefined();
    expect(attrs["http.response.header.content-type"]).toBe("application/json");
  });

  it("status 0 (opaque / CORS) → network.0 and cors_error", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://api.example.com/opaque",
      method: "GET",
      statusCode: 0,
      privacy: { captureQueryParams: false },
      optional: undefined,
      perfLookupUrl: "https://api.example.com/opaque",
    });

    expect(attrs["pulse.type"]).toBe("network.0");
    expect(attrs["http.response.status_code"]).toBe(0);
    expect(attrs["error.type"]).toBe("cors_error");
  });

  // URL path normalization integration: dynamic segments in url.full must be replaced
  it("normalizes dynamic path segments in url.full via sanitizeHttpUrl", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl:
        "https://api.example.com/api/orders/550e8400-e29b-41d4-a716-446655440000/items",
      method: "GET",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
    });

    expect(attrs["url.full"]).toBe(
      "https://api.example.com/api/orders/:id/items",
    );
  });

  // ISS-N14: non-standard HTTP method → _OTHER + http.request.method_original (OTel semconv)
  it("non-standard method PURGE → http.request.method _OTHER + http.request.method_original PURGE", () => {
    const attrs: Record<string, unknown> = {};
    const span = {
      setAttribute: (k: string, v: string | number | boolean) => {
        attrs[k] = v;
      },
      setStatus: vi.fn(),
    } as unknown as Span;

    applyPulseHttpClientSpanAttributes({
      span,
      resolvedUrl: "https://cache.example.com/v1/resource",
      method: "PURGE",
      statusCode: 200,
      privacy: { captureQueryParams: false },
      optional: undefined,
    });

    expect(attrs["http.request.method"]).toBe("_OTHER");
    expect(attrs["http.request.method_original"]).toBe("PURGE");
    expect(attrs["pulse.type"]).toBe("network.200");
  });
});

describe("normalizeUrlPath", () => {
  it("replaces numeric IDs (3+ digits)", () => {
    expect(normalizeUrlPath("/api/orders/12345")).toBe("/api/orders/:id");
  });

  it("replaces UUID v4 with dashes", () => {
    expect(
      normalizeUrlPath(
        "/api/orders/550e8400-e29b-41d4-a716-446655440000/items",
      ),
    ).toBe("/api/orders/:id/items");
  });

  it("replaces MongoDB ObjectId (24 hex chars)", () => {
    expect(
      normalizeUrlPath("/api/orders/507f1f77bcf86cd799439011/status"),
    ).toBe("/api/orders/:id/status");
  });

  it("preserves static-only paths unchanged", () => {
    expect(normalizeUrlPath("/api/health")).toBe("/api/health");
  });

  it("does NOT replace short alphanumeric slugs (not dynamic)", () => {
    // 'abc' is 3 chars but not purely digits, not UUID/ObjectId/ULID — unchanged
    expect(normalizeUrlPath("/api/users/abc")).toBe("/api/users/abc");
  });

  it("handles root path", () => {
    expect(normalizeUrlPath("/")).toBe("/");
  });

  it("replaces multiple dynamic segments", () => {
    expect(
      normalizeUrlPath("/users/12345/orders/507f1f77bcf86cd799439011"),
    ).toBe("/users/:id/orders/:id");
  });
});
