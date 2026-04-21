import { describe, it, expect } from "vitest";

import type { PulseSignalMatchCondition } from "../types/remote-config";
import { pulseSignalConditionMatches } from "../utils/sampling-signal-match";

describe("pulseSignalConditionMatches", () => {
  it("accepts invalid regex in condition.name via literal fallback (not silent drop-all)", () => {
    const condition: PulseSignalMatchCondition = {
      name: "(",
      props: [],
      scopes: ["LOGS"],
      sdks: ["pulse_web_js"],
    };
    expect(
      pulseSignalConditionMatches("LOGS", "(", {}, condition, "pulse_web_js"),
    ).toBe(true);
    expect(
      pulseSignalConditionMatches("LOGS", "x", {}, condition, "pulse_web_js"),
    ).toBe(false);
  });
});
