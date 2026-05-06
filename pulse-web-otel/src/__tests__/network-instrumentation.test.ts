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
    sessionProvider: {
      onSessionChange: () => () => {},
      emitInitialSession: () => {},
    } as SdkContext["sessionProvider"],
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
});
