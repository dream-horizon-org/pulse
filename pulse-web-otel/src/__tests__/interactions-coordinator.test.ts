import { afterEach, describe, expect, it, vi } from "vitest";
import type { InteractionConfig } from "../interactions/interaction-models";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-prop-keys";
import { InteractionCoordinator } from "../interactions/interaction-coordinator";

function flow(name: string, id: string): InteractionConfig {
  return {
    id,
    name,
    events: [
      { name: "step_a", required: true },
      { name: "step_b", required: true },
    ],
    thresholdInMs: 200,
    uptimeLowerLimitInMs: 5_000,
    uptimeMidLimitInMs: 15_000,
    uptimeUpperLimitInMs: 30_000,
    globalBlacklistedEvents: [],
  };
}

describe("InteractionCoordinator", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("fans out trackEvent to all trackers", () => {
    const names: string[] = [];
    const coord = new InteractionCoordinator({
      onInteractionTerminal: (i) =>
        names.push(String(i.props[INTERACTION_PROP_KEYS.NAME])),
    });
    coord.setConfigs([flow("F1", "1"), flow("F2", "2")]);
    coord.trackEvent("step_a", undefined, 1000);
    coord.trackEvent("step_b", undefined, 1001);
    expect(names.sort()).toEqual(["F1", "F2"]);
    coord.shutdown();
  });

  it("setConfigs mid-flight destroys old timers", () => {
    vi.useFakeTimers();
    const terminals: string[] = [];
    const coord = new InteractionCoordinator({
      onInteractionTerminal: (i) =>
        terminals.push(String(i.props[INTERACTION_PROP_KEYS.NAME])),
    });
    coord.setConfigs([flow("Old", "1")]);
    coord.trackEvent("step_a", undefined, 0);
    coord.setConfigs([flow("New", "2")]);
    vi.advanceTimersByTime(500);
    expect(terminals).toHaveLength(0);
    coord.shutdown();
  });
});
