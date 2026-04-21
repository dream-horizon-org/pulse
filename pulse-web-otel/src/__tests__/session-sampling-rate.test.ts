import { describe, it, expect } from "vitest";

import type { PulseSdkConfig } from "../types/remote-config";
import {
  type SessionSamplingRuleMatchContext,
  resolveSessionSamplingRate,
  sessionRuleMatchesWeb,
} from "../utils/session-sampling-rate";

function sampleCtx(
  overrides: Partial<SessionSamplingRuleMatchContext> & {
    parsedUa?: Partial<SessionSamplingRuleMatchContext["parsedUa"]>;
  } = {},
): SessionSamplingRuleMatchContext {
  const base: SessionSamplingRuleMatchContext = {
    serviceVersion: "1.0.0",
    parsedUa: {
      browserName: "Chrome",
      browserVersion: "120",
      osName: "Android",
      osVersion: "13",
      deviceType: "mobile",
    },
    networkType: "wifi",
    networkEffectiveType: "4g",
  };
  return {
    ...base,
    ...overrides,
    parsedUa: { ...base.parsedUa, ...overrides.parsedUa },
  };
}

describe("sessionRuleMatchesWeb", () => {
  it("matches empty or UNKNOWN name for any session", () => {
    expect(
      sessionRuleMatchesWeb({
        name: "",
        value: "",
        sdks: [],
        sessionSampleRate: 0,
      }),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb({
        name: "UNKNOWN",
        value: "",
        sdks: [],
        sessionSampleRate: 0,
      }),
    ).toBe(true);
  });

  it("matches platform rule against RUM platform web (literal or regex)", () => {
    expect(
      sessionRuleMatchesWeb({
        name: "platform",
        value: "web",
        sdks: [],
        sessionSampleRate: 0.5,
      }),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb({
        name: "PLATFORM",
        value: "^w.*b$",
        sdks: [],
        sessionSampleRate: 0.5,
      }),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb({
        name: "platform",
        value: "^Android$",
        sdks: [],
        sessionSampleRate: 0.5,
      }),
    ).toBe(false);
  });

  it("matches app_version against service.version context", () => {
    expect(
      sessionRuleMatchesWeb(
        {
          name: "app_version",
          value: "^1\\.0",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx({ serviceVersion: "1.0.9" }),
      ),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb(
        {
          name: "app_version",
          value: "^2\\.",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx({ serviceVersion: "1.0.9" }),
      ),
    ).toBe(false);
  });

  it("matches os_version against parsed OS name and version", () => {
    const ctx = sampleCtx();
    expect(
      sessionRuleMatchesWeb(
        {
          name: "os_version",
          value: "13",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        ctx,
      ),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb(
        {
          name: "os_version",
          value: "^13$",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        ctx,
      ),
    ).toBe(false);
  });

  it("matches network against type and effectiveType", () => {
    expect(
      sessionRuleMatchesWeb(
        {
          name: "network",
          value: "wifi",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx(),
      ),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb(
        {
          name: "network",
          value: "4g",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx(),
      ),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb(
        {
          name: "network",
          value: "^ethernet/",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx(),
      ),
    ).toBe(false);
  });

  it("matches device against parsed device type", () => {
    expect(
      sessionRuleMatchesWeb(
        {
          name: "device",
          value: "^mobile$",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx(),
      ),
    ).toBe(true);
    expect(
      sessionRuleMatchesWeb(
        {
          name: "device",
          value: "tablet",
          sdks: [],
          sessionSampleRate: 0.5,
        },
        sampleCtx(),
      ),
    ).toBe(false);
  });

  it("falls back to userAgent regex for other rule names", () => {
    expect(
      sessionRuleMatchesWeb({
        name: "custom_ua_slice",
        value: ".*",
        sdks: [],
        sessionSampleRate: 0.5,
      }),
    ).toBe(typeof navigator !== "undefined");
  });
});

describe("resolveSessionSamplingRate", () => {
  it("uses first matching platform rule for pulse_web_js", () => {
    const cfg: PulseSdkConfig = {
      version: 1,
      sampling: {
        default: { sessionSampleRate: 1 },
        rules: [
          {
            name: "platform",
            value: "web",
            sdks: ["pulse_web_js"],
            sessionSampleRate: 0.25,
          },
        ],
        signalsToSample: [],
      },
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
      interaction: { beforeInitQueueSize: 5000 },
      features: [],
    };
    expect(resolveSessionSamplingRate(cfg, "pulse_web_js")).toBe(0.25);
  });

  it("uses serviceVersion for app_version rules", () => {
    const cfg: PulseSdkConfig = {
      version: 1,
      sampling: {
        default: { sessionSampleRate: 1 },
        rules: [
          {
            name: "app_version",
            value: "^9\\.",
            sdks: ["pulse_web_js"],
            sessionSampleRate: 0.1,
          },
        ],
        signalsToSample: [],
      },
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [],
      },
      interaction: { beforeInitQueueSize: 5000 },
      features: [],
    };
    expect(
      resolveSessionSamplingRate(cfg, "pulse_web_js", {
        serviceVersion: "9.5.0",
      }),
    ).toBe(0.1);
    expect(
      resolveSessionSamplingRate(cfg, "pulse_web_js", {
        serviceVersion: "1.0.0",
      }),
    ).toBe(1);
  });
});
