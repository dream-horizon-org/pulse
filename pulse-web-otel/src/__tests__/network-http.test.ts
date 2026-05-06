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
    const span = { attributes: {} } as unknown as import("@opentelemetry/api").Span;
    expect(
      resolveFetchUrl(span, { method: "GET" } as RequestInit, res),
    ).toBe("https://final.example/api");
  });

  it("prefers Response.url over Request.url after redirect", () => {
    const req = new Request("https://start.example/redirect");
    const res = new Response("", {});
    Object.defineProperty(res, "url", {
      value: "https://final.example/after-redirect",
      configurable: true,
    });
    const span = { attributes: {} } as unknown as import("@opentelemetry/api").Span;
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
    const ignored = buildNetworkIgnoreUrls("http://localhost:8080");
    const target = "http://localhost:8080/v1/traces";
    expect(
      ignored.some((p) =>
        typeof p === "string" ? p === target : p.test(target),
      ),
    ).toBe(true);
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
});
