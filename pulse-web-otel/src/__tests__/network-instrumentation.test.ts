import { describe, it, expect, vi, beforeEach } from "vitest";
import { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";

import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import { FeatureGate } from "../feature-gate";
import { NetworkInstrumentation } from "../instrumentations/network";
import { PulseWebSemconv } from "../semconv";
import type { SdkContext } from "../types/instrumentation-registry";

type FetchCtorConfig = {
  ignoreUrls?: unknown;
  propagateTraceHeaderCorsUrls?: unknown;
  applyCustomAttributesOnSpan?: (
    span: unknown,
    request: unknown,
    result: unknown,
  ) => void;
};

const fetchConfigs: FetchCtorConfig[] = [];
const xhrConfigs: FetchCtorConfig[] = [];

vi.mock("@opentelemetry/instrumentation-fetch", () => ({
  FetchInstrumentation: vi.fn((cfg: FetchCtorConfig) => {
    fetchConfigs.push(cfg);
    return {
      setTracerProvider: vi.fn(),
      enable: vi.fn(),
      disable: vi.fn(),
    };
  }),
}));

vi.mock("@opentelemetry/instrumentation-xml-http-request", () => ({
  XMLHttpRequestInstrumentation: vi.fn((cfg: FetchCtorConfig) => {
    xhrConfigs.push(cfg);
    return {
      setTracerProvider: vi.fn(),
      enable: vi.fn(),
      disable: vi.fn(),
    };
  }),
}));

function makeSdk(overrides?: Partial<SdkContext>): SdkContext {
  const base: SdkContext = {
    endpointBaseUrl: "https://collector.example.com",
    gate: new FeatureGate(DEFAULT_SDK_CONFIG),
    // Network instrumentation only consumes the structural shape below; cast
    // through `unknown` because SessionProvider has many private fields that
    // are not relevant to this test.
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
          peerServiceMap: { "orders-api.example": "orders-service" },
        },
      },
    },
  };
  return { ...base, ...overrides };
}

describe("NetworkInstrumentation", () => {
  beforeEach(() => {
    fetchConfigs.length = 0;
    xhrConfigs.length = 0;
    vi.mocked(FetchInstrumentation).mockClear();
    vi.mocked(XMLHttpRequestInstrumentation).mockClear();
  });

  it("does not install when window is undefined (SSR)", () => {
    const w = globalThis.window;
    try {
      Reflect.deleteProperty(globalThis, "window");
      const instr = new NetworkInstrumentation();
      instr.install(makeSdk());
      expect(vi.mocked(FetchInstrumentation)).not.toHaveBeenCalled();
    } finally {
      Object.defineProperty(globalThis, "window", {
        value: w,
        configurable: true,
        writable: true,
      });
    }
  });

  it("returns early when tracerProvider is missing", () => {
    const instr = new NetworkInstrumentation();
    instr.install(
      makeSdk({
        tracerProvider: undefined,
      }),
    );
    expect(vi.mocked(FetchInstrumentation)).not.toHaveBeenCalled();
  });

  it("forwards propagateTraceHeaderCorsUrls to FetchInstrumentation", () => {
    const instr = new NetworkInstrumentation();
    instr.install(
      makeSdk({
        config: {
          apiKey: "k",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
          instrumentations: {
            network: {
              enabled: true,
              propagateTraceHeaderCorsUrls: [/api\.example\.com/],
            },
          },
        },
      }),
    );
    expect(fetchConfigs[0]?.propagateTraceHeaderCorsUrls).toEqual([
      /api\.example\.com/,
    ]);
  });

  it("applyCustomAttributesOnSpan sets peer.service from peerServiceMap", () => {
    // FetchInstrumentation is mocked — no real fetch runs. Read the ctor config from our
    // mock capture (`fetchConfigs`) and invoke `applyCustomAttributesOnSpan` manually (same
    // shape as vi.mocked(FetchInstrumentation).mock.calls[0][0].applyCustomAttributesOnSpan).
    const instr = new NetworkInstrumentation();
    instr.install(makeSdk());
    const cb = fetchConfigs[0]?.applyCustomAttributesOnSpan;
    expect(cb).toBeTypeOf("function");
    const span = {
      setAttribute: vi.fn(),
      setStatus: vi.fn(),
    };
    cb!(
      span,
      new Request("https://orders-api.example/v1/items"),
      new Response("", { status: 200 }),
    );
    expect(span.setAttribute).toHaveBeenCalledWith(
      PulseWebSemconv.AttributeKey.PEER_SERVICE,
      "orders-service",
    );
  });

  it("install is idempotent — second install does not construct OTel instrumentations again", () => {
    const instr = new NetworkInstrumentation();
    const sdk = makeSdk();
    instr.install(sdk);
    instr.install(sdk);
    expect(vi.mocked(FetchInstrumentation)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(XMLHttpRequestInstrumentation)).toHaveBeenCalledTimes(1);
  });

  it("uninstall twice does not throw", () => {
    const instr = new NetworkInstrumentation();
    instr.install(makeSdk());
    instr.uninstall();
    expect(() => instr.uninstall()).not.toThrow();
  });

  // ISS-N10: uninstall actually disables both instrumentations
  it("uninstall calls disable on both Fetch and XHR instrumentations", () => {
    const instr = new NetworkInstrumentation();
    instr.install(makeSdk());
    const fetchInstance = vi
      .mocked(FetchInstrumentation)
      .mock.results[0]?.value as { disable: ReturnType<typeof vi.fn> };
    const xhrInstance = vi
      .mocked(XMLHttpRequestInstrumentation)
      .mock.results[0]?.value as { disable: ReturnType<typeof vi.fn> };

    instr.uninstall();

    expect(fetchInstance.disable).toHaveBeenCalledTimes(1);
    expect(xhrInstance.disable).toHaveBeenCalledTimes(1);
  });

  // ISS-N03: XHR applyCustomAttributesOnSpan callback
  it("XHR applyCustomAttributesOnSpan stamps pulse.type and method at readyState DONE", () => {
    const instr = new NetworkInstrumentation();
    instr.install(
      makeSdk({
        config: {
          apiKey: "k",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
          instrumentations: { network: { enabled: true } },
        },
      }),
    );
    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    expect(cb).toBeTypeOf("function");

    const attrs: Record<string, unknown> = {};
    const span = { setAttribute: vi.fn((k: string, v: unknown) => { attrs[k] = v; }), setStatus: vi.fn() };
    const xhr = {
      readyState: XMLHttpRequest.DONE,
      status: 200,
      responseURL: "https://api.example.com/items",
      getResponseHeader: () => null,
    };

    cb!(span as unknown as Parameters<typeof cb>[0], xhr as unknown as Parameters<typeof cb>[1]);

    expect(attrs["pulse.type"]).toBe("network.200");
    expect(attrs["http.request.method"]).toBeTruthy();
    expect(String(attrs["url.full"])).toContain("api.example.com");
  });

  // ISS-N11: readyState < DONE guard
  it("XHR applyCustomAttributesOnSpan returns early when readyState is not DONE", () => {
    const instr = new NetworkInstrumentation();
    instr.install(makeSdk());
    const cb = xhrConfigs[0]?.applyCustomAttributesOnSpan;
    expect(cb).toBeTypeOf("function");

    const span = { setAttribute: vi.fn(), setStatus: vi.fn() };
    const xhr = {
      readyState: 1,
      status: 0,
      responseURL: "",
      getResponseHeader: () => null,
    };

    cb!(span as unknown as Parameters<typeof cb>[0], xhr as unknown as Parameters<typeof cb>[1]);

    expect(span.setAttribute).not.toHaveBeenCalled();
  });
});
