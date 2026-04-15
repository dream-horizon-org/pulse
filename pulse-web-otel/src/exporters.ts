// OTLP HTTP exporters (traces/logs/metrics) + batching + pagehide flush.
// Browser JSON or protobuf via @opentelemetry/otlp-transformer; optional gzip (CompressionStream);
// optional IndexedDB persistence on export failure (diskBuffering).

import {
  WebTracerProvider,
  BatchSpanProcessor,
} from "@opentelemetry/sdk-trace-web";
import {
  LoggerProvider,
  BatchLogRecordProcessor,
} from "@opentelemetry/sdk-logs";
import {
  MeterProvider,
  PeriodicExportingMetricReader,
} from "@opentelemetry/sdk-metrics";
import type { Resource } from "@opentelemetry/resources";
import type { SpanProcessor } from "@opentelemetry/sdk-trace-web";
import type { LogRecordProcessor } from "@opentelemetry/sdk-logs";

import { IdbSignalBuffer } from "./persistence/indexed-db";
import {
  PulseBrowserTraceExporter,
  PulseBrowserLogExporter,
  createPulseBrowserMetricExporter,
} from "./exporters/pulse-browser-otlp-exporters";

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
  meteringSessionId: string;
  format?: "json" | "protobuf";
  /** Default: gzip when CompressionStream is available; set 'none' to disable. */
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
  const headers = {
    "X-API-KEY": config.apiKey,
    "X-Pulse-Metering-Session-ID": config.meteringSessionId,
  };

  const batchOptions = {
    ...DEFAULT_BATCH_OPTIONS,
    ...config.batchOptions,
  };

  const tracesUrl = config.tracesUrl ?? `${config.endpointBaseUrl}/v1/traces`;
  const logsUrl = config.logsUrl ?? `${config.endpointBaseUrl}/v1/logs`;
  const metricsUrl =
    config.metricsUrl ?? `${config.endpointBaseUrl}/v1/metrics`;

  const useProtobuf = config.format === "protobuf";
  const useGzip = config.compression !== "none";
  const diskOpts = config.diskBuffer;
  if (diskOpts?.enabled === true && !diskOpts.buffer) {
    throw new Error(
      "[PulseWeb] diskBuffer.buffer is required when diskBuffering.enabled",
    );
  }
  const pulseDisk = {
    enabled: diskOpts?.enabled === true,
    buffer: diskOpts?.buffer ?? new IdbSignalBuffer(),
  };

  const traceExporter = new PulseBrowserTraceExporter(
    { url: tracesUrl, headers },
    {
      useProtobuf,
      useGzip,
      diskBuffer: pulseDisk,
      signalKind: "trace",
    },
  );

  const batchSpanProcessor = new BatchSpanProcessor(
    traceExporter,
    batchOptions,
  );

  const tracerProvider = new WebTracerProvider({ resource });
  for (const processor of spanProcessors) {
    tracerProvider.addSpanProcessor(processor);
  }
  tracerProvider.addSpanProcessor(batchSpanProcessor);

  const logExporter = new PulseBrowserLogExporter(
    { url: logsUrl, headers },
    {
      useProtobuf,
      useGzip,
      diskBuffer: pulseDisk,
      signalKind: "log",
    },
  );

  const batchLogProcessor = new BatchLogRecordProcessor(
    logExporter,
    batchOptions,
  );

  const loggerProvider = new LoggerProvider({ resource });
  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  const metricExporter = createPulseBrowserMetricExporter(
    { url: metricsUrl, headers },
    {
      useProtobuf,
      useGzip,
      diskBuffer: pulseDisk,
      signalKind: "metric",
    },
  );

  const metricReader = new PeriodicExportingMetricReader({
    exporter: metricExporter,
    exportIntervalMillis: batchOptions.scheduledDelayMillis,
  });

  const meterProvider = new MeterProvider({
    resource,
    readers: [metricReader],
  });

  if (typeof window !== "undefined") {
    const pagehideHandler = (e: PageTransitionEvent) => {
      if (!e.persisted) {
        void tracerProvider.forceFlush();
        void loggerProvider.forceFlush();
        void meterProvider.forceFlush();
      }
    };
    window.addEventListener("pagehide", pagehideHandler);
  }

  return { tracerProvider, loggerProvider, meterProvider };
}
