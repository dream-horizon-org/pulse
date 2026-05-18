import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";

import {
  ClickEventBuffer,
  DEFAULT_RAGE_CONFIG,
  type PendingClick,
} from "../instrumentations/click-rage-buffer";

function click(
  x: number,
  y: number,
  t: number,
  hasTarget = true,
): PendingClick {
  return {
    xPx: x,
    yPx: y,
    timestampMs: t,
    tapEpochMs: t,
    hasTarget,
    widgetName: "BTN",
    viewportWidthPx: 400,
    viewportHeightPx: 800,
  };
}

describe("ClickEventBuffer", () => {
  let now: number;
  let monotonicNow: () => number;

  beforeEach(() => {
    now = 0;
    monotonicNow = () => now;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("buffers singleton tap until flush", () => {
    const onRage = vi.fn();
    const onEmit = vi.fn();
    const buf = new ClickEventBuffer({
      densityScale: 1,
      rageConfig: DEFAULT_RAGE_CONFIG,
      onRage,
      onEmit,
      monotonicNow: () => now,
    });

    buf.record(click(10, 10, 0));
    expect(onEmit).not.toHaveBeenCalled();
    buf.flush();
    expect(onRage).not.toHaveBeenCalled();
    expect(onEmit).toHaveBeenCalledTimes(1);
    expect(onEmit.mock.calls[0]![0].xPx).toBe(10);
  });

  it("emits rage when threshold taps cluster then window elapses", () => {
    vi.useFakeTimers();
    const onRage = vi.fn();
    const onEmit = vi.fn();
    const buf = new ClickEventBuffer({
      densityScale: 1,
      rageConfig: { ...DEFAULT_RAGE_CONFIG, timeWindowMs: 1000, threshold: 3 },
      onRage,
      onEmit,
      monotonicNow,
    });

    buf.record(click(50, 50, 0));
    now = 10;
    buf.record(click(52, 51, 10));
    now = 20;
    buf.record(click(51, 49, 20));
    expect(onRage).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1000);
    expect(onRage).toHaveBeenCalledTimes(1);
    expect(onRage.mock.calls[0]![0].count).toBeGreaterThanOrEqual(3);
    expect(onEmit).not.toHaveBeenCalled();
  });

  it("dispose flushes rage cluster without dropping", () => {
    const onRage = vi.fn();
    const onEmit = vi.fn();
    const buf = new ClickEventBuffer({
      densityScale: 1,
      rageConfig: {
        ...DEFAULT_RAGE_CONFIG,
        timeWindowMs: 60_000,
        threshold: 3,
      },
      onRage,
      onEmit,
      monotonicNow: () => now,
    });

    buf.record(click(5, 5, 0));
    now = 1;
    buf.record(click(6, 6, 1));
    now = 2;
    buf.record(click(5, 6, 2));
    expect(onRage).not.toHaveBeenCalled();

    buf.dispose();
    expect(onRage).toHaveBeenCalledTimes(1);
  });
});
