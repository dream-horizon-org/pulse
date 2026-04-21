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
import { wrapLogExporterLifecycleDebug } from "./exporters/wrap-log-exporter-lifecycle-debug";
// Note: CompressionAlgorithm is Node-only in @opentelemetry/otlp-exporter-base 0.53.
// Browser gzip requires a custom XHR/fetch exporter wrapping CompressionStream — tracked as TODO.
import type {
  PushMetricExporter,
  ResourceMetrics,
} from "@opentelemetry/sdk-metrics";
import type { ExportResult } from "@opentelemetry/core";
import type { Attributes } from "@opentelemetry/api";

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

  export(
    metrics: ResourceMetrics,
    resultCallback: (result: ExportResult) => void,
  ): void {
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
        })) as ResourceMetrics["scopeMetrics"][number]["metrics"],
      })),
    };
    this.inner.export(patched, resultCallback);
  }

  forceFlush(): Promise<void> {
    return this.inner.forceFlush();
  }
  shutdown(): Promise<void> {
    return this.inner.shutdown();
  }

  selectAggregationTemporality: PushMetricExporter["selectAggregationTemporality"] =
    this.inner.selectAggregationTemporality?.bind(this.inner);

  selectAggregation: PushMetricExporter["selectAggregation"] =
    this.inner.selectAggregation?.bind(this.inner);
}

export interface ExporterConfig {
  endpointBaseUrl: string;
  apiKey: string;
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
  format?: "json" | "protobuf";
  /** Payload compression. Defaults to 'gzip'. Browser gzip is tracked as a TODO. */
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

  const baseLogExporter = new PulseBrowserLogExporter(
    { url: logsUrl, headers },
    {
      useProtobuf,
      useGzip,
      diskBuffer: pulseDisk,
      signalKind: "log",
    },
  );
  const logExporter =
    config.debugLogRecordLifecycle === true
      ? wrapLogExporterLifecycleDebug(baseLogExporter)
      : baseLogExporter;

  const batchLogProcessor = new BatchLogRecordProcessor(
    logExporter,
    batchOptions,
  );

  const loggerProvider = new LoggerProvider({ resource });
  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  if (config.debugLogRecordLifecycle === true) {
    console.log("[PulseWeb:logLifecycle]", {
      phase: "batch_config",
      scheduledDelayMillis: batchOptions.scheduledDelayMillis,
      maxQueueSize: batchOptions.maxQueueSize,
      maxExportBatchSize: batchOptions.maxExportBatchSize,
      note: "BatchLogRecordProcessor keeps a private in-memory queue; export runs on the timer, when the queue fills a batch slice, or on forceFlush (e.g. pagehide).",
    });
  }

  const rawMetricExporter = createPulseBrowserMetricExporter(
    { url: metricsUrl, headers },
    {
      useProtobuf,
      useGzip,
      diskBuffer: pulseDisk,
      signalKind: "metric",
    },
  );

  const metricExporter: PushMetricExporter = config.getMetricGlobalAttrs
    ? new GlobalAttributeInjectingMetricExporter(
        rawMetricExporter,
        config.getMetricGlobalAttrs,
      )
    : rawMetricExporter;

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
