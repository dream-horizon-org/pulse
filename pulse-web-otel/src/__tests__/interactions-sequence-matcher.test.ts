import { describe, expect, it } from "vitest";
import {
  buildPulseInteraction,
  matchInteractionSequence,
} from "../interactions/interaction-sequence-matcher";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-constants";
import {
  localEventMatchesConfigEvent,
  matchPropValue,
} from "../utils/interactions/event-matching";
import type { InteractionConfig } from "../interactions/interaction-models";

function cfg(over: Partial<InteractionConfig> = {}): InteractionConfig {
  return {
    id: "c1",
    name: "TestFlow",
    events: [
      { name: "step_a", required: true },
      { name: "step_b", required: true },
    ],
    thresholdInMs: 1000,
    uptimeLowerLimitInMs: 5_000,
    uptimeMidLimitInMs: 15_000,
    uptimeUpperLimitInMs: 30_000,
    globalBlacklistedEvents: [],
    ...over,
  };
}

describe("matchPropValue", () => {
  it("EQUALS is case-sensitive", () => {
    expect(matchPropValue("Ab", "EQUALS", "Ab")).toBe(true);
    expect(matchPropValue("ab", "EQUALS", "Ab")).toBe(false);
  });

  it("supports NOTEQUALS alias", () => {
    expect(matchPropValue("x", "NOTEQUALS", "y")).toBe(true);
    expect(matchPropValue("x", "NOT_EQUALS", "x")).toBe(false);
  });

  it("CONTAINS is case-sensitive", () => {
    expect(matchPropValue("ell", "CONTAINS", "Hello")).toBe(true);
    expect(matchPropValue("ell", "CONTAINS", "HELLO")).toBe(false);
  });

  it("STARTS_WITH / ENDS_WITH", () => {
    expect(matchPropValue("ab", "STARTS_WITH", "abc")).toBe(true);
    expect(matchPropValue("bc", "ENDS_WITH", "abc")).toBe(true);
  });

  it("supports NOT_CONTAINS", () => {
    expect(matchPropValue("xyz", "NOT_CONTAINS", "abc")).toBe(true);
    expect(matchPropValue("abc", "NOT_CONTAINS", "abc")).toBe(false);
  });
});

describe("localEventMatchesConfigEvent", () => {
  it("matches props with operators", () => {
    const ev = {
      name: "e",
      timeInNano: 1,
      props: { channel: "Organic" },
    };
    const ok = localEventMatchesConfigEvent(ev, {
      name: "e",
      required: true,
      props: [{ key: "channel", value: "organic", operator: "EQUALS" }],
    });
    expect(ok).toBe(false);
    const ok2 = localEventMatchesConfigEvent(ev, {
      name: "e",
      required: true,
      props: [{ key: "channel", value: "Org", operator: "CONTAINS" }],
    });
    expect(ok2).toBe(true);
  });
});

describe("matchInteractionSequence", () => {
  it("completes happy path", () => {
    const c = cfg();
    const t0 = 1_000_000_000;
    const events = [
      { name: "step_a", timeInNano: t0 },
      { name: "step_b", timeInNano: t0 + 2e9 },
    ];
    const r = matchInteractionSequence("id-1", events, [], c);
    expect(r).not.toBeNull();
    expect(r!.shouldResetList).toBe(true);
    expect(r!.interactionStatus.kind).toBe("ongoing");
    if (r!.interactionStatus.kind === "ongoing") {
      expect(r!.interactionStatus.interaction).not.toBeNull();
      expect(
        r!.interactionStatus.interaction!.props[INTERACTION_PROP_KEYS.IS_ERROR],
      ).toBe(false);
    }
  });

  it("sequence violation when wrong event during ongoing", () => {
    const c = cfg();
    const t0 = 1e12;
    const events = [
      { name: "step_a", timeInNano: t0 },
      { name: "bad", timeInNano: t0 + 1 },
    ];
    const r = matchInteractionSequence("id-1", events, [], c);
    expect(r).not.toBeNull();
    expect(r!.shouldTakeFirstEvent).toBe(true);
    if (r!.interactionStatus.kind === "ongoing") {
      expect(
        r!.interactionStatus.interaction?.props[INTERACTION_PROP_KEYS.IS_ERROR],
      ).toBe(true);
      expect(
        r!.interactionStatus.interaction?.props[
          INTERACTION_PROP_KEYS.ERROR_TYPE
        ],
      ).toBe("sequence_violation");
    }
  });

  it("global blacklist resets ongoing (no interaction payload)", () => {
    const c = cfg({ globalBlacklistedEvents: ["ad_impression"] });
    const t0 = 1e12;
    const events = [
      { name: "step_a", timeInNano: t0 },
      { name: "ad_impression", timeInNano: t0 + 1 },
    ];
    const r = matchInteractionSequence("id-1", events, [], c);
    expect(r).not.toBeNull();
    expect(r!.interactionStatus.kind).toBe("no_ongoing");
  });
});

describe("buildPulseInteraction", () => {
  it("forces error scoring on timeout path", () => {
    const c = cfg();
    const t0 = 1e12;
    const pulse = buildPulseInteraction(
      "x",
      c,
      [{ name: "step_a", timeInNano: t0 }],
      [],
      { type: "timeout", timeoutExpectedEventName: "step_b" },
    );
    expect(pulse.props[INTERACTION_PROP_KEYS.APDEX_SCORE]).toBeNull();
    expect(pulse.props[INTERACTION_PROP_KEYS.USER_CATEGORY]).toBeNull();
    expect(pulse.props[INTERACTION_PROP_KEYS.IS_ERROR]).toBe(true);
  });

  it("scores Excellent/Good/Average/Poor bands from duration", () => {
    const c = cfg({
      uptimeLowerLimitInMs: 1000,
      uptimeMidLimitInMs: 3000,
      uptimeUpperLimitInMs: 6000,
    });
    const t0 = 1_000_000_000_000;

    const excellent = buildPulseInteraction(
      "ex",
      c,
      [
        { name: "step_a", timeInNano: t0 },
        { name: "step_b", timeInNano: t0 + 500_000_000 },
      ],
      [],
      null,
    );
    expect(excellent.props[INTERACTION_PROP_KEYS.USER_CATEGORY]).toBe(
      "Excellent",
    );
    expect(excellent.props[INTERACTION_PROP_KEYS.APDEX_SCORE]).toBe(1.0);

    const good = buildPulseInteraction(
      "good",
      c,
      [
        { name: "step_a", timeInNano: t0 },
        { name: "step_b", timeInNano: t0 + 2_500_000_000 },
      ],
      [],
      null,
    );
    expect(good.props[INTERACTION_PROP_KEYS.USER_CATEGORY]).toBe("Good");
    expect(good.props[INTERACTION_PROP_KEYS.APDEX_SCORE]).toBeCloseTo(0.7, 3);

    const average = buildPulseInteraction(
      "avg",
      c,
      [
        { name: "step_a", timeInNano: t0 },
        { name: "step_b", timeInNano: t0 + 4_500_000_000 },
      ],
      [],
      null,
    );
    expect(average.props[INTERACTION_PROP_KEYS.USER_CATEGORY]).toBe("Average");
    expect(average.props[INTERACTION_PROP_KEYS.APDEX_SCORE]).toBeCloseTo(
      0.3,
      3,
    );

    const poor = buildPulseInteraction(
      "poor",
      c,
      [
        { name: "step_a", timeInNano: t0 },
        { name: "step_b", timeInNano: t0 + 8_000_000_000 },
      ],
      [],
      null,
    );
    expect(poor.props[INTERACTION_PROP_KEYS.USER_CATEGORY]).toBe("Poor");
    expect(poor.props[INTERACTION_PROP_KEYS.APDEX_SCORE]).toBe(0.0);
  });
});
