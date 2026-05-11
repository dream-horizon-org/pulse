/**
 * {@code PulseWebConfig.beforeSendData} — Android {@code PulseBeforeSendData} parity (generic → typed; {@code null} = drop).
 * Runs on the **main thread** at OTLP export batch time; see
 * docs/instrumentations/sdk-core/SPEC.md (beforeSend hooks).
 */

import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";
import type { ResourceMetrics } from "@opentelemetry/sdk-metrics";

import type {
  PulseExportSignal,
  PulseWebBeforeSendCallbacks,
  PulseWebBeforeSendConfig,
  ResolvedBeforeSend,
} from "./types/before-send";

export type {
  PulseBeforeSendResult,
  PulseExportSignal,
  PulseWebBeforeSendCallbacks,
  PulseWebBeforeSendConfig,
  ResolvedBeforeSend,
} from "./types/before-send";

export function resolveBeforeSend(
  input: PulseWebBeforeSendConfig | undefined,
): ResolvedBeforeSend | undefined {
  if (input === undefined) return undefined;
  if (typeof input === "function") {
    return { beforeSend: input };
  }
  return {
    beforeSend: input.beforeSend,
    beforeSendSpan: input.beforeSendSpan,
    beforeSendLog: input.beforeSendLog,
    beforeSendMetric: input.beforeSendMetric,
  };
}

export function hasBeforeSendForSpans(
  hooks: ResolvedBeforeSend | undefined,
): boolean {
  if (!hooks) return false;
  return !!(hooks.beforeSend || hooks.beforeSendSpan);
}

export function hasBeforeSendForLogs(
  hooks: ResolvedBeforeSend | undefined,
): boolean {
  if (!hooks) return false;
  return !!(hooks.beforeSend || hooks.beforeSendLog);
}

export function hasBeforeSendForMetrics(
  hooks: ResolvedBeforeSend | undefined,
): boolean {
  if (!hooks) return false;
  return !!(hooks.beforeSend || hooks.beforeSendMetric);
}

export function isReadableSpan(value: unknown): value is ReadableSpan {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as ReadableSpan).spanContext === "function"
  );
}

export function isReadableLogRecord(
  value: unknown,
): value is ReadableLogRecord {
  if (typeof value !== "object" || value === null) return false;
  if (isReadableSpan(value)) return false;
  const o = value as Record<string, unknown>;
  return "resource" in o;
}

export function isResourceMetrics(value: unknown): value is ResourceMetrics {
  return (
    typeof value === "object" &&
    value !== null &&
    "scopeMetrics" in (value as ResourceMetrics) &&
    Array.isArray((value as ResourceMetrics).scopeMetrics)
  );
}

/**
 * Runs {@link ResolvedBeforeSend.beforeSend} when present and validates the return shape
 * matches the input signal kind (otherwise drops, matching Android behaviour).
 */
export function applyBeforeSendGeneric(
  hooks: ResolvedBeforeSend | undefined,
  signal: PulseExportSignal,
): PulseExportSignal | null {
  if (!hooks?.beforeSend) return signal;
  const out = hooks.beforeSend(signal);
  if (out === null) return null;
  if (isReadableSpan(signal)) {
    return isReadableSpan(out) ? out : null;
  }
  if (isResourceMetrics(signal)) {
    return isResourceMetrics(out) ? out : null;
  }
  return isReadableLogRecord(out) ? out : null;
}

export function validateBeforeSendConfig(
  input: PulseWebBeforeSendConfig | undefined,
): void {
  if (input === undefined) return;
  if (typeof input === "function") return;
  if (typeof input !== "object" || input === null) {
    throw new Error(
      "[Pulse] beforeSendData must be a function or a callback object",
    );
  }
  const o = input as Record<string, unknown>;
  for (const key of [
    "beforeSend",
    "beforeSendSpan",
    "beforeSendLog",
    "beforeSendMetric",
  ] as const) {
    const v = o[key];
    if (v !== undefined && typeof v !== "function") {
      throw new Error(
        `[Pulse] beforeSendData.${key} must be a function when provided`,
      );
    }
  }
}
