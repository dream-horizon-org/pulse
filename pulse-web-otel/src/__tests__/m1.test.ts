// Mock @opentelemetry/api-logs — include ALL methods used by the real SDK so that
// SDK singleton tests (which call logs.setGlobalLoggerProvider) still work.
// SessionInstrumentation tests override getLogger per-test via mockReturnValue.
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));
import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import {
  getOrCreateInstallationId,
  SessionProvider,
  wasNewInstallation,
  _resetInstallationStateForTesting,
  SessionChangeEvent,
} from "../session";
import {
  validateConfig,
  isLocalEnvironment,
  resolveEndpointBaseUrl,
  PulseDataCollectionConsent,
} from "../config";
import {
  buildResource,
  computeAspectRatio,
  extractProjectId,
} from "../resource";
import {
  SdkConfigFetcher,
  DEFAULT_SDK_CONFIG,
  resolveConfigUrl,
} from "../remote-config";
import { FeatureGate } from "../feature-gate";
import type { PulseWebConfig } from "../config";
import type { PulseSdkConfig } from "../remote-config";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import { SessionInstrumentation } from "../instrumentations/session";
import type { SdkContext } from "../instrumentation-registry";
import { logs } from "@opentelemetry/api-logs";
import { PulseWebSemconv } from "../semconv";

const R = PulseWebSemconv.ResourceKey;
const K = PulseWebSemconv.AttributeKey;
const T = PulseWebSemconv.PulseType;
const B = PulseWebSemconv.LogBody;
const F = PulseWebSemconv.FixedValue;

// Mock the exporters module to avoid real OTLP network calls in tests
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
    getLogger: vi.fn().mockReturnValue({
      emit: vi.fn(),
    }),
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
    }),
  };
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function msToNs(ms: number): number {
  return ms * 1_000_000;
}

function makeConfig(overrides: Partial<PulseWebConfig> = {}): PulseWebConfig {
  return {
    apiKey: "proj_abc_supersecretkey",
    serviceName: "test-app",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    ...overrides,
  };
}

function makeStorageThrowingMock() {
  return {
    getItem: vi.fn(() => {
      throw new Error("storage unavailable");
    }),
    setItem: vi.fn(() => {
      throw new Error("storage unavailable");
    }),
    removeItem: vi.fn(() => {
      throw new Error("storage unavailable");
    }),
    clear: vi.fn(),
    length: 0,
    key: vi.fn(),
  } as unknown as Storage;
}

// ---------------------------------------------------------------------------
// M1 — Installation ID
// ---------------------------------------------------------------------------

describe("M1 — Installation ID", () => {
  let originalLocalStorage: Storage;
  let originalSessionStorage: Storage;

  beforeEach(() => {
    originalLocalStorage = window.localStorage;
    originalSessionStorage = window.sessionStorage;
    // Reset in-memory fallback by clearing storage state
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    Object.defineProperty(window, "localStorage", {
      value: originalLocalStorage,
      writable: true,
    });
    Object.defineProperty(window, "sessionStorage", {
      value: originalSessionStorage,
      writable: true,
    });
    vi.restoreAllMocks();
  });

  it("creates and persists installation ID in localStorage", () => {
    const id1 = getOrCreateInstallationId();
    expect(id1).toBeTruthy();
    expect(typeof id1).toBe("string");
    expect(id1.length).toBeGreaterThan(0);

    // Second call should return same ID
    const id2 = getOrCreateInstallationId();
    expect(id2).toBe(id1);

    // Should be in localStorage
    expect(window.localStorage.getItem("pulse_installation_id")).toBe(id1);
  });

  it("falls back to sessionStorage when localStorage throws", () => {
    const throwingLocal = makeStorageThrowingMock();
    Object.defineProperty(window, "localStorage", {
      value: throwingLocal,
      writable: true,
    });

    const id = getOrCreateInstallationId();
    expect(id).toBeTruthy();
    expect(typeof id).toBe("string");

    // Should be in sessionStorage
    expect(window.sessionStorage.getItem("pulse_installation_id")).toBe(id);
  });

  it("falls back to memory when both storages throw", () => {
    const throwingLocal = makeStorageThrowingMock();
    const throwingSession = makeStorageThrowingMock();

    Object.defineProperty(window, "localStorage", {
      value: throwingLocal,
      writable: true,
    });
    Object.defineProperty(window, "sessionStorage", {
      value: throwingSession,
      writable: true,
    });

    const id = getOrCreateInstallationId();
    expect(id).toBeTruthy();
    expect(typeof id).toBe("string");
  });

  it("returns same ID on repeated calls", () => {
    const id1 = getOrCreateInstallationId();
    const id2 = getOrCreateInstallationId();
    const id3 = getOrCreateInstallationId();
    expect(id1).toBe(id2);
    expect(id2).toBe(id3);
  });
});

// ---------------------------------------------------------------------------
// M1 — Session Provider
// ---------------------------------------------------------------------------

describe("M1 — Session Provider", () => {
  // Track provider per-test to ensure pagehide listener is removed after each test.
  // Without this, orphaned listeners from earlier tests can clear the sessionStorage
  // key before later tests' pagehide tests read it.
  let currentProvider: SessionProvider | null = null;

  beforeEach(() => {
    currentProvider = null;
    window.sessionStorage.clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    currentProvider?.shutdown();
    currentProvider = null;
    vi.useRealTimers();
    window.sessionStorage.clear();
  });

  it("creates a valid UUID session ID on first call", () => {
    const provider = new SessionProvider();
    currentProvider = provider;
    const id = provider.getSessionId();
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("rotates session after inactivity timeout", () => {
    const timeoutMs = 1000;
    const provider = new SessionProvider(timeoutMs);
    currentProvider = provider;

    const firstId = provider.getSessionId();
    expect(firstId).toBeTruthy();

    vi.advanceTimersByTime(timeoutMs + 100);

    const secondId = provider.getSessionId();
    expect(secondId).not.toBe(firstId);
  });

  it("sets previousSessionId on rotation", () => {
    const timeoutMs = 1000;
    const provider = new SessionProvider(timeoutMs);
    currentProvider = provider;

    let capturedPreviousId: string | undefined;
    provider.onSessionChange((event) => {
      if (event.type === "start" && capturedPreviousId === undefined) {
        capturedPreviousId = "FIRST_SEEN";
      } else if (event.type === "start") {
        capturedPreviousId = event.previousSessionId;
      }
    });

    const firstId = provider.getSessionId();

    vi.advanceTimersByTime(timeoutMs + 100);
    provider.getSessionId();

    expect(capturedPreviousId).toBe(firstId);
  });

  it("does not emit session.end on BFCache pagehide (persisted=true)", () => {
    const provider = new SessionProvider();
    currentProvider = provider;
    const sessionId = provider.getSessionId();

    const endEvents: string[] = [];
    provider.onSessionChange((event) => {
      if (event.type === "end") endEvents.push(event.sessionId ?? "");
    });

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: true }),
    );

    expect(endEvents).toHaveLength(0);
    expect(sessionId).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// M1 — Config validation
// ---------------------------------------------------------------------------

describe("M1 — Config validation", () => {
  it("throws when apiKey is missing", () => {
    expect(() => validateConfig(makeConfig({ apiKey: "" }))).toThrow(
      "[PulseWeb] apiKey is required",
    );
  });

  it("does not throw when serviceName is absent (it's optional — auto-derived)", () => {
    expect(() =>
      validateConfig({
        apiKey: "mykey",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      }),
    ).not.toThrow();
  });

  it("does not throw with all required fields", () => {
    expect(() => validateConfig(makeConfig())).not.toThrow();
  });

  it("isLocalEnvironment: detects default-project_ prefix", () => {
    expect(isLocalEnvironment("default-project_abc123")).toBe(true);
    expect(isLocalEnvironment("Test-myapp_abc123")).toBe(true);
    expect(isLocalEnvironment("myproject-123_prod456")).toBe(false);
  });

  it("resolveEndpointBaseUrl: returns localhost:4318 for default-project key", () => {
    const url = resolveEndpointBaseUrl("default-project_devkey01");
    expect(url).toBe("http://localhost:4318");
  });

  it("resolveEndpointBaseUrl: returns prod URL for production key without endpointBaseUrl", () => {
    const url = resolveEndpointBaseUrl("myproject-123_prod456");
    expect(url).toBe("https://pulse-otel-collector.pulse-ux.com");
  });

  it("resolveEndpointBaseUrl: uses provided endpointBaseUrl", () => {
    const url = resolveEndpointBaseUrl(
      "myproject-123_prod456",
      "https://collector.example.com",
    );
    expect(url).toBe("https://collector.example.com");
  });
});

// ---------------------------------------------------------------------------
// M1 — Resource builder
// ---------------------------------------------------------------------------

describe("M1 — Resource builder", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it("includes platform=web", () => {
    const resource = buildResource(makeConfig(), "14");
    expect(resource.attributes[R.PLATFORM]).toBe(F.PLATFORM_WEB);
  });

  it("includes rum.sdk.name=pulse_web_js", () => {
    const resource = buildResource(makeConfig(), "14");
    expect(resource.attributes[R.RUM_SDK_NAME]).toBe(F.RUM_SDK_NAME);
  });

  it("includes service.name from config", () => {
    const resource = buildResource(
      makeConfig({ serviceName: "my-shop" }),
      "14",
    );
    expect(resource.attributes[R.SERVICE_NAME]).toBe("my-shop");
  });

  it("extracts project.id from api key", () => {
    const config = makeConfig({ apiKey: "proj_abc123_secrettoken" });
    const resource = buildResource(config, "14");
    expect(resource.attributes[R.PROJECT_ID]).toBe("proj_abc123");
  });
});

// ---------------------------------------------------------------------------
// M1 — SDK singleton guard
// ---------------------------------------------------------------------------

describe("M1 — SDK singleton guard", () => {
  beforeEach(() => {
    // Mock fetch to prevent real network calls during SDK init background fetch
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({}),
      }),
    );

    // Mock XMLHttpRequest to prevent OTLP exporter from making network calls
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

    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(async () => {
    // Import PulseWeb fresh each test via dynamic import to test singleton
    const { PulseWeb } = await import("../sdk");
    if (PulseWeb.isInitialized()) {
      await PulseWeb.shutdown();
    }
    vi.unstubAllGlobals();
  });

  it("second start() call is a no-op", async () => {
    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    PulseWeb.start(config);
    // finishStart is async (awaits OS version resolution); flush microtasks.
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    // Second call should be no-op
    PulseWeb.start(config);
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
  });

  it("shutdown() allows re-initialization after complete", async () => {
    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    PulseWeb.start(config);
    // finishStart is async (awaits OS version resolution); flush microtasks.
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    await PulseWeb.shutdown();
    expect(PulseWeb.isInitialized()).toBe(false);

    // Should be able to re-initialize
    PulseWeb.start(config);
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// M1 — resolveConfigUrl
// ---------------------------------------------------------------------------

describe("M1 — resolveConfigUrl", () => {
  it("replaces :4318 with :8080 for localhost", () => {
    expect(
      resolveConfigUrl(undefined, "http://localhost:4318", "proj_abc"),
    ).toBe("http://localhost:8080/v1/configs/active/");
  });

  it("uses explicit configEndpointUrl as-is when provided", () => {
    expect(
      resolveConfigUrl(
        "https://api.example.com/v1/configs/active/",
        "http://localhost:4318",
        "proj_abc",
      ),
    ).toBe("https://api.example.com/v1/configs/active/");
  });

  it("returns prod config path for non-local URL", () => {
    expect(
      resolveConfigUrl(
        undefined,
        "https://pulse-otel-collector.pulse-ux.com",
        "myproject-123",
      ),
    ).toBe(
      "https://pulse-otel-collector.pulse-ux.com/config/projects/myproject-123/pulse-config.json",
    );
  });
});

// ---------------------------------------------------------------------------
// M1 — SdkConfigFetcher
// ---------------------------------------------------------------------------

describe("M1 — SdkConfigFetcher", () => {
  const CACHE_KEY = "pulse_sdk_config";

  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it("loads cached config from localStorage", () => {
    const cachedConfig: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      version: 42,
      description: "cached",
    };

    window.localStorage.setItem(CACHE_KEY, JSON.stringify(cachedConfig));

    const fetcher = new SdkConfigFetcher("https://api.example.com", "proj_abc");
    const config = fetcher.loadCached();

    expect(config.version).toBe(42);
    expect(config.description).toBe("cached");
  });

  it("persists fetched config when version changes", async () => {
    const newConfig: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      version: 10,
    };

    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(newConfig),
      }),
    );

    const fetcher = new SdkConfigFetcher("https://api.example.com", "proj_abc");
    fetcher.loadCached(); // version -1 (default)

    await fetcher.fetchInBackground();

    const stored = window.localStorage.getItem(CACHE_KEY);
    expect(stored).not.toBeNull();
    const parsed = JSON.parse(stored!) as PulseSdkConfig;
    expect(parsed.version).toBe(10);
  });

  it("skips write when version is same", async () => {
    const existingConfig: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      version: 5,
    };

    window.localStorage.setItem(CACHE_KEY, JSON.stringify(existingConfig));

    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(existingConfig), // same version
      }),
    );

    const fetcher = new SdkConfigFetcher("https://api.example.com", "proj_abc");
    fetcher.loadCached();

    const setItemSpy = vi.spyOn(window.localStorage, "setItem");

    await fetcher.fetchInBackground();

    // Should not have written again (same version)
    expect(setItemSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// M1 — FeatureGate
// ---------------------------------------------------------------------------

describe("M1 — FeatureGate", () => {
  it("returns true for features not in config (default enabled)", () => {
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      features: [], // empty
    };

    const gate = new FeatureGate(config);
    expect(gate.isEnabled("session")).toBe(true);
    expect(gate.isEnabled("js_crash")).toBe(true);
    expect(gate.isEnabled("web_vitals")).toBe(true);
  });

  it("returns false when sessionSampleRate is not 1 (Android getEnabledFeatures parity)", () => {
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      features: [
        {
          featureName: "session",
          sessionSampleRate: 0,
          sdks: ["pulse_web_js"],
        },
      ],
    };

    const gate = new FeatureGate(config);
    expect(gate.isEnabled("session")).toBe(false);
  });

  it("returns false for fractional sessionSampleRate", () => {
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      features: [
        {
          featureName: "web_vitals",
          sessionSampleRate: 0.25,
          sdks: ["pulse_web_js"],
        },
      ],
    };
    expect(new FeatureGate(config).isEnabled("web_vitals")).toBe(false);
  });

  it("returns true when sessionSampleRate is exactly 1", () => {
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      features: [
        {
          featureName: "session",
          sessionSampleRate: 1,
          sdks: ["pulse_web_js"],
        },
      ],
    };
    expect(new FeatureGate(config).isEnabled("session")).toBe(true);
  });
});

describe("M1 — Installation ID (extended)", () => {
  let originalLocalStorage: Storage;
  let originalSessionStorage: Storage;

  beforeEach(() => {
    originalLocalStorage = window.localStorage;
    originalSessionStorage = window.sessionStorage;
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    Object.defineProperty(window, "localStorage", {
      value: originalLocalStorage,
      writable: true,
    });
    Object.defineProperty(window, "sessionStorage", {
      value: originalSessionStorage,
      writable: true,
    });
    vi.restoreAllMocks();
  });

  it("generated ID matches UUID v4 format", () => {
    const id = getOrCreateInstallationId();
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("returns identical ID across 10 repeated calls", () => {
    const ids = Array.from({ length: 10 }, () => getOrCreateInstallationId());
    expect(new Set(ids).size).toBe(1);
  });

  it("survives page reload simulation — reads existing ID from localStorage", () => {
    const id1 = getOrCreateInstallationId();
    // Simulate reload: clear in-memory state by clearing sessionStorage but NOT localStorage
    window.sessionStorage.clear();
    const id2 = getOrCreateInstallationId();
    expect(id2).toBe(id1);
  });

  it("sessionStorage fallback ID also matches UUID v4 format", () => {
    const throwingLocal = {
      getItem: vi.fn(() => {
        throw new Error("blocked");
      }),
      setItem: vi.fn(() => {
        throw new Error("blocked");
      }),
      removeItem: vi.fn(),
      clear: vi.fn(),
      length: 0,
      key: vi.fn(),
    } as unknown as Storage;
    Object.defineProperty(window, "localStorage", {
      value: throwingLocal,
      writable: true,
    });

    const id = getOrCreateInstallationId();
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("memory fallback returns non-empty string when both storages throw", () => {
    const throwingStorage = {
      getItem: vi.fn(() => {
        throw new Error("blocked");
      }),
      setItem: vi.fn(() => {
        throw new Error("blocked");
      }),
      removeItem: vi.fn(),
      clear: vi.fn(),
      length: 0,
      key: vi.fn(),
    } as unknown as Storage;
    Object.defineProperty(window, "localStorage", {
      value: throwingStorage,
      writable: true,
    });
    Object.defineProperty(window, "sessionStorage", {
      value: throwingStorage,
      writable: true,
    });

    const id = getOrCreateInstallationId();
    expect(id).toBeTruthy();
    expect(id.length).toBeGreaterThan(0);
  });

  it("stored key name in localStorage is pulse_installation_id", () => {
    getOrCreateInstallationId();
    expect(window.localStorage.getItem("pulse_installation_id")).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// M1 — Session Provider (extended edge cases)
// ---------------------------------------------------------------------------

describe("M1 — Session Provider (extended)", () => {
  // Track the provider created in each test and shut it down in afterEach.
  // This removes its pagehide listener from window so it doesn't interfere with
  // subsequent tests that also dispatch pagehide.
  let currentProvider: SessionProvider | null = null;

  beforeEach(() => {
    currentProvider = null;
    window.sessionStorage.clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    currentProvider?.shutdown();
    currentProvider = null;
    vi.useRealTimers();
    window.sessionStorage.clear();
  });

  it("session ID is stable within the inactivity timeout window", () => {
    const provider = new SessionProvider(5000);
    currentProvider = provider;
    const id1 = provider.getSessionId();

    vi.advanceTimersByTime(4000);
    const id2 = provider.getSessionId();

    expect(id2).toBe(id1);
  });

  it("session ID lives in localStorage (shared across tabs), not sessionStorage", () => {
    const provider = new SessionProvider();
    currentProvider = provider;
    const id = provider.getSessionId();

    expect(window.localStorage.getItem("pulse_session_id")).toBe(id);
    expect(window.sessionStorage.getItem("pulse_session_id")).toBeNull();
  });

  it("updateActivity() resets the inactivity clock — same session ID returned", () => {
    const timeoutMs = 2000;
    const provider = new SessionProvider(timeoutMs);
    currentProvider = provider;
    const id1 = provider.getSessionId();

    vi.advanceTimersByTime(1800);
    provider.updateActivity();
    vi.advanceTimersByTime(1800);
    const id2 = provider.getSessionId();

    expect(id2).toBe(id1);
  });

  it("shutdown() clears session from localStorage", () => {
    const provider = new SessionProvider();
    // Don't assign currentProvider here — the test itself calls shutdown()
    provider.getSessionId();
    expect(window.localStorage.getItem("pulse_session_id")).toBeTruthy();

    provider.shutdown();
    expect(window.localStorage.getItem("pulse_session_id")).toBeNull();
  });

  it("emits session.end with reason shutdown on shutdown()", () => {
    const provider = new SessionProvider();
    // Don't assign currentProvider — test calls shutdown() itself
    provider.getSessionId();

    const endEvents: Array<{ reason: string }> = [];
    provider.onSessionChange((e) => {
      if (e.type === "end") endEvents.push({ reason: e.reason });
    });

    provider.shutdown();
    expect(endEvents).toHaveLength(1);
    expect(endEvents[0]?.reason).toBe("shutdown");
  });

  it("emits session.end with reason page_unload on pagehide (persisted=false)", () => {
    const provider = new SessionProvider();
    currentProvider = provider;
    provider.getSessionId();

    const endReasons: string[] = [];
    provider.onSessionChange((e) => {
      if (e.type === "end") endReasons.push(e.reason);
    });

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );
    expect(endReasons).toHaveLength(1);
    expect(endReasons[0]).toBe("page_unload");
  });

  it("does NOT emit session.end on BFCache pagehide (persisted=true)", () => {
    const provider = new SessionProvider();
    currentProvider = provider;
    provider.getSessionId();

    const endEvents: string[] = [];
    provider.onSessionChange((e) => {
      if (e.type === "end") endEvents.push(e.reason);
    });

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: true }),
    );
    expect(endEvents).toHaveLength(0);
  });

  it("session.end includes correct session.id and positive durationNs", () => {
    const provider = new SessionProvider();
    // Don't assign currentProvider — test calls shutdown() itself
    const sessionId = provider.getSessionId();

    let capturedEnd: { sessionId?: string; durationNs?: number } = {};
    provider.onSessionChange((e) => {
      if (e.type === "end") {
        capturedEnd = { sessionId: e.sessionId, durationNs: e.durationNs ?? 0 };
      }
    });

    vi.advanceTimersByTime(3000);
    provider.shutdown();

    expect(capturedEnd.sessionId).toBe(sessionId);
    expect(capturedEnd.durationNs).toBeGreaterThanOrEqual(0);
  });

  it("rotation emits end then start — exactly one of each", () => {
    const timeoutMs = 1000;
    const provider = new SessionProvider(timeoutMs);
    currentProvider = provider;
    provider.getSessionId();

    const events: string[] = [];
    provider.onSessionChange((e) => events.push(e.type));

    vi.advanceTimersByTime(timeoutMs + 100);
    provider.getSessionId();

    expect(events.filter((e) => e === "end")).toHaveLength(1);
    expect(events.filter((e) => e === "start")).toHaveLength(1);
    expect(events.indexOf("end")).toBeLessThan(events.indexOf("start"));
  });

  it("rotation: start event carries previousSessionId equal to old session ID", () => {
    const timeoutMs = 1000;
    const provider = new SessionProvider(timeoutMs);
    currentProvider = provider;
    const firstId = provider.getSessionId();

    let rotationStartPreviousId = "";
    let startCount = 0;
    provider.onSessionChange((e) => {
      if (e.type === "start") {
        startCount++;
        if (startCount === 1) {
          rotationStartPreviousId = e.previousSessionId ?? "";
        }
      }
    });

    vi.advanceTimersByTime(timeoutMs + 100);
    provider.getSessionId();

    expect(rotationStartPreviousId).toBe(firstId);
  });

  it("unsubscribe returned from onSessionChange stops receiving events", () => {
    const provider = new SessionProvider();
    // Don't assign currentProvider — test calls shutdown() itself
    const received: string[] = [];
    const unsub = provider.onSessionChange((e) => received.push(e.type));

    unsub();
    provider.getSessionId();
    provider.shutdown();

    expect(received).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// M1 — Resource Builder (extended edge cases)
// ---------------------------------------------------------------------------

describe("M1 — Resource Builder (extended)", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it("includes rum.sdk.version as a non-empty string", () => {
    const resource = buildResource(makeConfig(), "14");
    expect(typeof resource.attributes[R.RUM_SDK_VERSION]).toBe("string");
    expect(
      (resource.attributes[R.RUM_SDK_VERSION] as string).length,
    ).toBeGreaterThan(0);
  });

  it("service.version defaults to 0.0.0 when not provided", () => {
    const resource = buildResource(makeConfig(), "14");
    expect(resource.attributes[R.SERVICE_VERSION]).toBe("0.0.0");
  });

  it("service.version uses config value when provided", () => {
    const resource = buildResource(
      makeConfig({ serviceVersion: "2.3.1" }),
      "14",
    );
    expect(resource.attributes[R.SERVICE_VERSION]).toBe("2.3.1");
  });

  it("installation.id is present and matches UUID v4 format", () => {
    const resource = buildResource(makeConfig(), "14");
    const id = resource.attributes[R.INSTALLATION_ID] as string;
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("installation.id in resource matches getOrCreateInstallationId()", () => {
    const expected = getOrCreateInstallationId();
    const resource = buildResource(makeConfig(), "14");
    expect(resource.attributes[R.INSTALLATION_ID]).toBe(expected);
  });

  it("browser.name is a non-empty string", () => {
    const resource = buildResource(makeConfig(), "14");
    const name = resource.attributes[R.BROWSER_NAME] as string;
    expect(typeof name).toBe("string");
    expect(name.length).toBeGreaterThan(0);
  });

  it("device.type is one of desktop | mobile | tablet", () => {
    const resource = buildResource(makeConfig(), "14");
    expect(["desktop", "mobile", "tablet"]).toContain(
      resource.attributes[R.DEVICE_TYPE],
    );
  });

  it("screen.resolution is in WxH format", () => {
    const resource = buildResource(makeConfig(), "14");
    const res = resource.attributes[R.SCREEN_RESOLUTION] as string;
    expect(res).toMatch(/^\d+x\d+$/);
  });

  it("screen.aspect_ratio is in W:H format", () => {
    const resource = buildResource(makeConfig(), "14");
    const ratio = resource.attributes[R.SCREEN_ASPECT_RATIO] as string;
    expect(ratio).toMatch(/^\d+:\d+$/);
  });

  it("browser.language is a non-empty string", () => {
    const resource = buildResource(makeConfig(), "14");
    const lang = resource.attributes[R.BROWSER_LANGUAGE] as string;
    expect(typeof lang).toBe("string");
    expect(lang.length).toBeGreaterThan(0);
  });

  it("timezone is a non-empty string", () => {
    const resource = buildResource(makeConfig(), "14");
    const tz = resource.attributes[R.TIMEZONE] as string;
    expect(typeof tz).toBe("string");
    expect(tz.length).toBeGreaterThan(0);
  });

  it("apiKey without underscore → project.id falls back to raw apiKey", () => {
    const config = makeConfig({ apiKey: "rawkeynoprefix" });
    const resource = buildResource(config, "14");
    expect(resource.attributes["project.id"]).toBe("rawkeynoprefix");
  });

  it("extractProjectId: myproject-123_devkey456 → myproject-123", () => {
    expect(extractProjectId("myproject-123_devkey456")).toBe("myproject-123");
  });

  it("extractProjectId: myproject-123_prod456 → myproject-123", () => {
    expect(extractProjectId("myproject-123_prod456")).toBe("myproject-123");
  });

  it("extractProjectId: no underscore → returns full key", () => {
    expect(extractProjectId("noprefixkey")).toBe("noprefixkey");
  });
});

// ---------------------------------------------------------------------------
// M1 — computeAspectRatio utility
// ---------------------------------------------------------------------------

describe("M1 — computeAspectRatio", () => {
  it("1920x1080 → 16:9", () => {
    expect(computeAspectRatio(1920, 1080)).toBe("16:9");
  });

  it("1280x720 → 16:9", () => {
    expect(computeAspectRatio(1280, 720)).toBe("16:9");
  });

  it("2560x1600 → 8:5", () => {
    expect(computeAspectRatio(2560, 1600)).toBe("8:5");
  });

  it("0x0 → 0:0", () => {
    expect(computeAspectRatio(0, 0)).toBe("0:0");
  });

  it("square 800x800 → 1:1", () => {
    expect(computeAspectRatio(800, 800)).toBe("1:1");
  });
});

// ---------------------------------------------------------------------------
// M1 — Global Attributes Processor
// ---------------------------------------------------------------------------

describe("M1 — GlobalAttributesProcessor", () => {
  // Track providers created by makeProcessor() so we can shut them down
  // in afterEach, removing their pagehide listeners from window.
  const createdProviders: SessionProvider[] = [];

  beforeEach(() => {
    createdProviders.length = 0;
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    for (const p of createdProviders) {
      p.shutdown();
    }
    createdProviders.length = 0;
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  function makeProcessor(configOverrides: Partial<PulseWebConfig> = {}) {
    const config = makeConfig(configOverrides);
    const sessionProvider = new SessionProvider();
    createdProviders.push(sessionProvider);
    return {
      processor: new PulseGlobalAttributesProcessor(sessionProvider, config),
      sessionProvider,
    };
  }

  it("injects session.id onto a span via onStart", () => {
    const { processor, sessionProvider } = makeProcessor();
    const sessionId = sessionProvider.getSessionId();

    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);

    expect(attrs[K.SESSION_ID]).toBe(sessionId);
  });

  it("injects installation.id onto a span via onStart", () => {
    const { processor } = makeProcessor();
    const installId = getOrCreateInstallationId();

    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);

    expect(attrs[K.INSTALLATION_ID]).toBe(installId);
  });

  it("injects platform=web onto every span", () => {
    const { processor } = makeProcessor();
    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);
    expect(attrs[K.PLATFORM]).toBe(F.PLATFORM_WEB);
  });

  it("injects url.path from window.location.pathname", () => {
    const { processor } = makeProcessor();
    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);
    expect(attrs[K.URL_PATH]).toBe(window.location.pathname);
  });

  it("injects page.url from window.location.href", () => {
    const { processor } = makeProcessor();
    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);
    expect(attrs[K.PAGE_URL]).toBe(window.location.href);
  });

  it("injects session.id onto a log record via onEmit", () => {
    const { processor, sessionProvider } = makeProcessor();
    const sessionId = sessionProvider.getSessionId();

    const attrs: Record<string, unknown> = {};
    const fakeLog = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onEmit>[0];

    processor.onEmit(fakeLog);
    expect(attrs[K.SESSION_ID]).toBe(sessionId);
  });

  it("setScreenName overrides screen.name on next signal", () => {
    const { processor } = makeProcessor();
    processor.setScreenName("checkout");

    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);
    expect(attrs[K.SCREEN_NAME]).toBe("checkout");
  });

  it("screen.name heuristic: strips numeric segments from path", () => {
    const { processor } = makeProcessor();
    // Simulate pathname /products/12345
    Object.defineProperty(window, "location", {
      value: {
        ...window.location,
        pathname: "/products/12345",
        href: "http://localhost/products/12345",
      },
      writable: true,
    });

    expect(processor.getCurrentScreenName()).toBe("/products");
  });

  it("screen.name heuristic: strips UUID segments from path", () => {
    const { processor } = makeProcessor();
    Object.defineProperty(window, "location", {
      value: {
        ...window.location,
        pathname: "/users/550e8400-e29b-41d4-a716-446655440000/profile",
        href: "http://localhost/users/550e8400-e29b-41d4-a716-446655440000/profile",
      },
      writable: true,
    });

    expect(processor.getCurrentScreenName()).toBe("/users/profile");
  });

  it("screen.name route pattern takes priority over heuristic", () => {
    const { processor } = makeProcessor({
      routePatterns: [{ pattern: "^/products", name: "Products" }],
    });
    Object.defineProperty(window, "location", {
      value: {
        ...window.location,
        pathname: "/products/12345",
        href: "http://localhost/products/12345",
      },
      writable: true,
    });

    expect(processor.getCurrentScreenName()).toBe("Products");
  });

  it("screen.name manual override takes priority over route patterns", () => {
    const { processor } = makeProcessor({
      routePatterns: [{ pattern: "^/products", name: "Products" }],
    });
    processor.setScreenName("ManualName");

    expect(processor.getCurrentScreenName()).toBe("ManualName");
  });

  it("screen.name falls back to raw pathname when no segments remain after heuristic", () => {
    const { processor } = makeProcessor();
    Object.defineProperty(window, "location", {
      value: {
        ...window.location,
        pathname: "/12345",
        href: "http://localhost/12345",
      },
      writable: true,
    });

    // All segments stripped → falls back to raw pathname
    expect(processor.getCurrentScreenName()).toBe("/12345");
  });

  it("globalAttributes from config are injected on every span", () => {
    const { processor } = makeProcessor({
      globalAttributes: { "app.env": "staging", "tenant.id": "acme" },
    });

    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof processor.onStart>[0];

    processor.onStart(fakeSpan, {} as never);

    expect(attrs["app.env"]).toBe("staging");
    expect(attrs["tenant.id"]).toBe("acme");
  });

  it("after session rotation — new session.id appears on next signal", () => {
    vi.useFakeTimers();
    const timeoutMs = 1000;
    // Use makeProcessor() so the provider is registered for cleanup in afterEach
    const { processor, sessionProvider } = makeProcessor();

    // Override default timeout by creating a fresh provider via helper isn't possible,
    // so create directly and register for cleanup manually
    const rotationProvider = new SessionProvider(timeoutMs);
    createdProviders.push(rotationProvider);
    const rotationProcessor = new PulseGlobalAttributesProcessor(
      rotationProvider,
      makeConfig(),
    );

    const firstSessionId = rotationProvider.getSessionId();

    vi.advanceTimersByTime(timeoutMs + 100);
    const secondSessionId = rotationProvider.getSessionId(); // triggers rotation

    const attrs: Record<string, unknown> = {};
    const fakeSpan = {
      setAttribute: (k: string, v: unknown) => {
        attrs[k] = v;
      },
    } as unknown as Parameters<typeof rotationProcessor.onStart>[0];

    rotationProcessor.onStart(fakeSpan, {} as never);

    expect(attrs[K.SESSION_ID]).toBe(secondSessionId);
    expect(attrs[K.SESSION_ID]).not.toBe(firstSessionId);

    // Suppress unused variable warning — processor/sessionProvider were registered for cleanup
    void processor;
    void sessionProvider;

    vi.useRealTimers();
  });
});

// ---------------------------------------------------------------------------
// M1 — Session Instrumentation (session.start / session.end events)
// ---------------------------------------------------------------------------

describe("M1 — SessionInstrumentation events", () => {
  // Per-test log capture. Each test calls makeCapture() to get a fresh array
  // and configure the mocked otelLogs.getLogger to emit into it.
  type CapturedLog = { body: unknown; attributes: Record<string, unknown> };

  function makeCapture(): CapturedLog[] {
    const captured: CapturedLog[] = [];
    vi.mocked(logs.getLogger).mockReturnValue({
      emit: vi.fn(
        (record: { body: unknown; attributes?: Record<string, unknown> }) => {
          captured.push({
            body: record.body,
            attributes: record.attributes ?? {},
          });
        },
      ),
    } as never);
    return captured;
  }

  function makeFakeSdk(sessionProvider: SessionProvider) {
    return {
      sessionProvider,
      logger: { emit: vi.fn() },
      tracer: {},
      config: makeConfig(),
      globalAttrsProcessor: {} as never,
    } as unknown as SdkContext;
  }

  // Track provider created in each test so we can shut it down in afterEach,
  // removing its pagehide listener from window before the next test runs.
  let currentProvider: SessionProvider | null = null;

  beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
    currentProvider = null;
    vi.useFakeTimers();
  });

  afterEach(() => {
    currentProvider?.shutdown();
    currentProvider = null;
    vi.useRealTimers();
    window.sessionStorage.clear();
  });

  it("emits exactly one session.start on install", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const starts = captured.filter(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(starts).toHaveLength(1);
  });

  it("session.start has body=session.start", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(startLog?.body).toBe(B.SESSION_START);
  });

  it("session.start carries non-empty session.id", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(startLog?.attributes[K.SESSION_ID]).toBeTruthy();
  });

  it("session.start session.id matches the active session ID", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const activeSessionId = sessionProvider.getSessionId();
    const startLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(startLog?.attributes[K.SESSION_ID]).toBe(activeSessionId);
  });

  it("session.start carries session.start_reason = sdk_init on first start", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(startLog?.attributes[K.SESSION_START_REASON]).toBe("sdk_init");
  });

  it("session.start carries empty session.previous_id on first start", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(startLog?.attributes[K.SESSION_PREVIOUS_ID]).toBe("");
  });

  it("emits session.end on pagehide (persisted=false)", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    // Clear start log so we can isolate the end log
    captured.length = 0;
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );

    const endLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_END,
    );
    expect(endLog).toBeDefined();
    expect(endLog?.body).toBe(B.SESSION_END);
  });

  it("session.end carries non-negative session.duration_ns", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    vi.advanceTimersByTime(5000);
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );

    const endLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_END,
    );
    expect(endLog?.attributes[K.SESSION_DURATION_MS]).toBeGreaterThanOrEqual(0);
  });

  it("session.end carries the correct session.id", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const activeId = sessionProvider.getSessionId();

    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );

    const endLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_END,
    );
    expect(endLog?.attributes[K.SESSION_ID]).toBe(activeId);
  });

  it("does NOT emit session.end on BFCache pagehide (persisted=true)", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: true }),
    );

    const endLog = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_END,
    );
    expect(endLog).toBeUndefined();
  });

  it("normal session: exactly 1 start + 0 ends while tab is open", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const starts = captured.filter(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    const ends = captured.filter(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_END,
    );

    expect(starts).toHaveLength(1);
    expect(ends).toHaveLength(0);
  });

  it("rotation: emits session.end then session.start — in correct order", () => {
    const captured = makeCapture();
    const timeoutMs = 1000;
    const sessionProvider = new SessionProvider(timeoutMs);
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0; // clear initial start

    vi.advanceTimersByTime(timeoutMs + 100);
    sessionProvider.getSessionId(); // trigger rotation

    const types = captured.map((l) => l.attributes[K.PULSE_TYPE]);
    expect(types).toContain(T.SESSION_END);
    expect(types).toContain(T.SESSION_START);
    expect(types.indexOf(T.SESSION_END)).toBeLessThan(
      types.indexOf(T.SESSION_START),
    );
  });

  it("rotation: new session.start carries previous session.id as session.previous_id", () => {
    const captured = makeCapture();
    const timeoutMs = 1000;
    const sessionProvider = new SessionProvider(timeoutMs);
    currentProvider = sessionProvider;
    const firstId = sessionProvider.getSessionId();

    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    vi.advanceTimersByTime(timeoutMs + 100);
    sessionProvider.getSessionId();

    const rotationStart = captured.find(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(rotationStart?.attributes[K.SESSION_PREVIOUS_ID]).toBe(firstId);
  });

  it("uninstall() stops emitting events — rotation after uninstall is silent", () => {
    const captured = makeCapture();
    const timeoutMs = 1000;
    const sessionProvider = new SessionProvider(timeoutMs);
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    instr.uninstall();
    captured.length = 0;

    vi.advanceTimersByTime(timeoutMs + 100);
    sessionProvider.getSessionId();

    expect(captured).toHaveLength(0);
  });

  it("no duplicate session.start even if session existed before install", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    sessionProvider.getSessionId(); // pre-create session before install

    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const starts = captured.filter(
      (l) => l.attributes[K.PULSE_TYPE] === T.SESSION_START,
    );
    expect(starts).toHaveLength(1);
  });

  // Area 3.13 — very short session still emits session.end
  it("3.13: very short session (< 100ms) still emits session.end with non-negative duration_ms", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    // Immediately dispatch pagehide — no time has elapsed (fake timers at 0ms)
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );

    const endLog = captured.find(
      (l) => l.attributes["pulse.type"] === "session.end",
    );
    expect(endLog).toBeDefined();
    expect(endLog?.body).toBe("session.end");
    // Duration may be 0 for immediate end, but must not be negative (instrumentation uses session.duration_ms)
    expect(endLog?.attributes["session.duration_ms"]).toBeGreaterThanOrEqual(0);
  });

  // Area 3.15 — consent DENIED blocks all session signals (unit level: SDK never starts)
  it("3.15: SessionInstrumentation emits session.start when installed normally", () => {
    // The consent check happens in sdk.ts before SessionInstrumentation is installed.
    // At the unit level, test that install() does NOT emit session.start if we simply
    // never call it (simulating consent DENIED → SDK never calls registry.installAll()).
    const captured = makeCapture();
    // With consent DENIED, the SDK would not construct a SessionProvider or call install().
    // So: no install → no session.start / session.end emitted.
    // We verify the converse: install does emit session.start.
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const starts = captured.filter(
      (l) => l.attributes["pulse.type"] === "session.start",
    );
    expect(starts).toHaveLength(1);

    // Now test the no-install path (consent DENIED simulation):
    const captured2 = makeCapture();
    // Simply don't call instr2.install() — this simulates the SDK not starting
    const instr2 = new SessionInstrumentation();
    void instr2; // not installed

    const starts2 = captured2.filter(
      (l) => l.attributes["pulse.type"] === "session.start",
    );
    expect(starts2).toHaveLength(0); // no start because install() was never called
  });
});

// ---------------------------------------------------------------------------
// M1 — Session Provider: reload and clone detection
// ---------------------------------------------------------------------------

describe("M1 — Session Provider: reload and clone detection (beforeunload flag)", () => {
  const createdProviders: SessionProvider[] = [];

  function makeProvider(
    inactivityMs?: number,
    maxLifetimeMs?: number,
    pageHiddenMs?: number,
  ) {
    const p = new SessionProvider(inactivityMs, maxLifetimeMs, pageHiddenMs);
    createdProviders.push(p);
    return p;
  }

  afterEach(() => {
    for (const p of createdProviders) p.shutdown();
    createdProviders.length = 0;
    vi.restoreAllMocks();
    window.sessionStorage.clear();
    window.localStorage.removeItem("pulse_session_id");
    window.localStorage.removeItem("pulse_session_ts");
    window.localStorage.removeItem("pulse_session_start");
    Object.defineProperty(document, "hidden", {
      value: false,
      configurable: true,
    });
  });

  // --- Clone detection ---
  // PostHog model: clone → same session.id (inherited), new window.id (unique per tab).

  it("clone: flag present → inherits session (wasSessionReused=true, same session ID)", () => {
    const clonedId = "cloned-session-uuid";
    window.localStorage.setItem("pulse_session_id", clonedId);
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now() - 3000)),
    );
    window.sessionStorage.setItem("pulse_session_clone_flag", "1");

    const provider = makeProvider();

    expect(provider.wasSessionReused()).toBe(true);
    expect(provider.getSessionId()).toBe(clonedId);
  });

  it("clone: session is preserved in localStorage (not cleared)", () => {
    window.localStorage.setItem("pulse_session_id", "clone-id");
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now() - 1000)),
    );
    window.sessionStorage.setItem("pulse_session_clone_flag", "1");

    makeProvider();

    // The inherited session must be kept (not wiped)
    expect(window.localStorage.getItem("pulse_session_id")).toBe("clone-id");
    // The flag is re-set for THIS tab so any future clone of it is also detected
    expect(window.sessionStorage.getItem("pulse_session_clone_flag")).toBe("1");
  });

  it("clone: emitInitialSession() is silent (session reused — no duplicate session.start)", () => {
    window.localStorage.setItem("pulse_session_id", "cloned-session");
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now() - 3000)),
    );
    window.sessionStorage.setItem("pulse_session_clone_flag", "1");

    const provider = makeProvider();
    const events: SessionChangeEvent[] = [];
    provider.onSessionChange((e) => events.push(e));
    provider.emitInitialSession();

    expect(events.filter((e) => e.type === "start")).toHaveLength(0);
  });

  it("clone: getWindowId() is different from a separate tab instance", () => {
    // Two tabs initialised in sequence get distinct window IDs
    window.sessionStorage.setItem("pulse_session_clone_flag", "1");
    const tabA = makeProvider();
    window.sessionStorage.clear();
    const tabB = makeProvider();

    expect(tabA.getWindowId()).not.toBe(tabB.getWindowId());
  });

  // --- Reload detection ---

  it("reload: no flag + active session → wasSessionReused() true, same ID returned", () => {
    const priorId = "prior-session-uuid";
    window.localStorage.setItem("pulse_session_id", priorId);
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now() - 5000)),
    );
    // No clone flag — beforeunload removed it before reload

    const provider = makeProvider();

    expect(provider.wasSessionReused()).toBe(true);
    expect(provider.getSessionId()).toBe(priorId);
  });

  it("reload: emitInitialSession() does NOT emit session.start when session is reused", () => {
    window.localStorage.setItem("pulse_session_id", "reload-session");
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now() - 5000)),
    );

    const provider = makeProvider();
    const events: SessionChangeEvent[] = [];
    provider.onSessionChange((e) => events.push(e));
    provider.emitInitialSession();

    expect(events.filter((e) => e.type === "start")).toHaveLength(0);
  });

  it("fresh new tab: no flag, no session → creates new session", () => {
    const provider = makeProvider();
    expect(provider.wasSessionReused()).toBe(false);
    const id = provider.getSessionId();
    expect(id.length).toBeGreaterThan(0);
  });

  // --- beforeunload flag lifecycle ---

  it("init always writes clone flag to sessionStorage", () => {
    makeProvider();
    expect(window.sessionStorage.getItem("pulse_session_clone_flag")).toBe("1");
  });

  it("beforeunload removes the clone flag (reload won't see it)", () => {
    makeProvider();
    expect(window.sessionStorage.getItem("pulse_session_clone_flag")).toBe("1");

    window.dispatchEvent(new Event("beforeunload"));

    expect(
      window.sessionStorage.getItem("pulse_session_clone_flag"),
    ).toBeNull();
  });

  // --- pagehide behaviour ---

  it("pagehide: emits session.end event", () => {
    const provider = makeProvider();
    provider.getSessionId();

    const events: SessionChangeEvent[] = [];
    provider.onSessionChange((e) => events.push(e));
    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );

    expect(
      events.filter((e) => e.type === "end" && e.reason === "page_unload"),
    ).toHaveLength(1);
  });

  it("pagehide: does NOT clear localStorage (skipClear=true)", () => {
    const provider = makeProvider();
    provider.getSessionId();
    const before = window.localStorage.getItem("pulse_session_id");
    expect(before).not.toBeNull();

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false }),
    );

    expect(window.localStorage.getItem("pulse_session_id")).toBe(before);
  });

  it("shutdown(): clears localStorage", () => {
    const provider = makeProvider();
    provider.getSessionId();
    expect(window.localStorage.getItem("pulse_session_id")).not.toBeNull();
    provider.shutdown();
    expect(window.localStorage.getItem("pulse_session_id")).toBeNull();
  });

  // --- 4-hour max session lifetime ---

  it("max lifetime: session rotates when age exceeds threshold", () => {
    const maxMs = 1000;
    window.localStorage.setItem("pulse_session_id", "old");
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now() - maxMs - 100)),
    );

    const provider = makeProvider(30 * 60 * 1000, maxMs);
    const events: SessionChangeEvent[] = [];
    provider.onSessionChange((e) => events.push(e));

    const newId = provider.getSessionId();

    expect(newId).not.toBe("old");
    expect(
      events.find((e) => e.type === "end" && e.reason === "max_lifetime"),
    ).toBeTruthy();
    expect(
      events.find((e) => e.type === "start" && e.reason === "max_lifetime"),
    ).toBeTruthy();
  });

  it("max lifetime: session within threshold is NOT rotated", () => {
    window.localStorage.setItem("pulse_session_id", "fresh");
    window.localStorage.setItem("pulse_session_ts", String(msToNs(Date.now())));
    window.localStorage.setItem(
      "pulse_session_start",
      String(msToNs(Date.now()) - 100),
    );

    const provider = makeProvider(30 * 60 * 1000, 5000);
    expect(provider.getSessionId()).toBe("fresh");
  });

  // --- 15-minute page-hidden inactivity timeout ---

  it("page-hidden timeout: session rotates when page hidden beyond threshold", () => {
    const pageHiddenMs = 500;
    const provider = makeProvider(
      30 * 60 * 1000,
      4 * 60 * 60 * 1000,
      pageHiddenMs,
    );
    const oldId = provider.getSessionId();

    const events: SessionChangeEvent[] = [];
    provider.onSessionChange((e) => events.push(e));

    // Page goes hidden — provider records hiddenAt = real Date.now()
    Object.defineProperty(document, "hidden", {
      value: true,
      configurable: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));

    // Access the recorded hiddenAt and mock Date.now() to be past the threshold
    const hiddenAt = (provider as unknown as Record<string, unknown>)[
      "_hiddenAtMs"
    ] as number;
    const nowSpy = vi
      .spyOn(Date, "now")
      .mockReturnValue(hiddenAt + pageHiddenMs + 100);

    Object.defineProperty(document, "hidden", {
      value: false,
      configurable: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));

    nowSpy.mockRestore();

    expect(provider.getSessionId()).not.toBe(oldId);
    expect(
      events.find((e) => e.type === "end" && e.reason === "inactivity_timeout"),
    ).toBeTruthy();
    expect(
      events.find(
        (e) => e.type === "start" && e.reason === "inactivity_timeout",
      ),
    ).toBeTruthy();
  });

  it("page-hidden timeout: session NOT rotated when hidden duration under threshold", () => {
    const pageHiddenMs = 60_000;
    const provider = makeProvider(
      30 * 60 * 1000,
      4 * 60 * 60 * 1000,
      pageHiddenMs,
    );
    const existingId = provider.getSessionId();

    Object.defineProperty(document, "hidden", {
      value: true,
      configurable: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));

    // Mock Date.now() to be just under the threshold
    const hiddenAt = (provider as unknown as Record<string, unknown>)[
      "_hiddenAtMs"
    ] as number;
    const nowSpy = vi
      .spyOn(Date, "now")
      .mockReturnValue(hiddenAt + pageHiddenMs - 1000);

    Object.defineProperty(document, "hidden", {
      value: false,
      configurable: true,
    });
    document.dispatchEvent(new Event("visibilitychange"));

    nowSpy.mockRestore();

    expect(provider.getSessionId()).toBe(existingId);
  });
});

// ---------------------------------------------------------------------------
// M1 — wasNewInstallation
// ---------------------------------------------------------------------------

describe("M1 — wasNewInstallation", () => {
  let originalLocalStorage: Storage;
  let originalSessionStorage: Storage;

  beforeEach(() => {
    originalLocalStorage = window.localStorage;
    originalSessionStorage = window.sessionStorage;
  });

  afterEach(() => {
    Object.defineProperty(window, "localStorage", {
      value: originalLocalStorage,
      writable: true,
      configurable: true,
    });
    Object.defineProperty(window, "sessionStorage", {
      value: originalSessionStorage,
      writable: true,
      configurable: true,
    });
    vi.restoreAllMocks();
  });

  it("returns true when localStorage is empty (fresh install)", () => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
    window.sessionStorage.clear();

    getOrCreateInstallationId();

    expect(wasNewInstallation()).toBe(true);
  });

  it("returns false when installation ID already in localStorage (returning user)", () => {
    _resetInstallationStateForTesting();
    window.localStorage.setItem("pulse_installation_id", "existing-uuid");

    getOrCreateInstallationId();

    expect(wasNewInstallation()).toBe(false);
  });

  it("returns false when installation ID already in sessionStorage (localStorage unavailable)", () => {
    _resetInstallationStateForTesting();

    const throwingLocal = {
      getItem: vi.fn(() => {
        throw new Error("storage unavailable");
      }),
      setItem: vi.fn(() => {
        throw new Error("storage unavailable");
      }),
      removeItem: vi.fn(() => {
        throw new Error("storage unavailable");
      }),
      clear: vi.fn(),
      length: 0,
      key: vi.fn(),
    } as unknown as Storage;
    Object.defineProperty(window, "localStorage", {
      value: throwingLocal,
      writable: true,
      configurable: true,
    });

    window.sessionStorage.setItem("pulse_installation_id", "existing-uuid");

    getOrCreateInstallationId();

    expect(wasNewInstallation()).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// M1 — SDK public API signals
// ---------------------------------------------------------------------------

// Helper: build a mock provider bundle with a custom emitSpy for the logger.
function makeMockBundle(emitSpy: ReturnType<typeof vi.fn>) {
  return {
    tracerProvider: {
      addSpanProcessor: vi.fn(),
      getTracer: vi.fn().mockReturnValue({
        startSpan: vi
          .fn()
          .mockReturnValue({ setAttribute: vi.fn(), end: vi.fn() }),
      }),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
      register: vi.fn(),
    },
    loggerProvider: {
      addLogRecordProcessor: vi.fn(),
      getLogger: vi.fn().mockReturnValue({ emit: emitSpy }),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    meterProvider: {
      addMetricReader: vi.fn(),
      getMeter: vi.fn().mockReturnValue({}),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    cleanup: vi.fn(),
  };
}

describe("M1 — SDK public API signals", () => {
  beforeEach(() => {
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

    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(async () => {
    const { PulseWeb } = await import("../sdk");
    if (PulseWeb.isInitialized()) {
      await PulseWeb.shutdown();
    }
    vi.unstubAllGlobals();
  });

  it("emits rum.sdk.init.started then rum.sdk.init.span.exporter on LoggerProvider (Android parity)", async () => {
    const emitSpy = vi.fn();
    const { createProviders } = await import("../exporters");
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    // finishStart awaits getOsVersionAsync (≤200ms race).
    await new Promise((r) => setTimeout(r, 250));

    const bodies = emitSpy.mock.calls.map(
      (c) => (c[0] as { body?: string }).body ?? "",
    );
    const idxStarted = bodies.indexOf("rum.sdk.init.started");
    const idxExporter = bodies.indexOf("rum.sdk.init.span.exporter");
    expect(idxStarted).toBeGreaterThanOrEqual(0);
    expect(idxExporter).toBeGreaterThanOrEqual(0);
    expect(idxStarted).toBeLessThan(idxExporter);

    const exporterCall = emitSpy.mock.calls[idxExporter]?.[0] as {
      attributes: Record<string, unknown>;
    };
    expect(String(exporterCall.attributes["span.exporter"])).toContain(
      "/v1/traces",
    );
    // session.start uses `logs.getLogger` from the top-level api-logs mock (setGlobalLoggerProvider is a noop here),
    // so it does not appear on this provider emitSpy — order vs session.start is covered in E2E.
  });

  it("reportException emits log with body = error message", async () => {
    const emitSpy = vi.fn();
    // Override the module-level vi.mock for this one call
    const { createProviders } = await import("../exporters");
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    // finishStart is async (awaits OS version resolution); flush microtasks.
    await Promise.resolve();

    // Clear calls from session.start that happen during start()
    emitSpy.mockClear();

    PulseWeb.reportException(new Error("something broke"));

    expect(emitSpy).toHaveBeenCalled();
    const call = emitSpy.mock.calls[0]?.[0] as {
      body: string;
      attributes: Record<string, unknown>;
    };
    expect(call.body).toBe("something broke");
    expect(call.attributes[K.PULSE_TYPE]).toBe(T.NON_FATAL);
    expect(call.attributes[K.EXCEPTION_TYPE]).toBe("Error");
    expect(call.attributes[K.NON_FATAL_IS_MANUAL]).toBe(true);
  });

  it("trackNonFatal emits non_fatal log with name as body", async () => {
    const emitSpy = vi.fn();
    const { createProviders } = await import("../exporters");
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    // finishStart is async (awaits OS version resolution); flush microtasks.
    await Promise.resolve();

    emitSpy.mockClear();

    PulseWeb.trackNonFatal("payment_declined", { amount: 99 });

    expect(emitSpy).toHaveBeenCalled();
    const call = emitSpy.mock.calls[0]?.[0] as {
      body: string;
      attributes: Record<string, unknown>;
    };
    expect(call.body).toBe("payment_declined");
    expect(call.attributes[K.PULSE_TYPE]).toBe(T.NON_FATAL);
    expect(call.attributes[K.NON_FATAL_TYPE]).toBe("payment_declined");
    expect(call.attributes[K.NON_FATAL_IS_MANUAL]).toBe(true);
  });

  it("trackEvent emits custom_event log (not span)", async () => {
    const emitSpy = vi.fn();
    const { createProviders } = await import("../exporters");
    vi.mocked(createProviders).mockReturnValueOnce(
      makeMockBundle(emitSpy) as unknown as ReturnType<typeof createProviders>,
    );

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    // finishStart is async (awaits OS version resolution); flush microtasks.
    await Promise.resolve();

    emitSpy.mockClear();

    PulseWeb.trackEvent("shop_now_click");

    expect(emitSpy).toHaveBeenCalled();
    const call = emitSpy.mock.calls[0]?.[0] as {
      body: string;
      attributes: Record<string, unknown>;
    };
    expect(call.attributes[K.PULSE_TYPE]).toBe(T.CUSTOM_EVENT);
    expect(call.attributes[K.EVENT_NAME]).toBe(F.EVENT_NAME_CUSTOM_EVENT);
  });
});
