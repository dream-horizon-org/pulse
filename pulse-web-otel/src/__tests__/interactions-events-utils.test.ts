import { describe, expect, it } from "vitest";

import { localEventMatchesFirstConfigEvent } from "../utils/interactions/interaction-events";
import type { InteractionConfig } from "../interactions/interaction-models";

function cfg(events: InteractionConfig["events"]): InteractionConfig {
  return {
    id: 1,
    name: "Flow",
    description: "Flow",
    events,
    thresholdInMs: 5000,
    uptimeLowerLimitInMs: 1000,
    uptimeMidLimitInMs: 2000,
    uptimeUpperLimitInMs: 3000,
    globalBlacklistedEvents: [],
  };
}

describe("localEventMatchesFirstConfigEvent", () => {
  it("uses first non-blacklisted event", () => {
    const config = cfg([
      { name: "noise", isBlacklisted: true, props: [] },
      { name: "start", isBlacklisted: false, props: [] },
    ]);

    expect(
      localEventMatchesFirstConfigEvent(
        { name: "noise", timeInNano: 1 },
        config,
      ),
    ).toBe(false);
    expect(
      localEventMatchesFirstConfigEvent(
        { name: "start", timeInNano: 2 },
        config,
      ),
    ).toBe(true);
  });
});
