const wvMocks = vi.hoisted(() => ({
  onLCP: vi.fn(),
  onINP: vi.fn(),
  onCLS: vi.fn(),
  onFID: vi.fn(),
  onFCP: vi.fn(),
  onTTFB: vi.fn(),
}));

vi.mock("web-vitals", () => ({
  onLCP: wvMocks.onLCP,
  onINP: wvMocks.onINP,
  onCLS: wvMocks.onCLS,
  onFID: wvMocks.onFID,
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
import { DomEventType } from "../constants/pulse-otel-runtime";
import { WebVitalsInstrumentation } from "../instrumentations/web-vitals";
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

describe("WebVitalsInstrumentation", () => {
  beforeEach(() => {
    wvMocks.onLCP.mockClear();
    wvMocks.onINP.mockClear();
    wvMocks.onCLS.mockClear();
    wvMocks.onFID.mockClear();
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

  it("registers onLCP, onINP, onCLS, onFCP, onFID, and onTTFB once", () => {
    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    expect(wvMocks.onLCP).toHaveBeenCalledTimes(1);
    expect(wvMocks.onINP).toHaveBeenCalledTimes(1);
    expect(wvMocks.onCLS).toHaveBeenCalledTimes(1);
    expect(wvMocks.onFCP).toHaveBeenCalledTimes(1);
    expect(wvMocks.onFID).toHaveBeenCalledTimes(1);
    expect(wvMocks.onTTFB).toHaveBeenCalledTimes(1);

    instr.uninstall();
  });

  it("emits log with pulse.type, body, name, value, rating; omits navigation_type when callback has none", () => {
    const emit = vi.fn();
    vi.mocked(logs.getLogger).mockReturnValue({
      emit,
      enabled: vi.fn().mockReturnValue(true),
    });

    const instr = new WebVitalsInstrumentation();
    instr.install(makeMinimalSdk());

    const lcpCallback = wvMocks.onLCP.mock.calls[0]![0] as (m: {
      name: "LCP";
      value: number;
      rating: "good";
    }) => void;
    lcpCallback({ name: "LCP", value: 1200, rating: "good" });

    expect(emit).toHaveBeenCalledWith({
      body: PulseWebSemconv.LogBody.WEB_VITAL,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.WEB_VITAL,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAME]: "LCP",
        [PulseWebSemconv.AttributeKey.WEB_VITAL_VALUE]: 1200,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_RATING]: "good",
      },
    });

    emit.mockClear();
    const lcpNav = wvMocks.onLCP.mock.calls[0]![0] as (m: {
      name: "LCP";
      value: number;
      rating: "good";
      navigationType: "reload";
    }) => void;
    lcpNav({
      name: "LCP",
      value: 900,
      rating: "good",
      navigationType: "reload",
    });
    expect(emit).toHaveBeenCalledWith(
      expect.objectContaining({
        attributes: expect.objectContaining({
          [PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE]: "reload",
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
