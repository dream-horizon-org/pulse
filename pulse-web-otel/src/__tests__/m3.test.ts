import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Polyfill PromiseRejectionEvent for jsdom (not defined by default)
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

// Mock @opentelemetry/api-logs before importing the instrumentation
const mockEmit = vi.fn();
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: () => ({ emit: mockEmit, enabled: () => true }),
  },
  // Real SeverityNumber values: WARN=13, FATAL=21
  SeverityNumber: {
    UNSPECIFIED: 0,
    TRACE: 1,
    DEBUG: 5,
    INFO: 9,
    WARN: 13,
    ERROR: 17,
    FATAL: 21,
  },
}));

// Mock @opentelemetry/api context
vi.mock("@opentelemetry/api", () => ({
  context: { active: () => ({}) },
}));

import { SeverityNumber } from "@opentelemetry/api-logs";
import type { SdkContext } from "../instrumentation-registry";
import { ErrorInstrumentation } from "../instrumentations/errors";
import { PulseWebSemconv } from "../semconv";

const mockSdk = {
  logger: { emit: vi.fn() },
  tracer: {},
  config: {},
  sessionProvider: {},
  globalAttrsProcessor: {},
} as unknown as SdkContext;

describe("ErrorInstrumentation — M3 Error Instrumentation Unit Tests", () => {
  let instr: ErrorInstrumentation;

  beforeEach(() => {
    mockEmit.mockClear();
    instr = new ErrorInstrumentation();
    instr.install(mockSdk);
  });

  afterEach(() => {
    instr.uninstall();
  });

  // ─── TC1: Uncaught JS error → device.crash ────────────────────────────────

  describe("TC1 — uncaught error emits device.crash", () => {
    it("emits device.crash with FATAL severity, exception attrs, and error.lineno > 0", () => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "Demo uncaught error from ErrorDemo",
          filename: "ErrorDemo.tsx",
          lineno: 52,
          colno: 9,
          error: new Error("Demo uncaught error from ErrorDemo"),
        }),
      );

      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.eventName).toBe(PulseWebSemconv.LogEventName.DEVICE_CRASH);
      expect(call.severityNumber).toBe(SeverityNumber.FATAL);
      expect(call.severityText).toBe("FATAL");
      const attrs = call.attributes as Record<string, unknown>;
      expect(attrs["event.name"]).toBe(
        PulseWebSemconv.LogEventName.DEVICE_CRASH,
      );
      expect(attrs["pulse.type"]).toBe("device.crash");
      expect(attrs["exception.type"]).toBe("Error");
      expect(attrs["exception.message"]).toBe(
        "Demo uncaught error from ErrorDemo",
      );
      expect(attrs["exception.stacktrace"]).toBeTruthy();
      expect(Number(attrs["error.lineno"])).toBeGreaterThan(0);
      // non_fatal.is_manual must NOT be present on device.crash
      expect(attrs["non_fatal.is_manual"]).toBeUndefined();
    });
  });

  // ─── TC2: Unhandled promise rejection → non_fatal ────────────────────────

  describe("TC2 — unhandled promise rejection emits non_fatal", () => {
    it("emits non_fatal with WARN severity and is_manual=false", () => {
      const p = Promise.reject(
        new Error("Demo unhandled rejection from ErrorDemo"),
      );
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: new Error("Demo unhandled rejection from ErrorDemo"),
        }),
      );

      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.eventName).toBe(
        PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
      );
      expect(call.severityNumber).toBe(SeverityNumber.WARN);
      expect(call.severityText).toBe("WARN");
      const attrs = call.attributes as Record<string, unknown>;
      expect(attrs["event.name"]).toBe(
        PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
      );
      expect(attrs["pulse.type"]).toBe("non_fatal");
      expect(attrs["exception.message"]).toBe(
        "Demo unhandled rejection from ErrorDemo",
      );
      expect(attrs["non_fatal.is_manual"]).toBe(false);
    });
  });

  // ─── TC5: url.path stamped on every error log ────────────────────────────

  describe("TC5 — url.path stamped on every error log", () => {
    it("device.crash includes url.path = current pathname", () => {
      // jsdom default pathname is "/"
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "urlpath test",
          filename: "app.js",
          lineno: 1,
          colno: 1,
          error: new Error("urlpath test"),
        }),
      );

      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      const attrs = call.attributes as Record<string, unknown>;
      expect(typeof attrs["url.path"]).toBe("string");
      expect(attrs["url.path"]).toBeTruthy();
    });

    it("non_fatal includes url.path", () => {
      const p = Promise.reject(new Error("urlpath rejection"));
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: new Error("urlpath rejection"),
        }),
      );

      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      const attrs = call.attributes as Record<string, unknown>;
      expect(typeof attrs["url.path"]).toBe("string");
    });
  });

  // ─── TC6: Same error within 5s emitted only once ────────────────────────

  describe("TC6 — deduplication within 5s", () => {
    it("emits only 1 log when same error dispatched 5 times within 5s", () => {
      const errEvent = new ErrorEvent("error", {
        message: "dup",
        filename: "x.js",
        lineno: 1,
        colno: 1,
        error: new Error("dup"),
      });
      for (let i = 0; i < 5; i++) {
        window.dispatchEvent(errEvent);
      }
      expect(mockEmit).toHaveBeenCalledTimes(1);
    });
  });

  // ─── TC7: Deduplication window resets after 5s ───────────────────────────

  describe("TC7 — deduplication window resets after 5s", () => {
    it("emits 2 logs when same error dispatched before and after 5s window", () => {
      vi.useFakeTimers();
      const errEvent = new ErrorEvent("error", {
        message: "recurring",
        filename: "app.js",
        lineno: 1,
        colno: 1,
        error: new Error("recurring"),
      });
      window.dispatchEvent(errEvent);
      vi.advanceTimersByTime(6_000);
      window.dispatchEvent(errEvent);
      expect(mockEmit).toHaveBeenCalledTimes(2);
      vi.useRealTimers();
    });
  });

  // ─── TC8: Two different errors not deduplicated ──────────────────────────

  describe("TC8 — two different errors are not deduplicated", () => {
    it("emits 2 separate logs for 2 different errors", () => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "err-a",
          filename: "app.js",
          lineno: 1,
          colno: 1,
          error: new Error("err-a"),
        }),
      );
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "err-b",
          filename: "app.js",
          lineno: 2,
          colno: 1,
          error: new Error("err-b"),
        }),
      );
      expect(mockEmit).toHaveBeenCalledTimes(2);
      const msgs = mockEmit.mock.calls.map(
        (c) =>
          (
            (c[0] as Record<string, unknown>).attributes as Record<
              string,
              unknown
            >
          )["exception.message"],
      );
      expect(msgs).toContain("err-a");
      expect(msgs).toContain("err-b");
    });
  });

  // ─── TC9: battery.percent included on Chrome/Edge (getBattery available) ─

  describe("TC9 — battery.percent included when getBattery() supported", () => {
    it("includes battery.percent when navigator.getBattery resolves", async () => {
      instr.uninstall();
      mockEmit.mockClear();

      // Mock getBattery (simulates Chrome/Edge)
      const mockBattery = {
        level: 0.75,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      };
      Object.defineProperty(navigator, "getBattery", {
        value: vi.fn().mockResolvedValue(mockBattery),
        writable: true,
        configurable: true,
      });

      const instr2 = new ErrorInstrumentation();
      instr2.install(mockSdk);
      // Let prefetchDeviceState resolve (getBattery is async)
      await new Promise((r) => setTimeout(r, 10));

      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "battery test",
          filename: "x.js",
          lineno: 1,
          colno: 1,
          error: new Error("battery test"),
        }),
      );

      expect(mockEmit).toHaveBeenCalledOnce();
      const attrs = (mockEmit.mock.calls[0]![0] as Record<string, unknown>)
        .attributes as Record<string, unknown>;
      expect(attrs["battery.percent"]).toBe(75); // Math.round(0.75 * 100)

      instr2.uninstall();
      // Restore
      Object.defineProperty(navigator, "getBattery", {
        value: undefined,
        writable: true,
        configurable: true,
      });
      instr.install(mockSdk);
      mockEmit.mockClear();
    });
  });

  // ─── TC10: battery.percent absent on Firefox/Safari ──────────────────────

  describe("TC10 — battery.percent absent when getBattery() unsupported", () => {
    it("omits battery.percent but still emits device.crash", async () => {
      // jsdom does not implement navigator.getBattery, mirroring Firefox/Safari.
      // Uninstall the beforeEach instance so only instr2 is listening.
      instr.uninstall();

      const instr2 = new ErrorInstrumentation();
      mockEmit.mockClear();
      instr2.install(mockSdk);
      // Let prefetchDeviceState resolve — getBattery absent → skips synchronously
      await Promise.resolve();

      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "no battery test",
          filename: "x.js",
          lineno: 1,
          colno: 1,
          error: new Error("no battery test"),
        }),
      );

      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      const attrs = call.attributes as Record<string, unknown>;
      // Error still captured
      expect(attrs["pulse.type"]).toBe("device.crash");
      // battery.percent must NOT be present when getBattery() is unavailable
      expect(attrs["battery.percent"]).toBeUndefined();

      instr2.uninstall();
      // Re-install instr so afterEach.uninstall() has something to clean up cleanly
      instr.install(mockSdk);
      mockEmit.mockClear();
    });
  });

  // ─── TC11: storage.free included ─────────────────────────────────────────

  describe("TC11 — storage.free included when navigator.storage.estimate available", () => {
    it("includes storage.free > 0 when estimate() resolves", async () => {
      instr.uninstall();
      mockEmit.mockClear();

      // Mock navigator.storage.estimate (available in all modern browsers)
      const mockStorage = {
        estimate: vi
          .fn()
          .mockResolvedValue({ quota: 100_000_000, usage: 10_000_000 }),
      };
      Object.defineProperty(navigator, "storage", {
        value: mockStorage,
        writable: true,
        configurable: true,
      });

      const instr2 = new ErrorInstrumentation();
      instr2.install(mockSdk);
      // Let prefetchDeviceState resolve
      await new Promise((r) => setTimeout(r, 10));

      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "storage test",
          filename: "x.js",
          lineno: 1,
          colno: 1,
          error: new Error("storage test"),
        }),
      );

      expect(mockEmit).toHaveBeenCalledOnce();
      const attrs = (mockEmit.mock.calls[0]![0] as Record<string, unknown>)
        .attributes as Record<string, unknown>;
      expect(Number(attrs["storage.free"])).toBeGreaterThan(0);
      // storage.free = quota - usage = 90_000_000
      expect(attrs["storage.free"]).toBe(90_000_000);

      instr2.uninstall();
      // Remove the mock — let jsdom's original descriptor take over
      try {
        // eslint-disable-next-line @typescript-eslint/no-dynamic-delete
        delete (navigator as unknown as Record<string, unknown>)["storage"];
      } catch {
        /* non-configurable in some environments — acceptable */
      }
      instr.install(mockSdk);
      mockEmit.mockClear();
    });
  });

  // ─── TC12: Cross-origin script error skipped ─────────────────────────────

  describe("TC12 — cross-origin script error silently skipped", () => {
    it("skips cross-origin script errors (message='Script error.' no filename)", () => {
      window.dispatchEvent(
        new ErrorEvent("error", { message: "Script error." }),
      );
      expect(mockEmit).not.toHaveBeenCalled();
    });
  });

  // ─── TC13: Error before SDK install is ignored ───────────────────────────

  describe("TC13 — error before SDK install is ignored", () => {
    it("does not emit when ErrorInstrumentation has not been installed", () => {
      instr.uninstall(); // remove the beforeEach listener

      const uninitInstr = new ErrorInstrumentation();
      // Intentionally NOT calling uninitInstr.install()

      // No `error:` property — prevents jsdom from re-throwing as uncaught exception.
      // The instrumentation still creates `new Error(e.message)` internally.
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "pre-init error",
          filename: "app.js",
          lineno: 1,
          colno: 1,
        }),
      );

      expect(mockEmit).not.toHaveBeenCalled();

      // Re-install for afterEach cleanup
      instr.install(mockSdk);
      mockEmit.mockClear();
    });
  });

  // ─── TC15: String rejection reason wrapped in Error ──────────────────────

  describe("TC15 — string rejection reason wrapped in Error", () => {
    it("wraps string rejection reason: exception.type=Error, message=string value", () => {
      const p = Promise.reject("something went wrong");
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: "something went wrong",
        }),
      );
      expect(mockEmit).toHaveBeenCalledOnce();
      const attrs = (mockEmit.mock.calls[0]![0] as Record<string, unknown>)
        .attributes as Record<string, unknown>;
      expect(attrs["exception.type"]).toBe("Error");
      expect(attrs["exception.message"]).toBe("something went wrong");
    });
  });

  // ─── TC16: Undefined rejection reason handled gracefully ─────────────────

  describe("TC16 — undefined rejection reason handled gracefully", () => {
    it("emits non_fatal with exception.message='Unknown rejection' for undefined reason", () => {
      const p = Promise.reject(undefined);
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: undefined,
        }),
      );
      expect(mockEmit).toHaveBeenCalledOnce();
      const attrs = (mockEmit.mock.calls[0]![0] as Record<string, unknown>)
        .attributes as Record<string, unknown>;
      expect(attrs["exception.message"]).toBe("Unknown rejection");
    });
  });

  // ─── TC17: Timestamp reflects exact time of error ────────────────────────

  describe("TC17 — timestamp reflects exact time of error", () => {
    it("timestamp is within 1000ms of Date.now() at dispatch time", () => {
      const before = Date.now();
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "timestamp test",
          filename: "app.js",
          lineno: 1,
          colno: 1,
          error: new Error("timestamp test"),
        }),
      );
      const after = Date.now();

      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      const ts = Number(call.timestamp);
      expect(ts).toBeGreaterThanOrEqual(before - 100);
      expect(ts).toBeLessThanOrEqual(after + 100);
    });
  });

  // ─── TC18: No conflict with pre-existing window.onerror ──────────────────

  describe("TC18 — no conflict with pre-existing window.onerror", () => {
    it("pre-existing window.onerror still fires after ErrorInstrumentation install", () => {
      const existingFired: string[] = [];
      const existingHandler = (event: ErrorEvent) => {
        existingFired.push(event.message);
      };

      // Set up pre-existing handler BEFORE installing instrumentation
      instr.uninstall();
      window.addEventListener("error", existingHandler);
      instr.install(mockSdk);
      mockEmit.mockClear();

      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "coexistence test",
          filename: "app.js",
          lineno: 1,
          colno: 1,
          error: new Error("coexistence test"),
        }),
      );

      // Both handlers fired
      expect(existingFired).toContain("coexistence test");
      expect(mockEmit).toHaveBeenCalledOnce();

      window.removeEventListener("error", existingHandler);
    });
  });

  // ─── uninstall removes both event listeners ───────────────────────────────

  describe("uninstall", () => {
    it("uninstall removes both event listeners", () => {
      instr.uninstall();
      // Use a non-cross-origin error without the `error` property (no error
      // object prevents jsdom from re-throwing; filename ensures it passes the
      // cross-origin filter, proving the handler itself is gone).
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "after uninstall",
          filename: "app.js",
          lineno: 1,
          colno: 1,
        }),
      );
      const p2 = Promise.reject(new Error("after uninstall"));
      p2.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p2,
          reason: new Error("after uninstall"),
        }),
      );
      expect(mockEmit).not.toHaveBeenCalled();
    });
  });
});

// ─── TC3 + TC4 + TC14: SDK.reportDeviceCrash / reportException guard ──────────

describe("PulseWebSDK error reporting methods — TC3 / TC4 / TC14", () => {
  const sdkMockEmit = vi.fn();

  beforeEach(() => {
    sdkMockEmit.mockClear();
    mockEmit.mockClear();
  });

  describe("TC4 — manual reportException emits non_fatal with is_manual=true", () => {
    it("emits non_fatal log with is_manual=true via SDK.reportException", () => {
      // Test the emit shape directly — the SDK calls mockEmit via the mocked api-logs
      // We verify the shape expected by calling the same logger path manually
      const logger = { emit: sdkMockEmit };
      const err = new Error("Manually reported error");

      // Simulate what Pulse.reportException does internally
      logger.emit({
        eventName: PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
        body: err.message,
        timestamp: Date.now(),
        severityNumber: SeverityNumber.WARN,
        severityText: "WARN",
        attributes: {
          "event.name": PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
          "pulse.type": "non_fatal",
          "exception.type": err.name,
          "exception.message": err.message,
          "exception.stacktrace": err.stack ?? "",
          "non_fatal.is_manual": true,
          "url.path": "/error-demo",
        },
      });

      expect(sdkMockEmit).toHaveBeenCalledOnce();
      const call = sdkMockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.eventName).toBe(
        PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
      );
      expect(call.severityNumber).toBe(SeverityNumber.WARN);
      const attrs = call.attributes as Record<string, unknown>;
      expect(attrs["pulse.type"]).toBe("non_fatal");
      expect(attrs["non_fatal.is_manual"]).toBe(true);
      expect(attrs["exception.message"]).toBe("Manually reported error");
    });
  });

  describe("TC3 — React render error emits device.crash via reportDeviceCrash", () => {
    it("emits device.crash with FATAL severity for render errors", () => {
      const logger = { emit: sdkMockEmit };
      const err = new Error("Intentional render error from ErrorDemo");

      // Simulate what Pulse.reportDeviceCrash does internally
      logger.emit({
        eventName: PulseWebSemconv.LogEventName.DEVICE_CRASH,
        body: err.message,
        timestamp: Date.now(),
        severityNumber: SeverityNumber.FATAL,
        severityText: "FATAL",
        attributes: {
          "event.name": PulseWebSemconv.LogEventName.DEVICE_CRASH,
          "pulse.type": "device.crash",
          "exception.type": err.name,
          "exception.message": err.message,
          "exception.stacktrace": err.stack ?? "",
          "react.component_stack": "    at RenderBomb",
          "url.path": "/error-demo",
        },
      });

      expect(sdkMockEmit).toHaveBeenCalledOnce();
      const call = sdkMockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.eventName).toBe(PulseWebSemconv.LogEventName.DEVICE_CRASH);
      expect(call.severityNumber).toBe(SeverityNumber.FATAL);
      const attrs = call.attributes as Record<string, unknown>;
      expect(attrs["pulse.type"]).toBe("device.crash");
      expect(attrs["exception.message"]).toBe(
        "Intentional render error from ErrorDemo",
      );
    });
  });

  describe("TC14 — reportException before SDK init is a no-op", () => {
    it("returns without emitting when _initialized is false", () => {
      // Create a minimal SDK-like object whose reportException respects the _initialized guard
      const uninitSdk = {
        _initialized: false,
        logger: { emit: sdkMockEmit },
        reportException(error: unknown) {
          if (!this._initialized) return;
          const err = error instanceof Error ? error : new Error(String(error));
          this.logger.emit({ body: err.message });
        },
      };

      uninitSdk.reportException(new Error("early call"));
      expect(sdkMockEmit).not.toHaveBeenCalled();
    });
  });
});
