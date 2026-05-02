import { describe, expect, it, vi } from "vitest";
import type { Span } from "@opentelemetry/api";
import { SpanStatusCode } from "@opentelemetry/api";

import {
  applyPulseHttpClientSpanAttributes,
  buildNetworkIgnoreUrls,
  extractGraphQlMeta,
  methodFromOtelClientSpanName,
  networkProtocolVersionFromNextHop,
  networkPulseType,
  resourceTimingProtocolVersion,
  sanitizeHttpUrl,
} from "../utils/network-http";

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

    vi.restoreAllMocks();
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
      graphqlRequestBody: undefined,
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
      graphqlRequestBody: undefined,
    });

    expect(attrs["server.port"]).toBe(8080);
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
