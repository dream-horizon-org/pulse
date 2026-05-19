import { describe, expect, it, vi } from "vitest";
import type { Span } from "@opentelemetry/sdk-trace-base";
import { InteractionContextSpanProcessor } from "../processors/interaction-context-span-processor";
import { PulseWebSemconv } from "../semconv";
import type { PulseAttributes } from "../types/attributes";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeSpan(pulseType?: string): Span {
  const attrs: Record<string, unknown> = {};
  if (pulseType) attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE] = pulseType;
  return {
    attributes: attrs,
    endTime: [2000, 0] as [number, number],
    setAttribute: vi.fn((k: string, v: unknown) => {
      attrs[k] = v;
    }),
  } as unknown as Span;
}

function running(
  ...flows: Array<{ id: string; name: string }>
): () => Array<{ id: string; name: string }> {
  return () => flows;
}

function trackMock(): {
  fn: (name: string, attrs: PulseAttributes, timeMs: number) => void;
  calls: Array<{ name: string; timeMs: number }>;
} {
  const calls: Array<{ name: string; timeMs: number }> = [];
  const fn = (name: string, _attrs: PulseAttributes, timeMs: number): void => {
    calls.push({ name, timeMs });
  };
  return { fn, calls };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("InteractionContextSpanProcessor — onStart", () => {
  it("0 running flows → no setAttribute called", () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setGetRunning(running());
    const span = makeSpan("screen_load");
    proc.onStart(span, {} as never);
    expect(span.setAttribute).not.toHaveBeenCalled();
  });

  it("1 mid-sequence flow → NAMES and IDS set with correct values", () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setGetRunning(running({ id: "abc", name: "Checkout" }));
    const span = makeSpan("screen_load");
    proc.onStart(span, {} as never);
    expect(span.setAttribute).toHaveBeenCalledWith(
      PulseWebSemconv.InteractionAttributeKey.NAMES,
      ["Checkout"],
    );
    expect(span.setAttribute).toHaveBeenCalledWith(
      PulseWebSemconv.InteractionAttributeKey.IDS,
      ["abc"],
    );
  });

  it("2 concurrent flows → both appear in arrays", () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setGetRunning(
      running(
        { id: "id1", name: "Flow A" },
        { id: "id2", name: "Flow B" },
      ),
    );
    const span = makeSpan("screen_load");
    proc.onStart(span, {} as never);
    expect(span.setAttribute).toHaveBeenCalledWith(
      PulseWebSemconv.InteractionAttributeKey.NAMES,
      ["Flow A", "Flow B"],
    );
    expect(span.setAttribute).toHaveBeenCalledWith(
      PulseWebSemconv.InteractionAttributeKey.IDS,
      ["id1", "id2"],
    );
  });

  it("span is pulse.type=interaction → no setAttribute (no self-referential stamp)", () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setGetRunning(running({ id: "abc", name: "Checkout" }));
    const span = makeSpan(PulseWebSemconv.PulseType.INTERACTION);
    proc.onStart(span, {} as never);
    expect(span.setAttribute).not.toHaveBeenCalled();
  });

  it("getRunning null → no throw, no setAttribute", () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setGetRunning(null);
    const span = makeSpan("screen_load");
    expect(() => proc.onStart(span, {} as never)).not.toThrow();
    expect(span.setAttribute).not.toHaveBeenCalled();
  });
});

describe("InteractionContextSpanProcessor — onEnd", () => {
  it("screen_load → trackEvent called with correct name and timeMs", () => {
    const proc = new InteractionContextSpanProcessor();
    const { fn, calls } = trackMock();
    proc.setTrackEvent(fn);
    const span = makeSpan(PulseWebSemconv.PulseType.SCREEN_LOAD);
    // endTime [2000, 0] → 2000 * 1000 + 0 = 2_000_000 ms → but wait…
    // endTime[0] = seconds, endTime[1] = nanoseconds
    // timeMs = Math.round(2000 * 1000 + 0 / 1e6) = 2_000_000
    proc.onEnd(span);
    expect(calls).toHaveLength(1);
    expect(calls[0]!.name).toBe(PulseWebSemconv.PulseType.SCREEN_LOAD);
    expect(calls[0]!.timeMs).toBe(2_000_000);
  });

  it("network.200 → trackEvent called", () => {
    const proc = new InteractionContextSpanProcessor();
    const { fn, calls } = trackMock();
    proc.setTrackEvent(fn);
    const span = makeSpan("network.200");
    proc.onEnd(span);
    expect(calls).toHaveLength(1);
    expect(calls[0]!.name).toBe("network.200");
  });

  it("network.404 → trackEvent called", () => {
    const proc = new InteractionContextSpanProcessor();
    const { fn, calls } = trackMock();
    proc.setTrackEvent(fn);
    const span = makeSpan("network.404");
    proc.onEnd(span);
    expect(calls).toHaveLength(1);
    expect(calls[0]!.name).toBe("network.404");
  });

  it("pulse.type=interaction → no trackEvent", () => {
    const proc = new InteractionContextSpanProcessor();
    const { fn, calls } = trackMock();
    proc.setTrackEvent(fn);
    const span = makeSpan(PulseWebSemconv.PulseType.INTERACTION);
    proc.onEnd(span);
    expect(calls).toHaveLength(0);
  });

  it("ineligible type (session.start) → no trackEvent", () => {
    const proc = new InteractionContextSpanProcessor();
    const { fn, calls } = trackMock();
    proc.setTrackEvent(fn);
    const span = makeSpan(PulseWebSemconv.PulseType.SESSION_START);
    proc.onEnd(span);
    expect(calls).toHaveLength(0);
  });

  it("trackEvent null → no throw", () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setTrackEvent(null);
    const span = makeSpan(PulseWebSemconv.PulseType.SCREEN_LOAD);
    expect(() => proc.onEnd(span)).not.toThrow();
  });
});

describe("InteractionContextSpanProcessor — lifecycle", () => {
  it("shutdown nulls callbacks and resolves", async () => {
    const proc = new InteractionContextSpanProcessor();
    proc.setGetRunning(running({ id: "x", name: "Y" }));
    proc.setTrackEvent((_n, _a, _t) => {});
    await expect(proc.shutdown()).resolves.toBeUndefined();
    // After shutdown: no stamps or throws
    const span = makeSpan("screen_load");
    expect(() => proc.onStart(span, {} as never)).not.toThrow();
    expect(span.setAttribute).not.toHaveBeenCalled();
  });

  it("forceFlush resolves immediately", async () => {
    const proc = new InteractionContextSpanProcessor();
    await expect(proc.forceFlush()).resolves.toBeUndefined();
  });
});
