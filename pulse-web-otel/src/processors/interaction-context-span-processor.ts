import type { Context } from "@opentelemetry/api";
import type { Span, SpanProcessor } from "@opentelemetry/sdk-trace-base";
import { PulseWebSemconv } from "../semconv";
import type { PulseAttributes } from "../types/attributes";

type GetRunning = () => Array<{ id: string; name: string }>;
type TrackEvent = (
  name: string,
  attrs: PulseAttributes,
  timeMs: number,
) => void;

const ELIGIBLE_REVERSE_TYPES = new Set([
  PulseWebSemconv.PulseType.SCREEN_LOAD,
  PulseWebSemconv.PulseType.SCREEN_SESSION,
]);

function isEligibleForReverse(pulseType: string): boolean {
  return (
    ELIGIBLE_REVERSE_TYPES.has(pulseType as never) ||
    pulseType.startsWith("network.")
  );
}

/**
 * Forward-stamps `pulse.interaction.names` / `pulse.interaction.ids` on every
 * span started while an interaction flow is mid-sequence, and reverse-feeds
 * eligible span ends (screen_load, screen_session, network.*) into
 * `trackEvent` so they can advance a waiting interaction flow.
 *
 * Android parity: `InteractionAttributesSpanAppender` + partial
 * `InteractionLogListener` correlation logic.
 *
 * Wiring: injected as a span processor between globalAttrsProcessor and
 * filterProcessor in sdk.ts. Callbacks are set to null before provider
 * shutdown so the processor is safe to hold after SDK.shutdown().
 */
export class InteractionContextSpanProcessor implements SpanProcessor {
  private getRunning: GetRunning | null = null;
  private trackEvent: TrackEvent | null = null;

  setGetRunning(fn: GetRunning | null): void {
    this.getRunning = fn;
  }

  setTrackEvent(fn: TrackEvent | null): void {
    this.trackEvent = fn;
  }

  onStart(span: Span, _context: Context): void {
    const fn = this.getRunning;
    if (fn == null) return;
    const running = fn();
    if (running.length === 0) return;
    // Skip the terminal interaction span itself — no self-referential stamp.
    const attrs = span.attributes as Record<string, unknown>;
    if (
      attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE] ===
      PulseWebSemconv.PulseType.INTERACTION
    )
      return;
    span.setAttribute(
      PulseWebSemconv.InteractionAttributeKey.NAMES,
      running.map((r) => r.name),
    );
    span.setAttribute(
      PulseWebSemconv.InteractionAttributeKey.IDS,
      running.map((r) => r.id),
    );
  }

  onEnd(span: Span): void {
    const fn = this.trackEvent;
    if (fn == null) return;
    const attrs = span.attributes as Record<string, unknown>;
    const pulseType = attrs[PulseWebSemconv.AttributeKey.PULSE_TYPE];
    if (typeof pulseType !== "string" || !isEligibleForReverse(pulseType))
      return;
    const timeMs = Math.round(
      span.endTime[0] * 1000 + span.endTime[1] / 1_000_000,
    );
    fn(pulseType, attrs as PulseAttributes, timeMs);
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    this.getRunning = null;
    this.trackEvent = null;
    return Promise.resolve();
  }
}
