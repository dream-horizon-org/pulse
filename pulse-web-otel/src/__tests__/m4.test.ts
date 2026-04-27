import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type SpanCallArgs = [string, { attributes?: Record<string, unknown>; [k: string]: unknown }, unknown];

// Use vi.hoisted so mock functions are available before vi.mock hoisting
const { mockStartSpan, mockGetTracer } = vi.hoisted(() => {
  const mockStartSpan = vi.fn((_name: string, _opts: unknown, _ctx: unknown) => ({
    end: vi.fn(),
    setAttribute: vi.fn(),
    setAttributes: vi.fn(),
    setStatus: vi.fn(),
    recordException: vi.fn(),
    isRecording: () => true,
    spanContext: () => ({ traceId: "0", spanId: "0", traceFlags: 0 }),
  }));
  const mockGetTracer = vi.fn(() => ({ startSpan: mockStartSpan }));
  return { mockStartSpan, mockGetTracer };
});

// Helper to get typed span calls
function getSpanCalls(): SpanCallArgs[] {
  return mockStartSpan.mock.calls as unknown as SpanCallArgs[];
}

vi.mock("@opentelemetry/api", () => ({
  trace: { getTracer: mockGetTracer },
  SpanKind: { INTERNAL: 1 },
  ROOT_CONTEXT: {},
  context: { active: () => ({}) },
}));

import { NavigationInstrumentation } from "../instrumentations/navigation";
import type { SdkContext } from "../instrumentation-registry";

const mockGlobalAttrsProcessor = {
  getCurrentScreenName: vi.fn(() => window.location.pathname),
  setLastScreenName: vi.fn(),
};

const mockSdk = {
  tracer: { startSpan: mockStartSpan },
  config: {
    routePatterns: [
      { pattern: "/products/:id", name: "ProductDetail" },
    ],
  },
  globalAttrsProcessor: mockGlobalAttrsProcessor,
  sessionProvider: {},
} as unknown as SdkContext;

// Helper — make performance.getEntriesByType return a fake nav entry
function fakeNavEntry(overrides: Partial<PerformanceNavigationTiming> = {}): PerformanceNavigationTiming {
  return {
    type: "navigate",
    startTime: 0,
    loadEventEnd: 500,
    domInteractive: 300,
    domComplete: 450,
    domainLookupStart: 10,
    domainLookupEnd: 20,
    connectStart: 20,
    connectEnd: 50,
    responseStart: 55,
    requestStart: 50,
    ...overrides,
  } as unknown as PerformanceNavigationTiming;
}

describe("NavigationInstrumentation", () => {
  let instr: NavigationInstrumentation;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
    mockGlobalAttrsProcessor.getCurrentScreenName.mockImplementation(() => window.location.pathname);
    // Reset location
    history.replaceState({}, "", "/");
    instr = new NavigationInstrumentation();
  });

  afterEach(() => {
    instr.uninstall();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  // --- screen_load / screen_interactive ---

  describe("capturePageLoad", () => {
    it("emits screen_load and screen_interactive spans on install when page is loaded", () => {
      // Patch performance
      const nav = fakeNavEntry();
      vi.spyOn(performance, "getEntriesByType").mockReturnValue([nav] as unknown as PerformanceEntry[]);
      Object.defineProperty(document, "readyState", { value: "complete", configurable: true });

      instr.install(mockSdk);

      // spans are emitted in setTimeout(0) — run timers
      return new Promise<void>(resolve => {
        setTimeout(() => {
          const calls = getSpanCalls().map(c => c[0]);
          expect(calls).toContain("screen_load");
          expect(calls).toContain("screen_interactive");
          resolve();
        }, 10);
      });
    });

    it("screen_load span has correct timing attributes (load.duration_ms, ttfb_ms)", () => {
      const nav = fakeNavEntry({ loadEventEnd: 600, domInteractive: 300 });
      vi.spyOn(performance, "getEntriesByType").mockReturnValue([nav] as unknown as PerformanceEntry[]);
      Object.defineProperty(document, "readyState", { value: "complete", configurable: true });

      instr.install(mockSdk);

      return new Promise<void>(resolve => {
        setTimeout(() => {
          const loadCall = getSpanCalls().find(c => c[0] === "screen_load");
          expect(loadCall).toBeDefined();
          const attrs = loadCall![1].attributes ?? {};
          expect(attrs["pulse.type"]).toBe("screen_load");
          // E2E contract: load.duration_ms not page.load_time
          expect(attrs["load.duration_ms"]).toBe(600); // loadEventEnd - startTime = 600 - 0
          // E2E contract: ttfb_ms not ttfb
          expect(attrs["ttfb_ms"]).toBe(5); // responseStart(55) - requestStart(50)
          expect(attrs["tti"]).toBeUndefined(); // tti only on screen_interactive
          expect(attrs["navigation.type"]).toBe("navigate");
          expect(attrs["start.type"]).toBe("cold");
          resolve();
        }, 10);
      });
    });

    it("screen_interactive span has tti attribute", () => {
      const nav = fakeNavEntry({ domInteractive: 280 });
      vi.spyOn(performance, "getEntriesByType").mockReturnValue([nav] as unknown as PerformanceEntry[]);
      Object.defineProperty(document, "readyState", { value: "complete", configurable: true });

      instr.install(mockSdk);

      return new Promise<void>(resolve => {
        setTimeout(() => {
          const intCall = getSpanCalls().find(c => c[0] === "screen_interactive");
          expect(intCall).toBeDefined();
          const attrs = intCall![1].attributes ?? {};
          expect(attrs["pulse.type"]).toBe("screen_interactive");
          expect(attrs["tti"]).toBe(280);
          resolve();
        }, 10);
      });
    });

    it("start.type is reload on page reload", () => {
      const nav = fakeNavEntry({ type: "reload" });
      vi.spyOn(performance, "getEntriesByType").mockReturnValue([nav] as unknown as PerformanceEntry[]);
      Object.defineProperty(document, "readyState", { value: "complete", configurable: true });

      instr.install(mockSdk);

      return new Promise<void>(resolve => {
        setTimeout(() => {
          const loadCall = getSpanCalls().find(c => c[0] === "screen_load");
          expect((loadCall![1].attributes ?? {})["start.type"]).toBe("reload");
          resolve();
        }, 10);
      });
    });
  });

  // --- pushState / screen_session ---

  describe("screen_session on SPA navigation", () => {
    it("emits screen_session on pushState after 100ms", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      instr.install(mockSdk);
      mockStartSpan.mockClear();

      // Advance mock time so session duration > 100ms
      mockNow = 200;
      history.pushState({}, "", "/about");

      const calls = getSpanCalls().map(c => c[0]);
      expect(calls).toContain("screen_session");
    });

    it("screen_session has correct screen.name", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      history.replaceState({}, "", "/home");
      mockGlobalAttrsProcessor.getCurrentScreenName.mockImplementation(() => window.location.pathname);

      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 200;
      history.pushState({}, "", "/about");

      const sessionCall = getSpanCalls().find(c => c[0] === "screen_session");
      expect(sessionCall).toBeDefined();
      const attrs = sessionCall![1].attributes ?? {};
      expect(attrs["screen.name"]).toBe("/home");
      expect(attrs["pulse.type"]).toBe("screen_session");
      expect(attrs["session.duration"]).toBeGreaterThan(0);
    });

    it("last.screen.name is empty on first navigation", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      history.replaceState({}, "", "/");
      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 200;
      history.pushState({}, "", "/products");

      const sessionCall = getSpanCalls().find(c => c[0] === "screen_session");
      expect((sessionCall![1].attributes ?? {})["last.screen.name"]).toBe("");
    });

    it("previous_screen.name is empty on first navigation", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      history.replaceState({}, "", "/");
      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 200;
      history.pushState({}, "", "/products");

      const sessionCall = getSpanCalls().find(c => c[0] === "screen_session");
      expect((sessionCall![1].attributes ?? {})["previous_screen.name"]).toBe("");
    });

    it("previous_screen.name is correct on second navigation", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      history.replaceState({}, "", "/");
      mockGlobalAttrsProcessor.getCurrentScreenName.mockImplementation(() => window.location.pathname);

      instr.install(mockSdk);
      mockNow = 200;
      history.pushState({}, "", "/products");
      mockStartSpan.mockClear();

      mockNow = 400;
      history.pushState({}, "", "/cart");

      // The session span for /products ends when we navigate to /cart.
      // previous_screen.name = the screen before /products = /
      const sessionCall = getSpanCalls().find(c => c[0] === "screen_session");
      expect(sessionCall).toBeDefined();
      const attrs = sessionCall![1].attributes ?? {};
      expect(attrs["screen.name"]).toBe("/products");
      expect(attrs["previous_screen.name"]).toBe("/");
    });

    it("sub-100ms navigation does NOT emit screen_session", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 50; // only 50ms
      history.pushState({}, "", "/fast");

      const calls = getSpanCalls().map(c => c[0]);
      expect(calls).not.toContain("screen_session");
    });

    it("same-route pushState does NOT emit screen_session", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      history.replaceState({}, "", "/products");
      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 200;
      // pushState to same pathname — should not split the session
      history.pushState({}, "", "/products");

      expect(getSpanCalls().map(c => c[0])).not.toContain("screen_session");
    });

    it("replaceState does NOT emit screen_session", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 200;
      history.replaceState({}, "", "/updated-url");

      const calls = getSpanCalls().map(c => c[0]);
      expect(calls).not.toContain("screen_session");
    });

    it("popstate emits screen_session", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      instr.install(mockSdk);
      mockStartSpan.mockClear();

      mockNow = 200;
      window.dispatchEvent(new PopStateEvent("popstate"));

      const calls = getSpanCalls().map(c => c[0]);
      expect(calls).toContain("screen_session");
    });

    it("TC18 — hash-only change does NOT emit screen_session", () => {
      // Hash changes (#section) do not modify pathname so pushState/popstate
      // are never triggered — the session must not be split.
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      history.replaceState({}, "", "/products");
      instr.install(mockSdk);
      mockStartSpan.mockClear();

      // Simulate anchor hash navigation — only hash changes, pathname stays the same
      mockNow = 300;
      history.pushState({}, "", "/products#section");

      expect(getSpanCalls().map(c => c[0])).not.toContain("screen_session");
    });

    it("TC20 — navigation before install emits no spans", () => {
      // SDK not started — History API never patched, no spans emitted
      mockStartSpan.mockClear();

      history.pushState({}, "", "/products");

      expect(getSpanCalls()).toHaveLength(0);
    });
  });

  // --- uninstall ---

  describe("uninstall", () => {
    it("restores original pushState after uninstall", () => {
      const origPush = history.pushState;
      instr.install(mockSdk);
      instr.uninstall();
      expect(history.pushState).toBe(origPush);
    });

    it("after uninstall pushState does not emit spans", () => {
      let mockNow = 0;
      vi.spyOn(performance, "now").mockImplementation(() => mockNow);

      instr.install(mockSdk);
      instr.uninstall();
      mockStartSpan.mockClear();

      mockNow = 200;
      history.pushState({}, "", "/after-uninstall");

      expect(getSpanCalls().map(c => c[0])).not.toContain("screen_session");
    });
  });
});
