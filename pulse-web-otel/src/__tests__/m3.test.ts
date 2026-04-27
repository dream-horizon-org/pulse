import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Polyfill PromiseRejectionEvent for jsdom (not defined by default)
if (typeof PromiseRejectionEvent === "undefined") {
  (globalThis as unknown as Record<string, unknown>)["PromiseRejectionEvent"] =
    class PromiseRejectionEvent extends Event {
      readonly promise: Promise<unknown>;
      readonly reason: unknown;
      constructor(type: string, init: { promise: Promise<unknown>; reason?: unknown }) {
        super(type);
        this.promise = init.promise;
        this.reason = init.reason;
      }
    };
}

// Mock @opentelemetry/api-logs before importing the instrumentation
const mockEmit = vi.fn();
vi.mock("@opentelemetry/api-logs", () => ({
  logs: { getLogger: () => ({ emit: mockEmit }) },
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

const mockSdk = {
  logger: { emit: vi.fn() },
  tracer: {},
  config: {},
  sessionProvider: {},
  globalAttrsProcessor: {},
} as unknown as SdkContext;

describe("ErrorInstrumentation", () => {
  let instr: ErrorInstrumentation;

  beforeEach(() => {
    mockEmit.mockClear();
    instr = new ErrorInstrumentation();
    instr.install(mockSdk);
  });

  afterEach(() => {
    instr.uninstall();
  });

  describe("device.crash on window error event", () => {
    it("emits device.crash log on window error event", () => {
      window.dispatchEvent(
        new ErrorEvent("error", {
          message: "ReferenceError: foo",
          filename: "app.js",
          lineno: 42,
          colno: 5,
          error: new ReferenceError("foo"),
        }),
      );

      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.severityNumber).toBe(SeverityNumber.FATAL);
      expect(call.severityText).toBe("FATAL");
      expect(call.attributes).toMatchObject({
        "pulse.type": "device.crash",
        "exception.type": "ReferenceError",
        "error.lineno": 42,
        "error.colno": 5,
      });
      // non_fatal.is_manual must NOT be present on device.crash
      expect(
        (call.attributes as Record<string, unknown>)["non_fatal.is_manual"],
      ).toBeUndefined();
    });
  });

  describe("cross-origin filter", () => {
    it("skips cross-origin script errors", () => {
      window.dispatchEvent(
        new ErrorEvent("error", { message: "Script error." }),
      );
      expect(mockEmit).not.toHaveBeenCalled();
    });
  });

  describe("deduplication", () => {
    it("deduplicates same error within 5s", () => {
      const errEvent = new ErrorEvent("error", {
        message: "same error",
        filename: "app.js",
        lineno: 1,
        colno: 1,
        error: new Error("same error"),
      });
      window.dispatchEvent(errEvent);
      window.dispatchEvent(errEvent);
      window.dispatchEvent(errEvent);
      expect(mockEmit).toHaveBeenCalledTimes(1);
    });

    it("allows same error after 5s window", () => {
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

  describe("non_fatal on unhandled promise rejection", () => {
    it("emits non_fatal on unhandled promise rejection", () => {
      const p = Promise.reject(new TypeError("Cannot read"));
      p.catch(() => undefined); // prevent unhandled rejection noise
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: new TypeError("Cannot read"),
        }),
      );
      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.severityNumber).toBe(SeverityNumber.WARN);
      expect(call.attributes).toMatchObject({
        "pulse.type": "non_fatal",
        "non_fatal.is_manual": false,
      });
    });

    it("wraps string rejection reason in Error", () => {
      const p = Promise.reject("plain string");
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: "plain string",
        }),
      );
      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(call.attributes).toMatchObject({
        "exception.type": "Error",
        "exception.message": "plain string",
      });
    });

    it("wraps null/undefined rejection reason", () => {
      const p = Promise.reject(undefined);
      p.catch(() => undefined);
      window.dispatchEvent(
        new PromiseRejectionEvent("unhandledrejection", {
          promise: p,
          reason: undefined,
        }),
      );
      expect(mockEmit).toHaveBeenCalledOnce();
      const call = mockEmit.mock.calls[0]![0] as Record<string, unknown>;
      expect(
        (call.attributes as Record<string, unknown>)["exception.message"],
      ).toBe("Unknown rejection");
    });
  });

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
