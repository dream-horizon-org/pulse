import { describe, it, expect } from "vitest";

import {
  mergePulseSdkConfig,
  normalizeSignalMatchCondition,
} from "../remote-config";
import { DEFAULT_SDK_CONFIG } from "../constants/default-sdk-config";
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
  it("normalizes criticalSessionPolicies.alwaysSend and strips legacy criticalEventPolicies from merged sampling", () => {
    const raw = {
      ...DEFAULT_SDK_CONFIG,
      version: 1,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        criticalSessionPolicies: { alwaysSend: [cond] },
      },
    } as unknown as PulseSdkConfig;
    const m = mergePulseSdkConfig(raw);
    expect(m.sampling.criticalSessionPolicies?.alwaysSend).toHaveLength(1);
    expect(
      (m.sampling as { criticalEventPolicies?: unknown }).criticalEventPolicies,
    ).toBeUndefined();
  });

  it("uses only criticalSessionPolicies when both keys appear on raw JSON", () => {
    const evOnly = { ...cond, name: "^ev$" };
    const sessOnly = { ...cond, name: "^sess$" };
    const raw = {
      ...DEFAULT_SDK_CONFIG,
      version: 1,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        criticalSessionPolicies: { alwaysSend: [sessOnly] },
        criticalEventPolicies: { alwaysSend: [evOnly] },
      },
    } as unknown as PulseSdkConfig;
    const m = mergePulseSdkConfig(raw);
    expect(m.sampling.criticalSessionPolicies?.alwaysSend?.[0]?.name).toBe(
      "^sess$",
    );
    expect(
      (m.sampling as { criticalEventPolicies?: unknown }).criticalEventPolicies,
    ).toBeUndefined();
  });

  it("normalizes lowercase scopes on signal conditions", () => {
    const raw = {
      ...DEFAULT_SDK_CONFIG,
      version: 2,
      signals: {
        ...DEFAULT_SDK_CONFIG.signals,
        attributesToDrop: [
          {
            values: ["screen.name"],
            condition: {
              name: "^sdk\\.init$",
              props: [],
              scopes: ["traces", "logs"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
      },
    } as unknown as PulseSdkConfig;
    const m = mergePulseSdkConfig(raw);
    expect(m.signals.attributesToDrop[0]?.condition.scopes).toEqual([
      "TRACES",
      "LOGS",
    ]);
  });

  it("normalizes metricsToAdd: top props name→key, nested target.condition scopes, attributesToPick", () => {
    const raw = {
      ...DEFAULT_SDK_CONFIG,
      version: 3,
      signals: {
        ...DEFAULT_SDK_CONFIG.signals,
        metricsToAdd: [
          {
            name: "mock_metric",
            target: {
              type: "attribute",
              condition: {
                name: ".*",
                props: [{ name: "pulse\\.type", value: "^x$" }],
                scopes: ["traces"],
                sdks: ["pulse_web_js"],
              },
            },
            condition: {
              name: ".*",
              props: [{ name: "platform", value: "web" }],
              scopes: ["traces"],
              sdks: ["pulse_web_js"],
            },
            type: { type: "counter" },
            attributesToPick: [
              {
                name: ".*",
                props: [{ name: "session\\.id", value: ".+" }],
                scopes: ["traces"],
                sdks: ["pulse_web_js"],
              },
            ],
          },
        ],
      },
    } as unknown as PulseSdkConfig;
    const m = mergePulseSdkConfig(raw);
    const e = m.signals.metricsToAdd[0];
    expect(e?.condition.props[0]?.key).toBe("platform");
    expect(e?.condition.scopes).toEqual(["TRACES"]);
    expect(e?.target.type).toBe("attribute");
    if (e?.target.type === "attribute") {
      expect(e.target.condition.scopes).toEqual(["TRACES"]);
      expect(e.target.condition.props[0]?.key).toBe("pulse\\.type");
    }
    expect(e?.attributesToPick?.[0]?.scopes).toEqual(["TRACES"]);
    expect(e?.attributesToPick?.[0]?.props[0]?.key).toBe("session\\.id");
  });
});

describe("normalizeSignalMatchCondition", () => {
  it("uppercases logs/traces/metrics and drops unknown scope tokens", () => {
    expect(
      normalizeSignalMatchCondition({
        name: ".*",
        props: [],
        scopes: [
          "Logs",
          "METRICS",
          "nope",
        ] as unknown as PulseSignalMatchCondition["scopes"],
        sdks: ["pulse_web_js"],
      }).scopes,
    ).toEqual(["LOGS", "METRICS"]);
  });
});

describe("getCriticalAlwaysSendConditions", () => {
  it("dedupes identical conditions", () => {
    const cfg: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        ...DEFAULT_SDK_CONFIG.sampling,
        criticalSessionPolicies: { alwaysSend: [cond, cond] },
      },
    };
    expect(getCriticalAlwaysSendConditions(cfg)).toHaveLength(1);
  });
});
