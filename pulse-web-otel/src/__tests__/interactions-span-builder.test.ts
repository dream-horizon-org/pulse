import { describe, expect, it, vi } from "vitest";
import { ROOT_CONTEXT, SpanKind, SpanStatusCode } from "@opentelemetry/api";

import { InteractionSpanBuilder } from "../interactions/interaction-span-builder";
import { INTERACTION_PROP_KEYS } from "../constants/interactions/interaction-prop-keys";
import { PulseWebSemconv } from "../semconv";

function makeSpan() {
  return {
    setAttribute: vi.fn(),
    setAttributes: vi.fn(),
    addEvent: vi.fn(),
    setStatus: vi.fn(),
    end: vi.fn(),
  };
}

function makeTracer(span = makeSpan()) {
  return {
    startSpan: vi.fn().mockReturnValue(span),
    span,
  };
}

function makeInteraction(overrides: Record<string, unknown> = {}) {
  const t0 = 1_000_000_000_000; // 1e12 ns = 1000s
  return {
    id: "int-1",
    name: "TestFlow",
    props: {
      [INTERACTION_PROP_KEYS.NAME]: "TestFlow",
      [INTERACTION_PROP_KEYS.CONFIG_ID]: "cfg-1",
      [INTERACTION_PROP_KEYS.TIME_TO_COMPLETE_IN_NANO]: 2_000_000_000,
      [INTERACTION_PROP_KEYS.APDEX_SCORE]: 0.9,
      [INTERACTION_PROP_KEYS.USER_CATEGORY]: "Excellent",
      [INTERACTION_PROP_KEYS.IS_ERROR]: false,
      [INTERACTION_PROP_KEYS.LOCAL_EVENTS]: [
        { name: "step_a", timeInNano: t0 },
        { name: "step_b", timeInNano: t0 + 2_000_000_000 },
      ],
      ...overrides,
    },
  };
}

describe("InteractionSpanBuilder.emitInteraction", () => {
  it("starts span with ROOT_CONTEXT (no parent)", () => {
    const { startSpan } = makeTracer();
    const tracer = { startSpan } as never;
    const builder = new InteractionSpanBuilder(tracer);

    builder.emitInteraction(makeInteraction());

    expect(startSpan).toHaveBeenCalledWith(
      "TestFlow",
      expect.objectContaining({ kind: SpanKind.INTERNAL }),
      ROOT_CONTEXT,
    );
  });

  it("sets pulse.type = interaction", () => {
    const span = makeSpan();
    const tracer = makeTracer(span).startSpan.mockReturnValue(span) && {
      startSpan: vi.fn().mockReturnValue(span),
    };
    const builder = new InteractionSpanBuilder(tracer as never);

    builder.emitInteraction(makeInteraction());

    const attrs = span.setAttributes.mock.calls[0]?.[0] as Record<
      string,
      unknown
    >;
    expect(attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE]).toBe(
      PulseWebSemconv.PulseType.INTERACTION,
    );
  });

  it("span start/end derived from first/last event timestamps", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);
    const t0 = 1_500_000_000_000;

    builder.emitInteraction(
      makeInteraction({
        [INTERACTION_PROP_KEYS.LOCAL_EVENTS]: [
          { name: "step_a", timeInNano: t0 },
          { name: "step_b", timeInNano: t0 + 3_000_000_000 },
        ],
      }),
    );

    const startMs = Math.round(t0 / 1_000_000);
    const endMs = Math.round((t0 + 3_000_000_000) / 1_000_000);
    expect(tracer.startSpan).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ startTime: startMs }),
      ROOT_CONTEXT,
    );
    expect(span.end).toHaveBeenCalledWith(endMs);
  });

  it("success: is_error=false → OK status, no error attrs", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);

    builder.emitInteraction(makeInteraction());

    expect(span.setStatus).toHaveBeenCalledWith({
      code: SpanStatusCode.OK,
    });
    const attrs = span.setAttributes.mock.calls[0]?.[0] as Record<
      string,
      unknown
    >;
    expect(PulseWebSemconv.InteractionAttributeKey.ERROR_TYPE in attrs).toBe(
      false,
    );
    expect(PulseWebSemconv.InteractionAttributeKey.ERROR_MESSAGE in attrs).toBe(
      false,
    );
  });

  it("error: is_error=true → ERROR status, forced poor category, error attrs present", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);

    builder.emitInteraction(
      makeInteraction({
        [INTERACTION_PROP_KEYS.IS_ERROR]: true,
        [INTERACTION_PROP_KEYS.ERROR_TYPE]: "timeout",
        [INTERACTION_PROP_KEYS.ERROR_MESSAGE]: "step_b not reached",
      }),
    );

    expect(span.setStatus).toHaveBeenCalledWith(
      expect.objectContaining({ code: SpanStatusCode.ERROR }),
    );
    const attrs = span.setAttributes.mock.calls[0]?.[0] as Record<
      string,
      unknown
    >;
    expect(attrs[PulseWebSemconv.InteractionAttributeKey.IS_ERROR]).toBe(true);
    expect(attrs[PulseWebSemconv.InteractionAttributeKey.USER_CATEGORY]).toBe(
      "Poor",
    );
    expect(attrs[PulseWebSemconv.InteractionAttributeKey.APDEX_SCORE]).toBe(
      0.0,
    );
    expect(attrs[PulseWebSemconv.InteractionAttributeKey.ERROR_TYPE]).toBe(
      "timeout",
    );
    expect(attrs[PulseWebSemconv.InteractionAttributeKey.ERROR_MESSAGE]).toBe(
      "step_b not reached",
    );
  });

  it("adds OTel events for each local event at correct timestamp", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);
    const t0 = 2_000_000_000_000;

    builder.emitInteraction(
      makeInteraction({
        [INTERACTION_PROP_KEYS.LOCAL_EVENTS]: [
          { name: "step_a", timeInNano: t0 },
          { name: "step_b", timeInNano: t0 + 1_000_000_000, props: { x: "1" } },
        ],
      }),
    );

    expect(span.addEvent).toHaveBeenCalledTimes(2);
    expect(span.addEvent).toHaveBeenNthCalledWith(
      1,
      "step_a",
      undefined,
      Math.round(t0 / 1_000_000),
    );
    expect(span.addEvent).toHaveBeenNthCalledWith(
      2,
      "step_b",
      { x: "1" },
      Math.round((t0 + 1_000_000_000) / 1_000_000),
    );
  });

  it("complete_time on success reflects nanos value from props", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);

    builder.emitInteraction(
      makeInteraction({
        [INTERACTION_PROP_KEYS.TIME_TO_COMPLETE_IN_NANO]: 5_000_000_000,
      }),
    );

    const attrs = span.setAttributes.mock.calls[0]?.[0] as Record<
      string,
      unknown
    >;
    expect(attrs[PulseWebSemconv.InteractionAttributeKey.COMPLETE_TIME]).toBe(
      5_000_000_000,
    );
  });

  it("fallback timestamps when no local events", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);

    builder.emitInteraction(
      makeInteraction({
        [INTERACTION_PROP_KEYS.LOCAL_EVENTS]: [],
        [INTERACTION_PROP_KEYS.TIME_TO_COMPLETE_IN_NANO]: 1_000_000_000,
      }),
    );

    const firstCall = tracer.startSpan.mock.calls[0] as [
      string,
      { startTime: number },
      unknown,
    ];
    const startTime = firstCall[1].startTime;
    const endTime = span.end.mock.calls[0]![0] as number;
    expect(endTime).toBeGreaterThan(startTime);
  });

  it("does not export pulse.internal.* attributes", () => {
    const span = makeSpan();
    const tracer = { startSpan: vi.fn().mockReturnValue(span) };
    const builder = new InteractionSpanBuilder(tracer as never);

    builder.emitInteraction(makeInteraction());

    const attrs = span.setAttributes.mock.calls[0]?.[0] as Record<
      string,
      unknown
    >;
    const internalKeys = Object.keys(attrs).filter((k) =>
      k.startsWith("pulse.internal."),
    );
    expect(internalKeys).toHaveLength(0);
  });
});
