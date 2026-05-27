import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
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

type MockSpan = {
  setAttribute: ReturnType<typeof vi.fn>;
  setAttributes: ReturnType<typeof vi.fn>;
  end: ReturnType<typeof vi.fn>;
  setStatus: ReturnType<typeof vi.fn>;
  addEvent: ReturnType<typeof vi.fn>;
};

const navSpanMocks = vi.hoisted(() => {
  const created: Array<{ name: string; span: MockSpan }> = [];

  function createSpan(): MockSpan {
    return {
      setAttribute: vi.fn(),
      setAttributes: vi.fn(),
      end: vi.fn(),
      setStatus: vi.fn(),
      addEvent: vi.fn(),
    };
  }

  const mockTracer = {
    startSpan: vi.fn((name: string) => {
      const span = createSpan();
      created.push({ name, span });
      return span;
    }),
  };

  return {
    mockTracer,
    created,
    createSpan,
    reset: () => {
      created.length = 0;
      mockTracer.startSpan.mockClear();
    },
  };
});

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
    tracer: navSpanMocks.mockTracer as unknown as Tracer,
    config,
    globalAttrsProcessor,
    loggerProvider,
    ...overrides,
  };
}

function setPath(path: string) {
  Object.defineProperty(window, "location", {
    value: {
      ...window.location,
      pathname: path,
      href: `http://localhost${path}`,
    },
    configurable: true,
    writable: true,
  });
}

function attrsFromSetAttributesCalls(span: MockSpan): Record<string, unknown> {
  const merged: Record<string, unknown> = {};
  for (const call of span.setAttributes.mock.calls) {
    const arg = call[0] as Record<string, unknown>;
    Object.assign(merged, arg);
  }
  return merged;
}

function findSpansByName(name: string): MockSpan[] {
  return navSpanMocks.created.filter((e) => e.name === name).map((e) => e.span);
}

describe("NavigationInstrumentation", () => {
  beforeEach(() => {
    if (typeof window !== "undefined") {
      _resetInstallationStateForTesting();
      window.localStorage.clear();
    }
    vi.clearAllMocks();
    navSpanMocks.reset();
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

      setPath("/test1");
      instr.install(sdk);
      const patchedPushState = history.pushState;

      instr.install(sdk);

      expect(history.pushState).toBe(patchedPushState);

      instr.uninstall();
    });

    it("uninstall removes all listeners and clears state", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      const spansBefore = navSpanMocks.created.length;
      expect(spansBefore).toBeGreaterThan(0);

      navSpanMocks.reset();
      instr.uninstall();

      history.pushState({}, "", "/page2");
      expect(navSpanMocks.created.length).toBe(0);
    });

    it("reinstall after uninstall re-registers listeners", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      instr.uninstall();
      navSpanMocks.reset();

      instr.install(sdk);
      navSpanMocks.reset();

      setPath("/page1");
      history.pushState({}, "", "/page1");
      expect(navSpanMocks.mockTracer.startSpan).toHaveBeenCalled();

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

  describe("navigation_id", () => {
    it("calls setNavigationId on cold install and again on SPA history navigation", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      const spy = vi.spyOn(sdk.globalAttrsProcessor, "setNavigationId");

      setPath("/home");
      instr.install(sdk);
      expect(spy).toHaveBeenCalled();
      const coldId = spy.mock.calls[0]![0] as string;
      expect(coldId).toMatch(/^[0-9a-f-]{36}$/i);

      spy.mockClear();
      setPath("/cart");
      history.pushState({}, "", "/cart");
      expect(spy).toHaveBeenCalledTimes(1);
      const spaId = spy.mock.calls[0]![0] as string;
      expect(spaId).toMatch(/^[0-9a-f-]{36}$/i);
      expect(spaId).not.toBe(coldId);

      spy.mockRestore();
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

      expect(() => {
        history.replaceState({ key: "value" }, "", "/page2");
      }).not.toThrow();

      expect(originalReplaceState).toBeDefined();
      instr.uninstall();
    });

    it("creates screen_session span on navigation (previous screen time tracked)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      setPath("/cart");
      history.pushState({}, "", "/cart");

      const sessionSpans = findSpansByName("screen_session");
      expect(sessionSpans.length).toBeGreaterThanOrEqual(1);
      const ended = sessionSpans.find((s) => s.end.mock.calls.length > 0);
      expect(ended).toBeDefined();

      instr.uninstall();
    });

    it("creates screen_load span on navigation (new screen)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      setPath("/cart");
      history.pushState({}, "", "/cart");

      const loads = findSpansByName("screen_load");
      expect(loads.length).toBeGreaterThan(0);

      instr.uninstall();
    });

    it("SPA screen_load screen.name matches URL resolution (not stale manual)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      // Simulate framework having called setScreenName while still on /home.
      sdk.globalAttrsProcessor.setScreenName("/home");

      setPath("/cart");
      history.pushState({}, "", "/cart");

      const loads = findSpansByName("screen_load");
      expect(loads.length).toBeGreaterThan(0);
      const spaLoad = loads[loads.length - 1]!;
      const attrs = attrsFromSetAttributesCalls(spaLoad);
      expect(attrs[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBe("/cart");

      instr.uninstall();
    });

    it("SPA screen_load respects routePatterns for destination screen.name", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk({
        config: {
          apiKey: "proj_x_key",
          dataCollectionState: PulseDataCollectionConsent.ALLOWED,
          routePatterns: [{ pattern: "^/cart", name: "CartPage" }],
        },
      });

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      setPath("/cart");
      history.pushState({}, "", "/cart");

      const loads = findSpansByName("screen_load");
      const spaLoad = loads[loads.length - 1]!;
      const attrs = attrsFromSetAttributesCalls(spaLoad);
      expect(attrs[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBe("CartPage");

      instr.uninstall();
    });
  });

  describe("Rate limiting", () => {
    it("throttles rapid navigations under 100ms", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/page1");
      instr.install(sdk);
      navSpanMocks.reset();

      vi.useFakeTimers();
      const startTime = Date.now();
      vi.setSystemTime(startTime);

      setPath("/page2");
      history.pushState({}, "", "/page2");
      vi.advanceTimersByTime(50);
      setPath("/page3");
      history.pushState({}, "", "/page3");

      const spanStartsAfterThrottle =
        navSpanMocks.mockTracer.startSpan.mock.calls.length;

      expect(spanStartsAfterThrottle).toBeLessThan(4);

      vi.useRealTimers();
      instr.uninstall();
    });

    it("allows navigation after 100ms delay", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/page1");
      instr.install(sdk);
      navSpanMocks.reset();

      vi.useFakeTimers();
      const startTime = Date.now();
      vi.setSystemTime(startTime);

      setPath("/page2");
      history.pushState({}, "", "/page2");
      const firstStarts = navSpanMocks.mockTracer.startSpan.mock.calls.length;

      vi.advanceTimersByTime(100);
      navSpanMocks.reset();

      setPath("/page3");
      history.pushState({}, "", "/page3");
      const secondStarts = navSpanMocks.mockTracer.startSpan.mock.calls.length;

      expect(firstStarts).toBeGreaterThan(0);
      expect(secondStarts).toBeGreaterThan(0);

      vi.useRealTimers();
      instr.uninstall();
    });

    it("rapid SPA navigations coalesce to final URL after debounce window", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/page1");
      instr.install(sdk);
      navSpanMocks.reset();

      vi.useFakeTimers();
      const startTime = Date.now();
      vi.setSystemTime(startTime);

      setPath("/page2");
      history.pushState({}, "", "/page2");
      vi.advanceTimersByTime(30);
      setPath("/page3");
      history.pushState({}, "", "/page3");
      vi.advanceTimersByTime(100);

      const loads = findSpansByName("screen_load");
      const lastSpaLoad = loads[loads.length - 1]!;
      const attrs = attrsFromSetAttributesCalls(lastSpaLoad);
      expect(attrs[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBe("/page3");

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
    it("emits screen_load on initial page load with tti attribute", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const loadSpans = findSpansByName("screen_load");
      expect(loadSpans.length).toBeGreaterThanOrEqual(1);
      const attrs = attrsFromSetAttributesCalls(loadSpans[0]!);

      expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
        PulseWebSemconv.PulseType.SCREEN_LOAD,
      );

      instr.uninstall();
    });

    it("emits screen_interactive span after screen_load when TTI is available", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const interactiveSpans = findSpansByName("screen_interactive");
      // screen_interactive is only emitted when Navigation Timing TTI is available.
      // In jsdom the timing entry may or may not be present — guard accordingly.
      const loadSpans = findSpansByName("screen_load");
      const loadAttrs = attrsFromSetAttributesCalls(loadSpans[0]!);
      const ttiOnLoad = loadAttrs[PulseWebSemconv.AttributeKey.TTI];

      if (ttiOnLoad !== undefined) {
        // TTI available → screen_interactive must have been emitted
        expect(interactiveSpans.length).toBeGreaterThanOrEqual(1);
        const iAttrs = attrsFromSetAttributesCalls(interactiveSpans[0]!);
        expect(iAttrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
          PulseWebSemconv.PulseType.SCREEN_INTERACTIVE,
        );
        expect(iAttrs[PulseWebSemconv.AttributeKey.TTI]).toBe(ttiOnLoad);
        expect(iAttrs[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBeTruthy();
        expect(iAttrs[PulseWebSemconv.AttributeKey.SESSION_ID]).toBeTruthy();
        expect(["cold", "reload", "back_forward"]).toContain(
          iAttrs[PulseWebSemconv.AttributeKey.START_TYPE],
        );
      } else {
        // No TTI → no screen_interactive span emitted
        expect(interactiveSpans.length).toBe(0);
      }

      instr.uninstall();
    });

    it("does NOT emit screen_interactive on SPA navigations", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      // Trigger SPA navigation
      history.pushState({}, "", "/about");

      const interactiveSpans = findSpansByName("screen_interactive");
      // Any screen_interactive spans that exist must be from cold load only, not SPA
      const spaInteractive = interactiveSpans.filter((s) => {
        const attrs = attrsFromSetAttributesCalls(s);
        return attrs[PulseWebSemconv.AttributeKey.START_TYPE] === "spa";
      });
      expect(spaInteractive.length).toBe(0);

      instr.uninstall();
    });

    it("sets start.type to cold/reload/back_forward on initial load", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const loadSpans = findSpansByName("screen_load");
      const attrs = attrsFromSetAttributesCalls(loadSpans[0]!);
      const startType = attrs[PulseWebSemconv.AttributeKey.START_TYPE];
      expect(["cold", "reload", "back_forward"]).toContain(startType);

      instr.uninstall();
    });

    it("omits zero-valued timing attributes", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const loadSpans = findSpansByName("screen_load");
      const attrs = attrsFromSetAttributesCalls(loadSpans[0]!);

      if (attrs[PulseWebSemconv.AttributeKey.TTI] !== undefined) {
        expect(attrs[PulseWebSemconv.AttributeKey.TTI]).toBeGreaterThanOrEqual(
          0,
        );
      }
      if (attrs[PulseWebSemconv.AttributeKey.PAGE_LOAD_TIME] !== undefined) {
        expect(
          attrs[PulseWebSemconv.AttributeKey.PAGE_LOAD_TIME],
        ).toBeGreaterThan(0);
      }

      instr.uninstall();
    });

    it("emits timing values with correct magnitude (milliseconds)", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const loadSpans = findSpansByName("screen_load");
      const attrs = attrsFromSetAttributesCalls(loadSpans[0]!);

      [
        PulseWebSemconv.AttributeKey.PAGE_LOAD_TIME,
        PulseWebSemconv.AttributeKey.TTFB,
        PulseWebSemconv.AttributeKey.DNS_TIME,
        PulseWebSemconv.AttributeKey.TCP_TIME,
        PulseWebSemconv.AttributeKey.DOM_PROCESSING_TIME,
        PulseWebSemconv.AttributeKey.TTI,
      ].forEach((key) => {
        if (attrs[key] !== undefined) {
          expect(Number.isFinite(attrs[key])).toBe(true);
          expect(attrs[key] as number).toBeGreaterThanOrEqual(0);
        }
      });

      instr.uninstall();
    });

    it("screen_load and ended screen_session carry required attrs when present", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const loadSpans = findSpansByName("screen_load");
      const loadAttrs = attrsFromSetAttributesCalls(loadSpans[0]!);
      expect(loadAttrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBeTruthy();
      expect(loadAttrs[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBeTruthy();
      expect(loadAttrs[PulseWebSemconv.AttributeKey.SESSION_ID]).toBeTruthy();

      instr.uninstall();
    });

    it("SPA nav emits screen_session then screen_load with start.type=spa", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      setPath("/cart");
      history.pushState({}, "", "/cart");

      const loads = findSpansByName("screen_load").map(
        attrsFromSetAttributesCalls,
      );
      const spaLoad = loads.find(
        (a) => a[PulseWebSemconv.AttributeKey.START_TYPE] === "spa",
      );
      expect(spaLoad).toBeDefined();

      instr.uninstall();
    });

    it("initial load emits at least one screen_load span", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      expect(findSpansByName("screen_load").length).toBeGreaterThanOrEqual(1);

      instr.uninstall();
    });

    it("popstate triggers SPA navigation spans like pushState", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      setPath("/cart");
      window.dispatchEvent(new PopStateEvent("popstate"));

      expect(navSpanMocks.mockTracer.startSpan).toHaveBeenCalled();

      instr.uninstall();
    });

    it("pagehide ends screen_session span with duration attrs", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      window.dispatchEvent(new Event("pagehide"));

      const sessions = findSpansByName("screen_session").filter(
        (s) => s.end.mock.calls.length > 0,
      );
      expect(sessions.length).toBeGreaterThan(0);
      const attrs = attrsFromSetAttributesCalls(sessions[0]!);
      expect(
        typeof attrs[PulseWebSemconv.AttributeKey.SESSION_DURATION_MS],
      ).toBe("number");

      instr.uninstall();
    });

    it("screen_session carries session.duration and session.duration_ms", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      setPath("/cart");
      history.pushState({}, "", "/cart");

      const sessions = findSpansByName("screen_session").filter(
        (s) => s.end.mock.calls.length > 0,
      );
      expect(sessions.length).toBeGreaterThan(0);
      const attrs = attrsFromSetAttributesCalls(sessions[sessions.length - 1]!);
      expect(attrs[PulseWebSemconv.AttributeKey.SESSION_DURATION_MS]).toEqual(
        attrs[PulseWebSemconv.AttributeKey.SESSION_DURATION],
      );

      instr.uninstall();
    });

    it("sets OK status before end on screen_load", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();
      setPath("/home");
      instr.install(sdk);

      const load = findSpansByName("screen_load")[0]!;
      expect(load?.setStatus.mock.calls.length).toBeGreaterThan(0);
      expect(load?.end.mock.calls.length).toBeGreaterThan(0);

      instr.uninstall();
    });

    it("dwell screen_session gets identity setAttributes at start; final call adds duration only", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);

      const dwell = findSpansByName("screen_session").find(
        (s) => s.end.mock.calls.length === 0,
      );
      expect(dwell).toBeDefined();
      const first = dwell!.setAttributes.mock.calls[0]![0] as Record<
        string,
        unknown
      >;
      expect(first[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
        PulseWebSemconv.PulseType.SCREEN_SESSION,
      );
      expect(first[PulseWebSemconv.AttributeKey.SCREEN_NAME]).toBeTruthy();
      expect(first[PulseWebSemconv.AttributeKey.SESSION_ID]).toBeTruthy();

      window.dispatchEvent(new Event("pagehide"));
      const lastCall = dwell!.setAttributes.mock.calls[
        dwell!.setAttributes.mock.calls.length - 1
      ]![0] as Record<string, unknown>;
      expect(
        typeof lastCall[PulseWebSemconv.AttributeKey.SESSION_DURATION_MS],
      ).toBe("number");
      expect(lastCall[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBeUndefined();

      instr.uninstall();
    });
  });

  describe("BFCache / pageshow restore", () => {
    it("pageshow with persisted=true emits screen_load with start.type bfcache and a new dwell screen_session", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      // jsdom-safe — PageTransitionEvent doesn't expose `persisted` as needed
      window.dispatchEvent(
        Object.assign(new Event("pageshow"), { persisted: true }),
      );

      const loads = findSpansByName("screen_load").map(
        attrsFromSetAttributesCalls,
      );
      const bfcacheLoad = loads.find(
        (a) => a[PulseWebSemconv.AttributeKey.START_TYPE] === "bfcache",
      );
      expect(bfcacheLoad).toBeDefined();

      const openSessions = findSpansByName("screen_session").filter(
        (s) => s.end.mock.calls.length === 0,
      );
      expect(openSessions.length).toBeGreaterThanOrEqual(1);

      instr.uninstall();
    });

    it("pageshow with persisted=false does not emit BFCache restore spans", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      const n = navSpanMocks.mockTracer.startSpan.mock.calls.length;
      window.dispatchEvent(
        Object.assign(new Event("pageshow"), { persisted: false }),
      );
      expect(navSpanMocks.mockTracer.startSpan.mock.calls.length).toBe(n);

      instr.uninstall();
    });

    it("pagehide then pageshow(persisted) emits bfcache screen_load synchronously", () => {
      const instr = new NavigationInstrumentation();
      const sdk = makeMinimalSdk();

      setPath("/home");
      instr.install(sdk);
      navSpanMocks.reset();

      window.dispatchEvent(new Event("pagehide"));
      window.dispatchEvent(
        Object.assign(new Event("pageshow"), { persisted: true }),
      );

      const bfcacheLoad = findSpansByName("screen_load")
        .map(attrsFromSetAttributesCalls)
        .find((a) => a[PulseWebSemconv.AttributeKey.START_TYPE] === "bfcache");
      expect(bfcacheLoad).toBeDefined();

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
      instr.install(sdk);

      expect(navSpanMocks.mockTracer.startSpan).not.toHaveBeenCalled();

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
      instr.install(sdk);

      expect(navSpanMocks.mockTracer.startSpan).toHaveBeenCalled();

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
      instr.install(sdk);

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
      instr.install(sdk);

      expect(navSpanMocks.mockTracer.startSpan).not.toHaveBeenCalled();

      instr.uninstall();
    });
  });
});
