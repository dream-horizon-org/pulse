/**
 * Config surface tests — verifies Web SDK matches Android's minimal public API.
 *
 * Android exposes: apiKey (required), dataCollectionState (required),
 * serviceName (optional/auto-derived), serviceVersion (optional),
 * globalAttributes, beforeSend, instrumentations.
 *
 * Everything else (endpointBaseUrl, export format/compression/batch,
 * diskBuffering, configEndpointUrl, debugLogRecordLifecycle) is internal-only.
 */

// Mock @opentelemetry/api-logs to avoid real OTLP network calls
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

// Mock exporters to avoid real OTLP network calls in tests
vi.mock("../exporters", () => {
  const mockProvider = {
    addSpanProcessor: vi.fn(),
    getTracer: vi.fn().mockReturnValue({
      startSpan: vi.fn().mockReturnValue({
        setAttribute: vi.fn(),
        end: vi.fn(),
      }),
    }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
    register: vi.fn(),
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
      tracerProvider: mockProvider,
      loggerProvider: mockLoggerProvider,
      meterProvider: mockMeterProvider,
      cleanup: vi.fn(),
    }),
  };
});

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { PulseWeb } from "../sdk";
import { PulseDataCollectionConsent } from "../types/config";
import { resolveEndpointBaseUrl, isLocalEnvironment } from "../config";

describe("Config surface — matches Android minimal API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({}),
      }),
    );
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(async () => {
    await PulseWeb.shutdown();
    vi.unstubAllGlobals();
  });

  // TC-C1
  it("TC-C1: start() requires apiKey — throws if missing", () => {
    expect(() =>
      PulseWeb.start({
        apiKey: "",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }),
    ).toThrow("[PulseWeb] apiKey is required");
  });

  // TC-C2
  it("TC-C2: dataCollectionState DENIED → SDK does not initialize (matches Android)", () => {
    PulseWeb.start({
      apiKey: "default-project_devkey01",
      dataCollectionState: PulseDataCollectionConsent.DENIED,
    });
    expect(PulseWeb.isInitialized()).toBe(false);
  });

  // TC-C3
  it("TC-C3: dataCollectionState PENDING → SDK does not initialize (matches Android)", () => {
    PulseWeb.start({
      apiKey: "default-project_devkey01",
      dataCollectionState: PulseDataCollectionConsent.PENDING,
    });
    expect(PulseWeb.isInitialized()).toBe(false);
  });

  // TC-C4
  it("TC-C4: ALLOWED with only apiKey + dataCollectionState → initializes (serviceName auto-derived)", async () => {
    PulseWeb.start({
      apiKey: "default-project_devkey01",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    });
    // finishStart is async (awaits OS version resolution); flush microtasks.
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
  });

  // TC-C5
  it("TC-C5: endpointBaseUrl auto-derives for dev key — not a public config field", () => {
    // TypeScript type should not have endpointBaseUrl — verified at type level
    // Runtime: resolveEndpointBaseUrl used internally
    expect(resolveEndpointBaseUrl("default-project_devkey01")).toBe(
      "http://localhost:4318",
    );
    expect(resolveEndpointBaseUrl("myapp-123_prodkey456")).toBe(
      "https://pulse-otel-collector.pulse-ux.com",
    );
  });

  // TC-C6
  it("TC-C6: isLocalEnvironment detects dev keys correctly", () => {
    expect(isLocalEnvironment("default-project_abc")).toBe(true);
    expect(isLocalEnvironment("Test-myapp_abc")).toBe(true);
    expect(isLocalEnvironment("myapp-prod_key123")).toBe(false);
  });

  // TC-C7
  it("TC-C7: serviceName optional — SDK starts without it", async () => {
    expect(() =>
      PulseWeb.start({
        apiKey: "default-project_devkey01",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }),
    ).not.toThrow();
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
  });

  // TC-C8
  it("TC-C8: globalAttributes passed through to processor", async () => {
    PulseWeb.start({
      apiKey: "default-project_devkey01",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      globalAttributes: { "app.env": "test", "tenant.id": "t1" },
    });
    await new Promise((r) => setTimeout(r, 50));
    const attrs = PulseWeb.globalAttrsProcessor?.getCommonAttrsForMetrics();
    expect(attrs?.["app.env"]).toBe("test");
    expect(attrs?.["tenant.id"]).toBe("t1");
  });

  // TC-C9
  it("TC-C9: second start() is no-op (singleton guard — matches Android)", async () => {
    PulseWeb.start({
      apiKey: "default-project_devkey01",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    });
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
    // Second call with different key should be ignored
    expect(() =>
      PulseWeb.start({
        apiKey: "different_key",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }),
    ).not.toThrow();
    expect(PulseWeb.isInitialized()).toBe(true);
  });

  // TC-C10
  it("TC-C10: dev key resolves localhost — prod key resolves prod URL", () => {
    expect(resolveEndpointBaseUrl("default-project_devkey01")).toBe(
      "http://localhost:4318",
    );
    expect(resolveEndpointBaseUrl("Test-myapp_abc123")).toBe(
      "http://localhost:4318",
    );
    expect(resolveEndpointBaseUrl("ecommerce-app_prod123")).toBe(
      "https://pulse-otel-collector.pulse-ux.com",
    );
  });
});
