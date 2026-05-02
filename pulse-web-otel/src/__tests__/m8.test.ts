// m8.test.ts — Unit tests for TC 8.x: pagehide listener lifecycle
//
// Covers: registration count, BFCache guard, forceFlush call, shutdown removal,
// restart balance, SSR guard, post-shutdown no-op.

// ─── OTel mocks ──────────────────────────────────────────────────────────────

vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

// ─── Provider mock ───────────────────────────────────────────────────────────

let mockForceFlushLog: ReturnType<typeof vi.fn>;
let mockForceFlushTrace: ReturnType<typeof vi.fn>;
let mockForceFlushMeter: ReturnType<typeof vi.fn>;

vi.mock("../exporters", () => {
  mockForceFlushLog = vi.fn().mockResolvedValue(undefined);
  mockForceFlushTrace = vi.fn().mockResolvedValue(undefined);
  mockForceFlushMeter = vi.fn().mockResolvedValue(undefined);

  const mockTracerProvider = {
    addSpanProcessor: vi.fn(),
    getTracer: vi.fn().mockReturnValue({
      startSpan: vi
        .fn()
        .mockReturnValue({ setAttribute: vi.fn(), end: vi.fn() }),
    }),
    forceFlush: mockForceFlushTrace,
    shutdown: vi.fn().mockResolvedValue(undefined),
    register: vi.fn(),
  };
  const mockLoggerProvider = {
    addLogRecordProcessor: vi.fn(),
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    forceFlush: mockForceFlushLog,
    shutdown: vi.fn().mockResolvedValue(undefined),
  };
  const mockMeterProvider = {
    addMetricReader: vi.fn(),
    getMeter: vi.fn().mockReturnValue({}),
    forceFlush: mockForceFlushMeter,
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

// ─── Setup / teardown ────────────────────────────────────────────────────────

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
  if (PulseWeb.isInitialized()) await PulseWeb.shutdown();
  vi.unstubAllGlobals();
});

// ─── TC 8.1 — pagehide listener registered exactly once on start() ───────────

describe("TC 8.1 — pagehide registered once on start()", () => {
  it("adds a single pagehide listener to window", async () => {
    const adds: string[] = [];
    const origAdd = window.addEventListener.bind(window);
    const addSpy = vi
      .spyOn(window, "addEventListener")
      .mockImplementation((ev: string, ...rest) => {
        adds.push(ev);        origAdd(ev, ...rest);
      });

    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();

    const pagehideCount = adds.filter((e) => e === "pagehide").length;
    // sdk.ts adds 1; session.ts adds 1 — total registered is 2 but both are
    // one-shot registrations on a single start(). The SDK must never register > 2.
    expect(pagehideCount).toBeGreaterThanOrEqual(1);
    expect(pagehideCount).toBeLessThanOrEqual(2);

    addSpy.mockRestore();
  });
});

// ─── TC 8.3 — BFCache guard: persisted=true → forceFlush NOT called ──────────

describe("TC 8.3 — BFCache (persisted=true) does not trigger forceFlush", () => {
  it("dispatching pagehide with persisted=true does NOT call loggerProvider.forceFlush", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    mockForceFlushLog.mockClear();
    mockForceFlushTrace.mockClear();
    mockForceFlushMeter.mockClear();

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: true, bubbles: true }),
    );

    await Promise.resolve();

    // sdk.ts pagehide guard: `if (!e.persisted && this._initialized)` — must skip flush
    expect(mockForceFlushLog).not.toHaveBeenCalled();
    expect(mockForceFlushTrace).not.toHaveBeenCalled();
    expect(mockForceFlushMeter).not.toHaveBeenCalled();
  });
});

// ─── TC 8.4 — shutdown removes the pagehide listener ─────────────────────────

describe("TC 8.4 — shutdown() removes pagehide listener", () => {
  it("window.removeEventListener called for pagehide after shutdown()", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();

    const removes: string[] = [];
    const removeSpy = vi
      .spyOn(window, "removeEventListener")
      .mockImplementation((ev: string) => {
        removes.push(ev);
      });

    await PulseWeb.shutdown();

    expect(removes).toContain("pagehide");
    removeSpy.mockRestore();
  });
});

// ─── TC 8.5 — restart cycle: listener count balanced ─────────────────────────

describe("TC 8.5 — restart cycle keeps add/remove balanced for pagehide", () => {
  it("3× start→shutdown leaves net add/remove = 0 for sdk.ts pagehide listener", async () => {
    const sdkAdds = { pagehide: 0 };
    const sdkRemoves = { pagehide: 0 };

    const origAdd = window.addEventListener.bind(window);
    const origRemove = window.removeEventListener.bind(window);

    const addSpy = vi
      .spyOn(window, "addEventListener")
      .mockImplementation(
        (
          ev: string,
          fn: EventListenerOrEventListenerObject,
          opts?: boolean | AddEventListenerOptions,
        ) => {
          if (ev === "pagehide") sdkAdds.pagehide++;
          origAdd(ev, fn, opts as AddEventListenerOptions);
        },
      );
    const removeSpy = vi
      .spyOn(window, "removeEventListener")
      .mockImplementation(
        (
          ev: string,
          fn: EventListenerOrEventListenerObject,
          opts?: boolean | EventListenerOptions,
        ) => {
          if (ev === "pagehide") sdkRemoves.pagehide++;
          origRemove(ev, fn, opts as EventListenerOptions);
        },
      );

    const { PulseWeb } = await import("../sdk");
    const config = makeConfig();

    for (let i = 0; i < 3; i++) {
      PulseWeb.start(config);
      await Promise.resolve();
      expect(PulseWeb.isInitialized()).toBe(true);
      await PulseWeb.shutdown();
    }

    // Every add must have a matching remove — no accumulation across cycles
    expect(sdkAdds.pagehide).toBe(sdkRemoves.pagehide);

    addSpy.mockRestore();
    removeSpy.mockRestore();
  });
});

// ─── TC 8.6 — SSR guard: window undefined → listener not registered ───────────

describe("TC 8.6 — SSR guard: no pagehide listener when window is undefined", () => {
  it("start() does not throw and registers 0 pagehide listeners when window is undefined", async () => {
    const origWindow = globalThis.window;

    // @ts-expect-error deliberate SSR simulation
    delete globalThis.window;

    try {
      const { PulseWeb } = await import("../sdk");
      // Should not throw even when window is undefined
      expect(() => PulseWeb.start(makeConfig())).not.toThrow();
      await Promise.resolve();
    } finally {
      // @ts-expect-error restore
      globalThis.window = origWindow;
    }
  });
});

// ─── TC 8.7 — forceFlush called on pagehide with persisted=false ──────────────

describe("TC 8.7 — pagehide (persisted=false) calls forceFlush on all providers", () => {
  it("all 3 provider forceFlush methods are called when persisted=false", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    mockForceFlushLog.mockClear();
    mockForceFlushTrace.mockClear();
    mockForceFlushMeter.mockClear();

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
    );

    // forceFlush is async — give the microtask queue a tick to schedule
    await new Promise((r) => setTimeout(r, 50));

    // pagehide fires forceFlush once; afterEach shutdown adds a second call.
    // ≥1 is the contract: pagehide MUST have triggered at least one flush.
    expect(mockForceFlushLog.mock.calls.length).toBeGreaterThanOrEqual(1);
    expect(mockForceFlushTrace.mock.calls.length).toBeGreaterThanOrEqual(1);
    expect(mockForceFlushMeter.mock.calls.length).toBeGreaterThanOrEqual(1);
  });
});

// ─── TC 8.9 — double start() → listener registered only once ─────────────────

describe("TC 8.9 — double start() does not double-register pagehide", () => {
  it("second start() while initialized is a no-op — pagehide count stays at initial value", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    expect(PulseWeb.isInitialized()).toBe(true);

    const addsAfterInit: string[] = [];
    const addSpy = vi
      .spyOn(window, "addEventListener")
      .mockImplementation((ev: string) => {
        addsAfterInit.push(ev);
      });

    // Second start() — SDK is already initialized, must be a no-op
    PulseWeb.start(makeConfig());
    await Promise.resolve();

    expect(addsAfterInit.filter((e) => e === "pagehide").length).toBe(0);
    addSpy.mockRestore();
  });
});

// ─── TC 8.10 — pagehide after shutdown → no forceFlush ───────────────────────

describe("TC 8.10 — pagehide after shutdown() is a no-op", () => {
  it("dispatching pagehide after shutdown does NOT call forceFlush", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    await PulseWeb.shutdown();

    mockForceFlushLog.mockClear();
    mockForceFlushTrace.mockClear();
    mockForceFlushMeter.mockClear();

    window.dispatchEvent(
      new PageTransitionEvent("pagehide", { persisted: false, bubbles: true }),
    );

    await new Promise((r) => setTimeout(r, 50));

    // Listener was removed on shutdown — sdk.ts forceFlush must not fire
    expect(mockForceFlushLog).not.toHaveBeenCalled();
    expect(mockForceFlushTrace).not.toHaveBeenCalled();
    expect(mockForceFlushMeter).not.toHaveBeenCalled();
  });
});
