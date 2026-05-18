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
    register: vi.fn(),
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

const interactionInstall = vi.fn();
const interactionTrack = vi.fn();
const interactionShutdown = vi.fn();
vi.mock("../instrumentations/interaction", () => ({
  InteractionInstrumentation: vi
    .fn()
    .mockImplementation(() => {
      let active = false;
      return {
        name: "interactions",
        install: (sdk: {
          config?: {
            instrumentations?: {
              interactions?: { enabled: boolean };
            };
          };
          gate: { isEnabled: (name: string) => boolean };
        }) => {
          const enabledByConfig =
            sdk.config?.instrumentations?.interactions?.enabled ?? true;
          active = enabledByConfig && sdk.gate.isEnabled("interaction");
          interactionInstall();
        },
        trackEvent: (
          name: string,
          attrs?: PulseAttributes,
          timestampMs?: number,
        ) => {
          if (!active) return;
          interactionTrack(name, attrs, timestampMs);
        },
        uninstall: interactionShutdown,
      };
    }),
}));

import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import type { PulseAttributes } from "../types/attributes";

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
  interactionInstall.mockClear();
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
  const { Pulse } = await import("../sdk");
  if (Pulse.isInitialized()) {
    await Pulse.shutdown();
  }
  vi.unstubAllGlobals();
});

describe("interaction log processor wiring", () => {
  it("registers InteractionLogProcessor in log pipeline (createProviders arg[3])", async () => {
    const { Pulse } = await import("../sdk");
    Pulse.init(makeConfig());
    await Promise.resolve();

    const logProcessors = createProvidersMock.mock.calls.at(-1)?.[3] as unknown[];
    expect(Array.isArray(logProcessors)).toBe(true);
    const names = logProcessors.map((p) => (p as { constructor: { name: string } }).constructor.name);
    expect(names).toContain("InteractionLogProcessor");
  });

  it("InteractionLogProcessor is positioned after globalAttrsProcessor and before filterProcessor", async () => {
    const { Pulse } = await import("../sdk");
    Pulse.init(makeConfig());
    await Promise.resolve();

    const logProcessors = createProvidersMock.mock.calls.at(-1)?.[3] as unknown[];
    const names = logProcessors.map((p) => (p as { constructor: { name: string } }).constructor.name);
    const globalIdx = names.findIndex((n) => n === "PulseGlobalAttributesProcessor");
    const interactionIdx = names.findIndex((n) => n === "InteractionLogProcessor");
    const filterIdx = names.findIndex((n) => n === "SignalFilterProcessor");
    expect(globalIdx).toBeGreaterThanOrEqual(0);
    expect(interactionIdx).toBeGreaterThan(globalIdx);
    expect(filterIdx).toBeGreaterThan(interactionIdx);
  });
});

describe("interactions SDK wiring", () => {
  it("forwards trackEvent(name, attrs, timestamp) into interaction feature", async () => {
    const { Pulse } = await import("../sdk");

    Pulse.init(makeConfig());
    await Promise.resolve();

    Pulse.trackEvent("checkout_click", { channel: "organic" }, 1234);

    expect(interactionTrack).toHaveBeenCalledWith(
      "checkout_click",
      { channel: "organic" },
      1234,
    );
  });

  it("does not forward interaction events when consent is denied", async () => {
    const { Pulse } = await import("../sdk");

    Pulse.init(
      makeConfig({ dataCollectionState: PulseDataCollectionConsent.DENIED }),
    );
    await Promise.resolve();

    Pulse.trackEvent("checkout_click");

    expect(interactionTrack).not.toHaveBeenCalled();
  });

  it("shuts down interaction feature during sdk shutdown", async () => {
    const { Pulse } = await import("../sdk");

    Pulse.init(makeConfig());
    await Promise.resolve();
    await Pulse.shutdown();

    expect(interactionShutdown).toHaveBeenCalled();
  });

  it("does not start interaction forwarding when feature gate disables interaction", async () => {
    const { Pulse } = await import("../sdk");
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

    Pulse.init(makeConfig());
    await Promise.resolve();
    Pulse.trackEvent("checkout_click");

    expect(interactionTrack).not.toHaveBeenCalled();
  });

  it("wires sampling gate from cached config with zero sample rate", async () => {
    const { Pulse } = await import("../sdk");
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

    Pulse.init(makeConfig());
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
