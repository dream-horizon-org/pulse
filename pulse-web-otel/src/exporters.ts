// M1: OTLP HTTP exporters (traces/logs/metrics) + BatchSpanProcessor
// + sendBeacon flush on pagehide.
//
// Wire format: JSON (application/json) — the only format supported by browser-compatible
// OTLP exporters (@opentelemetry/exporter-*-otlp-http). The -otlp-proto packages use
// OTLPExporterNodeBase and are Node.js-only; they cannot be bundled by Vite/browser.
// Native browser protobuf support is tracked as a TODO (requires a custom fetch exporter).
//
// See: web-sdk-plan/v1/01-foundation/pipeline.md

import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
// Note: CompressionAlgorithm is Node-only in @opentelemetry/otlp-exporter-base 0.53.
// Browser gzip requires a custom XHR/fetch exporter wrapping CompressionStream — tracked as TODO.
import { WebTracerProvider, BatchSpanProcessor } from '@opentelemetry/sdk-trace-web';
import { LoggerProvider, BatchLogRecordProcessor } from '@opentelemetry/sdk-logs';
import { MeterProvider, PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import type { PushMetricExporter, ResourceMetrics } from '@opentelemetry/sdk-metrics';
import type { ExportResult } from '@opentelemetry/core';
import type { Resource } from '@opentelemetry/resources';
import type { SpanProcessor } from '@opentelemetry/sdk-trace-web';
import type { LogRecordProcessor } from '@opentelemetry/sdk-logs';
import type { Attributes } from '@opentelemetry/api';

/**
 * Wraps any PushMetricExporter and merges dynamic global attributes (session.id,
 * installation.id, screen.name, platform, etc.) into every data point at export time.
 * This is necessary because metrics do not go through the SpanProcessor / LogRecordProcessor
 * pipeline — they need a separate injection point.
 */
class GlobalAttributeInjectingMetricExporter implements PushMetricExporter {
  constructor(
    private readonly inner: PushMetricExporter,
    private readonly getGlobalAttrs: () => Attributes,
  ) {}

  export(metrics: ResourceMetrics, resultCallback: (result: ExportResult) => void): void {
    const extra = this.getGlobalAttrs();
    const patched: ResourceMetrics = {
      ...metrics,
      scopeMetrics: metrics.scopeMetrics.map((sm) => ({
        ...sm,
        // Cast required: TypeScript loses the discriminated-union narrowing when
        // we spread each MetricData, but the shape is preserved — only attributes
        // on each DataPoint are extended with global attrs (extra takes lower
        // priority than per-instrument attrs so they cannot override them).
        metrics: sm.metrics.map((m) => ({
          ...m,
          dataPoints: m.dataPoints.map((dp) => ({
            ...dp,
            attributes: { ...extra, ...dp.attributes },
          })),
        })) as ResourceMetrics['scopeMetrics'][number]['metrics'],
      })),
    };
    this.inner.export(patched, resultCallback);
  }

  forceFlush(): Promise<void> { return this.inner.forceFlush(); }
  shutdown(): Promise<void>   { return this.inner.shutdown(); }

  selectAggregationTemporality: PushMetricExporter['selectAggregationTemporality'] =
    this.inner.selectAggregationTemporality?.bind(this.inner);

  selectAggregation: PushMetricExporter['selectAggregation'] =
    this.inner.selectAggregation?.bind(this.inner);
}

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
  /** Stable UUID generated at SDK init — sent as X-Pulse-Metering-Session-ID on every request. */
  meteringSessionId: string;
  /**
   * Called at each metric export to get current global attributes (session.id, screen.name, etc.).
   * If omitted, no extra attributes are injected into metric data points.
   */
  getMetricGlobalAttrs?: () => Attributes;
  /**
   * Wire format. Currently unused — browser OTLP exporters always send JSON
   * (application/json). Protobuf support requires a custom browser fetch exporter
   * and is tracked as a TODO.
   */
  format?: 'json' | 'protobuf';
  /** Payload compression. Defaults to 'gzip'. Browser gzip is tracked as a TODO. */
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
  // config.format and config.compression are reserved for future use —
  // browser-compatible protobuf and gzip require custom fetch exporters (TODO).

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
  const traceExporter = new OTLPTraceExporter({ url: tracesUrl, headers });

  const batchSpanProcessor = new BatchSpanProcessor(traceExporter, batchOptions);

  const tracerProvider = new WebTracerProvider({ resource });
  for (const processor of spanProcessors) {
    tracerProvider.addSpanProcessor(processor);
  }
  tracerProvider.addSpanProcessor(batchSpanProcessor);

  // ── Logs ────────────────────────────────────────────────────────────────────
  const logExporter = new OTLPLogExporter({ url: logsUrl, headers });

  const batchLogProcessor = new BatchLogRecordProcessor(logExporter, batchOptions);

  const loggerProvider = new LoggerProvider({ resource });
  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  // ── Metrics ─────────────────────────────────────────────────────────────────
  const rawMetricExporter = new OTLPMetricExporter({ url: metricsUrl, headers });

  // Wrap with global attribute injector so every data point carries session.id,
  // installation.id, screen.name, platform, etc. — mirroring what the
  // GlobalAttributesProcessor does for spans and logs.
  const metricExporter: PushMetricExporter = config.getMetricGlobalAttrs
    ? new GlobalAttributeInjectingMetricExporter(rawMetricExporter, config.getMetricGlobalAttrs)
    : rawMetricExporter;

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
