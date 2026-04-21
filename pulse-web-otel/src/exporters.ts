// OTLP HTTP exporters (traces/logs/metrics) + batching + pagehide flush.
// Browser JSON or protobuf via @opentelemetry/otlp-transformer; optional gzip (CompressionStream);
// optional IndexedDB persistence on export failure (diskBuffering).
// Log export on pagehide uses fetch({ keepalive: true }) with JSON OTLP (see KeepaliveFetchLogExporter)
// because XHR-based sends from PulseBrowserLogExporter are cancelled during unload.
// See: web-sdk-plan/v1/01-foundation/pipeline.md

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
import type {
  LogRecordProcessor,
  LogRecordExporter,
  ReadableLogRecord,
} from "@opentelemetry/sdk-logs";
import type {
  PushMetricExporter,
  ResourceMetrics,
} from "@opentelemetry/sdk-metrics";
import type { ExportResult } from "@opentelemetry/core";
import { ExportResultCode } from "@opentelemetry/core";
import type { Attributes } from "@opentelemetry/api";
import { createExportLogsServiceRequest } from "@opentelemetry/otlp-transformer";

import { IdbSignalBuffer } from "./persistence/indexed-db";
import {
  PulseBrowserTraceExporter,
  PulseBrowserLogExporter,
  createPulseBrowserMetricExporter,
} from "./exporters/pulse-browser-otlp-exporters";
import { wrapLogExporterLifecycleDebug } from "./exporters/wrap-log-exporter-lifecycle-debug";
// Note: CompressionAlgorithm is Node-only in @opentelemetry/otlp-exporter-base 0.53.
// Browser gzip for the normal path is handled by PulseBrowser* exporters + otlp-transport.

/**
 * Wraps a {@link LogRecordExporter} and, when `_pagehide` is set to true, replaces the
 * normal export (XHR / custom transport from {@link PulseBrowserLogExporter}) with a
 * `fetch` call using `keepalive: true`.
 *
 * Normal batches still use the inner exporter (gzip, protobuf, disk buffer as configured).
 * The pagehide path sends JSON OTLP (`application/json`) without gzip so the body stays
 * small and compatible with keepalive limits.
 */
class KeepaliveFetchLogExporter implements LogRecordExporter {
  /** Set to true in the pagehide handler before calling `loggerProvider.forceFlush()`. */
  _pagehide = false;

  constructor(
    private readonly inner: LogRecordExporter,
    private readonly logsUrl: string,
    private readonly headers: Record<string, string>,
  ) {}

  export(
    logs: ReadableLogRecord[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    if (!this._pagehide) {
      this.inner.export(logs, resultCallback);
      return;
    }

    const body = JSON.stringify(
      createExportLogsServiceRequest(logs, {
        useHex: true,
        useLongBits: false,
      }),
    );

    const fetchHeaders: Record<string, string> = {
      "Content-Type": "application/json",
      ...this.headers,
    };

    fetch(this.logsUrl, {
      method: "POST",
      keepalive: true,
      headers: fetchHeaders,
      body,
    })
      .then(() => {
        resultCallback({ code: ExportResultCode.SUCCESS });
      })
      .catch(() => {
        resultCallback({ code: ExportResultCode.FAILED });
      });
  }

  forceFlush(): Promise<void> {
    const maybeFlush = this.inner as LogRecordExporter & {
      forceFlush?: () => Promise<void>;
    };
    return maybeFlush.forceFlush?.() ?? Promise.resolve();
  }

  shutdown(): Promise<void> {
    return this.inner.shutdown();
  }
}

/**
 * Wraps any PushMetricExporter and merges dynamic global attributes into every data point.
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
  getMetricGlobalAttrs?: () => Attributes;
  /**
   * Wire format for Pulse browser trace/log/metric exporters (JSON vs protobuf on the wire).
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

  const keepaliveFetchLogExporter = new KeepaliveFetchLogExporter(
    baseLogExporter,
    logsUrl,
    headers,
  );

  const logExporter =
    config.debugLogRecordLifecycle === true
      ? wrapLogExporterLifecycleDebug(keepaliveFetchLogExporter)
      : keepaliveFetchLogExporter;

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
        keepaliveFetchLogExporter._pagehide = true;
        void loggerProvider.forceFlush().finally(() => {
          keepaliveFetchLogExporter._pagehide = false;
        });
        void tracerProvider.forceFlush();
        void meterProvider.forceFlush();
      }
    };
    window.addEventListener("pagehide", pagehideHandler);
  }

  return { tracerProvider, loggerProvider, meterProvider };
}
