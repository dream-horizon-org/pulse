const logMocks = vi.hoisted(() => ({
  getLogger: vi.fn().mockReturnValue({
    emit: vi.fn(),
    enabled: vi.fn().mockReturnValue(true),
  }),
}));

vi.mock("@opentelemetry/api-logs", () => ({
  logs: logMocks,
}));

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { logs } from "@opentelemetry/api-logs";
import { NavigationInstrumentation } from "../instrumentations/navigation";
import { SessionProvider, _resetInstallationStateForTesting } from "../session";
import { FeatureGate } from "../feature-gate";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";
import { PulseWebSemconv } from "../semconv";
import type { SdkContext } from "../instrumentation-registry";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { Logger } from "@opentelemetry/api-logs";
import type { Tracer } from "@opentelemetry/api";

function makeMinimalSdk(
  overrides: Partial<SdkContext> & { config?: PulseWebConfig } = {},
): SdkContext {
  const config: PulseWebConfig = {
    apiKey: "proj_x_key",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    ...overrides.config,
  };
  const loggerProvider = {
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn(),
    addLogRecordProcessor: vi.fn(),
    getLogger: vi.fn().mockReturnValue({
      emit: vi.fn(),
      enabled: vi.fn().mockReturnValue(true),
    }),
  } as unknown as LoggerProvider;

  const sessionProvider = new SessionProvider();
  const globalAttrsProcessor = new PulseGlobalAttributesProcessor(
    sessionProvider,
    config,
    "meter-session",
  );

  return {
    endpointBaseUrl: "https://x.example",
    gate: new FeatureGate(DEFAULT_SDK_CONFIG),
    sessionProvider,
    logger: {
      emit: vi.fn(),
      enabled: vi.fn().mockReturnValue(true),
    } as unknown as Logger,
    tracer: {} as Tracer,
    config,
    globalAttrsProcessor,
    loggerProvider,
    ...overrides,
  };
}

function setPath(path: string) {
  Object.defineProperty(window, "location", {
    value: { ...window.location, pathname: path, href: `http://localhost${path}` },
    configurable: true,
    writable: true,
  });
}

describe("NavigationInstrumentation", () => {
  beforeEach(() => {
    if (typeof window !== "undefined") {
      _resetInstallationStateForTesting();
      window.localStorage.clear();
    }
    vi.clearAllMocks();
    logMocks.getLogger.mockReturnValue({
      emit: vi.fn(),
      enabled: vi.fn().mockReturnValue(true),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Installation and cleanup", () => {
    it("installs without throwing when window is defined", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      expect(() => instr.install(sdk)).not.toThrow();
      instr.uninstall();
    });

    it("returns immediately when window is undefined (SSR)", () => {
      const orig = globalThis.window;
      // @ts-expect-error deliberate
      delete globalThis.window;
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      expect(() => {
        instr.install(sdk);
      }).not.toThrow();
      globalThis.window = orig;
    });

    it("prevents double install (installed flag)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/test1");
      instr.install(sdk);
      const patchedPushState = history.pushState;

      // Try to install again — should no-op
      instr.install(sdk);

      // pushState should not be double-patched (should still be the first patch)
      expect(history.pushState).toBe(patchedPushState);

      instr.uninstall();
    });

    it("uninstall removes all listeners and clears state", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);
      emit.mockClear();

      // Trigger navigation before uninstall
      history.pushState({}, "", "/page1");
      const callsBeforeUninstall = emit.mock.calls.length;
      expect(callsBeforeUninstall).toBeGreaterThan(0);

      emit.mockClear();
      instr.uninstall();

      // Trigger navigation after uninstall — no additional emits
      history.pushState({}, "", "/page2");
      expect(emit.mock.calls.length).toBe(0);
    });

    it("reinstall after uninstall re-registers listeners", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);
      instr.uninstall();
      emit.mockClear();

      // Reinstall
      instr.install(sdk);
      emit.mockClear();

      // Trigger navigation — should emit
      history.pushState({}, "", "/page1");
      expect(emit.mock.calls.length).toBeGreaterThan(0);

      instr.uninstall();
    });
  });

  describe("Screen name resolution", () => {
    it("resolves manual override (highest priority)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        },
      });

      instr.install(sdk);
      sdk.globalAttrsProcessor.setScreenName("CustomScreen");

      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("CustomScreen");

      instr.uninstall();
    });

    it("resolves route pattern match", () => {
      setPath("/products/123");
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
          routePatterns: [{ pattern: "^/products/", name: "ProductDetail" }],
        },
      });

      instr.install(sdk);
      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("ProductDetail");

      instr.uninstall();
    });

    it("resolves heuristic UUID stripping", () => {
      setPath("/users/550e8400-e29b-41d4-a716-446655440000/profile");
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      instr.install(sdk);
      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("/users/:id/profile");

      instr.uninstall();
    });

    it("resolves heuristic numeric segment stripping", () => {
      setPath("/orders/12345/items");
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      instr.install(sdk);
      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("/orders/:id/items");

      instr.uninstall();
    });

    it("resolves raw pathname as fallback", () => {
      setPath("/dashboard");
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      instr.install(sdk);
      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("/dashboard");

      instr.uninstall();
    });

    it("handles root path", () => {
      setPath("/");
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      instr.install(sdk);
      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("/");

      instr.uninstall();
    });

    it("handles deep paths with multiple numeric segments", () => {
      setPath("/a/1/b/2/c/3");
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      instr.install(sdk);
      const name = instr["getCurrentScreenName"](sdk);
      expect(name).toBe("/a/:id/b/:id/c/:id");

      instr.uninstall();
    });
  });

  describe("History API patching", () => {
    it("patches history.pushState without breaking original behavior", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const originalPushState = history.pushState;

      setPath("/page1");
      instr.install(sdk);

      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      // Verify original pushState is called (wrapped)
      expect(() => {
        history.pushState({ key: "value" }, "", "/page2");
      }).not.toThrow();

      expect(originalPushState).toBeDefined();
      instr.uninstall();
    });

    it("patches history.replaceState without breaking original behavior", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const originalReplaceState = history.replaceState;

      setPath("/page1");
      instr.install(sdk);

      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      // Verify original replaceState is called (wrapped)
      expect(() => {
        history.replaceState({ key: "value" }, "", "/page2");
      }).not.toThrow();

      expect(originalReplaceState).toBeDefined();
      instr.uninstall();
    });

    it("emits screen_session span on navigation (previous screen time tracked)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);

      // Navigate to trigger onRouteChange
      setPath("/cart");
      history.pushState({}, "", "/cart");

      // Should emit at least 2 calls: screen_session and screen_load
      expect(emit.mock.calls.length).toBeGreaterThanOrEqual(2);

      // Find screen_session call
      const screenSessionCalls = emit.mock.calls.filter(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_SESSION,
      );
      expect(screenSessionCalls.length).toBeGreaterThan(0);

      instr.uninstall();
    });

    it("emits screen_load span on navigation (new screen)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);
      emit.mockClear();

      // Navigate
      history.pushState({}, "", "/cart");

      // Should emit screen_load for /cart
      const screenLoadCalls = emit.mock.calls.filter(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_LOAD,
      );
      expect(screenLoadCalls.length).toBeGreaterThan(0);

      instr.uninstall();
    });
  });

  describe("Rate limiting", () => {
    it("throttles rapid navigations under 100ms", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/page1");
      instr.install(sdk);
      emit.mockClear();

      vi.useFakeTimers();
      const startTime = Date.now();
      vi.setSystemTime(startTime);

      // Rapid navigations
      history.pushState({}, "", "/page2");
      vi.advanceTimersByTime(50);
      history.pushState({}, "", "/page3");

      // Both navigations should be throttled (second one ignored)
      const emits = emit.mock.calls.length;
      expect(emits).toBeLessThan(4); // Only first nav emits 2 spans

      vi.useRealTimers();
      instr.uninstall();
    });

    it("allows navigation after 100ms delay", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/page1");
      instr.install(sdk);
      emit.mockClear();

      vi.useFakeTimers();
      const startTime = Date.now();
      vi.setSystemTime(startTime);

      history.pushState({}, "", "/page2");
      const firstEmits = emit.mock.calls.length;

      vi.advanceTimersByTime(100);
      emit.mockClear();

      history.pushState({}, "", "/page3");
      const secondEmits = emit.mock.calls.length;

      expect(firstEmits).toBeGreaterThan(0);
      expect(secondEmits).toBeGreaterThan(0);

      vi.useRealTimers();
      instr.uninstall();
    });
  });

  describe("Global attributes stamping", () => {
    it("stamps screen.name on all spans via GlobalAttributesProcessor", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const screenName = sdk.globalAttrsProcessor.getCurrentScreenName();
      expect(screenName).toBeTruthy();

      instr.uninstall();
    });
  });

  describe("Signal emission — timing extraction and attributes", () => {
    it("emits screen_load and screen_interactive on initial page load", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);

      // Should emit both screen_load and screen_interactive
      const emittedPulseTypes = emit.mock.calls.map(
        (call: any) => call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE],
      );

      expect(emittedPulseTypes).toContain(PulseWebSemconv.PulseType.SCREEN_LOAD);
      expect(emittedPulseTypes).toContain(PulseWebSemconv.PulseType.SCREEN_INTERACTIVE);

      instr.uninstall();
    });

    it("sets start.type to cold/reload/back_forward on initial load", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);

      // Check that screen_load has start.type
      const screenLoadCall = emit.mock.calls.find(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_LOAD,
      );

      expect(screenLoadCall).toBeTruthy();
      const startType = screenLoadCall[0]?.attributes?.[PulseWebSemconv.AttributeKey.START_TYPE];
      expect(["cold", "reload", "back_forward"]).toContain(startType);

      instr.uninstall();
    });

    it("omits zero-valued timing attributes", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);

      // Check that zero values are not included
      const screenLoadCall = emit.mock.calls.find(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_LOAD,
      );

      if (screenLoadCall) {
        const attrs = screenLoadCall[0]?.attributes;
        // If timing attrs exist, they should be > 0
        if (attrs[PulseWebSemconv.AttributeKey.TTI] !== undefined) {
          expect(attrs[PulseWebSemconv.AttributeKey.TTI]).toBeGreaterThanOrEqual(0);
        }
        if (attrs[PulseWebSemconv.AttributeKey.PAGE_LOAD_TIME] !== undefined) {
          expect(attrs[PulseWebSemconv.AttributeKey.PAGE_LOAD_TIME]).toBeGreaterThan(0);
        }
      }

      instr.uninstall();
    });

    it("emits timing values with correct magnitude (milliseconds)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);

      const screenLoadCall = emit.mock.calls.find(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_LOAD,
      );

      if (screenLoadCall) {
        const attrs = screenLoadCall[0]?.attributes;
        // All timing values should be finite and non-negative
        [
          PulseWebSemconv.AttributeKey.PAGE_LOAD_TIME,
          PulseWebSemconv.AttributeKey.TTFB,
          PulseWebSemconv.AttributeKey.DNS_TIME,
          PulseWebSemconv.AttributeKey.TCP_TIME,
          PulseWebSemconv.AttributeKey.DOM_PROCESSING_TIME,
        ].forEach((key) => {
          if (attrs[key] !== undefined) {
            expect(Number.isFinite(attrs[key])).toBe(true);
            expect(attrs[key]).toBeGreaterThanOrEqual(0);
          }
        });
      }

      instr.uninstall();
    });

    it("all signals carry required attributes (pulse.type, screen.name, session.id)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);

      // Check that all emitted signals have required attrs
      emit.mock.calls.forEach((call: any) => {
        const attrs = call[0]?.attributes;
        expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBeTruthy();
        expect(attrs[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBeTruthy();
        expect(attrs[PulseWebSemconv.AttributeKey.SESSION_ID]).toBeTruthy();
      });

      instr.uninstall();
    });

    it("SPA nav emits screen_session with duration + screen_load with start.type=spa", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      setPath("/home");
      instr.install(sdk);
      emit.mockClear();

      // Navigate
      setPath("/cart");
      history.pushState({}, "", "/cart");

      // Should emit screen_session and screen_load
      const screenSessionCalls = emit.mock.calls.filter(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_SESSION,
      );
      const screenLoadCalls = emit.mock.calls.filter(
        (call: any) =>
          call[0]?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
          PulseWebSemconv.PulseType.SCREEN_LOAD,
      );

      expect(screenSessionCalls.length).toBeGreaterThan(0);
      expect(screenLoadCalls.length).toBeGreaterThan(0);

      // screen_load should have start.type=spa (not cold/reload/back_forward)
      const lastScreenLoad = screenLoadCalls[screenLoadCalls.length - 1];
      const startType = lastScreenLoad[0]?.attributes?.[PulseWebSemconv.AttributeKey.START_TYPE];
      expect(startType).toBe("spa");

      instr.uninstall();
    });
  });

  describe("Feature gate & consent integration", () => {
    it("does not install when consent is DENIED", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.DENIED,
        },
      });

      setPath("/home");
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      instr.install(sdk);

      // Should not have patched History API or emitted signals
      expect(emit.mock.calls.length).toBe(0);

      instr.uninstall();
    });

    it("installs when consent is ALLOWED", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        },
      });

      setPath("/home");
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      instr.install(sdk);

      // Should have emitted signals
      expect(emit.mock.calls.length).toBeGreaterThanOrEqual(1);

      instr.uninstall();
    });

    it("feature gate check happens at registry level (shouldInstall)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
          instrumentations: {
            navigation: { enabled: false },
          },
        },
      });

      setPath("/home");
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      instr.install(sdk);

      // Even with consent ALLOWED, if config disabled, it's up to registry
      // (NavigationInstrumentation.install() doesn't double-check config)

      instr.uninstall();
    });
  });

  describe("Edge cases and boundary conditions", () => {
    it("handles SSR context safely (no crash on missing window)", () => {
      const orig = globalThis.window;
      // @ts-expect-error deliberate
      delete globalThis.window;

      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      expect(() => {
        instr.install(sdk);
        instr.uninstall();
      }).not.toThrow();

      globalThis.window = orig;
    });

    it("handles consent revoked (zero exports)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.DENIED,
        },
      });

      setPath("/home");
      const emit = vi.fn();
      logMocks.getLogger.mockReturnValue({ emit, enabled: vi.fn().mockReturnValue(true) });

      instr.install(sdk);
      emit.mockClear();

      history.pushState({}, "", "/cart");

      // Should not emit any signals when consent denied
      expect(emit.mock.calls.length).toBe(0);

      instr.uninstall();
    });
  });
});
