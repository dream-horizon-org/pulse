// M1: OTLP HTTP exporters (traces/logs/metrics) + BatchSpanProcessor
// + gzip compression + sendBeacon flush on pagehide.
//
// Wire format: protobuf by default (application/x-protobuf).
// Set config.export.format = 'json' for human-readable JSON (dev/DevTools mode).
//
// See: web-sdk-plan/v1/01-foundation/pipeline.md

import { OTLPTraceExporter as OTLPTraceExporterJSON } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPLogExporter as OTLPLogExporterJSON } from '@opentelemetry/exporter-logs-otlp-http';
import { OTLPMetricExporter as OTLPMetricExporterJSON } from '@opentelemetry/exporter-metrics-otlp-http';
import { OTLPTraceExporter as OTLPTraceExporterProto } from '@opentelemetry/exporter-trace-otlp-proto';
import { OTLPLogExporter as OTLPLogExporterProto } from '@opentelemetry/exporter-logs-otlp-proto';
import { OTLPMetricExporter as OTLPMetricExporterProto } from '@opentelemetry/exporter-metrics-otlp-proto';
// Note: CompressionAlgorithm is Node-only in @opentelemetry/otlp-exporter-base 0.53.
// Browser gzip requires a custom XHR/fetch exporter wrapping CompressionStream — tracked as TODO.
import { WebTracerProvider, BatchSpanProcessor } from '@opentelemetry/sdk-trace-web';
import { LoggerProvider, BatchLogRecordProcessor } from '@opentelemetry/sdk-logs';
import { MeterProvider, PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import type { Resource } from '@opentelemetry/resources';
import type { SpanProcessor } from '@opentelemetry/sdk-trace-web';
import type { LogRecordProcessor } from '@opentelemetry/sdk-logs';

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
  /** Stable UUID generated at SDK init — sent as X-Pulse-Metering-Session-ID on every request. */
  meteringSessionId: string;
  /** Wire format. Defaults to 'protobuf'. Use 'json' in dev for readable DevTools payloads. */
  format?: 'json' | 'protobuf';
  /** Payload compression. Defaults to 'gzip'. Use 'none' in dev for readable DevTools payloads. */
  compression?: 'gzip' | 'none';
  batchOptions?: {
    scheduledDelayMillis?: number;
    maxQueueSize?: number;
    maxExportBatchSize?: number;
  };
  logsUrl?: string;
  tracesUrl?: string;
  metricsUrl?: string;
}

export interface ProviderBundle {
  tracerProvider: WebTracerProvider;
  loggerProvider: LoggerProvider;
  meterProvider: MeterProvider;
}

const DEFAULT_BATCH_OPTIONS = {
  scheduledDelayMillis: 5000,
  maxQueueSize: 2048,
  maxExportBatchSize: 512,
  exportTimeoutMillis: 30000,
};

export function createProviders(
  config: ExporterConfig,
  resource: Resource,
  spanProcessors: SpanProcessor[],
  logProcessors: LogRecordProcessor[],
): ProviderBundle {
  const useProto = (config.format ?? 'protobuf') === 'protobuf';
  // config.compression is stored for future use when browser gzip exporter is implemented.

  // Custom headers on every OTLP request — Content-Type is set by the exporter itself.
  const headers: Record<string, string> = {
    'X-API-KEY': config.apiKey,
    'X-Pulse-Metering-Session-ID': config.meteringSessionId,
  };

  const batchOptions = {
    ...DEFAULT_BATCH_OPTIONS,
    ...config.batchOptions,
  };

  const tracesUrl  = config.tracesUrl  ?? `${config.endpointBaseUrl}/v1/traces`;
  const logsUrl    = config.logsUrl    ?? `${config.endpointBaseUrl}/v1/logs`;
  const metricsUrl = config.metricsUrl ?? `${config.endpointBaseUrl}/v1/metrics`;

  // ── Traces ──────────────────────────────────────────────────────────────────
  const traceExporter = useProto
    ? new OTLPTraceExporterProto({ url: tracesUrl, headers })
    : new OTLPTraceExporterJSON({ url: tracesUrl, headers });

  const batchSpanProcessor = new BatchSpanProcessor(traceExporter, batchOptions);

  const tracerProvider = new WebTracerProvider({ resource });
  for (const processor of spanProcessors) {
    tracerProvider.addSpanProcessor(processor);
  }
  tracerProvider.addSpanProcessor(batchSpanProcessor);

  // ── Logs ────────────────────────────────────────────────────────────────────
  const logExporter = useProto
    ? new OTLPLogExporterProto({ url: logsUrl, headers })
    : new OTLPLogExporterJSON({ url: logsUrl, headers });

  const batchLogProcessor = new BatchLogRecordProcessor(logExporter, batchOptions);

  const loggerProvider = new LoggerProvider({ resource });
  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  // ── Metrics ─────────────────────────────────────────────────────────────────
  const metricExporter = useProto
    ? new OTLPMetricExporterProto({ url: metricsUrl, headers })
    : new OTLPMetricExporterJSON({ url: metricsUrl, headers });

  const metricReader = new PeriodicExportingMetricReader({
    exporter: metricExporter,
    exportIntervalMillis: batchOptions.scheduledDelayMillis,
  });

  const meterProvider = new MeterProvider({
    resource,
    readers: [metricReader],
  });

  // ── Pagehide flush ──────────────────────────────────────────────────────────
  if (typeof window !== 'undefined') {
    const pagehideHandler = (e: PageTransitionEvent) => {
      if (!e.persisted) {
        void tracerProvider.forceFlush();
        void loggerProvider.forceFlush();
        void meterProvider.forceFlush();
      }
    };
    window.addEventListener('pagehide', pagehideHandler);
  }

  return { tracerProvider, loggerProvider, meterProvider };
}
