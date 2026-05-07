/**
 * User id / user properties — Android parity (PulseWeb.setUserId, persistence, lifecycle logs).
 */

const { emitFn } = vi.hoisted(() => ({
  emitFn: vi.fn(),
}));

vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn(() => ({ emit: emitFn })),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

vi.mock("../exporters", () => ({
  createProviders: vi.fn().mockReturnValue({
    tracerProvider: {
      addSpanProcessor: vi.fn(),
      getTracer: vi.fn().mockReturnValue({
        startSpan: vi.fn().mockReturnValue({
          setAttribute: vi.fn(),
          end: vi.fn(),
        }),
      }),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
      register: vi.fn(),
    },
    loggerProvider: {
      addLogRecordProcessor: vi.fn(),
      getLogger: vi.fn(() => ({ emit: emitFn })),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    meterProvider: {
      addMetricReader: vi.fn(),
      getMeter: vi.fn().mockReturnValue({}),
      forceFlush: vi.fn().mockResolvedValue(undefined),
      shutdown: vi.fn().mockResolvedValue(undefined),
    },
    cleanup: vi.fn(),
    prepareForDocumentUnload: vi.fn(),
  }),
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  SessionProvider,
  getPersistedUserId,
  getPersistedUserProperties,
} from "../session";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";
import { PulseWebSemconv } from "../semconv";

function makeConfig(overrides: Partial<PulseWebConfig> = {}): PulseWebConfig {
  return {
    apiKey: "proj_abc_supersecretkey",
    serviceName: "test-app",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    ...overrides,
  };
}

type LoggerEmitPayload = {
  body?: string;
  attributes?: Record<string, string>;
};

function userLifecycleCalls(): Array<[LoggerEmitPayload]> {
  const K = PulseWebSemconv.AttributeKey.PULSE_TYPE;
  return emitFn.mock.calls.filter((call) => {
    const attrs = call[0] as LoggerEmitPayload;
    const pt = attrs?.attributes?.[K];
    return (
      pt === PulseWebSemconv.PulseType.USER_SESSION_START ||
      pt === PulseWebSemconv.PulseType.USER_SESSION_END
    );
  }) as Array<[LoggerEmitPayload]>;
}

describe("PulseGlobalAttributesProcessor — user identity attrs", () => {
  it("stamps user.id and pulse.user.*", () => {
    const sessionProvider = new SessionProvider();
    const p = new PulseGlobalAttributesProcessor(
      sessionProvider,
      makeConfig(),
      "meter-1",
    );
    p.setUserId("user-42");
    p.setUserProperty("plan", "pro");
    const attrs = p.getCommonAttrsForMetrics();
    expect(attrs[PulseWebSemconv.AttributeKey.USER_ID]).toBe("user-42");
    expect(attrs["pulse.user.plan"]).toBe("pro");
  });

  it("setUserProperty(null) removes pulse.user key", () => {
    const sessionProvider = new SessionProvider();
    const p = new PulseGlobalAttributesProcessor(
      sessionProvider,
      makeConfig(),
      "m",
    );
    p.setUserProperty("plan", "pro");
    expect(p.getCommonAttrsForMetrics()["pulse.user.plan"]).toBe("pro");
    p.setUserProperty("plan", null);
    expect(p.getCommonAttrsForMetrics()["pulse.user.plan"]).toBeUndefined();
  });

  it("hydrateUserIdentity restores without extra APIs", () => {
    const sessionProvider = new SessionProvider();
    const p = new PulseGlobalAttributesProcessor(
      sessionProvider,
      makeConfig(),
      "m",
    );
    p.hydrateUserIdentity("saved-id", { tier: "gold" });
    const attrs = p.getCommonAttrsForMetrics();
    expect(attrs[PulseWebSemconv.AttributeKey.USER_ID]).toBe("saved-id");
    expect(attrs["pulse.user.tier"]).toBe("gold");
  });

  it("config globalAttributes applied before user id layer", () => {
    const sessionProvider = new SessionProvider();
    const p = new PulseGlobalAttributesProcessor(
      sessionProvider,
      makeConfig({ globalAttributes: { "user.id": "from-config" } }),
      "m",
    );
    p.setUserId("from-api");
    expect(p.getCommonAttrsForMetrics()[PulseWebSemconv.AttributeKey.USER_ID]).toBe(
      "from-api",
    );
  });
});

describe("PulseWeb — setUserId lifecycle + persistence", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({}),
      }),
    );
    const mockXHR = {
      open: vi.fn(),
      send: vi.fn(),
      setRequestHeader: vi.fn(),
      abort: vi.fn(),
      readyState: 4,
      status: 200,
      responseText: "",
      onreadystatechange: null,
      onload: null,
      onerror: null,
      ontimeout: null,
      timeout: 0,
      withCredentials: false,
      upload: { addEventListener: vi.fn() },
    };
    const XhrCtor = vi.fn(() => mockXHR);
    Object.assign(XhrCtor, { DONE: 4 });
    vi.stubGlobal("XMLHttpRequest", XhrCtor);
    window.localStorage.clear();
    window.sessionStorage.clear();
    emitFn.mockClear();
  });

  afterEach(async () => {
    const { PulseWeb } = await import("../sdk");
    if (PulseWeb.isInitialized()) {
      await PulseWeb.shutdown();
    }
    vi.unstubAllGlobals();
  });

  it("setUserId emits user session start once for first id", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    emitFn.mockClear();

    PulseWeb.setUserId("user-a");
    const life = userLifecycleCalls();
    expect(life).toHaveLength(1);
    const arg = life[0]?.[0] as {
      body?: string;
      attributes?: Record<string, string>;
    };
    expect(arg?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.USER_SESSION_START,
    );
    expect(arg?.attributes?.[PulseWebSemconv.AttributeKey.USER_ID]).toBe(
      "user-a",
    );
    expect(
      arg?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_USER_PREVIOUS_ID],
    ).toBeUndefined();
  });

  it("setUserId same value is a no-op for lifecycle", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserId("same");
    emitFn.mockClear();
    PulseWeb.setUserId("same");
    expect(userLifecycleCalls()).toHaveLength(0);
  });

  it("switching user emits end then start with pulse.user.previous_id", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserId("old-u");
    emitFn.mockClear();
    PulseWeb.setUserId("new-u");
    const life = userLifecycleCalls();
    expect(life).toHaveLength(2);
    const end = life[0]?.[0] as { attributes?: Record<string, string> };
    const start = life[1]?.[0] as { attributes?: Record<string, string> };
    expect(end?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.USER_SESSION_END,
    );
    expect(end?.attributes?.[PulseWebSemconv.AttributeKey.USER_ID]).toBe(
      "old-u",
    );
    expect(start?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.USER_SESSION_START,
    );
    expect(start?.attributes?.[PulseWebSemconv.AttributeKey.USER_ID]).toBe(
      "new-u",
    );
    expect(
      start?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_USER_PREVIOUS_ID],
    ).toBe("old-u");
  });

  it("setUserId(null) emits end only", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserId("gone");
    emitFn.mockClear();
    PulseWeb.setUserId(null);
    const life = userLifecycleCalls();
    expect(life).toHaveLength(1);
    const end = life[0]?.[0] as { attributes?: Record<string, string> };
    expect(end?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.USER_SESSION_END,
    );
    expect(end?.attributes?.[PulseWebSemconv.AttributeKey.USER_ID]).toBe(
      "gone",
    );
  });

  it("persists user id and rehydrates on next start without lifecycle", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserId("persist-me");
    expect(getPersistedUserId()).toBe("persist-me");
    emitFn.mockClear();
    await PulseWeb.shutdown();

    PulseWeb.start(makeConfig());
    await Promise.resolve();
    expect(userLifecycleCalls()).toHaveLength(0);
    expect(getPersistedUserId()).toBe("persist-me");
  });

  it("setUserProperty persists JSON blob", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserProperty("plan", "pro");
    expect(getPersistedUserProperties().plan).toBe("pro");
    PulseWeb.setUserProperty("plan", null);
    expect(getPersistedUserProperties().plan).toBeUndefined();
  });

  it("setUserId before start is silent no-op (no logs, no persistence)", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.setUserId("pre-init");
    expect(userLifecycleCalls()).toHaveLength(0);
    expect(getPersistedUserId()).toBeNull();
  });

  it("setUserProperty before start is silent no-op (no persistence)", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.setUserProperty("plan", "starter");
    expect(getPersistedUserProperties()).toEqual({});
  });

  it("setUserId(\"\") clears id and emits only session end", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserId("u-clear");
    emitFn.mockClear();

    PulseWeb.setUserId("");
    const life = userLifecycleCalls();
    expect(life).toHaveLength(1);
    const end = life[0]?.[0] as { attributes?: Record<string, string> };
    expect(end?.attributes?.[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.USER_SESSION_END,
    );
    expect(end?.attributes?.[PulseWebSemconv.AttributeKey.USER_ID]).toBe("u-clear");
    expect(getPersistedUserId()).toBeNull();
  });

  it("setUserProperties persists full merged snapshot and rehydrates on restart", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();

    PulseWeb.setUserProperties({
      plan: "pro",
      cohort: "beta",
      region: "in",
    });
    expect(getPersistedUserProperties()).toEqual({
      plan: "pro",
      cohort: "beta",
      region: "in",
    });

    PulseWeb.setUserProperties({ cohort: null, plan: "enterprise" });
    expect(getPersistedUserProperties()).toEqual({
      plan: "enterprise",
      region: "in",
    });

    await PulseWeb.shutdown();
    PulseWeb.start(makeConfig());
    await Promise.resolve();

    const p = getPersistedUserProperties();
    expect(p).toEqual({ plan: "enterprise", region: "in" });
    expect(userLifecycleCalls()).toHaveLength(0);
  });

  it("multiple user switches emit ordered end/start pairs", async () => {
    const { PulseWeb } = await import("../sdk");
    PulseWeb.start(makeConfig());
    await Promise.resolve();
    PulseWeb.setUserId("u1");
    emitFn.mockClear();

    PulseWeb.setUserId("u2");
    PulseWeb.setUserId("u3");

    const life = userLifecycleCalls();
    expect(life).toHaveLength(4);
    const pulseTypes = life.map(
      (c) =>
        (c[0] as { attributes?: Record<string, string> }).attributes?.[
          PulseWebSemconv.AttributeKey.PULSE_TYPE
        ],
    );
    expect(pulseTypes).toEqual([
      PulseWebSemconv.PulseType.USER_SESSION_END,
      PulseWebSemconv.PulseType.USER_SESSION_START,
      PulseWebSemconv.PulseType.USER_SESSION_END,
      PulseWebSemconv.PulseType.USER_SESSION_START,
    ]);
  });
});

describe("User identity persistence helpers", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("getPersistedUserProperties returns {} on invalid JSON", () => {
    window.localStorage.setItem("pulse_user_properties", "{not-json");
    expect(getPersistedUserProperties()).toEqual({});
  });

  it("getPersistedUserProperties returns {} for non-object payloads", () => {
    window.localStorage.setItem("pulse_user_properties", JSON.stringify(["x"]));
    expect(getPersistedUserProperties()).toEqual({});
    window.localStorage.setItem("pulse_user_properties", JSON.stringify("x"));
    expect(getPersistedUserProperties()).toEqual({});
  });

  it("getPersistedUserProperties keeps only string values", () => {
    window.localStorage.setItem(
      "pulse_user_properties",
      JSON.stringify({
        plan: "pro",
        active: true,
        age: 42,
        nested: { x: 1 },
      }),
    );
    expect(getPersistedUserProperties()).toEqual({ plan: "pro" });
  });
});
