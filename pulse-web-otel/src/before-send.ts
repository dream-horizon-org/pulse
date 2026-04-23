/**
 * {@code PulseWebConfig.beforeSendData} — Android {@code PulseBeforeSendData} parity (generic → typed; {@code null} = drop).
 * Runs on the **main thread** at OTLP export batch time; see
 * `web-sdk-plan/v1/01-foundation/before-send-web-android-parity.md`.
 */

import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";
import type { ResourceMetrics } from "@opentelemetry/sdk-metrics";

/** Optional typed hooks after {@link PulseWebBeforeSendCallbacks.beforeSend} (Android order). */
export interface PulseWebBeforeSendCallbacks {
  /**
   * Generic hook for every signal at export time. Runs first; return {@code null} to drop
   * (typed hooks are not called). Return a value that is not the expected kind for this
   * exporter → drop (Android parity).
   */
  beforeSend?: (signal: unknown) => unknown | null;
  beforeSendSpan?: (span: ReadableSpan) => ReadableSpan | null;
  beforeSendLog?: (log: ReadableLogRecord) => ReadableLogRecord | null;
  beforeSendMetric?: (metrics: ResourceMetrics) => ResourceMetrics | null;
}

/** Single callback (generic only) or full callback object. */
export type PulseWebBeforeSendConfig =
  | ((signal: unknown) => unknown | null)
  | PulseWebBeforeSendCallbacks;

/** Normalized hooks used by exporter wrappers. */
export interface ResolvedBeforeSend {
  beforeSend?: (signal: unknown) => unknown | null;
  beforeSendSpan?: (span: ReadableSpan) => ReadableSpan | null;
  beforeSendLog?: (log: ReadableLogRecord) => ReadableLogRecord | null;
  beforeSendMetric?: (metrics: ResourceMetrics) => ResourceMetrics | null;
}

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

export function validateBeforeSendConfig(
  input: PulseWebBeforeSendConfig | undefined,
): void {
  if (input === undefined) return;
  if (typeof input === "function") return;
  if (typeof input !== "object" || input === null) {
    throw new Error(
      "[PulseWeb] beforeSendData must be a function or a callback object",
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
        `[PulseWeb] beforeSendData.${key} must be a function when provided`,
      );
    }
  }
}
