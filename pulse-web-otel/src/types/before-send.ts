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
