import { beforeEach, describe, expect, it, vi } from "vitest";

const initMock = vi.fn().mockResolvedValue(undefined);
const trackEventMock = vi.fn();
const shutdownMock = vi.fn();

vi.mock("../interactions/interaction-feature", () => ({
  InteractionFeature: vi.fn().mockImplementation(() => ({
    init: initMock,
    trackEvent: trackEventMock,
    shutdown: shutdownMock,
  })),
}));

import { FeatureGate } from "../feature-gate";
import type { SdkContext } from "../instrumentation-registry";
import { InteractionInstrumentation } from "../instrumentations/interaction";
import { PulseDataCollectionConsent } from "../config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";

function makeSdkContext(): SdkContext {
  return {
    endpointBaseUrl: "https://collector.example.com",
    gate: new FeatureGate(DEFAULT_SDK_CONFIG),
    sessionProvider: {} as never,
    logger: {} as never,
    tracer: {} as never,
    config: {
      apiKey: "proj_abc_secret",
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    },
    globalAttrsProcessor: {} as never,
  };
}

describe("InteractionInstrumentation", () => {
  beforeEach(() => {
    initMock.mockClear();
    trackEventMock.mockClear();
    shutdownMock.mockClear();
  });

  it("install initializes wrapped feature", () => {
    const instr = new InteractionInstrumentation();
    instr.install(makeSdkContext());

    expect(initMock).toHaveBeenCalledTimes(1);
  });

  it("trackEvent before install is a no-op", () => {
    const instr = new InteractionInstrumentation();
    instr.trackEvent("checkout_step_1", { source: "test" }, 1_000);

    expect(trackEventMock).not.toHaveBeenCalled();
  });

  it("trackEvent after install delegates to wrapped feature", () => {
    const instr = new InteractionInstrumentation();
    instr.install(makeSdkContext());

    instr.trackEvent("checkout_step_2", { source: "test" }, 2_000);
    expect(trackEventMock).toHaveBeenCalledWith(
      "checkout_step_2",
      { source: "test" },
      2_000,
    );
  });

  it("uninstall shuts down feature and is idempotent", () => {
    const instr = new InteractionInstrumentation();
    instr.install(makeSdkContext());

    instr.uninstall();
    instr.uninstall();

    expect(shutdownMock).toHaveBeenCalledTimes(1);
  });
});
