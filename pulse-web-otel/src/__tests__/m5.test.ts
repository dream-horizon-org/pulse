/**
 * M5 — Network Instrumentation unit tests.
 *
 * Tests pure helper functions (sanitizeUrl, isGraphQL, extractOpName, extractOpType,
 * getRequestUrl, getRequestBody, getRequestHeader) and the NetworkInstrumentation
 * class install/uninstall lifecycle.
 *
 * Note: applyFetchAttrs / applyXhrAttrs are tested indirectly by extracting the
 * applyCustomAttributesOnSpan callbacks from the constructor config. In practice
 * the OTel instrumentation passes RequestInit (not Request) when the user calls
 * fetch(url, init) — so body is a plain string.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// ─── Explicit mock refs at module level for lifecycle tests ───────────────────

const mockFetchEnable = vi.fn();
const mockFetchDisable = vi.fn();
const mockFetchSetTracerProvider = vi.fn();
let mockFetchApplyCb: ((...args: unknown[]) => void) | undefined;

const mockXhrEnable = vi.fn();
const mockXhrDisable = vi.fn();
const mockXhrSetTracerProvider = vi.fn();
let mockXhrApplyCb: ((...args: unknown[]) => void) | undefined;
let mockFetchIgnoreUrls: Array<string | RegExp> | undefined;

vi.mock("@opentelemetry/instrumentation-fetch", () => ({
  FetchInstrumentation: vi.fn().mockImplementation((config: {
    applyCustomAttributesOnSpan?: (...args: unknown[]) => void;
    ignoreUrls?: Array<string | RegExp>;
  }) => {
    mockFetchApplyCb = config?.applyCustomAttributesOnSpan;
    mockFetchIgnoreUrls = config?.ignoreUrls;
    return {
      enable: mockFetchEnable,
      disable: mockFetchDisable,
      setTracerProvider: mockFetchSetTracerProvider,
    };
  }),
}));

vi.mock("@opentelemetry/instrumentation-xml-http-request", () => ({
  XMLHttpRequestInstrumentation: vi.fn().mockImplementation((config: {
    applyCustomAttributesOnSpan?: (...args: unknown[]) => void;
  }) => {
    mockXhrApplyCb = config?.applyCustomAttributesOnSpan;
    return {
      enable: mockXhrEnable,
      disable: mockXhrDisable,
      setTracerProvider: mockXhrSetTracerProvider,
    };
  }),
}));

vi.mock("@opentelemetry/api", () => ({
  SpanStatusCode: { OK: 0, UNSET: 1, ERROR: 2 },
  trace: { getTracerProvider: vi.fn(() => ({})) },
}));

import {
  sanitizeUrl,
  getRequestUrl,
  getRequestBody,
  getRequestHeader,
  isGraphQL,
  extractOpName,
  extractOpType,
  NetworkInstrumentation,
} from "../instrumentations/network";
import type { SdkContext } from "../instrumentation-registry";

// ─── Shared SDK mock ──────────────────────────────────────────────────────────

const mockSdk = {
  config: {
    apiKey: "Test-project_abc123",
    instrumentations: {},
  },
  tracer: {},
  logger: { emit: vi.fn() },
  globalAttrsProcessor: {},
  sessionProvider: {},
} as unknown as SdkContext;

// ─── sanitizeUrl ──────────────────────────────────────────────────────────────

describe("sanitizeUrl", () => {
  it("strips query params by default", () => {
    expect(sanitizeUrl("https://api.example.com/users?token=secret&page=2")).toBe(
      "https://api.example.com/users",
    );
  });

  it("preserves query params when captureQueryParams=true", () => {
    expect(sanitizeUrl("https://api.example.com/search?q=test", true)).toBe(
      "https://api.example.com/search?q=test",
    );
  });

  it("handles URLs with no query params", () => {
    expect(sanitizeUrl("https://api.example.com/products")).toBe(
      "https://api.example.com/products",
    );
  });

  it("returns the resolved (base-relative) URL for relative paths in browser context", () => {
    // jsdom resolves relative paths against http://localhost:3000
    // A relative-looking path resolves — we don't strip it
    const result = sanitizeUrl("/api/users?q=test");
    expect(result).toContain("/api/users");
    expect(result).not.toContain("q=test");
  });

  it("strips query params but keeps path and origin", () => {
    const result = sanitizeUrl("https://api.example.com/v1/users?page=1");
    expect(result).toBe("https://api.example.com/v1/users");
  });
});

// ─── getRequestUrl ────────────────────────────────────────────────────────────

describe("getRequestUrl", () => {
  it("returns url from Request object", () => {
    const req = new Request("https://api.example.com/data");
    expect(getRequestUrl(req)).toBe("https://api.example.com/data");
  });

  it("returns undefined for RequestInit (no url field)", () => {
    const init: RequestInit = { method: "POST" };
    expect(getRequestUrl(init)).toBeUndefined();
  });
});

// ─── getRequestBody ───────────────────────────────────────────────────────────

describe("getRequestBody", () => {
  it("returns string body from RequestInit", () => {
    const init: RequestInit = { method: "POST", body: '{"query":"test"}' };
    expect(getRequestBody(init)).toBe('{"query":"test"}');
  });

  it("returns null for non-string body (FormData)", () => {
    const init: RequestInit = { body: new FormData() };
    expect(getRequestBody(init)).toBeNull();
  });

  it("returns null for missing body", () => {
    const init: RequestInit = { method: "GET" };
    expect(getRequestBody(init)).toBeNull();
  });

  it("returns null for Request object (body is ReadableStream — not readable sync)", () => {
    const req = new Request("https://api.example.com/", {
      method: "POST",
      body: '{"query":"test"}',
    });
    // Request.body is a ReadableStream, not a string
    expect(getRequestBody(req)).toBeNull();
  });
});

// ─── getRequestHeader ─────────────────────────────────────────────────────────

describe("getRequestHeader", () => {
  it("reads header from Request.headers", () => {
    const req = new Request("https://api.example.com/", {
      headers: { "Content-Type": "application/json", "Content-Length": "42" },
    });
    expect(getRequestHeader(req, "content-length")).toBe("42");
  });

  it("is case-insensitive with Request.headers", () => {
    const req = new Request("https://api.example.com/", {
      headers: { "Content-Type": "application/json" },
    });
    expect(getRequestHeader(req, "CONTENT-TYPE")).toBe("application/json");
  });

  it("reads header from Headers instance in RequestInit", () => {
    const headers = new Headers({ "X-Custom": "value" });
    const init: RequestInit = { headers };
    expect(getRequestHeader(init, "x-custom")).toBe("value");
  });

  it("reads header from plain record in RequestInit", () => {
    const init: RequestInit = { headers: { "Content-Type": "text/plain" } };
    expect(getRequestHeader(init, "content-type")).toBe("text/plain");
  });

  it("reads header from array-of-pairs in RequestInit", () => {
    const init: RequestInit = { headers: [["Content-Length", "100"]] };
    expect(getRequestHeader(init, "content-length")).toBe("100");
  });

  it("returns null for missing header", () => {
    const req = new Request("https://api.example.com/");
    expect(getRequestHeader(req, "x-missing")).toBeNull();
  });
});

// ─── isGraphQL ────────────────────────────────────────────────────────────────

describe("isGraphQL", () => {
  it("returns true for body with query field string value", () => {
    expect(isGraphQL(JSON.stringify({ query: "{ products { id } }" }))).toBe(true);
  });

  it("returns true for body with query + operationName", () => {
    expect(
      isGraphQL(JSON.stringify({ query: "query GetUser { user { id } }", operationName: "GetUser" })),
    ).toBe(true);
  });

  it("returns false for REST JSON body without query field", () => {
    expect(isGraphQL(JSON.stringify({ userId: 1, action: "delete" }))).toBe(false);
  });

  it("returns false for empty string", () => {
    expect(isGraphQL("")).toBe(false);
  });

  it("returns false for malformed JSON", () => {
    expect(isGraphQL("{not valid json")).toBe(false);
  });

  it("returns false when query field is not a string", () => {
    expect(isGraphQL(JSON.stringify({ query: 123 }))).toBe(false);
  });
});

// ─── extractOpName ────────────────────────────────────────────────────────────

describe("extractOpName", () => {
  it("extracts named query operation", () => {
    expect(extractOpName(JSON.stringify({ query: "query GetUser { user { id } }" }))).toBe(
      "GetUser",
    );
  });

  it("extracts named mutation operation", () => {
    expect(
      extractOpName(JSON.stringify({ query: "mutation CreateOrder { createOrder { id } }" })),
    ).toBe("CreateOrder");
  });

  it("prefers explicit operationName field over query string match", () => {
    expect(
      extractOpName(
        JSON.stringify({
          query: "query GetUser { user { id } }",
          operationName: "CustomName",
        }),
      ),
    ).toBe("CustomName");
  });

  it("returns null for anonymous query (no operation name)", () => {
    expect(extractOpName(JSON.stringify({ query: "{ products { id } }" }))).toBeNull();
  });

  it("returns null for invalid JSON", () => {
    expect(extractOpName("{bad json")).toBeNull();
  });
});

// ─── extractOpType ────────────────────────────────────────────────────────────

describe("extractOpType", () => {
  it("returns query for explicit query keyword", () => {
    expect(extractOpType(JSON.stringify({ query: "query GetUser { user { id } }" }))).toBe("query");
  });

  it("returns mutation for mutation keyword", () => {
    expect(
      extractOpType(JSON.stringify({ query: "mutation CreateOrder { createOrder { id } }" })),
    ).toBe("mutation");
  });

  it("returns subscription for subscription keyword", () => {
    expect(
      extractOpType(
        JSON.stringify({ query: "subscription OnMessage { message { text } }" }),
      ),
    ).toBe("subscription");
  });

  it("returns query for anonymous shorthand (starts with {)", () => {
    expect(extractOpType(JSON.stringify({ query: "{ products { id } }" }))).toBe("query");
  });

  it("returns null for invalid JSON", () => {
    expect(extractOpType("{bad json")).toBeNull();
  });
});

// ─── NetworkInstrumentation lifecycle ────────────────────────────────────────

describe("NetworkInstrumentation lifecycle", () => {
  let instr: NetworkInstrumentation;

  beforeEach(() => {
    vi.clearAllMocks();
    instr = new NetworkInstrumentation();
  });

  afterEach(() => {
    instr.uninstall();
  });

  it("calls enable on FetchInstrumentation and XHR on install", () => {
    instr.install(mockSdk);
    expect(mockFetchEnable).toHaveBeenCalledOnce();
    expect(mockXhrEnable).toHaveBeenCalledOnce();
  });

  it("calls disable on both when uninstall is called", () => {
    instr.install(mockSdk);
    instr.uninstall();
    expect(mockFetchDisable).toHaveBeenCalledOnce();
    expect(mockXhrDisable).toHaveBeenCalledOnce();
  });

  it("calls setTracerProvider on both instrumentations", () => {
    instr.install(mockSdk);
    expect(mockFetchSetTracerProvider).toHaveBeenCalledOnce();
    expect(mockXhrSetTracerProvider).toHaveBeenCalledOnce();
  });

  it("does not throw on repeated uninstall", () => {
    instr.install(mockSdk);
    expect(() => {
      instr.uninstall();
      instr.uninstall();
    }).not.toThrow();
  });

  it("skips install in non-browser environment (no window)", () => {
    const origWindow = global.window;
    // @ts-expect-error simulate non-browser
    delete global.window;
    expect(() => instr.install(mockSdk)).not.toThrow();
    expect(mockFetchEnable).not.toHaveBeenCalled();
    global.window = origWindow;
  });
});

// ─── applyFetchAttrs via captured callback ────────────────────────────────────

describe("applyFetchAttrs — span attribute logic (via captured callback)", () => {
  let mockSpan: {
    setAttribute: ReturnType<typeof vi.fn>;
    setStatus: ReturnType<typeof vi.fn>;
  };

  // Simulate RequestInit (what OTel passes when fetch(url, init) is called)
  function makeInit(body?: string, headers?: Record<string, string>): RequestInit {
    return { method: "POST", body: body ?? null, headers };
  }

  beforeEach(() => {
    vi.clearAllMocks();
    const instr = new NetworkInstrumentation();
    instr.install(mockSdk);
    mockSpan = { setAttribute: vi.fn(), setStatus: vi.fn() };
  });

  function callCb(span: unknown, request: unknown, result: unknown): void {
    expect(mockFetchApplyCb).toBeDefined();
    mockFetchApplyCb!(span, request, result);
  }

  it("sets pulse.type = http", () => {
    const response = new Response("ok", { status: 200 });
    callCb(mockSpan, makeInit(), response);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("pulse.type", "http");
  });

  it("sanitizes url.full — strips query params (uses Request.url when Request passed)", () => {
    const response = new Response("ok", { status: 200 });
    const request = new Request("https://api.example.com/users?token=abc");
    callCb(mockSpan, request, response);
    const urlCall = (mockSpan.setAttribute as ReturnType<typeof vi.fn>).mock.calls.find(
      (args: unknown[]) => args[0] === "url.full",
    );
    expect(urlCall?.[1]).toBe("https://api.example.com/users");
  });

  it("sets ERROR status for 4xx response", () => {
    const response = new Response("not found", { status: 404 });
    callCb(mockSpan, makeInit(), response);
    expect(mockSpan.setStatus).toHaveBeenCalledWith({ code: 2 });
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("error.type", "4xx");
  });

  it("sets ERROR status for 5xx response", () => {
    const response = new Response("server error", { status: 500 });
    callCb(mockSpan, makeInit(), response);
    expect(mockSpan.setStatus).toHaveBeenCalledWith({ code: 2 });
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("error.type", "5xx");
  });

  it("does NOT set ERROR status for 2xx response", () => {
    const response = new Response("ok", { status: 200 });
    callCb(mockSpan, makeInit(), response);
    expect(mockSpan.setStatus).not.toHaveBeenCalled();
    const errorType = (mockSpan.setAttribute as ReturnType<typeof vi.fn>).mock.calls.find(
      (args: unknown[]) => args[0] === "error.type",
    );
    expect(errorType).toBeUndefined();
  });

  it("sets ERROR + network_error for FetchError (no response)", () => {
    const fetchError = { message: "Failed to fetch" };
    callCb(mockSpan, makeInit(), fetchError);
    expect(mockSpan.setStatus).toHaveBeenCalledWith({ code: 2 });
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("error.type", "network_error");
  });

  it("sets graphql.operation.name + type from RequestInit body", () => {
    const response = new Response("{}", { status: 200 });
    const body = JSON.stringify({
      query: "query GetProducts { products { id } }",
      operationName: "GetProducts",
    });
    callCb(mockSpan, makeInit(body), response);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("graphql.operation.name", "GetProducts");
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("graphql.operation.type", "query");
  });

  it("does NOT set graphql attrs for non-GraphQL POST", () => {
    const response = new Response("{}", { status: 200 });
    const body = JSON.stringify({ userId: 123, action: "buy" });
    callCb(mockSpan, makeInit(body), response);
    const graphqlCalls = (mockSpan.setAttribute as ReturnType<typeof vi.fn>).mock.calls.filter(
      (args: unknown[]) => String(args[0]).startsWith("graphql"),
    );
    expect(graphqlCalls).toHaveLength(0);
  });

  it("sets peer.service when peerServiceMap matches hostname", () => {
    vi.clearAllMocks();
    const sdkWithPeerMap = {
      ...mockSdk,
      config: {
        ...mockSdk.config,
        instrumentations: {
          network: { peerServiceMap: { "api.example.com": "orders-service" } },
        },
      },
    } as unknown as SdkContext;

    const instr2 = new NetworkInstrumentation();
    instr2.install(sdkWithPeerMap);

    const span2 = { setAttribute: vi.fn(), setStatus: vi.fn() };
    const response = new Response("{}", { status: 200 });
    const request = new Request("https://api.example.com/orders");
    mockFetchApplyCb!(span2, request, response);

    expect(span2.setAttribute).toHaveBeenCalledWith("peer.service", "orders-service");
    instr2.uninstall();
  });

  it("sets http.response.body.size from content-length header", () => {
    const headers = new Headers({ "content-length": "512" });
    const response = new Response("data", { status: 200, headers });
    callCb(mockSpan, makeInit(), response);
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("http.response.body.size", 512);
  });

  it("sets cors_error for status 0 (no-cors mode)", () => {
    // Status 0 = opaque response from no-cors mode
    const response = { headers: new Headers(), status: 0, statusText: "opaque" };
    callCb(mockSpan, makeInit(), response);
    expect(mockSpan.setStatus).toHaveBeenCalledWith({ code: 2 });
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("error.type", "cors_error");
  });

  it("captures custom request headers from allowlist", () => {
    vi.clearAllMocks();
    const sdkWithHeaders = {
      ...mockSdk,
      config: {
        ...mockSdk.config,
        instrumentations: {
          network: { capturedRequestHeaders: ["x-request-id"] },
        },
      },
    } as unknown as SdkContext;

    const instr2 = new NetworkInstrumentation();
    instr2.install(sdkWithHeaders);

    const span2 = { setAttribute: vi.fn(), setStatus: vi.fn() };
    const response = new Response("{}", { status: 200 });
    const init: RequestInit = {
      method: "GET",
      headers: { "x-request-id": "req-123" },
    };
    mockFetchApplyCb!(span2, init, response);

    expect(span2.setAttribute).toHaveBeenCalledWith(
      "http.request.header.x-request-id",
      "req-123",
    );
    instr2.uninstall();
  });

  it("captures custom response headers from allowlist (TC20)", () => {
    vi.clearAllMocks();
    const sdkWithRespHeaders = {
      ...mockSdk,
      config: {
        ...mockSdk.config,
        instrumentations: {
          network: { capturedResponseHeaders: ["x-trace-id", "cf-ray"] },
        },
      },
    } as unknown as SdkContext;

    const instr2 = new NetworkInstrumentation();
    instr2.install(sdkWithRespHeaders);

    const span2 = { setAttribute: vi.fn(), setStatus: vi.fn() };
    const headers = new Headers({ "x-trace-id": "trace-abc", "cf-ray": "12345-LHR" });
    const response = new Response("{}", { status: 200, headers });
    mockFetchApplyCb!(span2, makeInit(), response);

    expect(span2.setAttribute).toHaveBeenCalledWith("http.response.header.x-trace-id", "trace-abc");
    expect(span2.setAttribute).toHaveBeenCalledWith("http.response.header.cf-ray", "12345-LHR");
    instr2.uninstall();
  });
});

// ─── applyXhrAttrs via captured callback (TC17) ───────────────────────────────

describe("applyXhrAttrs — XHR span attribute logic (TC17)", () => {
  let mockSpan: {
    setAttribute: ReturnType<typeof vi.fn>;
    setStatus: ReturnType<typeof vi.fn>;
  };

  function makeXhr(status: number, headers?: Record<string, string>): XMLHttpRequest {
    return {
      status,
      getResponseHeader: (name: string) => headers?.[name.toLowerCase()] ?? null,
    } as unknown as XMLHttpRequest;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    const instr = new NetworkInstrumentation();
    instr.install(mockSdk);
    mockSpan = { setAttribute: vi.fn(), setStatus: vi.fn() };
  });

  function callXhrCb(span: unknown, xhr: unknown): void {
    expect(mockXhrApplyCb).toBeDefined();
    mockXhrApplyCb!(span, xhr);
  }

  it("sets pulse.type = http on XHR span", () => {
    callXhrCb(mockSpan, makeXhr(200));
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("pulse.type", "http");
  });

  it("sets ERROR + error.type=4xx for 4xx XHR response", () => {
    callXhrCb(mockSpan, makeXhr(404));
    expect(mockSpan.setStatus).toHaveBeenCalledWith({ code: 2 });
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("error.type", "4xx");
  });

  it("sets ERROR + error.type=5xx for 5xx XHR response", () => {
    callXhrCb(mockSpan, makeXhr(503));
    expect(mockSpan.setStatus).toHaveBeenCalledWith({ code: 2 });
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("error.type", "5xx");
  });

  it("does NOT set ERROR for 2xx XHR response", () => {
    callXhrCb(mockSpan, makeXhr(201));
    expect(mockSpan.setStatus).not.toHaveBeenCalled();
    const errorType = (mockSpan.setAttribute as ReturnType<typeof vi.fn>).mock.calls.find(
      (args: unknown[]) => args[0] === "error.type",
    );
    expect(errorType).toBeUndefined();
  });

  it("sets http.response.body.size from content-length on XHR", () => {
    callXhrCb(mockSpan, makeXhr(200, { "content-length": "1024" }));
    expect(mockSpan.setAttribute).toHaveBeenCalledWith("http.response.body.size", 1024);
  });

  it("captures custom response headers from allowlist on XHR (TC20)", () => {
    vi.clearAllMocks();
    const sdkWithHeaders = {
      ...mockSdk,
      config: {
        ...mockSdk.config,
        instrumentations: { network: { capturedResponseHeaders: ["x-request-id"] } },
      },
    } as unknown as SdkContext;

    const instr2 = new NetworkInstrumentation();
    instr2.install(sdkWithHeaders);

    const span2 = { setAttribute: vi.fn(), setStatus: vi.fn() };
    mockXhrApplyCb!(span2, makeXhr(200, { "x-request-id": "xhr-req-456" }));

    expect(span2.setAttribute).toHaveBeenCalledWith(
      "http.response.header.x-request-id",
      "xhr-req-456",
    );
    instr2.uninstall();
  });
});

// ─── blockedUrls config passed to FetchInstrumentation (TC25) ─────────────────

describe("blockedUrls config (TC25)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("OTLP endpoint always in ignoreUrls (as RegExp matching the base URL)", () => {
    const instr = new NetworkInstrumentation();
    instr.install(mockSdk);
    // network.ts converts the endpoint to a RegExp (^<escaped-url>) so that
    // sub-paths like /v1/traces are also excluded. Check that a matching RegExp exists.
    const hasEndpointRegex = mockFetchIgnoreUrls?.some(
      (r) => r instanceof RegExp && r.test("http://localhost:4318/v1/traces"),
    );
    expect(hasEndpointRegex).toBe(true);
    instr.uninstall();
  });

  it("custom blockedUrls appended to ignoreUrls alongside OTLP endpoint RegExp", () => {
    const blockedPattern = /analytics\.example\.com/;
    const sdk2 = {
      ...mockSdk,
      config: {
        ...mockSdk.config,
        instrumentations: {
          network: { blockedUrls: [blockedPattern, "https://ads.example.com"] },
        },
      },
    } as unknown as SdkContext;

    const instr = new NetworkInstrumentation();
    instr.install(sdk2);

    // OTLP endpoint → RegExp prefix-match
    const hasEndpointRegex = mockFetchIgnoreUrls?.some(
      (r) => r instanceof RegExp && r.test("http://localhost:4318/v1/traces"),
    );
    expect(hasEndpointRegex).toBe(true);
    expect(mockFetchIgnoreUrls).toContain(blockedPattern);
    expect(mockFetchIgnoreUrls).toContain("https://ads.example.com");
    instr.uninstall();
  });
});

// ─── deprecated keys never set (TC22) ────────────────────────────────────────

describe("deprecated semconv keys never set by our code (TC22)", () => {
  let mockSpan: { setAttribute: ReturnType<typeof vi.fn>; setStatus: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.clearAllMocks();
    const instr = new NetworkInstrumentation();
    instr.install(mockSdk);
    mockSpan = { setAttribute: vi.fn(), setStatus: vi.fn() };
  });

  const DEPRECATED_KEYS = ["http.method", "http.url", "http.status_code", "net.peer.name"];

  it("fetch callback never sets deprecated http.method / http.url / http.status_code / net.peer.name", () => {
    const response = new Response("{}", { status: 200 });
    const request = new Request("https://api.example.com/data");
    mockFetchApplyCb!(mockSpan, request, response);

    const setKeys = (mockSpan.setAttribute as ReturnType<typeof vi.fn>).mock.calls.map(
      (args: unknown[]) => args[0],
    );
    for (const deprecated of DEPRECATED_KEYS) {
      expect(setKeys).not.toContain(deprecated);
    }
  });
});
