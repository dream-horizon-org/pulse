/**
 * Scoped error log contract helpers (~6–8 assertions) for m3-errors parity.
 */
import { expect, getAttr, type OtlpLogRecord } from "./fixture";

export function assertCommonLogContract(
  log: OtlpLogRecord,
  pulseType: "device.crash" | "non_fatal",
): void {
  expect(getAttr(log.attributes, "pulse.type")).toBe(pulseType);
  expect(getAttr(log.attributes, "session.id")).toBeTruthy();
  expect(getAttr(log.attributes, "screen.name")).toBeTruthy();
  expect(getAttr(log.attributes, "url.path")).toBeTruthy();
  expect(getAttr(log.attributes, "exception.message")).toBeTruthy();
}

export function assertDeviceCrashSeverity(log: OtlpLogRecord): void {
  expect(log.severityText).toBe("FATAL");
  expect(log.severityNumber).toBe(21);
}

export function assertNonFatalSeverity(log: OtlpLogRecord): void {
  expect(log.severityText).toBe("WARN");
  expect(log.severityNumber).toBe(13);
}

export function assertStackLineCount(log: OtlpLogRecord, minLines = 2): void {
  const stack = String(getAttr(log.attributes, "exception.stacktrace") ?? "");
  const lines = stack.split("\n").filter((l) => l.trim().length > 0);
  expect(lines.length).toBeGreaterThanOrEqual(minLines);
}

/** ERR-07 / ERR-17 / ERR-18 / ERR-19 — same-origin crash location attrs. */
export function assertErrorLocationAttrs(log: OtlpLogRecord): void {
  const filename = getAttr(log.attributes, "error.filename");
  expect(typeof filename).toBe("string");
  expect(String(filename).length).toBeGreaterThan(0);

  const line = Number(getAttr(log.attributes, "error.lineno"));
  const col = Number(getAttr(log.attributes, "error.colno"));
  expect(Number.isFinite(line)).toBe(true);
  expect(Number.isFinite(col)).toBe(true);
  expect(line).toBeGreaterThan(0);
  expect(col).toBeGreaterThanOrEqual(0);
}

/** ERR-09 / ERR-16 — multi-line stack with per-frame content. */
export function assertMultilineExceptionStack(
  log: OtlpLogRecord,
  minLines = 2,
): void {
  const stackAttr = getAttr(log.attributes, "exception.stacktrace");
  expect(stackAttr).toBeDefined();
  expect(typeof stackAttr).toBe("string");
  assertStackLineCount(log, minLines);
  const stack = String(stackAttr);
  expect(stack).toContain("\n");
}
