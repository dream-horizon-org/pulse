import { describe, expect, it, vi } from "vitest";
import type { Span } from "@opentelemetry/api";

import {
  applyPulseHttpClientSpanAttributes,
  buildNetworkIgnoreUrls,
  extractGraphQlMeta,
  methodFromOtelClientSpanName,
  networkPulseType,
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

describe("applyPulseHttpClientSpanAttributes", () => {
  it("sets pulse.type http and stable keys for success response", () => {
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
});
