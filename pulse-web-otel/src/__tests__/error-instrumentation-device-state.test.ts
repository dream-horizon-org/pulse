// Polyfill PromiseRejectionEvent for jsdom (not defined by default in jsdom)
if (typeof PromiseRejectionEvent === "undefined") {
  (globalThis as unknown as Record<string, unknown>)["PromiseRejectionEvent"] =
    class PromiseRejectionEvent extends Event {
      readonly promise: Promise<unknown>;
      readonly reason: unknown;
      constructor(
        type: string,
        init: { promise: Promise<unknown>; reason?: unknown },
      ) {
        super(type);
        this.promise = init.promise;
        this.reason = init.reason;
      }
    };
}

/**
 * Unit tests for ErrorInstrumentation device state capture:
 *   - navigator.getBattery() → battery.percent stamped on device.crash
 *   - navigator.storage.estimate() → storage.free_bytes stamped on device.crash
 *   - battery levelchange listener → updates in-flight percent
 *   - uninstall() removes battery listener
 *   - graceful degradation when APIs not available
 *
 * Positive cases: battery/storage captured on crash, levelchange updates value
 * Negative cases: getBattery() missing, getBattery() rejects, storage.estimate() rejects,
 *                 non-Error rejection types (string, null, object)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { SdkContext } from "../instrumentation-registry";
import { ErrorInstrumentation } from "../instrumentations/errors";
import { PulseWebSemconv } from "../semconv";

// ─── Mock @opentelemetry/api-logs ─────────────────────────────────────────────
const mockEmit = vi.fn();
vi.mock("@opentelemetry/api-logs", () => ({
  logs: { getLogger: () => ({ emit: mockEmit }) },
  SeverityNumber: {
    UNSPECIFIED: 0, TRACE: 1, DEBUG: 5, INFO: 9, WARN: 13, ERROR: 17, FATAL: 21,
  },
}));
vi.mock("@opentelemetry/api", () => ({
  context: { active: () => ({}) },
}));

import { SeverityNumber } from "@opentelemetry/api-logs";

const mockSdk = {
  logger: { emit: mockEmit },
  tracer: {},
  config: {},
  sessionProvider: {},
  globalAttrsProcessor: {},
} as unknown as SdkContext;

// ─── Battery mock factory ─────────────────────────────────────────────────────
function makeBattery(level = 0.8) {
  const listeners: Record<string, (() => void)[]> = {};
  const battery = {
    level,
    addEventListener: vi.fn((type: string, cb: () => void) => {
      listeners[type] = listeners[type] ?? [];
      listeners[type].push(cb);
    }),
    removeEventListener: vi.fn((type: string, cb: () => void) => {
      listeners[type] = (listeners[type] ?? []).filter((l) => l !== cb);
    }),
    triggerLevelChange(newLevel: number) {
      battery.level = newLevel;
      for (const cb of listeners["levelchange"] ?? []) cb();
    },
  };
  return battery;
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("ErrorInstrumentation — battery capture", () => {
  let instr: ErrorInstrumentation;

  beforeEach(() => {
    mockEmit.mockClear();
    instr = new ErrorInstrumentation();
  });

  afterEach(() => {
    instr.uninstall();
    vi.restoreAllMocks();
  });

  it("stamps battery.percent on device.crash when getBattery() is available", async () => {
    const battery = makeBattery(0.75);
    Object.defineProperty(navigator, "getBattery", {
      value: vi.fn().mockResolvedValue(battery),
      configurable: true,
      writable: true,
    });

    instr.install(mockSdk);
    // Wait for async prefetch
    await new Promise((r) => setTimeout(r, 10));

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash",
        error: new Error("crash"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );

    const call = mockEmit.mock.calls[0]?.[0] as Record<string, unknown>;
    expect(call.attributes).toMatchObject({
      [PulseWebSemconv.AttributeKey.BATTERY_PERCENT]: 75,
    });
  });

  it("updates battery.percent when levelchange fires", async () => {
    const battery = makeBattery(0.9);
    Object.defineProperty(navigator, "getBattery", {
      value: vi.fn().mockResolvedValue(battery),
      configurable: true,
      writable: true,
    });

    instr.install(mockSdk);
    await new Promise((r) => setTimeout(r, 10));

    // Simulate battery draining
    battery.triggerLevelChange(0.5);

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash after drain",
        error: new Error("crash after drain"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );

    const call = mockEmit.mock.calls[0]?.[0] as Record<string, unknown>;
    expect(call.attributes).toMatchObject({
      [PulseWebSemconv.AttributeKey.BATTERY_PERCENT]: 50,
    });
  });

  it("uninstall() removes the levelchange listener", async () => {
    const battery = makeBattery(0.8);
    Object.defineProperty(navigator, "getBattery", {
      value: vi.fn().mockResolvedValue(battery),
      configurable: true,
      writable: true,
    });

    instr.install(mockSdk);
    await new Promise((r) => setTimeout(r, 10));

    instr.uninstall();

    expect(battery.removeEventListener).toHaveBeenCalledWith(
      "levelchange",
      expect.any(Function),
    );
  });

  it("does NOT stamp battery.percent when getBattery() is unavailable", async () => {
    // Remove getBattery
    const nav = navigator as Navigator & { getBattery?: unknown };
    const original = nav.getBattery;
    delete nav.getBattery;

    instr.install(mockSdk);
    await new Promise((r) => setTimeout(r, 10));

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash no battery",
        error: new Error("crash no battery"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );

    const attrs = (mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
    expect(attrs[PulseWebSemconv.AttributeKey.BATTERY_PERCENT]).toBeUndefined();

    // Restore
    if (original !== undefined) nav.getBattery = original;
  });

  it("does not crash when getBattery() promise rejects", async () => {
    Object.defineProperty(navigator, "getBattery", {
      value: vi.fn().mockRejectedValue(new Error("not supported")),
      configurable: true,
      writable: true,
    });

    instr.install(mockSdk);
    // Should not throw
    await expect(new Promise((r) => setTimeout(r, 10))).resolves.not.toThrow();

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash",
        error: new Error("crash"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );
    // battery.percent should be undefined — no crash on missing attr
    const attrs = (mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
    expect(attrs[PulseWebSemconv.AttributeKey.BATTERY_PERCENT]).toBeUndefined();
  });
});

describe("ErrorInstrumentation — storage capture", () => {
  let instr: ErrorInstrumentation;

  beforeEach(() => {
    mockEmit.mockClear();
    // Remove getBattery to isolate storage tests
    const nav = navigator as Navigator & { getBattery?: unknown };
    delete nav.getBattery;
  });

  afterEach(() => {
    instr.uninstall();
    vi.restoreAllMocks();
  });

  it("stamps storage.free_bytes on device.crash when storage.estimate() is available", async () => {
    Object.defineProperty(navigator, "storage", {
      value: { estimate: vi.fn().mockResolvedValue({ quota: 1_000_000, usage: 300_000 }) },
      configurable: true,
    });

    // Create and install AFTER mock is set so prefetchDeviceState reads the mock
    instr = new ErrorInstrumentation();
    instr.install(mockSdk);
    await new Promise((r) => setTimeout(r, 10));

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash",
        error: new Error("crash"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );

    const attrs = (mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
    expect(attrs[PulseWebSemconv.AttributeKey.STORAGE_FREE]).toBe(700_000);
  });

  it("does NOT stamp storage.free_bytes when storage API is unavailable", async () => {
    // storage exists but no estimate
    Object.defineProperty(navigator, "storage", {
      value: {},
      configurable: true,
    });

    instr = new ErrorInstrumentation();
    instr.install(mockSdk);
    await new Promise((r) => setTimeout(r, 10));

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash",
        error: new Error("crash"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );

    const attrs = (mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
    expect(attrs[PulseWebSemconv.AttributeKey.STORAGE_FREE]).toBeUndefined();
  });

  it("does not crash when storage.estimate() rejects", async () => {
    Object.defineProperty(navigator, "storage", {
      value: { estimate: vi.fn().mockRejectedValue(new Error("quota API unavailable")) },
      configurable: true,
    });

    instr = new ErrorInstrumentation();
    instr.install(mockSdk);
    await expect(new Promise((r) => setTimeout(r, 10))).resolves.not.toThrow();

    window.dispatchEvent(
      new ErrorEvent("error", {
        message: "crash",
        error: new Error("crash"),
        filename: "app.js",
        lineno: 1,
        colno: 1,
      }),
    );
    const attrs = (mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
    expect(attrs[PulseWebSemconv.AttributeKey.STORAGE_FREE]).toBeUndefined();
  });
});

describe("ErrorInstrumentation — non-Error rejection types", () => {
  let instr: ErrorInstrumentation;

  beforeEach(() => {
    mockEmit.mockClear();
    instr = new ErrorInstrumentation();
    instr.install(mockSdk);
  });

  afterEach(() => {
    instr.uninstall();
  });

  it("handles string rejection", async () => {
    // Suppress unhandled rejection noise — PromiseRejectionEvent holds a real rejected promise
    const p1 = Promise.reject("string rejection");
    p1.catch(() => {});
    window.dispatchEvent(
      new PromiseRejectionEvent("unhandledrejection", {
        promise: p1,
        reason: "string rejection",
      }),
    );
    await Promise.resolve(); // flush microtasks

    expect(mockEmit).toHaveBeenCalledOnce();
    const call = mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> };
    expect(call.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.NON_FATAL,
    );
  });

  it("handles null rejection without crashing", async () => {
    const p2 = Promise.reject(null);
    p2.catch(() => {});
    window.dispatchEvent(
      new PromiseRejectionEvent("unhandledrejection", {
        promise: p2,
        reason: null,
      }),
    );
    await Promise.resolve();

    expect(mockEmit).toHaveBeenCalledOnce();
  });

  it("handles plain-object rejection", async () => {
    const p3 = Promise.reject({ code: 42, msg: "bad" });
    p3.catch(() => {});
    window.dispatchEvent(
      new PromiseRejectionEvent("unhandledrejection", {
        promise: p3,
        reason: { code: 42, msg: "bad" },
      }),
    );
    await Promise.resolve();

    expect(mockEmit).toHaveBeenCalledOnce();
    const attrs = (mockEmit.mock.calls[0]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
    expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.NON_FATAL,
    );
  });

  it("has WARN severity for unhandled rejections", async () => {
    const p4 = Promise.reject(new Error("rej"));
    p4.catch(() => {});
    window.dispatchEvent(
      new PromiseRejectionEvent("unhandledrejection", {
        promise: p4,
        reason: new Error("rej"),
      }),
    );
    await Promise.resolve();

    const call = mockEmit.mock.calls[0]?.[0] as { severityNumber?: number };
    expect(call.severityNumber).toBe(SeverityNumber.WARN);
  });
});
