// OTLP HTTP exporters (traces/logs/metrics) + batching + pagehide flush.
// Uses browser-compatible @opentelemetry/exporter-*-otlp-http (JSON over XHR/fetch).
// Log export on pagehide uses fetch({ keepalive: true }) with JSON OTLP (see KeepaliveFetchLogExporter)
// because unload can cancel normal exporter requests before they complete.
// See: web-sdk-plan/v1/01-foundation/pipeline.md

import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { OTLPLogExporter } from "@opentelemetry/exporter-logs-otlp-http";
import { OTLPMetricExporter } from "@opentelemetry/exporter-metrics-otlp-http";
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

/**
 * Wraps a log exporter and, when `_pagehide` is set to true, replaces the normal export
 * with `fetch(..., { keepalive: true })` and JSON OTLP.
 */
class KeepaliveFetchLogExporter implements LogRecordExporter {
  _pagehide = false;

  constructor(
    private readonly inner: OTLPLogExporter,
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
    return this.inner.forceFlush();
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
  format?: "json" | "protobuf";
  compression?: "gzip" | "none";
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

  const traceExporter = new OTLPTraceExporter({ url: tracesUrl, headers });
  const batchSpanProcessor = new BatchSpanProcessor(
    traceExporter,
    batchOptions,
  );

  const tracerProvider = new WebTracerProvider({ resource });
  for (const processor of spanProcessors) {
    tracerProvider.addSpanProcessor(processor);
  }
  tracerProvider.addSpanProcessor(batchSpanProcessor);

  const innerLogExporter = new OTLPLogExporter({ url: logsUrl, headers });
  const keepaliveFetchLogExporter = new KeepaliveFetchLogExporter(
    innerLogExporter,
    logsUrl,
    headers,
  );

  const batchLogProcessor = new BatchLogRecordProcessor(
    keepaliveFetchLogExporter,
    batchOptions,
  );

  const loggerProvider = new LoggerProvider({ resource });
  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  const rawMetricExporter = new OTLPMetricExporter({
    url: metricsUrl,
    headers,
  });
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
