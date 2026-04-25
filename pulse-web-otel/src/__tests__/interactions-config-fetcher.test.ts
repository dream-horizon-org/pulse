import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { InteractionConfig } from "../interactions/interaction-models";
import { PulseWebLogger } from "../pulse-web-logger";
import { InteractionConfigFetcher } from "../interactions/config-fetcher";

const THIRTY_MIN_MS = 30 * 60 * 1000;

const VALID_CONFIGS: InteractionConfig[] = [
  {
    id: "checkout_flow",
    name: "Checkout Flow",
    events: [
      { name: "checkout_step_1", required: true },
      { name: "checkout_step_2", required: true },
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

  it("refresh timer chains at 30-minute intervals", async () => {
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

    vi.advanceTimersByTime(THIRTY_MIN_MS);
    await Promise.resolve();
    expect(fetchFn).toHaveBeenCalledTimes(2);

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
