import { describe, it, expect, vi, afterEach } from "vitest";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";

import { DEFAULT_SDK_CONFIG } from "../remote-config";
import { ExportSamplingGate } from "../sampling/export-sampling-gate";
import type { PulseSdkConfig } from "../types/remote-config";

afterEach(() => {
  vi.restoreAllMocks();
});

function makeLog(
  body: string,
  attrs: Record<string, string | number | boolean> = {},
): ReadableLogRecord {
  return {
    body,
    attributes: attrs,
  } as unknown as ReadableLogRecord;
}

describe("ExportSamplingGate", () => {
  it("drops all logs when default sessionSampleRate is 0 (Android-style)", () => {
    vi.spyOn(Math, "random").mockReturnValue(0.5);
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        default: { sessionSampleRate: 0 },
        rules: [],
        signalsToSample: [],
      },
    };
    const gate = new ExportSamplingGate(config, "pulse_web_js");
    const logs = [makeLog("session.start", { "pulse.type": "session.start" })];
    expect(gate.filterReadableLogs(logs)).toHaveLength(0);
  });

  it("keeps logs when default sessionSampleRate is 1", () => {
    vi.spyOn(Math, "random").mockReturnValue(0.5);
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        default: { sessionSampleRate: 1 },
        rules: [],
        signalsToSample: [],
      },
    };
    const gate = new ExportSamplingGate(config, "pulse_web_js");
    const logs = [makeLog("session.start")];
    expect(gate.filterReadableLogs(logs)).toHaveLength(1);
  });

  it("alwaysSend bypasses session rate 0", () => {
    vi.spyOn(Math, "random").mockReturnValue(0.5);
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        default: { sessionSampleRate: 0 },
        rules: [],
        signalsToSample: [],
        criticalEventPolicies: {
          alwaysSend: [
            {
              name: ".*",
              props: [{ key: "pulse\\.type", value: "device\\.crash" }],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
          ],
        },
      },
    };
    const gate = new ExportSamplingGate(config, "pulse_web_js");
    const logs = [
      makeLog("boom", {
        "pulse.type": "device.crash",
      }),
    ];
    expect(gate.filterReadableLogs(logs)).toHaveLength(1);
  });

  it("uses signalsToSample rate when entry matches", () => {
    vi.spyOn(Math, "random").mockReturnValue(0.5);
    const config: PulseSdkConfig = {
      ...DEFAULT_SDK_CONFIG,
      sampling: {
        default: { sessionSampleRate: 0 },
        rules: [],
        signalsToSample: [
          {
            sampleRate: 1,
            condition: {
              name: ".*",
              props: [{ key: "pulse\\.type", value: "session\\.start" }],
              scopes: ["LOGS"],
              sdks: ["pulse_web_js"],
            },
          },
        ],
      },
    };
    const gate = new ExportSamplingGate(config, "pulse_web_js");
    const logs = [makeLog("session.start", { "pulse.type": "session.start" })];
    expect(gate.filterReadableLogs(logs)).toHaveLength(1);
  });
});
