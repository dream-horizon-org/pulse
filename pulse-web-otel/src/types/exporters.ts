import type { Attributes } from "@opentelemetry/api";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { MeterProvider } from "@opentelemetry/sdk-metrics";
import type { WebTracerProvider } from "@opentelemetry/sdk-trace-web";

import type { ExportSamplingGate } from "../sampling/export-sampling-gate";
import type { PulseMetricsToAddEntry, PulseSdkName } from "./remote-config";

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
  meteringSessionId: string;
  getMetricGlobalAttrs?: () => Attributes;
  logsUrl?: string;
  tracesUrl?: string;
  metricsUrl?: string;
  /** When true, use protobuf wire format instead of JSON. */
  useProtobuf?: boolean;

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
  /** Removes the pagehide listener registered by createProviders. Call in shutdown(). */
  cleanup: () => void;
}
