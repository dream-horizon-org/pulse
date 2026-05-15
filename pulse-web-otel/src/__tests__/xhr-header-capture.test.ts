/**
 * Tests for the XHR request header capture fix (Option B — WeakMap patch).
 *
 * Problem: browser hides XHR sent headers after xhr.send(), so
 * applyCustomAttributesOnSpan cannot call any API to retrieve them.
 * Fix: monkey-patch XMLHttpRequest.prototype.setRequestHeader to store
 * headers in a module-scoped WeakMap before send() is called.
 *
 * These tests drive the behavior through the NetworkInstrumentation public
 * surface (mock at OTel ctor level; never mock OTel SDK internals).
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";

import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import { FeatureGate } from "../feature-gate";
import {
  NetworkInstrumentation,
  xhrHeaderStore,
} from "../instrumentations/network";
import { PulseWebSemconv } from "../semconv";
import type { SdkContext } from "../types/instrumentation-registry";

// ---------- OTel mock infrastructure (same pattern as network-instrumentation.test.ts) ----------

type ApplyCb = (span: unknown, request: unknown, result?: unknown) => void;
type XhrApplyCb = (span: unknown, xhr: unknown) => void;

type FetchCtorConfig = {
  applyCustomAttributesOnSpan?: ApplyCb;
};

type XhrCtorConfig = {
  applyCustomAttributesOnSpan?: XhrApplyCb;
};

const fetchConfigs: FetchCtorConfig[] = [];
const xhrConfigs: XhrCtorConfig[] = [];

vi.mock("@opentelemetry/instrumentation-fetch", () => ({
  FetchInstrumentation: vi.fn((cfg: FetchCtorConfig) => {
    fetchConfigs.push(cfg);
    return { setTracerProvider: vi.fn(), enable: vi.fn(), disable: vi.fn() };
  }),
}));

vi.mock("@opentelemetry/instrumentation-xml-http-request", () => ({
  XMLHttpRequestInstrumentation: vi.fn((cfg: XhrCtorConfig) => {
    xhrConfigs.push(cfg);
    return { setTracerProvider: vi.fn(), enable: vi.fn(), disable: vi.fn() };
  }),
}));

// ---------- helpers ----------

function makeSdk(
  capturedRequestHeaders?: string[],
  overrides?: Partial<SdkContext>,
): SdkContext {
  return {
    endpointBaseUrl: "https://collector.example.com",
    gate: new FeatureGate(DEFAULT_SDK_CONFIG),
    sessionProvider: {
      onSessionChange: () => () => {},
      emitInitialSession: () => {},
    } as unknown as SdkContext["sessionProvider"],
    logger: {} as SdkContext["logger"],
    tracer: {} as SdkContext["tracer"],
    globalAttrsProcessor: {} as SdkContext["globalAttrsProcessor"],
    tracerProvider: new WebTracerProvider(),
    config: {
      apiKey: "test-key",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      instrumentations: {
        network: {
          enabled: true,
          ...(capturedRequestHeaders !== undefined
            ? { capturedRequestHeaders }
            : {}),
        },
      },
    },
    ...overrides,
  };
}

function makeXhrLike(opts?: {
  status?: number;
  responseURL?: string;
  getResponseHeader?: (name: string) => string | null;
}): XMLHttpRequest {
  return {
    readyState: XMLHttpRequest.DONE,
    status: opts?.status ?? 200,
    responseURL: opts?.responseURL ?? "https://api.example.com/items",
    getResponseHeader: opts?.getResponseHeader ?? (() => null),
  } as unknown as XMLHttpRequest;
}

function makeSpan(): {
  attrs: Record<string, unknown>;
  span: { setAttribute: ReturnType<typeof vi.fn>; setStatus: ReturnType<typeof vi.fn> };
} {
  const attrs: Record<string, unknown> = {};
  const span = {
    setAttribute: vi.fn((k: string, v: unknown) => {
      attrs[k] = v;
    }),
    setStatus: vi.fn(),
  };
  return { attrs, span };
}

// ---------- tests ----------

describe("XHR request header capture (WeakMap patch)", () => {
  let instr: NetworkInstrumentation;

  beforeEach(() => {
    fetchConfigs.length = 0;
    xhrConfigs.length = 0;
    vi.mocked(FetchInstrumentation).mockClear();
    vi.mocked(XMLHttpRequestInstrumentation).mockClear();
    instr = new NetworkInstrumentation();
  });

  afterEach(() => {
    instr.uninstall();
    vi.restoreAllMocks();
  });

  it("XHR span gets http.request.header.<name> when capturedRequestHeaders includes that header", () => {
    instr.install(makeSdk(["X-Request-Id", "Content-Type"]));

    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    expect(cb).toBeTypeOf("function");

    const xhr = makeXhrLike();
    // Pre-populate the WeakMap as the monkey-patch would do before send()
    xhrHeaderStore.set(xhr, {
      "x-request-id": "req-abc",
      "content-type": "application/json",
    });

    const { attrs, span } = makeSpan();
    cb!(span, xhr);

    expect(attrs["http.request.header.x-request-id"]).toBe("req-abc");
    expect(attrs["http.request.header.content-type"]).toBe("application/json");
  });

  it("headers NOT in capturedRequestHeaders do not appear on the span", () => {
    instr.install(makeSdk(["X-Request-Id"]));

    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    const xhr = makeXhrLike();
    // Both stored — but only x-request-id is in capturedRequestHeaders
    xhrHeaderStore.set(xhr, {
      "x-request-id": "req-123",
      "x-internal-token": "secret",
    });

    const { attrs, span } = makeSpan();
    cb!(span, xhr);

    expect(attrs["http.request.header.x-request-id"]).toBe("req-123");
    expect(attrs["http.request.header.x-internal-token"]).toBeUndefined();
  });

  it("WeakMap entry is cleaned up after applyCustomAttributesOnSpan runs", () => {
    instr.install(makeSdk(["X-Trace"]));

    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    const xhr = makeXhrLike();
    xhrHeaderStore.set(xhr, { "x-trace": "trace-val" });

    // Entry should be present before the callback
    expect(xhrHeaderStore.has(xhr)).toBe(true);

    const { span } = makeSpan();
    cb!(span, xhr);

    // Entry must be gone after the callback
    expect(xhrHeaderStore.has(xhr)).toBe(false);
  });

  it("setRequestHeader still calls through to original (does not swallow the call)", () => {
    // Replace the prototype method with a spy BEFORE install() so that
    // installXhrHeaderPatch() captures the spy as `_origSetRequestHeader`.
    // When our patch runs, it calls `_origSetRequestHeader` (== spy).
    const callLog: Array<[string, string]> = [];
    const origImpl = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function (
      name: string,
      value: string,
    ) {
      callLog.push([name.toLowerCase(), value]);
      return origImpl.call(this, name, value);
    };

    instr.install(makeSdk(["X-Custom"]));

    // Need a real XHR instance so jsdom's prototype check passes.
    // open() is required before setRequestHeader in the spec; jsdom enforces it.
    const xhr = new XMLHttpRequest();
    xhr.open("GET", "https://api.example.com/items");
    xhr.setRequestHeader("X-Custom", "val");

    // callLog should have exactly one entry from the call-through
    expect(callLog).toEqual([["x-custom", "val"]]);
    // And the WeakMap should have the entry
    expect(xhrHeaderStore.get(xhr)).toEqual({ "x-custom": "val" });

    // Restore our temporary override so afterEach uninstall restores cleanly
    XMLHttpRequest.prototype.setRequestHeader = origImpl;
  });

  it("does NOT install the setRequestHeader patch when capturedRequestHeaders is empty", () => {
    const origImpl = XMLHttpRequest.prototype.setRequestHeader;

    instr.install(makeSdk([])); // empty array — no patch

    // Prototype should be unchanged
    expect(XMLHttpRequest.prototype.setRequestHeader).toBe(origImpl);
  });

  it("does NOT install the setRequestHeader patch when capturedRequestHeaders is undefined", () => {
    const origImpl = XMLHttpRequest.prototype.setRequestHeader;

    instr.install(makeSdk(undefined)); // no capturedRequestHeaders config

    expect(XMLHttpRequest.prototype.setRequestHeader).toBe(origImpl);
  });

  it("uninstall restores the original setRequestHeader prototype method", () => {
    const origImpl = XMLHttpRequest.prototype.setRequestHeader;

    instr.install(makeSdk(["X-Trace"]));
    // Prototype should now be patched
    expect(XMLHttpRequest.prototype.setRequestHeader).not.toBe(origImpl);

    instr.uninstall();
    // Prototype should be restored
    expect(XMLHttpRequest.prototype.setRequestHeader).toBe(origImpl);
  });

  it("Fetch span still gets captured request headers (no regression)", () => {
    instr.install(makeSdk(["X-Request-Id"]));

    const cb = fetchConfigs[0]?.applyCustomAttributesOnSpan;
    expect(cb).toBeTypeOf("function");

    const req = new Request("https://api.example.com/items", {
      method: "GET",
      headers: { "X-Request-Id": "fetch-req-1" },
    });
    const res = new Response("", { status: 200 });
    Object.defineProperty(res, "url", {
      value: "https://api.example.com/items",
      configurable: true,
    });

    const { attrs, span } = makeSpan();
    cb!(span, req, res);

    expect(attrs["http.request.header.x-request-id"]).toBe("fetch-req-1");
    expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe("network.200");
  });

  it("XHR span without any stored headers has no http.request.header.* attrs", () => {
    instr.install(makeSdk(["X-Custom"]));

    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    const xhr = makeXhrLike();
    // No setRequestHeader calls — nothing stored

    const { attrs, span } = makeSpan();
    cb!(span, xhr);

    const headerKeys = Object.keys(attrs).filter((k) =>
      k.startsWith("http.request.header."),
    );
    expect(headerKeys).toHaveLength(0);
    // But other standard attrs should still be set
    expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe("network.200");
  });

  it("header name lookup is case-insensitive", () => {
    instr.install(makeSdk(["x-request-id"]));

    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    const xhr = makeXhrLike();
    // Patch stores keys lowercase; capturedRequestHeaders lookup is also lowercased.
    xhrHeaderStore.set(xhr, { "x-request-id": "casetest" });

    const { attrs, span } = makeSpan();
    cb!(span, xhr);

    expect(attrs["http.request.header.x-request-id"]).toBe("casetest");
  });
});
