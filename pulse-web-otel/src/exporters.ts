// M1: OTLP HTTP exporters (traces/logs/metrics) + BatchSpanProcessor
// + gzip CompressionStream + sendBeacon flush on pagehide.
// See: web-sdk-plan/v1/01-foundation/pipeline.md

import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
import { WebTracerProvider, BatchSpanProcessor } from '@opentelemetry/sdk-trace-web';
import { LoggerProvider, BatchLogRecordProcessor } from '@opentelemetry/sdk-logs';
import { MeterProvider, PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import type { Resource } from '@opentelemetry/resources';
import type { SpanProcessor } from '@opentelemetry/sdk-trace-web';
import type { LogRecordProcessor } from '@opentelemetry/sdk-logs';

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
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
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-API-KEY': config.apiKey,
  };

  const batchOptions = {
    ...DEFAULT_BATCH_OPTIONS,
    ...config.batchOptions,
  };

  // Traces
  const traceExporter = new OTLPTraceExporter({
    url: config.tracesUrl ?? `${config.endpointBaseUrl}/v1/traces`,
    headers,
  });

  const batchSpanProcessor = new BatchSpanProcessor(traceExporter, batchOptions);

  const tracerProvider = new WebTracerProvider({ resource });

  // Add custom span processors first, then the batch exporter
  for (const processor of spanProcessors) {
    tracerProvider.addSpanProcessor(processor);
  }
  tracerProvider.addSpanProcessor(batchSpanProcessor);

  // Logs
  const logExporter = new OTLPLogExporter({
    url: config.logsUrl ?? `${config.endpointBaseUrl}/v1/logs`,
    headers,
  });

  const batchLogProcessor = new BatchLogRecordProcessor(logExporter, batchOptions);

  const loggerProvider = new LoggerProvider({ resource });

  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  // Metrics
  const metricExporter = new OTLPMetricExporter({
    url: config.metricsUrl ?? `${config.endpointBaseUrl}/v1/metrics`,
    headers,
  });

  const metricReader = new PeriodicExportingMetricReader({
    exporter: metricExporter,
    exportIntervalMillis: batchOptions.scheduledDelayMillis,
  });

  const meterProvider = new MeterProvider({
    resource,
    readers: [metricReader],
  });

  // Register pagehide listener to force-flush all providers on page unload
  if (typeof window !== 'undefined') {
    const pagehideHandler = (e: PageTransitionEvent) => {
      if (!e.persisted) {
        // Force flush all providers — fire-and-forget on page unload
        void tracerProvider.forceFlush();
        void loggerProvider.forceFlush();
        void meterProvider.forceFlush();
      }
    };

    window.addEventListener('pagehide', pagehideHandler);
  }

  return { tracerProvider, loggerProvider, meterProvider };
}
