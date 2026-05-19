import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { InteractionConfig } from "../interactions/interaction-models";
import { PulseWebLogger } from "../pulse-web-logger";
import { InteractionConfigFetcher } from "../interactions/config-fetcher";

const THIRTY_MIN_MS = 30 * 60 * 1000;

const VALID_CONFIGS: InteractionConfig[] = [
  {
    id: 1,
    name: "Checkout Flow",
    description: "Checkout Flow",
    events: [
      { name: "checkout_step_1", isBlacklisted: false },
      { name: "checkout_step_2", isBlacklisted: false, props: null },
    ],
    thresholdInMs: 5000,
    uptimeLowerLimitInMs: 1000,
    uptimeMidLimitInMs: 3000,
    uptimeUpperLimitInMs: 6000,
    globalBlacklistedEvents: [],
  },
];

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("InteractionConfigFetcher", () => {
  it("enabled=false performs no fetch", async () => {
    const fetchFn = vi.fn();
    const f = new InteractionConfigFetcher(
      { enabled: false, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );
    await f.init();
    expect(fetchFn).not.toHaveBeenCalled();
    expect(f.getConfigs()).toEqual([]);
  });

  it("non-OK response warns and keeps process alive", async () => {
    const warnSpy = vi
      .spyOn(PulseWebLogger, "warn")
      .mockImplementation(() => {});
    const fetchFn = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    });
    const f = new InteractionConfigFetcher(
      { enabled: true, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );

    await expect(f.init()).resolves.toBeUndefined();
    expect(warnSpy).toHaveBeenCalled();
  });

  it("invalid schema warns and does not throw", async () => {
    const warnSpy = vi
      .spyOn(PulseWebLogger, "warn")
      .mockImplementation(() => {});
    const fetchFn = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ invalid: true }),
    });
    const f = new InteractionConfigFetcher(
      { enabled: true, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );

    await expect(f.init()).resolves.toBeUndefined();
    expect(warnSpy).toHaveBeenCalled();
    expect(f.getConfigs()).toEqual([]);
  });

  it("accepts nullable and omitted props", async () => {
    const withNullableProps: InteractionConfig[] = [
      {
        id: 2,
        name: "Nullable Props Flow",
        description: "Nullable Props Flow",
        events: [
          { name: "a", isBlacklisted: false, props: null },
          { name: "b", isBlacklisted: false },
        ],
        thresholdInMs: 1000,
        uptimeLowerLimitInMs: 100,
        uptimeMidLimitInMs: 200,
        uptimeUpperLimitInMs: 300,
        globalBlacklistedEvents: [
          { name: "x", isBlacklisted: true, props: [] },
        ],
      },
    ];
    const fetchFn = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => withNullableProps,
    });
    const f = new InteractionConfigFetcher(
      { enabled: true, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );

    await expect(f.init()).resolves.toBeUndefined();
    expect(f.getConfigs()).toEqual(withNullableProps);
  });

  it("handles corrupted cache/localStorage failures with no throw", async () => {
    const getItemSpy = vi
      .spyOn(window.localStorage.__proto__, "getItem")
      .mockImplementation(() => {
        throw new Error("blocked");
      });
    const fetchFn = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => VALID_CONFIGS,
    });
    const f = new InteractionConfigFetcher(
      { enabled: true, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );

    await expect(f.init()).resolves.toBeUndefined();
    expect(getItemSpy).toHaveBeenCalled();
    expect(f.getConfigs()).toEqual(VALID_CONFIGS);
  });

  it("no periodic refresh — fetch called exactly once at init (Android parity)", async () => {
    // P34 fix: periodic 30-min refresh was dropped to align with Android which
    // fetches configs once per session. This prevents in-flight flows being
    // silently destroyed on config refresh boundaries.
    vi.useFakeTimers();
    const fetchFn = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => VALID_CONFIGS,
    });
    const f = new InteractionConfigFetcher(
      { enabled: true, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );

    await f.init();
    expect(fetchFn).toHaveBeenCalledTimes(1);

    // Advance well past the old 30-min interval — no additional fetch should fire.
    vi.advanceTimersByTime(THIRTY_MIN_MS * 3);
    await Promise.resolve();
    expect(fetchFn).toHaveBeenCalledTimes(1);

    f.destroy();
    vi.useRealTimers();
  });

  it("cache hit stays available when refresh fails", async () => {
    window.localStorage.setItem(
      "pulse_interaction_config",
      JSON.stringify(VALID_CONFIGS),
    );
    const fetchFn = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    });
    const f = new InteractionConfigFetcher(
      { enabled: true, url: "http://x", headers: {} },
      fetchFn as unknown as typeof fetch,
    );

    await f.init();
    expect(f.getConfigs()).toEqual(VALID_CONFIGS);
  });
});
