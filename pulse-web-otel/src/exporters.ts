// OTLP HTTP exporters (traces/logs/metrics) + batching; unload prep via ProviderBundle hooks.
// Default wire format follows `ExporterConfig.useProtobuf` (JSON vs protobuf). Compression: off.
// On real document unload, `prepareForDocumentUnload` swaps trace + log browser transports to
// keepalive `fetch` (same pipeline as normal export); see `buildBrowserExportTransport`.
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
import type { Meter } from "@opentelemetry/api";
import type { Resource } from "@opentelemetry/resources";
import type { SpanExporter, SpanProcessor } from "@opentelemetry/sdk-trace-web";
import type {
  LogRecordProcessor,
  LogRecordExporter,
} from "@opentelemetry/sdk-logs";
import type {
  PushMetricExporter,
  ResourceMetrics,
} from "@opentelemetry/sdk-metrics";
import type { ExportResult } from "@opentelemetry/core";
import type { Attributes } from "@opentelemetry/api";

import { IdbSignalBuffer } from "./persistence/indexed-db";
import type { ExporterConfig, ProviderBundle } from "./types/exporters";
export type { ExporterConfig, ProviderBundle } from "./types/exporters";
import {
  PulseBrowserTraceExporter,
  PulseBrowserLogExporter,
  createPulseBrowserMetricExporter,
} from "./exporters/pulse-browser-otlp-exporters";
import { DEFAULT_BATCH_OPTIONS } from "./constants/exporters";
import type { ExportSamplingGate } from "./sampling/export-sampling-gate";
import {
  MetricsToAddLogRecordExporter,
  MetricsToAddSpanExporter,
  SampledLogRecordExporter,
  SampledPushMetricExporter,
  SampledSpanExporter,
} from "./sampling/sampling-exporters";
import {
  hasBeforeSendForLogs,
  hasBeforeSendForMetrics,
  hasBeforeSendForSpans,
} from "./before-send";
import {
  BeforeSendLogRecordExporter,
  BeforeSendMetricExporter,
  BeforeSendSpanExporter,
} from "./exporters/before-send-exporters";

// Compression is hardcoded off — not exposed in public config (mirrors Android internals)
const USE_GZIP = false; // no compression — keep it simple and compatible

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

  const useProtobuf = config.useProtobuf ?? false;
  const batchOptions = { ...DEFAULT_BATCH_OPTIONS };

  const tracesUrl = config.tracesUrl ?? `${config.endpointBaseUrl}/v1/traces`;
  const logsUrl = config.logsUrl ?? `${config.endpointBaseUrl}/v1/logs`;
  const metricsUrl =
    config.metricsUrl ?? `${config.endpointBaseUrl}/v1/metrics`;

  const disk = config.diskBuffering;
  const diskEnabled = disk?.enabled !== false;
  const idbBuffer = new IdbSignalBuffer(
    disk?.maxAgeMs,
    disk?.maxCacheSizeBytes,
  );
  const pulseDisk = {
    enabled: diskEnabled,
    buffer: idbBuffer,
  };

  const beforeSendData = config.beforeSendData;

  const innerTraceExporter = new PulseBrowserTraceExporter(
    { url: tracesUrl, headers },
    {
      useProtobuf,
      useGzip: USE_GZIP,
      diskBuffer: pulseDisk,
      signalKind: "trace",
    },
  );
  let traceExporter: SpanExporter = innerTraceExporter;
  if (config.samplingGate) {
    traceExporter = new SampledSpanExporter(traceExporter, config.samplingGate);
  }
  const metricsEntries = config.metricsToAdd ?? [];
  let meterForDerivedMetrics: Meter | undefined;
  if (metricsEntries.length > 0 && config.metricsToAddSdkName) {
    traceExporter = new MetricsToAddSpanExporter(traceExporter, {
      entries: metricsEntries,
      sdkName: config.metricsToAddSdkName,
      getMeter: () => meterForDerivedMetrics!,
    });
  }
  if (beforeSendData && hasBeforeSendForSpans(beforeSendData)) {
    traceExporter = new BeforeSendSpanExporter(traceExporter, beforeSendData);
  }
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
      useGzip: USE_GZIP,
      diskBuffer: pulseDisk,
      signalKind: "log",
    },
  );

  let logInnerChain: LogRecordExporter = baseLogExporter;
  if (config.samplingGate !== undefined) {
    logInnerChain = new SampledLogRecordExporter(
      logInnerChain,
      config.samplingGate,
    );
  }
  let logExporterHead: LogRecordExporter = logInnerChain;
  if (metricsEntries.length > 0 && config.metricsToAddSdkName) {
    logExporterHead = new MetricsToAddLogRecordExporter(logExporterHead, {
      entries: metricsEntries,
      sdkName: config.metricsToAddSdkName,
      getMeter: () => meterForDerivedMetrics!,
    });
  }
  if (beforeSendData && hasBeforeSendForLogs(beforeSendData)) {
    logExporterHead = new BeforeSendLogRecordExporter(
      logExporterHead,
      beforeSendData,
    );
  }

  const batchLogProcessor = new BatchLogRecordProcessor(
    logExporterHead,
    batchOptions,
  );

  const loggerProvider = new LoggerProvider({ resource });
  for (const processor of logProcessors) {
    loggerProvider.addLogRecordProcessor(processor);
  }
  loggerProvider.addLogRecordProcessor(batchLogProcessor);

  const rawMetricExporter = createPulseBrowserMetricExporter(
    { url: metricsUrl, headers },
    {
      useProtobuf,
      useGzip: USE_GZIP,
      diskBuffer: pulseDisk,
      signalKind: "metric",
    },
  );

  let metricExporter: PushMetricExporter = rawMetricExporter;
  if (config.samplingGate) {
    metricExporter = new SampledPushMetricExporter(
      metricExporter,
      config.samplingGate,
    );
  }
  if (config.getMetricGlobalAttrs) {
    metricExporter = new GlobalAttributeInjectingMetricExporter(
      metricExporter,
      config.getMetricGlobalAttrs,
    );
  }
  if (beforeSendData && hasBeforeSendForMetrics(beforeSendData)) {
    metricExporter = new BeforeSendMetricExporter(
      metricExporter,
      beforeSendData,
    );
  }

  const metricReader = new PeriodicExportingMetricReader({
    exporter: metricExporter,
    exportIntervalMillis: batchOptions.scheduledDelayMillis,
  });

  const meterProvider = new MeterProvider({
    resource,
    readers: [metricReader],
  });

  if (metricsEntries.length > 0) {
    meterForDerivedMetrics = meterProvider.getMeter(
      "pulse.web.metrics_derived",
      "1.0.0",
    );
  }

  const prepareForDocumentUnload = (): void => {
    // Switch to beacon-first unload transport:
    // - sendBeacon for small payloads (browser-guaranteed delivery even after page close)
    // - keepalive fetch fallback for payloads > 64 KiB
    innerTraceExporter.switchToBeacon(config.apiKey, config.beaconRelayUrl);
    baseLogExporter.switchToBeacon(config.apiKey, config.beaconRelayUrl);
  };

  const cleanup = () => {};

  return {
    tracerProvider,
    loggerProvider,
    meterProvider,
    cleanup,
    prepareForDocumentUnload,
    ...(diskEnabled ? { idbSignalBuffer: idbBuffer } : {}),
  };
}
