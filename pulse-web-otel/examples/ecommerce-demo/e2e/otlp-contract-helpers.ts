/**
 * Shared OTLP contract assertions for manual-gap E2E specs.
 */
import { expect, getAttr, type OtlpLogRecord, type OtlpSpan } from "./fixture";

export function assertTimestampSanity(record: OtlpLogRecord | OtlpSpan): void {
  const isSpan = "startTimeUnixNano" in record && record.startTimeUnixNano;
  const start = isSpan
    ? (record as OtlpSpan).startTimeUnixNano
    : (record as OtlpLogRecord).timeUnixNano;
  const end = isSpan
    ? (record as OtlpSpan).endTimeUnixNano
    : (record as OtlpLogRecord).timeUnixNano;
  expect(start).toBeTruthy();
  const startMs = Number(BigInt(String(start)) / 1_000_000n);
  const now = Date.now();
  expect(startMs).toBeGreaterThan(now - 60 * 60 * 1000);
  expect(startMs).toBeLessThanOrEqual(now + 5_000);
  if (end !== undefined && end !== start) {
    const endMs = Number(BigInt(String(end)) / 1_000_000n);
    expect(endMs).toBeGreaterThanOrEqual(startMs);
  }
}

export function assertDeviceCrashContract(log: OtlpLogRecord): void {
  expect(getAttr(log.attributes, "pulse.type")).toBe("device.crash");
  expect(log.severityText).toBe("FATAL");
  expect(log.severityNumber).toBe(21);
  expect(getAttr(log.attributes, "session.id")).toBeTruthy();
  expect(getAttr(log.attributes, "exception.message")).toBeTruthy();
  const stack = String(getAttr(log.attributes, "exception.stacktrace") ?? "");
  expect(
    stack.split("\n").filter((l) => l.trim().length > 0).length,
  ).toBeGreaterThanOrEqual(1);
}

export function assertWebVitalContract(
  attrs: OtlpLogRecord["attributes"],
): void {
  expect(getAttr(attrs, "platform")).toBe("web");
  expect(getAttr(attrs, "pulse.type")).toBe("web_vital");
  const name = getAttr(attrs, "web_vital.name");
  expect(typeof name).toBe("string");
  const value = getAttr(attrs, "web_vital.value");
  expect(typeof value).toBe("number");
  expect(Number.isFinite(value as number)).toBe(true);
  const rating = getAttr(attrs, "web_vital.rating");
  expect(["good", "needs-improvement", "poor"]).toContain(rating);
  const sessionId = getAttr(attrs, "session.id");
  expect(typeof sessionId).toBe("string");
  expect(sessionId as string).toMatch(/^[0-9a-f-]{36}$/i);
}
