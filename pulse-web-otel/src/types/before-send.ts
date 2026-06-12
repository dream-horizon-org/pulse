import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";
import type { ResourceMetrics } from "@opentelemetry/sdk-metrics";

/**
 * OTLP batch item types seen by {@link PulseWebBeforeSendCallbacks.beforeSend} at export time.
 * Keeps the generic hook type-safe; malformed returns are dropped at runtime (Android parity).
 */
export type PulseExportSignal =
  | ReadableSpan
  | ReadableLogRecord
  | ResourceMetrics;

/** Return {@code null} from {@link PulseWebBeforeSendCallbacks.beforeSend} to drop the batch item. */
export type PulseBeforeSendResult = PulseExportSignal | null;

/** Optional typed hooks after {@link PulseWebBeforeSendCallbacks.beforeSend} (Android order). */
export interface PulseWebBeforeSendCallbacks {
  /**
   * Generic hook for every signal at export time. Runs first; return {@code null} to drop
   * (typed hooks are not called). Return a different signal kind than the input → drop (Android parity).
   */
  beforeSend?: (signal: PulseExportSignal) => PulseBeforeSendResult;
  beforeSendSpan?: (span: ReadableSpan) => ReadableSpan | null;
  beforeSendLog?: (log: ReadableLogRecord) => ReadableLogRecord | null;
  beforeSendMetric?: (metrics: ResourceMetrics) => ResourceMetrics | null;
}

/** Single callback (generic only) or full callback object. */
export type PulseWebBeforeSendConfig =
  | ((signal: PulseExportSignal) => PulseBeforeSendResult)
  | PulseWebBeforeSendCallbacks;

/** Normalized hooks used by exporter wrappers. */
export interface ResolvedBeforeSend {
  beforeSend?: (signal: PulseExportSignal) => PulseBeforeSendResult;
  beforeSendSpan?: (span: ReadableSpan) => ReadableSpan | null;
  beforeSendLog?: (log: ReadableLogRecord) => ReadableLogRecord | null;
  beforeSendMetric?: (metrics: ResourceMetrics) => ResourceMetrics | null;
}
