import { afterEach, describe, expect, it, vi } from "vitest";
import type { InteractionConfig } from "../interactions/interaction-models";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-constants";
import { InteractionCoordinator } from "../interactions/interaction-coordinator";

function flow(name: string, id: number): InteractionConfig {
  return {
    id,
    name,
    description: name,
    events: [
      { name: "step_a", isBlacklisted: false },
      { name: "step_b", isBlacklisted: false },
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
    coord.setConfigs([flow("F1", 1), flow("F2", 2)]);
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
    coord.setConfigs([flow("Old", 1)]);
    coord.trackEvent("step_a", undefined, 0);
    coord.setConfigs([flow("New", 2)]);
    vi.advanceTimersByTime(500);
    expect(terminals).toHaveLength(0);
    coord.shutdown();
  });

  it("addMarkerToAll fans out to all trackers", () => {
    const terminals: Array<{ name: string; props: Record<string, unknown> }> = [];
    const coord = new InteractionCoordinator({
      onInteractionTerminal: (i) =>
        terminals.push({ name: String(i.props[INTERACTION_PROP_KEYS.NAME]), props: i.props }),
    });
    coord.setConfigs([flow("F1", 1), flow("F2", 2)]);

    coord.trackEvent("step_a", undefined, 1000);
    coord.addMarkerToAll("non_fatal", undefined, 1050);
    coord.trackEvent("step_b", undefined, 1100);

    expect(terminals).toHaveLength(2);
    for (const t of terminals) {
      const markers = t.props[INTERACTION_PROP_KEYS.MARKER_EVENTS] as unknown[];
      expect(Array.isArray(markers)).toBe(true);
      expect(markers.length).toBeGreaterThan(0);
      expect((markers[0] as Record<string, unknown>)["name"]).toBe("non_fatal");
    }
    coord.shutdown();
  });

  it("addMarkerToAll is no-op when no trackers configured", () => {
    const coord = new InteractionCoordinator();
    expect(() => coord.addMarkerToAll("non_fatal", undefined, 1000)).not.toThrow();
    coord.shutdown();
  });

  it("handles parallel configs and emits both completions", () => {
    const terminals: Array<{ name: string; isError: boolean }> = [];
    const coord = new InteractionCoordinator({
      onInteractionTerminal: (i) =>
        terminals.push({
          name: String(i.props[INTERACTION_PROP_KEYS.NAME]),
          isError: i.props[INTERACTION_PROP_KEYS.IS_ERROR] === true,
        }),
    });
    coord.setConfigs([
      {
        ...flow("CheckoutFlow", 3),
        events: [
          { name: "checkout_a", isBlacklisted: false },
          { name: "checkout_b", isBlacklisted: false },
        ],
      },
      {
        ...flow("SignupFlow", 4),
        events: [
          { name: "signup_a", isBlacklisted: false },
          { name: "signup_b", isBlacklisted: false },
        ],
      },
    ]);

    coord.trackEvent("checkout_a", undefined, 1000);
    coord.trackEvent("checkout_b", undefined, 1001);
    coord.trackEvent("signup_a", undefined, 1002);
    coord.trackEvent("signup_b", undefined, 1003);

    expect(terminals.map((t) => t.name).sort()).toEqual([
      "CheckoutFlow",
      "SignupFlow",
    ]);
    expect(terminals.every((t) => !t.isError)).toBe(true);
    coord.shutdown();
  });
});
