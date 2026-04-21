import type { Attributes } from "@opentelemetry/api";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { MeterProvider } from "@opentelemetry/sdk-metrics";
import type { WebTracerProvider } from "@opentelemetry/sdk-trace-web";

import type { IdbSignalBuffer } from "../persistence/indexed-db";
import type { ExportSamplingGate } from "../sampling/export-sampling-gate";
import type { PulseMetricsToAddEntry, PulseSdkName } from "./remote-config";

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
  meteringSessionId: string;
  getMetricGlobalAttrs?: () => Attributes;
  /**
   * OTLP wire format. Omitted or `"protobuf"` → `application/x-protobuf`.
   * Set `"json"` for human-readable bodies (dev, tests, DevTools).
   */
  format?: "json" | "protobuf";
  /** Payload compression for Pulse browser exporters. Defaults to 'gzip' when supported. */
  compression?: "gzip" | "none";
  batchOptions?: {
    scheduledDelayMillis?: number;
    maxQueueSize?: number;
    maxExportBatchSize?: number;
  };
  logsUrl?: string;
  tracesUrl?: string;
  metricsUrl?: string;
  /** When enabled, failed exports are written to IndexedDB for later replay. */
  diskBuffer?: {
    enabled: boolean;
    buffer: IdbSignalBuffer;
  };

  /** Log each log batch at OTLP export (see PulseWebConfig.debugLogRecordLifecycle). */
  debugLogRecordLifecycle?: boolean;

  /** Android-style export-time session + per-signal sampling (optional for tests). */
  samplingGate?: ExportSamplingGate;

  /**
   * When non-empty, records derived metrics from trace/log export batches before the sampling gate
   * using the same {@link MeterProvider} as RUM metrics (wired in {@link createProviders}).
   */
  metricsToAdd?: PulseMetricsToAddEntry[];
  metricsToAddSdkName?: PulseSdkName;
}

export interface ProviderBundle {
  tracerProvider: WebTracerProvider;
  loggerProvider: LoggerProvider;
  meterProvider: MeterProvider;
}
