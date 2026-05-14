vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({
      emit: vi.fn(),
      enabled: () => true,
    }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { afterEach, describe, expect, it, vi } from "vitest";
import { logs } from "@opentelemetry/api-logs";

import { FeatureGate } from "../feature-gate";
import { InstrumentationRegistry } from "../instrumentation-registry";
import { ErrorInstrumentation } from "../instrumentations/errors";
import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
import type { SdkContext } from "../instrumentation-registry";
import type { PulseSdkConfig } from "../remote-config";
import { PulseFeature } from "../remote-config";

function makeSdk(overrides?: Partial<SdkContext["config"]>): SdkContext {
  const config = {
    apiKey: "proj_abc_secret",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    ...overrides,
    instrumentations: {
      ...overrides?.instrumentations,
    },
  };
  return {
    endpointBaseUrl: "https://collector.example.com",
    gate: new FeatureGate(DEFAULT_SDK_CONFIG),
    sessionProvider: {
      onSessionChange: () => () => {},
      emitInitialSession: () => {},
    } as unknown as SdkContext["sessionProvider"],
    logger: {} as never,
    tracer: {} as never,
    config,
    globalAttrsProcessor: {} as never,
  };
}

const disabledJsCrashConfig: PulseSdkConfig = {
  ...DEFAULT_SDK_CONFIG,
  features: [
    {
      featureName: PulseFeature.JS_CRASH,
      sessionSampleRate: 0,
      sdks: ["pulse_web_js"],
    },
  ],
};

describe("InstrumentationRegistry + errors (JS_CRASH) gate", () => {
  afterEach(() => {
    vi.mocked(logs.getLogger).mockClear();
    vi.restoreAllMocks();
  });

  it("does not install ErrorInstrumentation when PulseFeature.JS_CRASH is gated off", () => {
    const installSpy = vi.spyOn(ErrorInstrumentation.prototype, "install");

    const sdk = makeSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      new FeatureGate(disabledJsCrashConfig),
      {},
    );

    registry.installAll();
    registry.uninstallAll();

    expect(installSpy).not.toHaveBeenCalled();
    installSpy.mockRestore();
  });

  it("installs ErrorInstrumentation when JS_CRASH feature is enabled (default config)", () => {
    const installSpy = vi.spyOn(ErrorInstrumentation.prototype, "install");

    const sdk = makeSdk();
    const registry = new InstrumentationRegistry(
      sdk,
      new FeatureGate(DEFAULT_SDK_CONFIG),
      {},
    );

    registry.installAll();
    registry.uninstallAll();

    expect(installSpy).toHaveBeenCalledTimes(1);
    installSpy.mockRestore();
  });
});

describe("ErrorInstrumentation — SSR / no window", () => {
  const mockSdk = {
    logger: { emit: vi.fn() },
    tracer: {},
    config: {},
    sessionProvider: {},
    globalAttrsProcessor: {},
  } as unknown as SdkContext;

  it("install is a no-op when window is undefined", () => {
    vi.stubGlobal("window", undefined);
    try {
      const instr = new ErrorInstrumentation();
      expect(() => instr.install(mockSdk)).not.toThrow();
      expect(() => instr.uninstall()).not.toThrow();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
