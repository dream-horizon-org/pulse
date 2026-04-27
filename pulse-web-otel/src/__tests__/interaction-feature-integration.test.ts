import { afterEach, describe, expect, it, vi } from "vitest";
import type { Tracer, Attributes } from "@opentelemetry/api";

import type { FeatureGate } from "../feature-gate";
import { InteractionFeature } from "../interactions/interaction-feature";
import { PulseWebSemconv } from "../semconv";

function makeInteractionConfig() {
  return [
    {
      id: "checkout_flow",
      name: "Checkout Flow",
      events: [
        { name: "checkout_step_1", required: true },
        { name: "checkout_step_2", required: true },
      ],
      thresholdInMs: 5000,
      uptimeLowerLimitInMs: 1000,
      uptimeMidLimitInMs: 3000,
      uptimeUpperLimitInMs: 6000,
      globalBlacklistedEvents: [],
    },
  ];
}

describe("InteractionFeature integration pipeline", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("runs fetcher -> coordinator -> tracker -> matcher -> span builder end-to-end", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => makeInteractionConfig(),
      }),
    );

    const captured: { attrs?: Attributes; spanName?: string } = {};
    const span = {
      setAttributes: (attrs: Attributes) => {
        captured.attrs = attrs;
      },
      addEvent: vi.fn(),
      setStatus: vi.fn(),
      end: vi.fn(),
    };
    const tracer = {
      startSpan: vi.fn((name: string) => {
        captured.spanName = name;
        return span;
      }),
    } as unknown as Tracer;

    const feature = new InteractionFeature(
      "http://localhost:4318",
      { apiKey: "default-project_devkey01" },
      { isEnabled: () => true } as unknown as FeatureGate,
      true,
      tracer,
    );
    await feature.init();
    feature.trackEvent("checkout_step_1", { source: "test" }, 1000);
    feature.trackEvent("checkout_step_2", { source: "test" }, 2000);

    expect(captured.spanName).toBe("Checkout Flow");
    expect(captured.attrs?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.INTERACTION,
    );
    expect(
      captured.attrs?.[PulseWebSemconv.InteractionAttributeKey.CONFIG_ID],
    ).toBe("checkout_flow");

    feature.shutdown();
  });
});
