/**
 * Unit tests for PulseWeb public SDK methods that had ZERO coverage:
 *   - trackEvent()
 *   - reportException()
 *   - reportDeviceCrash()
 *   - trackNonFatal()
 *   - setUserProperties() (merge semantics)
 *   - clearUserIdentity() (SDK-level, not just session helper)
 *   - setScreenName()
 *
 * Positive cases: correct pulse.type, correct attributes, correct severity
 * Negative cases: no-op before init, non-Error input coercion, null attrs
 */

const { emitFn } = vi.hoisted(() => ({ emitFn: vi.fn() }));

vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn(() => ({ emit: emitFn })),
    setGlobalLoggerProvider: vi.fn(),
  },
  SeverityNumber: {
    UNSPECIFIED: 0, TRACE: 1, DEBUG: 5, INFO: 9, WARN: 13, ERROR: 17, FATAL: 21,
  },
}));

vi.mock("../exporters", () => ({
  createProviders: vi.fn().mockReturnValue({
    tracerProvider: {
      addSpanProcessor: vi.fn(),
      getTracer: vi.fn().mockReturnValue({
        startSpan: vi.fn().mockReturnValue({ setAttribute: vi.fn(), end: vi.fn() }),
      }),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
      register: vi.fn(),
    },
    loggerProvider: {
      addLogRecordProcessor: vi.fn(),
      getLogger: vi.fn(() => ({ emit: emitFn })),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    meterProvider: {
      addMetricReader: vi.fn(),
      getMeter: vi.fn().mockReturnValue({}),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    idbSignalBuffer: null,
    cleanup: vi.fn(),
    prepareForDocumentUnload: vi.fn(),
  }),
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { PulseDataCollectionConsent } from "../config";
import { PulseWebSemconv } from "../semconv";
import {
  getPersistedUserId,
  getPersistedUserProperties,
  _resetInstallationStateForTesting,
} from "../session";

const BASE_CONFIG = {
  apiKey: "default-project_devkey01",
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
};

function emittedAttrs(callIdx = 0): Record<string, unknown> {
  return (emitFn.mock.calls[callIdx]?.[0] as { attributes?: Record<string, unknown> })?.attributes ?? {};
}

function emittedCall(callIdx = 0): Record<string, unknown> {
  return (emitFn.mock.calls[callIdx]?.[0] as Record<string, unknown>) ?? {};
}

// Follow user-identity.test.ts pattern — no vi.resetModules(), SDK imported once
describe("PulseWeb public SDK methods", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    _resetInstallationStateForTesting();
    emitFn.mockClear();
  });

  afterEach(async () => {
    const { PulseWeb } = await import("../sdk");
    if (PulseWeb.isInitialized()) await PulseWeb.shutdown();
    vi.unstubAllGlobals();
  });

  // ─── trackEvent ───────────────────────────────────────────────────────────

  describe("trackEvent()", () => {
    it("emits a log record with pulse.type = custom_event", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackEvent("button_click");

      const attrs = emittedAttrs();
      expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
        PulseWebSemconv.PulseType.CUSTOM_EVENT,
      );
    });

    it("uses the event name as the log body", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackEvent("checkout_started");

      expect(emittedCall().body).toBe("checkout_started");
    });

    it("merges custom attrs into the log record", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackEvent("add_to_cart", { item_id: "sku-123", quantity: "2" });

      const attrs = emittedAttrs();
      expect(attrs["item_id"]).toBe("sku-123");
      expect(attrs["quantity"]).toBe("2");
    });

    it("is a no-op before SDK is initialized", async () => {
      const { PulseWeb } = await import("../sdk");
      // NOT started
      PulseWeb.trackEvent("should_not_emit");

      expect(emitFn).not.toHaveBeenCalled();
    });

    it("handles absent attrs gracefully (no throw)", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      expect(() => PulseWeb.trackEvent("bare_event")).not.toThrow();
      expect(emitFn).toHaveBeenCalledOnce();
    });
  });

  // ─── reportException ─────────────────────────────────────────────────────

  describe("reportException()", () => {
    it("emits with pulse.type = non_fatal and WARN severity", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportException(new Error("network timeout"));

      const call = emittedCall();
      const attrs = emittedAttrs();
      expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
        PulseWebSemconv.PulseType.NON_FATAL,
      );
      expect(call.severityText).toBe("WARN");
    });

    it("stamps is_manual = true", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportException(new Error("manual error"));

      expect(emittedAttrs()[PulseWebSemconv.AttributeKey.NON_FATAL_IS_MANUAL]).toBe(true);
    });

    it("captures exception type, message, stacktrace from Error", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportException(new TypeError("bad type"));

      const attrs = emittedAttrs();
      expect(attrs[PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]).toBe("TypeError");
      expect(attrs[PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]).toBe("bad type");
      expect(typeof attrs[PulseWebSemconv.AttributeKey.EXCEPTION_STACKTRACE]).toBe("string");
    });

    it("coerces non-Error string input to Error message", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportException("something went wrong");

      expect(emittedAttrs()[PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]).toBe(
        "something went wrong",
      );
    });

    it("merges custom attrs into log record", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportException(new Error("oops"), { component: "Checkout" });

      expect(emittedAttrs()["component"]).toBe("Checkout");
    });

    it("is a no-op before SDK is initialized", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.reportException(new Error("early"));

      expect(emitFn).not.toHaveBeenCalled();
    });
  });

  // ─── reportDeviceCrash ───────────────────────────────────────────────────

  describe("reportDeviceCrash()", () => {
    it("emits with pulse.type = device.crash and FATAL severity", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportDeviceCrash(new Error("render crash"));

      const call = emittedCall();
      const attrs = emittedAttrs();
      expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
        PulseWebSemconv.PulseType.DEVICE_CRASH,
      );
      expect(call.severityText).toBe("FATAL");
    });

    it("captures exception type and message", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportDeviceCrash(new RangeError("out of range"));

      const attrs = emittedAttrs();
      expect(attrs[PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]).toBe("RangeError");
      expect(attrs[PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]).toBe("out of range");
    });

    it("coerces non-Error input — produces a string message", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportDeviceCrash({ message: "crash obj" });

      const attrs = emittedAttrs();
      expect(typeof attrs[PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]).toBe("string");
    });

    it("merges custom attrs", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.reportDeviceCrash(new Error("crash"), { component_stack: "App > Router" });

      expect(emittedAttrs()["component_stack"]).toBe("App > Router");
    });

    it("is a no-op before SDK is initialized", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.reportDeviceCrash(new Error("early crash"));

      expect(emitFn).not.toHaveBeenCalled();
    });
  });

  // ─── trackNonFatal ───────────────────────────────────────────────────────

  describe("trackNonFatal()", () => {
    it("emits with pulse.type = non_fatal", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackNonFatal("api_timeout");

      expect(emittedAttrs()[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
        PulseWebSemconv.PulseType.NON_FATAL,
      );
    });

    it("stamps non_fatal_type = name and is_manual = true", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackNonFatal("payment_declined");

      const attrs = emittedAttrs();
      expect(attrs[PulseWebSemconv.AttributeKey.NON_FATAL_TYPE]).toBe("payment_declined");
      expect(attrs[PulseWebSemconv.AttributeKey.NON_FATAL_IS_MANUAL]).toBe(true);
    });

    it("uses the name as the log body", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackNonFatal("slow_render");

      expect(emittedCall().body).toBe("slow_render");
    });

    it("merges custom attrs", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.trackNonFatal("api_error", { endpoint: "/checkout", status: "503" });

      const attrs = emittedAttrs();
      expect(attrs["endpoint"]).toBe("/checkout");
      expect(attrs["status"]).toBe("503");
    });

    it("is a no-op before SDK is initialized", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.trackNonFatal("early_non_fatal");

      expect(emitFn).not.toHaveBeenCalled();
    });
  });

  // ─── setUserProperties ───────────────────────────────────────────────────

  describe("setUserProperties()", () => {
    it("merges multiple properties without overwriting unrelated keys", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();

      PulseWeb.setUserId("u1");
      PulseWeb.setUserProperty("plan", "pro");
      PulseWeb.setUserProperties({ locale: "en-IN", tier: "gold" });

      const props = getPersistedUserProperties();
      expect(props.plan).toBe("pro");
      expect(props.locale).toBe("en-IN");
      expect(props.tier).toBe("gold");
    });

    it("null value removes the key from persisted props", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();

      PulseWeb.setUserId("u1");
      PulseWeb.setUserProperties({ plan: "pro", temp: "x" });
      PulseWeb.setUserProperties({ temp: null });

      const props = getPersistedUserProperties();
      expect(props.plan).toBe("pro");
      expect("temp" in props).toBe(false);
    });

    it("is a no-op before SDK is initialized (no crash)", async () => {
      const { PulseWeb } = await import("../sdk");
      expect(() => PulseWeb.setUserProperties({ plan: "pro" })).not.toThrow();
    });

    it("overwrites an existing key with a new value", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();

      PulseWeb.setUserId("u1");
      PulseWeb.setUserProperties({ plan: "free" });
      PulseWeb.setUserProperties({ plan: "enterprise" });

      expect(getPersistedUserProperties().plan).toBe("enterprise");
    });
  });

  // ─── clearUserIdentity ───────────────────────────────────────────────────

  describe("clearUserIdentity()", () => {
    it("removes userId and properties from localStorage", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();

      PulseWeb.setUserId("user-to-clear");
      PulseWeb.setUserProperty("plan", "pro");
      PulseWeb.clearUserIdentity();

      expect(getPersistedUserId()).toBeNull();
      expect(getPersistedUserProperties()).toEqual({});
    });

    it("after clearing, user.id is absent from emitted signals", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();

      PulseWeb.setUserId("user-to-clear");
      PulseWeb.clearUserIdentity();
      emitFn.mockClear();

      PulseWeb.trackEvent("post_clear_event");

      const attrs = emittedAttrs();
      expect(attrs["user.id"]).toBeUndefined();
    });

    it("is safe to call when nothing was set (no crash)", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();

      expect(() => PulseWeb.clearUserIdentity()).not.toThrow();
    });

    it("is safe to call before SDK is initialized (no crash)", async () => {
      const { PulseWeb } = await import("../sdk");
      expect(() => PulseWeb.clearUserIdentity()).not.toThrow();
    });
  });

  // ─── setScreenName ───────────────────────────────────────────────────────

  describe("setScreenName()", () => {
    it("does not throw and allows subsequent emit calls", async () => {
      const { PulseWeb } = await import("../sdk");
      PulseWeb.start(BASE_CONFIG);
      await Promise.resolve();
      emitFn.mockClear();

      PulseWeb.setScreenName("/checkout");

      expect(() => PulseWeb.trackEvent("view")).not.toThrow();
      expect(emitFn).toHaveBeenCalledOnce();
    });

    it("is a no-op before SDK is initialized (no crash)", async () => {
      const { PulseWeb } = await import("../sdk");
      expect(() => PulseWeb.setScreenName("/early")).not.toThrow();
    });
  });
});
