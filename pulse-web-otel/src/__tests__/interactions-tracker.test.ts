import { afterEach, describe, expect, it, vi } from "vitest";
import type { InteractionConfig } from "../interactions/interaction-models";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-constants";
import { InteractionTracker } from "../interactions/interaction-tracker";

function cfg(over: Partial<InteractionConfig> = {}): InteractionConfig {
  return {
    id: 1,
    name: "TestFlow",
    description: "TestFlow",
    events: [
      { name: "step_a", isBlacklisted: false },
      { name: "step_b", isBlacklisted: false },
    ],
    thresholdInMs: 50,
    uptimeLowerLimitInMs: 5_000,
    uptimeMidLimitInMs: 15_000,
    uptimeUpperLimitInMs: 30_000,
    globalBlacklistedEvents: [],
    ...over,
  };
}

describe("InteractionTracker", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("emits terminal interaction on success", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg(), {
      onInteractionTerminal: (i) => terminals.push(i),
    });
    tracker.checkAndAdd({ name: "step_a", timeInNano: 1e12 });
    tracker.checkAndAdd({ name: "step_b", timeInNano: 1e12 + 2e6 });
    expect(terminals).toHaveLength(1);
    const p = terminals[0] as { props: Record<string, unknown> };
    expect(p.props[INTERACTION_PROP_KEYS.IS_ERROR]).toBe(false);
  });

  it("emits TIMEOUT after threshold + 10ms", () => {
    vi.useFakeTimers();
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg({ thresholdInMs: 40 }), {
      onInteractionTerminal: (i) => terminals.push(i),
    });
    tracker.checkAndAdd({ name: "step_a", timeInNano: 1e12 });
    vi.advanceTimersByTime(61);
    expect(terminals).toHaveLength(1);
    const p = terminals[0] as { props: Record<string, unknown> };
    expect(p.props[INTERACTION_PROP_KEYS.ERROR_TYPE]).toBe("timeout");
    expect(p.props[INTERACTION_PROP_KEYS.IS_ERROR]).toBe(true);
    tracker.destroy();
  });

  it("destroy clears pending timeout", () => {
    vi.useFakeTimers();
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg({ thresholdInMs: 100 }), {
      onInteractionTerminal: (i) => terminals.push(i),
    });
    tracker.checkAndAdd({ name: "step_a", timeInNano: 1e12 });
    tracker.destroy();
    vi.advanceTimersByTime(500);
    expect(terminals).toHaveLength(0);
  });

  it("silent reset on global blacklist during ongoing (no terminal)", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(
      cfg({
        globalBlacklistedEvents: [
          { name: "noise", isBlacklisted: true, props: [] },
        ],
      }),
      { onInteractionTerminal: (i) => terminals.push(i) },
    );
    tracker.checkAndAdd({ name: "step_a", timeInNano: 1e12 });
    tracker.checkAndAdd({ name: "noise", timeInNano: 1e12 + 1 });
    expect(terminals).toHaveLength(0);
  });
});
