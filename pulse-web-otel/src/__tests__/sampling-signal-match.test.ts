import { describe, it, expect } from "vitest";

import type { PulseSignalMatchCondition } from "../types/remote-config";
import {
  attributeKeyMatchesAnyDropPattern,
  pulseSignalConditionMatches,
} from "../utils/sampling-signal-match";

describe("attributeKeyMatchesAnyDropPattern", () => {
  it("matches attribute keys against drop regex list (Android semantics)", () => {
    expect(
      attributeKeyMatchesAnyDropPattern("screen.name", ["screen\\.name"]),
    ).toBe(true);
    expect(
      attributeKeyMatchesAnyDropPattern("screen.foo", ["screen\\..*"]),
    ).toBe(true);
    expect(attributeKeyMatchesAnyDropPattern("page.url", ["screen\\..*"])).toBe(
      false,
    );
  });
});

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
