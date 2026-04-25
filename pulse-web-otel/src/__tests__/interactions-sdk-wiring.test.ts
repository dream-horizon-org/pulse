vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const createProvidersMock = vi.fn();
vi.mock("../exporters", () => {
  const mockTracerProvider = {
    addSpanProcessor: vi.fn(),
    getTracer: vi.fn().mockReturnValue({
      startSpan: vi.fn().mockReturnValue({
        setAttribute: vi.fn(),
        end: vi.fn(),
      }),
    }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };
  const mockLoggerProvider = {
    addLogRecordProcessor: vi.fn(),
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };
  const mockMeterProvider = {
    addMetricReader: vi.fn(),
    getMeter: vi.fn().mockReturnValue({}),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };

  return {
    createProviders: createProvidersMock.mockReturnValue({
      tracerProvider: mockTracerProvider,
      loggerProvider: mockLoggerProvider,
      meterProvider: mockMeterProvider,
      cleanup: vi.fn(),
      prepareForDocumentUnload: vi.fn(),
    }),
  };
});

const interactionInit = vi.fn().mockResolvedValue(undefined);
const interactionTrack = vi.fn();
const interactionShutdown = vi.fn();
vi.mock("../interactions/interaction-feature", () => ({
  InteractionFeature: vi
    .fn()
    .mockImplementation(
      (
        _endpoint: string,
        _config: unknown,
        gate: { isEnabled: (name: string) => boolean },
        interactionsEnabledByConfig: boolean,
      ) => {
        let active = false;
        return {
          init: async () => {
            active =
              interactionsEnabledByConfig && gate.isEnabled("interaction");
            await interactionInit();
          },
          trackEvent: (
            name: string,
            attrs?: Record<string, unknown>,
            timestampMs?: number,
          ) => {
            if (!active) return;
            interactionTrack(name, attrs, timestampMs);
          },
          shutdown: interactionShutdown,
        };
      },
    ),
}));

import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../remote-config";

const loadCachedMock = vi.fn().mockReturnValue(DEFAULT_SDK_CONFIG);
const fetchInBackgroundMock = vi.fn().mockResolvedValue(undefined);
vi.mock("../remote-config", async () => {
  const actual = await vi.importActual("../remote-config");
  return {
    ...actual,
    SdkConfigFetcher: vi.fn().mockImplementation(() => ({
      loadCached: loadCachedMock,
      fetchInBackground: fetchInBackgroundMock,
    })),
  };
});

function makeConfig(overrides: Partial<PulseWebConfig> = {}): PulseWebConfig {
  return {
    apiKey: "proj_abc_supersecretkey",
    serviceName: "test-app",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    ...overrides,
  };
}

beforeEach(() => {
  interactionInit.mockClear();
  interactionTrack.mockClear();
  interactionShutdown.mockClear();
  createProvidersMock.mockClear();
  loadCachedMock.mockReturnValue(DEFAULT_SDK_CONFIG);
  fetchInBackgroundMock.mockClear();

  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({}),
    }),
  );

  const mockXHR = {
    open: vi.fn(),
    send: vi.fn(),
    setRequestHeader: vi.fn(),
    abort: vi.fn(),
    readyState: 4,
    status: 200,
    responseText: "",
    onreadystatechange: null,
    onload: null,
    onerror: null,
    ontimeout: null,
    timeout: 0,
    withCredentials: false,
    upload: { addEventListener: vi.fn() },
  };
  vi.stubGlobal(
    "XMLHttpRequest",
    vi.fn(() => mockXHR),
  );
});

afterEach(async () => {
  const { PulseWeb } = await import("../sdk");
  if (PulseWeb.isInitialized()) {
    await PulseWeb.shutdown();
  }
  vi.unstubAllGlobals();
});

describe("interactions SDK wiring", () => {
  it("forwards trackEvent(name, attrs, timestamp) into interaction feature", async () => {
    const { PulseWeb } = await import("../sdk");

    PulseWeb.start(makeConfig());
    await Promise.resolve();

    PulseWeb.trackEvent("checkout_click", { channel: "organic" }, 1234);

    expect(interactionTrack).toHaveBeenCalledWith(
      "checkout_click",
      { channel: "organic" },
      1234,
    );
  });

  it("does not forward interaction events when consent is denied", async () => {
    const { PulseWeb } = await import("../sdk");

    PulseWeb.start(
      makeConfig({ dataCollectionState: PulseDataCollectionConsent.DENIED }),
    );
    await Promise.resolve();

    PulseWeb.trackEvent("checkout_click");

    expect(interactionTrack).not.toHaveBeenCalled();
  });

  it("shuts down interaction feature during sdk shutdown", async () => {
    const { PulseWeb } = await import("../sdk");

    PulseWeb.start(makeConfig());
    await Promise.resolve();
    await PulseWeb.shutdown();

    expect(interactionShutdown).toHaveBeenCalled();
  });

  it("does not start interaction forwarding when feature gate disables interaction", async () => {
    const { PulseWeb } = await import("../sdk");
    loadCachedMock.mockReturnValue({
      ...DEFAULT_SDK_CONFIG,
      features: [
        {
          featureName: "interaction",
          sessionSampleRate: 0,
          sdks: ["pulse_web_js"],
        },
      ],
    });

    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.trackEvent("checkout_click");

    expect(interactionTrack).not.toHaveBeenCalled();
  });

  it("wires sampling gate from cached config with zero sample rate", async () => {
    const { PulseWeb } = await import("../sdk");
    const randomSpy = vi.spyOn(Math, "random").mockReturnValue(0.99);
    loadCachedMock.mockReturnValue({
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        default: { sessionSampleRate: 0 },
        rules: [],
        signalsToSample: [],
      },
    });

    PulseWeb.start(makeConfig());
    await Promise.resolve();

    const exportersConfig = createProvidersMock.mock.calls.at(-1)?.[0] as {
      samplingGate: {
        shouldExportSignal: (
          scope: "TRACES" | "LOGS" | "METRICS",
          signalName: string,
          attrs: Record<string, unknown>,
        ) => boolean;
      };
    };

    const shouldExport =
      exportersConfig?.samplingGate?.shouldExportSignal?.(
        "TRACES",
        "interaction",
        {
          "pulse.type": "interaction",
        },
      ) ?? true;
    expect(shouldExport).toBe(false);
    randomSpy.mockRestore();
  });
});
