import { describe, it, expect, beforeEach } from "vitest";
import { SessionCrashCountProcessor } from "../processors/session-crash-count-processor";
import { PulseWebSemconv } from "../semconv";
import type { SdkLogRecord } from "@opentelemetry/sdk-logs";

const K = PulseWebSemconv.AttributeKey;
const T = PulseWebSemconv.PulseType;

// Minimal SdkLogRecord stub — only the fields our processor touches.
function makeLog(pulseType: string): SdkLogRecord {
  const attrs: Record<string, unknown> = { [K.PULSE_TYPE]: pulseType };
  return {
    attributes: attrs,
    setAttribute(key: string, value: unknown) {
      attrs[key] = value;
    },
  } as unknown as SdkLogRecord;
}

describe("SessionCrashCountProcessor", () => {
  let processor: SessionCrashCountProcessor;

  beforeEach(() => {
    processor = new SessionCrashCountProcessor();
  });

  // ── counter accumulation ──────────────────────────────────────────────────

  it("increments crashCount on device.crash", () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    expect(processor.getCounters().crashCount).toBe(2);
  });

  it("increments nonFatalCount on non_fatal", () => {
    processor.onEmit(makeLog(T.NON_FATAL));
    expect(processor.getCounters().nonFatalCount).toBe(1);
  });

  it("ignores unrelated pulse types", () => {
    processor.onEmit(makeLog(T.SESSION_START));
    processor.onEmit(makeLog(T.SCREEN_LOAD));
    const { crashCount, nonFatalCount } = processor.getCounters();
    expect(crashCount).toBe(0);
    expect(nonFatalCount).toBe(0);
  });

  // ── session.end attachment ────────────────────────────────────────────────

  it("attaches crash count to session.end when > 0", () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    const endLog = makeLog(T.SESSION_END);
    processor.onEmit(endLog);

    expect(endLog.attributes[K.SESSION_CRASH_COUNT]).toBe(2);
  });

  it("attaches non_fatal count to session.end when > 0", () => {
    processor.onEmit(makeLog(T.NON_FATAL));
    const endLog = makeLog(T.SESSION_END);
    processor.onEmit(endLog);

    expect(endLog.attributes[K.SESSION_NON_FATAL_COUNT]).toBe(1);
  });

  it("attaches both counts when both > 0 — Android parity", () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    processor.onEmit(makeLog(T.NON_FATAL));
    processor.onEmit(makeLog(T.NON_FATAL));
    const endLog = makeLog(T.SESSION_END);
    processor.onEmit(endLog);

    expect(endLog.attributes[K.SESSION_CRASH_COUNT]).toBe(1);
    expect(endLog.attributes[K.SESSION_NON_FATAL_COUNT]).toBe(2);
  });

  it("omits crash count when 0 — matches Android ?.let pattern", () => {
    const endLog = makeLog(T.SESSION_END);
    processor.onEmit(endLog);

    expect(endLog.attributes[K.SESSION_CRASH_COUNT]).toBeUndefined();
  });

  it("omits non_fatal count when 0 — matches Android ?.let pattern", () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    const endLog = makeLog(T.SESSION_END);
    processor.onEmit(endLog);

    expect(endLog.attributes[K.SESSION_NON_FATAL_COUNT]).toBeUndefined();
  });

  // ── counter reset after session.end ──────────────────────────────────────

  it("resets counters after session.end — next session starts at 0", () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    processor.onEmit(makeLog(T.SESSION_END));

    // Second session — no crashes
    const endLog2 = makeLog(T.SESSION_END);
    processor.onEmit(endLog2);

    expect(endLog2.attributes[K.SESSION_CRASH_COUNT]).toBeUndefined();
    expect(processor.getCounters().crashCount).toBe(0);
  });

  it("accumulates correctly across two sessions", () => {
    // Session 1: 2 crashes
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    const end1 = makeLog(T.SESSION_END);
    processor.onEmit(end1);
    expect(end1.attributes[K.SESSION_CRASH_COUNT]).toBe(2);

    // Session 2: 1 crash only
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    const end2 = makeLog(T.SESSION_END);
    processor.onEmit(end2);
    expect(end2.attributes[K.SESSION_CRASH_COUNT]).toBe(1);
  });

  // ── reset() / shutdown() ─────────────────────────────────────────────────

  it("reset() clears all counters", () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    processor.onEmit(makeLog(T.NON_FATAL));
    processor.reset();
    expect(processor.getCounters()).toEqual({
      crashCount: 0,
      nonFatalCount: 0,
    });
  });

  it("shutdown() clears counters and resolves", async () => {
    processor.onEmit(makeLog(T.DEVICE_CRASH));
    await processor.shutdown();
    expect(processor.getCounters()).toEqual({
      crashCount: 0,
      nonFatalCount: 0,
    });
  });
});
