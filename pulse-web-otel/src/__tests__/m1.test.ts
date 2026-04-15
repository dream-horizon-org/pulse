import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { getOrCreateInstallationId, SessionProvider } from "../session";
import { validateConfig } from "../config";
import {
  buildResource,
  extractProjectId,
  computeAspectRatio,
} from "../resource";
import {
  SdkConfigFetcher,
  DEFAULT_SDK_CONFIG,
  resolveConfigUrl,
} from "../remote-config";
import { FeatureGate } from "../feature-gate";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import { SessionInstrumentation } from "../instrumentations/session";
import { logs as otelLogs } from "@opentelemetry/api-logs";
import type { PulseWebConfig } from "../config";
import type { PulseSdkConfig } from "../remote-config";
import type { SdkContext } from "../instrumentation-registry";

// Mock @opentelemetry/api-logs — include ALL methods used by the real SDK so that
// SDK singleton tests (which call logs.setGlobalLoggerProvider) still work.
// SessionInstrumentation tests override getLogger per-test via mockReturnValue.
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

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

function makeConfig(overrides: Partial<PulseWebConfig> = {}): PulseWebConfig {
  return {
    endpointBaseUrl: "https://collector.example.com",
    apiKey: "proj_abc_supersecretkey",
    serviceName: "test-app",
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

    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: true }));

    expect(endEvents).toHaveLength(0);
    expect(sessionId).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// M1 — Config validation
// ---------------------------------------------------------------------------

describe("M1 — Config validation", () => {
  it("throws when endpointBaseUrl is missing", () => {
    expect(() => validateConfig(makeConfig({ endpointBaseUrl: "" }))).toThrow(
      "[PulseWeb] endpointBaseUrl is required",
    );
  });

  it("throws when apiKey is missing", () => {
    expect(() => validateConfig(makeConfig({ apiKey: "" }))).toThrow(
      "[PulseWeb] apiKey is required",
    );
  });

  it("throws when serviceName is missing", () => {
    expect(() => validateConfig(makeConfig({ serviceName: "" }))).toThrow(
      "[PulseWeb] serviceName is required",
    );
  });

  it("does not throw with all required fields", () => {
    expect(() => validateConfig(makeConfig())).not.toThrow();
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
    const resource = buildResource(makeConfig());
    expect(resource.attributes["platform"]).toBe("web");
  });

  it("includes rum.sdk.name=pulse_web_js", () => {
    const resource = buildResource(makeConfig());
    expect(resource.attributes["rum.sdk.name"]).toBe("pulse_web_js");
  });

  it("includes service.name from config", () => {
    const resource = buildResource(makeConfig({ serviceName: "my-shop" }));
    expect(resource.attributes["service.name"]).toBe("my-shop");
  });

  it("extracts project.id from api key", () => {
    const config = makeConfig({ apiKey: "proj_abc123_secrettoken" });
    const resource = buildResource(config);
    expect(resource.attributes["project.id"]).toBe("proj_abc123");
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
    expect(PulseWeb.isInitialized()).toBe(true);

    // Second call should be no-op
    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);
  });

  it("shutdown() allows re-initialization after complete", async () => {
    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);

    await PulseWeb.shutdown();
    expect(PulseWeb.isInitialized()).toBe(false);

    // Should be able to re-initialize
    PulseWeb.start(config);
    expect(PulseWeb.isInitialized()).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// M1 — resolveConfigUrl
// ---------------------------------------------------------------------------

describe("M1 — resolveConfigUrl", () => {
  it("replaces :4318 with :8080 when no explicit configEndpointUrl", () => {
    expect(resolveConfigUrl(undefined, "http://localhost:4318")).toBe(
      "http://localhost:8080/v1/configs/active/",
    );
  });

  it("uses explicit configEndpointUrl as-is when provided", () => {
    expect(
      resolveConfigUrl(
        "https://api.example.com/v1/configs/active/",
        "http://localhost:4318",
      ),
    ).toBe("https://api.example.com/v1/configs/active/");
  });

  it("leaves non-4318 URLs unchanged", () => {
    expect(resolveConfigUrl(undefined, "https://ingest.pulse.io")).toBe(
      "https://ingest.pulse.io/v1/configs/active/",
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

  it("returns false when sessionSampleRate is 0", () => {
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

  it("session ID lives in sessionStorage, not localStorage", () => {
    const provider = new SessionProvider();
    currentProvider = provider;
    const id = provider.getSessionId();

    expect(window.sessionStorage.getItem("pulse_session_id")).toBe(id);
    expect(window.localStorage.getItem("pulse_session_id")).toBeNull();
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

  it("shutdown() clears session from sessionStorage", () => {
    const provider = new SessionProvider();
    // Don't assign currentProvider here — the test itself calls shutdown()
    provider.getSessionId();
    expect(window.sessionStorage.getItem("pulse_session_id")).toBeTruthy();

    provider.shutdown();
    expect(window.sessionStorage.getItem("pulse_session_id")).toBeNull();
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

    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));
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

    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: true }));
    expect(endEvents).toHaveLength(0);
  });

  it("session.end includes correct session.id and positive durationMs", () => {
    const provider = new SessionProvider();
    // Don't assign currentProvider — test calls shutdown() itself
    const sessionId = provider.getSessionId();

    let capturedEnd: { sessionId?: string; durationMs?: number } = {};
    provider.onSessionChange((e) => {
      if (e.type === "end") {
        capturedEnd = { sessionId: e.sessionId, durationMs: e.durationMs };
      }
    });

    vi.advanceTimersByTime(3000);
    provider.shutdown();

    expect(capturedEnd.sessionId).toBe(sessionId);
    expect(capturedEnd.durationMs).toBeGreaterThanOrEqual(0);
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
    const resource = buildResource(makeConfig());
    expect(typeof resource.attributes["rum.sdk.version"]).toBe("string");
    expect(
      (resource.attributes["rum.sdk.version"] as string).length,
    ).toBeGreaterThan(0);
  });

  it("service.version defaults to 0.0.0 when not provided", () => {
    const resource = buildResource(makeConfig());
    expect(resource.attributes["service.version"]).toBe("0.0.0");
  });

  it("service.version uses config value when provided", () => {
    const resource = buildResource(makeConfig({ serviceVersion: "2.3.1" }));
    expect(resource.attributes["service.version"]).toBe("2.3.1");
  });

  it("installation.id is present and matches UUID v4 format", () => {
    const resource = buildResource(makeConfig());
    const id = resource.attributes["installation.id"] as string;
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it("installation.id in resource matches getOrCreateInstallationId()", () => {
    const expected = getOrCreateInstallationId();
    const resource = buildResource(makeConfig());
    expect(resource.attributes["installation.id"]).toBe(expected);
  });

  it("browser.name is a non-empty string", () => {
    const resource = buildResource(makeConfig());
    const name = resource.attributes["browser.name"] as string;
    expect(typeof name).toBe("string");
    expect(name.length).toBeGreaterThan(0);
  });

  it("device.type is one of desktop | mobile | tablet", () => {
    const resource = buildResource(makeConfig());
    expect(["desktop", "mobile", "tablet"]).toContain(
      resource.attributes["device.type"],
    );
  });

  it("screen.resolution is in WxH format", () => {
    const resource = buildResource(makeConfig());
    const res = resource.attributes["screen.resolution"] as string;
    expect(res).toMatch(/^\d+x\d+$/);
  });

  it("screen.aspect_ratio is in W:H format", () => {
    const resource = buildResource(makeConfig());
    const ratio = resource.attributes["screen.aspect_ratio"] as string;
    expect(ratio).toMatch(/^\d+:\d+$/);
  });

  it("browser.language is a non-empty string", () => {
    const resource = buildResource(makeConfig());
    const lang = resource.attributes["browser.language"] as string;
    expect(typeof lang).toBe("string");
    expect(lang.length).toBeGreaterThan(0);
  });

  it("timezone is a non-empty string", () => {
    const resource = buildResource(makeConfig());
    const tz = resource.attributes["timezone"] as string;
    expect(typeof tz).toBe("string");
    expect(tz.length).toBeGreaterThan(0);
  });

  it("apiKey without proj_ prefix → project.id falls back to raw apiKey", () => {
    const config = makeConfig({ apiKey: "raw_key_without_prefix" });
    const resource = buildResource(config);
    expect(resource.attributes["project.id"]).toBe("raw_key_without_prefix");
  });

  it("extractProjectId: proj_abc_secret → proj_abc", () => {
    expect(extractProjectId("proj_abc_supersecret")).toBe("proj_abc");
  });

  it("extractProjectId: no prefix → returns full key", () => {
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

    expect(attrs["session.id"]).toBe(sessionId);
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

    expect(attrs["installation.id"]).toBe(installId);
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
    expect(attrs["platform"]).toBe("web");
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
    expect(attrs["url.path"]).toBe(window.location.pathname);
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
    expect(attrs["page.url"]).toBe(window.location.href);
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
    expect(attrs["session.id"]).toBe(sessionId);
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
    expect(attrs["screen.name"]).toBe("checkout");
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
    const rotationProcessor = new PulseGlobalAttributesProcessor(rotationProvider, makeConfig());

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

    expect(attrs["session.id"]).toBe(secondSessionId);
    expect(attrs["session.id"]).not.toBe(firstSessionId);

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
    vi.mocked(otelLogs.getLogger).mockReturnValue({
      emit: vi.fn((record: { body: unknown; attributes?: Record<string, unknown> }) => {
        captured.push({ body: record.body, attributes: record.attributes ?? {} });
      }),
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

    const starts = captured.filter((l) => l.attributes["pulse.type"] === "session.start");
    expect(starts).toHaveLength(1);
  });

  it("session.start has body=session.start", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find((l) => l.attributes["pulse.type"] === "session.start");
    expect(startLog?.body).toBe("session.start");
  });

  it("session.start carries non-empty session.id", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find((l) => l.attributes["pulse.type"] === "session.start");
    expect(startLog?.attributes["session.id"]).toBeTruthy();
  });

  it("session.start session.id matches the active session ID", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const activeSessionId = sessionProvider.getSessionId();
    const startLog = captured.find((l) => l.attributes["pulse.type"] === "session.start");
    expect(startLog?.attributes["session.id"]).toBe(activeSessionId);
  });

  it("session.start carries session.start_reason = sdk_init on first start", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find((l) => l.attributes["pulse.type"] === "session.start");
    expect(startLog?.attributes["session.start_reason"]).toBe("sdk_init");
  });

  it("session.start carries empty session.previous_id on first start", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const startLog = captured.find((l) => l.attributes["pulse.type"] === "session.start");
    expect(startLog?.attributes["session.previous_id"]).toBe("");
  });

  it("emits session.end on pagehide (persisted=false)", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    // Clear start log so we can isolate the end log
    captured.length = 0;
    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));

    const endLog = captured.find((l) => l.attributes["pulse.type"] === "session.end");
    expect(endLog).toBeDefined();
    expect(endLog?.body).toBe("session.end");
  });

  it("session.end carries non-negative session.duration_ms", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    vi.advanceTimersByTime(5000);
    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));

    const endLog = captured.find((l) => l.attributes["pulse.type"] === "session.end");
    expect(endLog?.attributes["session.duration_ms"]).toBeGreaterThanOrEqual(0);
  });

  it("session.end carries the correct session.id", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const activeId = sessionProvider.getSessionId();

    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: false }));

    const endLog = captured.find((l) => l.attributes["pulse.type"] === "session.end");
    expect(endLog?.attributes["session.id"]).toBe(activeId);
  });

  it("does NOT emit session.end on BFCache pagehide (persisted=true)", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));
    captured.length = 0;

    window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: true }));

    const endLog = captured.find((l) => l.attributes["pulse.type"] === "session.end");
    expect(endLog).toBeUndefined();
  });

  it("normal session: exactly 1 start + 0 ends while tab is open", () => {
    const captured = makeCapture();
    const sessionProvider = new SessionProvider();
    currentProvider = sessionProvider;
    const instr = new SessionInstrumentation();
    instr.install(makeFakeSdk(sessionProvider));

    const starts = captured.filter((l) => l.attributes["pulse.type"] === "session.start");
    const ends = captured.filter((l) => l.attributes["pulse.type"] === "session.end");

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

    const types = captured.map((l) => l.attributes["pulse.type"]);
    expect(types).toContain("session.end");
    expect(types).toContain("session.start");
    expect(types.indexOf("session.end")).toBeLessThan(types.indexOf("session.start"));
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

    const rotationStart = captured.find((l) => l.attributes["pulse.type"] === "session.start");
    expect(rotationStart?.attributes["session.previous_id"]).toBe(firstId);
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

    const starts = captured.filter((l) => l.attributes["pulse.type"] === "session.start");
    expect(starts).toHaveLength(1);
  });
});
