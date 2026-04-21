import { describe, it, expect } from "vitest";

import { DEFAULT_SDK_CONFIG, mergePulseSdkConfig } from "../remote-config";
import { getCriticalAlwaysSendConditions } from "../utils/session-sampling-rate";
import type {
  PulseSdkConfig,
  PulseSignalMatchCondition,
} from "../types/remote-config";

const cond: PulseSignalMatchCondition = {
  name: ".*",
  props: [],
  scopes: ["LOGS"],
  sdks: ["pulse_web_js"],
};

describe("mergePulseSdkConfig", () => {
  it("maps criticalSessionPolicies into criticalEventPolicies and omits session key", () => {
    const raw = {
      ...DEFAULT_SDK_CONFIG,
      version: 1,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        criticalSessionPolicies: { alwaysSend: [cond] },
      },
    } as unknown as PulseSdkConfig;
    const m = mergePulseSdkConfig(raw);
    expect(m.sampling.criticalEventPolicies?.alwaysSend).toHaveLength(1);
    expect(m.sampling.criticalSessionPolicies).toBeUndefined();
  });

  it("prefers criticalEventPolicies when both are present", () => {
    const evOnly = { ...cond, name: "^ev$" };
    const sessOnly = { ...cond, name: "^sess$" };
    const raw = {
      ...DEFAULT_SDK_CONFIG,
      version: 1,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        criticalEventPolicies: { alwaysSend: [evOnly] },
        criticalSessionPolicies: { alwaysSend: [sessOnly] },
      },
    } as unknown as PulseSdkConfig;
    const m = mergePulseSdkConfig(raw);
    expect(m.sampling.criticalEventPolicies?.alwaysSend?.[0]?.name).toBe(
      "^ev$",
    );
    expect(m.sampling.criticalSessionPolicies).toBeUndefined();
  });
});

describe("getCriticalAlwaysSendConditions", () => {
  it("dedupes identical conditions", () => {
    const cfg: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        criticalEventPolicies: { alwaysSend: [cond, cond] },
      },
    };
    expect(getCriticalAlwaysSendConditions(cfg)).toHaveLength(1);
  });
});
