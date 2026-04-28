import { describe, expect, it, vi } from "vitest";
import type { Tracer } from "@opentelemetry/api";

const coordinatorTrackEvent = vi.fn();
const coordinatorSetConfigs = vi.fn();
const coordinatorShutdown = vi.fn();
vi.mock("../interactions/interaction-coordinator", () => ({
  InteractionCoordinator: vi.fn().mockImplementation(() => ({
    trackEvent: coordinatorTrackEvent,
    setConfigs: coordinatorSetConfigs,
    shutdown: coordinatorShutdown,
  })),
}));

const fetcherInit = vi.fn().mockResolvedValue(undefined);
const fetcherDestroy = vi.fn();
const fetcherGetConfigs = vi.fn().mockReturnValue([]);
const fetcherOnChange = vi.fn();
vi.mock("../interactions/config-fetcher", () => ({
  InteractionConfigFetcher: vi.fn().mockImplementation(() => ({
    init: fetcherInit,
    destroy: fetcherDestroy,
    getConfigs: fetcherGetConfigs,
    onChange: fetcherOnChange,
  })),
  resolveInteractionConfigRequest: vi.fn().mockReturnValue({
    enabled: true,
    url: "http://localhost/v1/interaction-configs/",
    headers: {},
  }),
}));

vi.mock("../interactions/interaction-span-builder", () => ({
  InteractionSpanBuilder: vi.fn().mockImplementation(() => ({
    emitInteraction: vi.fn(),
  })),
}));

import type { FeatureGate } from "../feature-gate";
import { InteractionFeature } from "../interactions/interaction-feature";

describe("InteractionFeature gating", () => {
  const tracer = {} as Tracer;

  function makeFeature(
    gateEnabled: boolean,
    interactionsEnabledByConfig = true,
  ) {
    return new InteractionFeature(
      "http://localhost:4318",
      { apiKey: "default-project_devkey01" },
      {
        isEnabled: () => gateEnabled,
      } as unknown as FeatureGate,
      interactionsEnabledByConfig,
      tracer,
    );
  }

  it("feature gate disabled -> init and track are no-op", async () => {
    fetcherInit.mockClear();
    coordinatorTrackEvent.mockClear();
    const feature = makeFeature(false, true);

    await feature.init();
    feature.trackEvent("checkout_step_1");

    expect(fetcherInit).not.toHaveBeenCalled();
    expect(coordinatorTrackEvent).not.toHaveBeenCalled();
  });

  it("instrumentation disabled by config -> init and track are no-op", async () => {
    fetcherInit.mockClear();
    coordinatorTrackEvent.mockClear();
    const feature = makeFeature(true, false);

    await feature.init();
    feature.trackEvent("checkout_step_1");

    expect(fetcherInit).not.toHaveBeenCalled();
    expect(coordinatorTrackEvent).not.toHaveBeenCalled();
  });

  it("successful init pushes initial configs to coordinator", async () => {
    fetcherInit.mockClear();
    coordinatorSetConfigs.mockClear();
    const cached = [{ id: 1 }];
    fetcherGetConfigs.mockReturnValue(cached);
    const feature = makeFeature(true, true);

    await feature.init();

    expect(fetcherInit).toHaveBeenCalledTimes(1);
    expect(coordinatorSetConfigs).toHaveBeenCalledWith(cached);
  });

  it("shutdown tears down fetcher and coordinator", async () => {
    fetcherDestroy.mockClear();
    coordinatorShutdown.mockClear();
    const feature = makeFeature(true, true);

    await feature.init();
    feature.shutdown();

    expect(fetcherDestroy).toHaveBeenCalledTimes(1);
    expect(coordinatorShutdown).toHaveBeenCalledTimes(1);
  });
});
