// sdk-lifecycle.test.ts — Tests for SDK singleton lifecycle, shutdown guards,
// restart cycles, and the race condition between shutdown() and finishStart().

// Mock @opentelemetry/api-logs — same pattern as m1.test.ts
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

// Mock the exporters module — createProviders must return cleanup as vi.fn()
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
      tracerProvider: mockTracerProvider,
      loggerProvider: mockLoggerProvider,
      meterProvider: mockMeterProvider,
      cleanup: vi.fn(),
    }),
  };
});

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

// ---------------------------------------------------------------------------
// Shared beforeEach / afterEach — stub fetch + XHR, clear storage
// ---------------------------------------------------------------------------

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
  // Always attempt to shut down between tests to avoid singleton pollution.
  // Dynamic import gives us the same module instance without resetting it.
  const { PulseWeb } = await import("../sdk");
  if (PulseWeb.isInitialized()) {
    await PulseWeb.shutdown();
  }
  vi.unstubAllGlobals();
});

// ---------------------------------------------------------------------------
// Test 1 — shutdown() on uninitialized SDK returns silently
// ---------------------------------------------------------------------------

describe("SDK lifecycle — shutdown() before start()", () => {
  it("shutdown() on uninitialized SDK returns without error; isInitialized stays false", async () => {
    const { PulseWeb } = await import("../sdk");

    // Should not throw and should return cleanly
    await expect(PulseWeb.shutdown()).resolves.toBeUndefined();
    expect(PulseWeb.isInitialized()).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Test 2 — shutdown() resets _starting so restart works
// ---------------------------------------------------------------------------

describe("SDK lifecycle — shutdown during _starting resets state for restart", () => {
  it("start() → flush microtasks → shutdown() → start() again → isInitialized() true", async () => {
    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    PulseWeb.start(config);
    // Flush the microtask queue (finishStart awaits getOsVersionAsync)
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    await PulseWeb.shutdown();
    expect(PulseWeb.isInitialized()).toBe(false);

    // Restart — should work because _starting was reset by shutdown()
    PulseWeb.start(config);
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Test 3 — restart cycle × 3
// ---------------------------------------------------------------------------

describe("SDK lifecycle — 3× restart cycle never accumulates state", () => {
  it("start → shutdown → start → shutdown → start → each time isInitialized correct", async () => {
    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    for (let i = 0; i < 3; i++) {
      PulseWeb.start(config);
      await Promise.resolve();
      expect(PulseWeb.isInitialized()).toBe(true);

      await PulseWeb.shutdown();
      expect(PulseWeb.isInitialized()).toBe(false);
    }

    // Final start: ends initialised
    PulseWeb.start(config);
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Test 4 — cleanup() called on shutdown (pagehide listener removal)
// ---------------------------------------------------------------------------

describe("SDK lifecycle — cleanup() called on shutdown", () => {
  it("createProviders cleanup fn is called when shutdown() is called after start()", async () => {
    const { createProviders } = await import("../exporters");
    const cleanupFn = vi.fn();

    vi.mocked(createProviders).mockReturnValueOnce({
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
      } as never,
      loggerProvider: {
        addLogRecordProcessor: vi.fn(),
        getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
        forceFlush: vi.fn().mockResolvedValue(undefined),
        shutdown: vi.fn().mockResolvedValue(undefined),
      } as never,
      meterProvider: {
        addMetricReader: vi.fn(),
        getMeter: vi.fn().mockReturnValue({}),
        forceFlush: vi.fn().mockResolvedValue(undefined),
        shutdown: vi.fn().mockResolvedValue(undefined),
      } as never,
      cleanup: cleanupFn,
    });

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    await PulseWeb.shutdown();

    expect(cleanupFn).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// Test 5 — uninstallAll called on shutdown
// ---------------------------------------------------------------------------

describe("SDK lifecycle — uninstallAll() called on shutdown", () => {
  it("InstrumentationRegistry.uninstallAll called once after start() + shutdown()", async () => {
    const { InstrumentationRegistry } = await import(
      "../instrumentation-registry"
    );
    const uninstallAllSpy = vi.spyOn(
      InstrumentationRegistry.prototype,
      "uninstallAll",
    );

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    await PulseWeb.shutdown();

    expect(uninstallAllSpy).toHaveBeenCalledTimes(1);
    uninstallAllSpy.mockRestore();
  });
});

// ---------------------------------------------------------------------------
// Test 7 — all DOM listeners added during start() are removed during shutdown()
// Part C regression guard: every addEventListener must have a matching
// removeEventListener on shutdown(), else restart cycles stack listeners.
// ---------------------------------------------------------------------------

describe("SDK lifecycle — all DOM listeners removed on shutdown", () => {
  it("window + document add/remove counts balance for pagehide/pageshow/beforeunload/visibilitychange", async () => {
    const tracked = [
      "pagehide",
      "pageshow",
      "beforeunload",
      "visibilitychange",
    ] as const;
    type TrackedEvent = (typeof tracked)[number];
    const isTracked = (ev: string): ev is TrackedEvent =>
      (tracked as readonly string[]).includes(ev);

    const adds: Record<TrackedEvent, number> = {
      pagehide: 0,
      pageshow: 0,
      beforeunload: 0,
      visibilitychange: 0,
    };
    const removes: Record<TrackedEvent, number> = {
      pagehide: 0,
      pageshow: 0,
      beforeunload: 0,
      visibilitychange: 0,
    };

    const winAdd = vi
      .spyOn(window, "addEventListener")
      .mockImplementation((ev: string) => {
        if (isTracked(ev)) adds[ev]++;
      });
    const winRemove = vi
      .spyOn(window, "removeEventListener")
      .mockImplementation((ev: string) => {
        if (isTracked(ev)) removes[ev]++;
      });
    const docAdd = vi
      .spyOn(document, "addEventListener")
      .mockImplementation((ev: string) => {
        if (isTracked(ev)) adds[ev]++;
      });
    const docRemove = vi
      .spyOn(document, "removeEventListener")
      .mockImplementation((ev: string) => {
        if (isTracked(ev)) removes[ev]++;
      });

    try {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(makeConfig());
      await Promise.resolve();
      expect(PulseWeb.isInitialized()).toBe(true);

      // Sanity: start() actually registered the listeners we're tracking.
      expect(adds.pagehide).toBeGreaterThan(0);
      expect(adds.pageshow).toBeGreaterThan(0);
      expect(adds.beforeunload).toBeGreaterThan(0);
      expect(adds.visibilitychange).toBeGreaterThan(0);

      await PulseWeb.shutdown();

      // Every listener added must have been removed. Counts balance per event.
      expect(removes.pagehide).toBe(adds.pagehide);
      expect(removes.pageshow).toBe(adds.pageshow);
      expect(removes.beforeunload).toBe(adds.beforeunload);
      expect(removes.visibilitychange).toBe(adds.visibilitychange);
    } finally {
      winAdd.mockRestore();
      winRemove.mockRestore();
      docAdd.mockRestore();
      docRemove.mockRestore();
    }
  });
});

// ---------------------------------------------------------------------------
// Test 6 — shutdown during async init (race) — Bug 2
// ---------------------------------------------------------------------------

describe("SDK lifecycle — shutdown during async init race (Bug 2)", () => {
  it("_starting cleared and isInitialized stays false when shutdown fires mid-await", async () => {
    // Control the resolution of getOsVersionAsync
    let resolveOsVersion!: (v: string) => void;
    const osVersionPromise = new Promise<string>((resolve) => {
      resolveOsVersion = resolve;
    });

    vi.doMock("../utils/ua-parser", async (importOriginal) => {
      const original =
        await importOriginal<typeof import("../utils/ua-parser")>();
      return {
        ...original,
        getOsVersionAsync: vi.fn().mockReturnValue(osVersionPromise),
      };
    });

    // Get the already-loaded SDK singleton (same module, not re-isolated)
    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    // Start — finishStart will pause at await getOsVersionAsync
    PulseWeb.start(config);

    // At this point _starting=true, _initialized=false
    // Call shutdown() before the OS version resolves
    const shutdownPromise = PulseWeb.shutdown();

    // Now resolve the OS version — finishStart resumes but should see _shuttingDown=true
    resolveOsVersion("14");

    await shutdownPromise;

    // Give any remaining microtasks a chance to run
    await Promise.resolve();
    await Promise.resolve();

    // The SDK must NOT become initialized after shutdown was called mid-flight
    expect(PulseWeb.isInitialized()).toBe(false);

    vi.doUnmock("../utils/ua-parser");
  });
});
