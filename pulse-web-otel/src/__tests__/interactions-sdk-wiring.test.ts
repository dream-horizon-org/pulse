vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
    createProviders: vi.fn().mockReturnValue({
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
  InteractionFeature: vi.fn().mockImplementation(() => ({
    init: interactionInit,
    trackEvent: interactionTrack,
    shutdown: interactionShutdown,
  })),
}));

import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";

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

    PulseWeb.start(makeConfig());
    await Promise.resolve();
    (PulseWeb as unknown as { config: PulseWebConfig }).config = makeConfig({
      dataCollectionState: PulseDataCollectionConsent.DENIED,
    });

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
});
