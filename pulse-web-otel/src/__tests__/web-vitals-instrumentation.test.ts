const wvMocks = vi.hoisted(() => ({
  onLCP: vi.fn(),
  onINP: vi.fn(),
  onCLS: vi.fn(),
  onFCP: vi.fn(),
  onTTFB: vi.fn(),
}));

vi.mock("web-vitals", () => ({
  onLCP: wvMocks.onLCP,
  onINP: wvMocks.onINP,
  onCLS: wvMocks.onCLS,
  onFCP: wvMocks.onFCP,
  onTTFB: wvMocks.onTTFB,
}));

vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({
      emit: vi.fn(),
      enabled: vi.fn().mockReturnValue(true),
    }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { logs } from "@opentelemetry/api-logs";
import type { CLSMetric, INPMetric, LCPMetric, Metric } from "web-vitals";
import { DomEventType } from "../constants/pulse-otel-runtime";
import { NavigationInstrumentation } from "../instrumentations/navigation";
import {
  WebVitalsInstrumentation,
  webVitalContextFromNavigationType,
} from "../instrumentations/web-vitals";
import { FeatureGate } from "../feature-gate";
import { InstrumentationRegistry } from "../instrumentation-registry";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";
import { PulseWebSemconv } from "../semconv";
import type { SdkContext } from "../instrumentation-registry";
import { SessionProvider } from "../session";
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
  const forceFlush = vi.fn().mockResolvedValue(undefined);
  const loggerProvider = {
    forceFlush,
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

function lcpMetric(over: Partial<LCPMetric> = {}): LCPMetric {
  return {
    name: "LCP",
    value: 1200,
    rating: "good",
    delta: 1200,
    id: "lcp-test",
    entries: [],
    navigationType: "navigate",
    ...over,
  } as LCPMetric;
}

describe("webVitalContextFromNavigationType", () => {
  it("maps back-forward and prerender to pageload", () => {
    expect(webVitalContextFromNavigationType("back-forward")).toBe("pageload");
    expect(webVitalContextFromNavigationType("prerender")).toBe("pageload");
  });

  it("maps soft-navigation to navigation", () => {
    expect(webVitalContextFromNavigationType("soft-navigation")).toBe(
      "navigation",
    );
  });
});

describe("WebVitalsInstrumentation", () => {
  beforeEach(() => {
    wvMocks.onLCP.mockClear();
    wvMocks.onINP.mockClear();
    wvMocks.onCLS.mockClear();
    wvMocks.onFCP.mockClear();
    wvMocks.onTTFB.mockClear();
    vi.mocked(logs.getLogger).mockClear();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit: vi.fn(),
      enabled: vi.fn().mockReturnValue(true),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("registers onLCP, onINP, onCLS, onFCP, and onTTFB once", () => {
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    expect(wvMocks.onLCP).toHaveBeenCalledTimes(1);
    expect(wvMocks.onINP).toHaveBeenCalledTimes(1);
    expect(wvMocks.onCLS).toHaveBeenCalledTimes(1);
    expect(wvMocks.onFCP).toHaveBeenCalledTimes(1);
    expect(wvMocks.onTTFB).toHaveBeenCalledTimes(1);

    instr.uninstall();
  });

  it("emits log with pulse.type, body, name, value, rating, delta, navigation_type, and context", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });

    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    const lcpCallback = wvMocks.onLCP.mock.calls[0]![0] as (
      m: LCPMetric,
    ) => void;
    lcpCallback(
      lcpMetric({ value: 1200, delta: 1200, navigationType: "navigate" }),
    );

    expect(emit).toHaveBeenCalledWith({
      body: PulseWebSemconv.LogBody.WEB_VITAL,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.WEB_VITAL,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAME]: "LCP",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_VALUE]: 1200,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_RATING]: "good",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_DELTA]: 1200,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE]: "navigate",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_CONTEXT]: "pageload",
      },
    });

    emit.mockClear();
    lcpCallback(
      lcpMetric({
        value: 900,
        delta: 50,
        navigationType: "reload",
      }),
    );
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        attributes: expect.objectContaining({
          [PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE]: "reload",
          [PulseWebSemconv.AttributeKey.WEB_VITAL_CONTEXT]: "pageload",
          [PulseWebSemconv.AttributeKey.WEB_VITAL_DELTA]: 50,
        }),
      }),
    );

    instr.uninstall();
  });

  it("registers onCLS and onINP with reportAllChanges true", () => {
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());
    expect(wvMocks.onCLS).toHaveBeenCalledWith(expect.any(Function), {
      reportAllChanges: true,
    });
    expect(wvMocks.onINP).toHaveBeenCalledWith(expect.any(Function), {
      reportAllChanges: true,
    });
    instr.uninstall();
  });

  it("emits incremental web_vital.delta for CLS; maps soft-navigation to web_vital.context navigation", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    const clsCb = wvMocks.onCLS.mock.calls[0]![0] as (m: CLSMetric) => void;
    clsCb({
      name: "CLS",
      value: 0.12,
      rating: "good",
      delta: 0.05,
      id: "cls-1",
      entries: [],
      navigationType: "navigate",
    } as CLSMetric);
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        attributes: expect.objectContaining({
          [PulseWebSemconv.AttributeKey.WEB_VITAL_DELTA]: 0.05,
        }),
      }),
    );

    emit.mockClear();
    const inpCb = wvMocks.onINP.mock.calls[0]![0] as (m: INPMetric) => void;
    inpCb({
      name: "INP",
      value: 42,
      rating: "good",
      delta: 42,
      id: "inp-1",
      entries: [],
      navigationType: "soft-navigation",
    } as unknown as INPMetric);
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        attributes: expect.objectContaining({
          [PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE]:
            "soft-navigation",
          [PulseWebSemconv.AttributeKey.WEB_VITAL_CONTEXT]: "navigation",
        }),
      }),
    );

    instr.uninstall();
  });

  it("calls loggerProvider.forceFlush on visibilitychange hidden", () => {
    const sdk = makeMinimalSdk();
    const instr = new WebVitalsInstrumentation();
    instr.install(sdk);

    const visSpy = vi.spyOn(document, "visibilityState", "get");
    visSpy.mockReturnValue("visible");
    document.dispatchEvent(new Event(DomEventType.VISIBILITY_CHANGE));
    expect(sdk.loggerProvider?.forceFlush).not.toHaveBeenCalled();

    visSpy.mockReturnValue("hidden");
    document.dispatchEvent(new Event(DomEventType.VISIBILITY_CHANGE));
    expect(sdk.loggerProvider?.forceFlush).toHaveBeenCalled();

    visSpy.mockRestore();
    instr.uninstall();
  });

  it("calls loggerProvider.forceFlush on pageshow with persisted true", () => {
    const sdk = makeMinimalSdk();
    const instr = new WebVitalsInstrumentation();
    instr.install(sdk);

    const ev = new PageTransitionEvent(DomEventType.PAGESHOW, {
      persisted: true,
    });
    window.dispatchEvent(ev);
    expect(sdk.loggerProvider?.forceFlush).toHaveBeenCalled();

    instr.uninstall();
  });

  it("does not call loggerProvider.forceFlush on pageshow with persisted false", () => {
    const sdk = makeMinimalSdk();
    const instr = new WebVitalsInstrumentation();
    instr.install(sdk);
    vi.mocked(sdk.loggerProvider!.forceFlush).mockClear();

    const ev = new PageTransitionEvent(DomEventType.PAGESHOW, {
      persisted: false,
    });
    window.dispatchEvent(ev);
    expect(sdk.loggerProvider?.forceFlush).not.toHaveBeenCalled();

    instr.uninstall();
  });

  it("after uninstall, visibilitychange hidden does not call forceFlush", () => {
    const sdk = makeMinimalSdk();
    const instr = new WebVitalsInstrumentation();
    instr.install(sdk);
    instr.uninstall();
    vi.mocked(sdk.loggerProvider!.forceFlush).mockClear();

    const visSpy = vi.spyOn(document, "visibilityState", "get");
    visSpy.mockReturnValue("hidden");
    document.dispatchEvent(new Event(DomEventType.VISIBILITY_CHANGE));
    expect(sdk.loggerProvider?.forceFlush).not.toHaveBeenCalled();
    visSpy.mockRestore();
  });

  it("after uninstall, pageshow persisted true does not call forceFlush", () => {
    const sdk = makeMinimalSdk();
    const instr = new WebVitalsInstrumentation();
    instr.install(sdk);
    instr.uninstall();
    vi.mocked(sdk.loggerProvider!.forceFlush).mockClear();

    const ev = new PageTransitionEvent(DomEventType.PAGESHOW, {
      persisted: true,
    });
    window.dispatchEvent(ev);
    expect(sdk.loggerProvider?.forceFlush).not.toHaveBeenCalled();
  });

  it("emits FCP and TTFB when onFCP and onTTFB callbacks fire", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    const fcpMetric = {
      name: "FCP",
      value: 180,
      rating: "good",
      delta: 180,
      id: "fcp-1",
      entries: [],
      navigationType: "navigate",
    } as Metric;
    const fcpCb = wvMocks.onFCP.mock.calls[0]![0] as (m: Metric) => void;
    fcpCb(fcpMetric);
    expect(emit).toHaveBeenCalledWith({
      body: PulseWebSemconv.LogBody.WEB_VITAL,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.WEB_VITAL,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAME]: "FCP",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_VALUE]: 180,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_RATING]: "good",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_DELTA]: 180,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE]: "navigate",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_CONTEXT]: "pageload",
      },
    });

    emit.mockClear();
    const ttfbMetric = {
      name: "TTFB",
      value: 95,
      rating: "good",
      delta: 95,
      id: "ttfb-1",
      entries: [],
      navigationType: "navigate",
    } as Metric;
    const ttfbCb = wvMocks.onTTFB.mock.calls[0]![0] as (m: Metric) => void;
    ttfbCb(ttfbMetric);
    expect(emit).toHaveBeenCalledWith({
      body: PulseWebSemconv.LogBody.WEB_VITAL,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.WEB_VITAL,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAME]: "TTFB",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_VALUE]: 95,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_RATING]: "good",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_DELTA]: 95,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE]: "navigate",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_CONTEXT]: "pageload",
      },
    });

    instr.uninstall();
  });

  it("emits sequential CLS callbacks with cumulative value and incremental delta", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    const clsCb = wvMocks.onCLS.mock.calls[0]![0] as (m: CLSMetric) => void;
    clsCb({
      name: "CLS",
      value: 0.12,
      rating: "good",
      delta: 0.05,
      id: "cls-1",
      entries: [],
      navigationType: "navigate",
    } as CLSMetric);
    emit.mockClear();
    clsCb({
      name: "CLS",
      value: 0.17,
      rating: "good",
      delta: 0.05,
      id: "cls-2",
      entries: [],
      navigationType: "navigate",
    } as CLSMetric);
    expect(emit).toHaveBeenCalledTimes(1);
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        attributes: expect.objectContaining({
          [PulseWebSemconv.AttributeKey.WEB_VITAL_VALUE]: 0.17,
          [PulseWebSemconv.AttributeKey.WEB_VITAL_DELTA]: 0.05,
        }),
      }),
    );

    instr.uninstall();
  });

  it("does not throw when loggerProvider is absent and visibility becomes hidden", () => {
    const sdk = makeMinimalSdk({ loggerProvider: undefined });
    const instr = new WebVitalsInstrumentation();
    instr.install(sdk);

    const visSpy = vi.spyOn(document, "visibilityState", "get");
    visSpy.mockReturnValue("hidden");
    expect(() => {
      document.dispatchEvent(new Event(DomEventType.VISIBILITY_CHANGE));
    }).not.toThrow();
    visSpy.mockRestore();
    instr.uninstall();
  });

  it("after reinstall, a single LCP metric emits only one log record", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());
    const lcpCallback = wvMocks.onLCP.mock.calls[0]![0] as (
      m: LCPMetric,
    ) => void;

    instr.uninstall();
    instr.install(makeMinimalSdk());
    const lcpCallbackSecond = wvMocks.onLCP.mock.calls[1]![0] as (
      m: LCPMetric,
    ) => void;

    emit.mockClear();
    lcpCallbackSecond(lcpMetric({ value: 300, delta: 300 }));
    expect(emit).toHaveBeenCalledTimes(1);

    emit.mockClear();
    lcpCallback(lcpMetric({ value: 999, delta: 999 }));
    expect(emit).toHaveBeenCalledTimes(0);

    instr.uninstall();
  });

  it("after uninstall, web-vitals metric callback does not call logger.emit", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    const lcpCallback = wvMocks.onLCP.mock.calls[0]![0] as (
      m: LCPMetric,
    ) => void;
    lcpCallback(lcpMetric({ value: 100, delta: 100 }));
    expect(emit).toHaveBeenCalledTimes(1);

    instr.uninstall();
    lcpCallback(lcpMetric({ value: 200, delta: 100 }));
    expect(emit).toHaveBeenCalledTimes(1);
  });

  it("double forceFlush does not throw", async () => {
    const sdk = makeMinimalSdk();
    await sdk.loggerProvider?.forceFlush();
    await sdk.loggerProvider?.forceFlush();
  });

  it("returns immediately when window is undefined (SSR)", () => {
    const orig = globalThis.window;
    // @ts-expect-error deliberate
    delete globalThis.window;
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());
    expect(wvMocks.onLCP).not.toHaveBeenCalled();
    globalThis.window = orig;
  });
});

describe("InstrumentationRegistry Web Vitals gate", () => {
  it("does not install WebVitals when feature web_vitals is disabled", () => {
    wvMocks.onLCP.mockClear();
    const sdk = makeMinimalSdk({
      gate: new FeatureGate({
        ...DEFAULT_SDK_CONFIG,
        features: [
          {
            featureName: "web_vitals",
            sessionSampleRate: 0,
            sdks: ["pulse_web_js"],
          },
        ],
      }),
    });
    const registry = new InstrumentationRegistry(
      sdk,
      sdk.gate,
      sdk.config.instrumentations,
    );
    registry.installAll();
    expect(wvMocks.onLCP).not.toHaveBeenCalled();
    registry.uninstallAll();
  });

  it("installs Web Vitals when gate allows and config enabled", () => {
    wvMocks.onLCP.mockClear();
    const sdk = makeMinimalSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      sdk.gate,
      sdk.config.instrumentations,
    );
    registry.installAll();
    expect(wvMocks.onLCP).toHaveBeenCalled();
    registry.uninstallAll();
  });

  it("installAll installs Navigation before Web Vitals", () => {
    const order: string[] = [];
    const origNav = NavigationInstrumentation.prototype.install;
    const origWv = WebVitalsInstrumentation.prototype.install;
    const navSpy = vi
      .spyOn(NavigationInstrumentation.prototype, "install")
      .mockImplementation(function (
        this: NavigationInstrumentation,
        sdk: SdkContext,
      ) {
        order.push("navigation");
        return origNav.call(this, sdk);
      });
    const wvSpy = vi
      .spyOn(WebVitalsInstrumentation.prototype, "install")
      .mockImplementation(function (
        this: WebVitalsInstrumentation,
        sdk: SdkContext,
      ) {
        order.push("web-vitals");
        return origWv.call(this, sdk);
      });

    wvMocks.onLCP.mockClear();
    const sdk = makeMinimalSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      sdk.gate,
      sdk.config.instrumentations,
    );
    registry.installAll();

    const navIdx = order.indexOf("navigation");
    const wvIdx = order.indexOf("web-vitals");
    expect(navIdx).toBeGreaterThanOrEqual(0);
    expect(wvIdx).toBeGreaterThanOrEqual(0);
    expect(navIdx).toBeLessThan(wvIdx);

    registry.uninstallAll();
    navSpy.mockRestore();
    wvSpy.mockRestore();
  });

  it("does not install Web Vitals when local enabled is false (kill switch; BE gate cannot override)", () => {
    // Mirrors InstrumentationRegistry.shouldInstall: `enabled: false` opts out locally;
    // remote feature gate is not consulted for that instrumentation.
    wvMocks.onLCP.mockClear();
    const sdk = makeMinimalSdk({
      config: {
        apiKey: "proj_x_key",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        instrumentations: { webVitals: { enabled: false } },
      },
    });
    const registry = new InstrumentationRegistry(
      sdk,
      sdk.gate,
      sdk.config.instrumentations,
    );
    registry.installAll();
    expect(wvMocks.onLCP).not.toHaveBeenCalled();
    registry.uninstallAll();
  });

  it("installAll twice without uninstall does not double-register onLCP (single owner)", () => {
    wvMocks.onLCP.mockClear();
    const sdk = makeMinimalSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      sdk.gate,
      sdk.config.instrumentations,
    );
    registry.installAll();
    registry.installAll();
    expect(wvMocks.onLCP).toHaveBeenCalledTimes(1);
    registry.uninstallAll();
  });

  it("installAll after uninstallAll registers onLCP again", () => {
    wvMocks.onLCP.mockClear();
    const sdk = makeMinimalSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      sdk.gate,
      sdk.config.instrumentations,
    );
    registry.installAll();
    registry.uninstallAll();
    registry.installAll();
    expect(wvMocks.onLCP).toHaveBeenCalledTimes(2);
    registry.uninstallAll();
  });

  it(
    "installAll continues installing remaining instrumentations when one " +
      "throws and still flips the single-owner gate",
    () => {
      wvMocks.onLCP.mockClear();
      const sdk = makeMinimalSdk();
      const registry = new InstrumentationRegistry(
        sdk,
        sdk.gate,
        sdk.config.instrumentations,
      );

      // Simulate a transient throw inside one instrumentation install.
      // We register a custom instrumentation via registerAndInstall first to
      // exercise the per-call try/catch; then installAll should still bring
      // up Web Vitals (onLCP) and flip installAllCompleted.
      const throwing = {
        name: "throwing-test-instr",
        install() {
          throw new Error("simulated install failure");
        },
        uninstall() {},
      };
      const ok = registry.registerAndInstall(throwing);
      expect(ok).toBe(false);

      registry.installAll();
      expect(wvMocks.onLCP).toHaveBeenCalledTimes(1);

      // Second installAll() must no-op (single owner) — the throw above must
      // not have left the gate stuck open or closed unexpectedly.
      registry.installAll();
      expect(wvMocks.onLCP).toHaveBeenCalledTimes(1);

      registry.uninstallAll();
    },
  );
});
