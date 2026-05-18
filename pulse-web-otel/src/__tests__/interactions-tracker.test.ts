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

describe("InteractionTracker — first step never fired", () => {
  it("INT-P26: second step fires alone (first never sent) → no terminal emitted", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg(), {
      onInteractionTerminal: (i) => terminals.push(i),
    });
    // Fire step_b without ever firing step_a
    tracker.checkAndAdd({ name: "step_b", timeInNano: 1e12 });
    expect(terminals).toHaveLength(0);
    tracker.destroy();
  });
});

describe("InteractionTracker — marker events", () => {
  it("addMarker mid-flow → MARKER_EVENTS in terminal props", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg(), {
      onInteractionTerminal: (i) => terminals.push(i),
    });

    tracker.checkAndAdd({ name: "step_a", timeInNano: 1_000_000_000 });
    tracker.addMarker({ name: "non_fatal", timeInNano: 1_100_000_000 });
    tracker.checkAndAdd({ name: "step_b", timeInNano: 2_000_000_000 });

    expect(terminals).toHaveLength(1);
    const props = (terminals[0] as { props: Record<string, unknown> }).props;
    const markers = props[INTERACTION_PROP_KEYS.MARKER_EVENTS] as unknown[];
    expect(Array.isArray(markers)).toBe(true);
    expect(markers).toHaveLength(1);
    expect((markers[0] as { name: string }).name).toBe("non_fatal");
  });

  it("marker before flow start is excluded from MARKER_EVENTS (outside window)", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg(), {
      onInteractionTerminal: (i) => terminals.push(i),
    });

    // marker added BEFORE step_a — should be outside the [step_a, step_b] window
    tracker.addMarker({ name: "pre_crash", timeInNano: 500_000_000 });
    tracker.checkAndAdd({ name: "step_a", timeInNano: 1_000_000_000 });
    tracker.checkAndAdd({ name: "step_b", timeInNano: 2_000_000_000 });

    expect(terminals).toHaveLength(1);
    const props = (terminals[0] as { props: Record<string, unknown> }).props;
    const markers = props[INTERACTION_PROP_KEYS.MARKER_EVENTS] as unknown[];
    expect(markers).toHaveLength(0);
  });

  it("multiple markers mid-flow all appear in MARKER_EVENTS", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg(), {
      onInteractionTerminal: (i) => terminals.push(i),
    });

    tracker.checkAndAdd({ name: "step_a", timeInNano: 1_000_000_000 });
    tracker.addMarker({ name: "crash_1", timeInNano: 1_200_000_000 });
    tracker.addMarker({ name: "crash_2", timeInNano: 1_500_000_000 });
    tracker.checkAndAdd({ name: "step_b", timeInNano: 2_000_000_000 });

    const props = (terminals[0] as { props: Record<string, unknown> }).props;
    const markers = props[INTERACTION_PROP_KEYS.MARKER_EVENTS] as Array<{ name: string }>;
    expect(markers.map((m) => m.name)).toEqual(["crash_1", "crash_2"]);
  });

  it("no markers → MARKER_EVENTS is empty array", () => {
    const terminals: unknown[] = [];
    const tracker = new InteractionTracker(cfg(), {
      onInteractionTerminal: (i) => terminals.push(i),
    });
    tracker.checkAndAdd({ name: "step_a", timeInNano: 1_000_000_000 });
    tracker.checkAndAdd({ name: "step_b", timeInNano: 2_000_000_000 });

    const props = (terminals[0] as { props: Record<string, unknown> }).props;
    const markers = props[INTERACTION_PROP_KEYS.MARKER_EVENTS] as unknown[];
    expect(markers).toHaveLength(0);
  });
});
