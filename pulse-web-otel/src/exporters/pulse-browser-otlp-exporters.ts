import { diag } from "@opentelemetry/api";
import type { ExportResult } from "@opentelemetry/core";
import { ExportResultCode } from "@opentelemetry/core";
import {
  OTLPExporterError,
  type IOtlpExportDelegate,
  type OTLPExporterConfigBase,
} from "@opentelemetry/otlp-exporter-base";
import type { ISerializer } from "@opentelemetry/otlp-transformer";
import {
  JsonTraceSerializer,
  JsonLogsSerializer,
  JsonMetricsSerializer,
  ProtobufTraceSerializer,
  ProtobufLogsSerializer,
  ProtobufMetricsSerializer,
} from "@opentelemetry/otlp-transformer";
import type { ReadableSpan, SpanExporter } from "@opentelemetry/sdk-trace-web";
import type {
  ReadableLogRecord,
  LogRecordExporter,
} from "@opentelemetry/sdk-logs";
import type { ResourceMetrics } from "@opentelemetry/sdk-metrics";
import type { IExportTraceServiceResponse } from "@opentelemetry/otlp-transformer";
import type { IExportLogsServiceResponse } from "@opentelemetry/otlp-transformer";
import type { IExportMetricsServiceResponse } from "@opentelemetry/otlp-transformer";
import {
  OTLPMetricExporterBase,
  type OTLPMetricExporterOptions,
} from "@opentelemetry/exporter-metrics-otlp-http";

import { isGzipSupported } from "../utils/otlp-gzip";
import {
  buildBrowserExportTransport,
  type BrowserExportTransport,
} from "./otlp-transport";
import type { PersistMeta } from "../types/otlp-transport";
import type { PulseBrowserExporterOptions } from "../types/browser-exporter";

export type { PulseBrowserExporterOptions } from "../types/browser-exporter";

function staticHeaders(
  config: OTLPExporterConfigBase | undefined,
  contentType: string,
  useGzip: boolean,
): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": contentType };
  const ch = config?.headers;
  if (ch && typeof ch === "object" && !Array.isArray(ch)) {
    Object.assign(headers, ch as Record<string, string>);
  }
  if (useGzip) headers["Content-Encoding"] = "gzip";
  return headers;
}

function resolveTimeout(config: OTLPExporterConfigBase | undefined): number {
  return config?.timeoutMillis ?? 10_000;
}

abstract class PulseBrowserBatchExporter<BatchT, ServiceResponse> {
  protected readonly _serializer: ISerializer<BatchT, ServiceResponse>;
  protected _transport?: BrowserExportTransport;
  protected _shutdown = false;
  private readonly _sendingPromises: Promise<unknown>[] = [];
  protected readonly _pulse: PulseBrowserExporterOptions;
  protected readonly _contentType: string;
  protected readonly _config: OTLPExporterConfigBase | undefined;
  protected abstract defaultUrl(): string;

  protected constructor(
    config: OTLPExporterConfigBase | undefined,
    serializer: ISerializer<BatchT, ServiceResponse>,
    contentType: string,
    pulse: PulseBrowserExporterOptions,
  ) {
    this._config = config;
    this._serializer = serializer;
    this._contentType = contentType;
    this._pulse = pulse;
  }

  switchToKeepalive(): void {
    this._transport?.switchToKeepalive();
  }

  switchToBeacon(apiKey?: string, beaconRelayUrl?: string): void {
    this._transport?.switchToBeacon({
      apiKey,
      beaconRelayUrl,
      contentType: this._contentType,
    });
  }

  private ensureTransport(): void {
    if (this._transport) return;
    const pulse = this._pulse;
    const cfg = this._config;
    const useGzip = pulse.useGzip && isGzipSupported();
    const headers = staticHeaders(cfg, this._contentType, useGzip);
    const meta: PersistMeta = {
      contentType: this._contentType,
      ...(useGzip ? { contentEncoding: "gzip" as const } : {}),
    };
    const url = typeof cfg?.url === "string" ? cfg.url : this.defaultUrl();
    this._transport = buildBrowserExportTransport(
      { url, headers },
      {
        useGzip,
        diskPersistence: {
          enabled: pulse.diskBuffer.enabled,
          buffer: pulse.diskBuffer.buffer,
          signalKind: pulse.signalKind,
          meta,
        },
      },
    );
  }

  protected exportBatch(
    batch: BatchT,
    resultCallback: (result: ExportResult) => void,
  ): void {
    if (this._shutdown) {
      diag.debug("Exporter shut down; skip export");
      resultCallback({
        code: ExportResultCode.FAILED,
        error: new Error("Exporter shut down"),
      });
      return;
    }
    this.ensureTransport();
    const data = this._serializer.serializeRequest(batch);
    if (data == null) {
      resultCallback({
        code: ExportResultCode.FAILED,
        error: new OTLPExporterError("Could not serialize message"),
      });
      return;
    }
    const timeoutMillis = resolveTimeout(this._config);
    const promise = this._transport!.send(data, timeoutMillis).then(
      (response) => {
        if (response.status === "success") {
          resultCallback({ code: ExportResultCode.SUCCESS });
        } else if (response.status === "failure" && response.error) {
          resultCallback({
            code: ExportResultCode.FAILED,
            error: response.error,
          });
        } else if (response.status === "retryable") {
          resultCallback({
            code: ExportResultCode.FAILED,
            error: new OTLPExporterError("Export failed with retryable status"),
          });
        } else {
          resultCallback({
            code: ExportResultCode.FAILED,
            error: new OTLPExporterError("Export failed with unknown status"),
          });
        }
      },
      (err: unknown) =>
        resultCallback({
          code: ExportResultCode.FAILED,
          error: err instanceof Error ? err : new Error(String(err)),
        }),
    );
    this._sendingPromises.push(promise);
    const popPromise = () => {
      const i = this._sendingPromises.indexOf(promise);
      if (i >= 0) this._sendingPromises.splice(i, 1);
    };
    promise.then(popPromise, popPromise);
  }

  async shutdown(): Promise<void> {
    this._shutdown = true;
    await Promise.all(this._sendingPromises);
    this._transport?.shutdown();
  }

  async forceFlush(): Promise<void> {
    await Promise.all(this._sendingPromises);
  }
}

export class PulseBrowserTraceExporter
  extends PulseBrowserBatchExporter<ReadableSpan[], IExportTraceServiceResponse>
  implements SpanExporter
{
  constructor(
    config: OTLPExporterConfigBase | undefined,
    pulse: PulseBrowserExporterOptions,
  ) {
    const protobuf = pulse.useProtobuf;
    super(
      config,
      protobuf ? ProtobufTraceSerializer : JsonTraceSerializer,
      protobuf ? "application/x-protobuf" : "application/json",
      pulse,
    );
  }

  protected defaultUrl(): string {
    return "http://localhost:4318/v1/traces";
  }

  export(
    spans: ReadableSpan[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    this.exportBatch(spans, resultCallback);
  }
}

export class PulseBrowserLogExporter
  extends PulseBrowserBatchExporter<
    ReadableLogRecord[],
    IExportLogsServiceResponse
  >
  implements LogRecordExporter
{
  constructor(
    config: OTLPExporterConfigBase | undefined,
    pulse: PulseBrowserExporterOptions,
  ) {
    const protobuf = pulse.useProtobuf;
    super(
      config,
      protobuf ? ProtobufLogsSerializer : JsonLogsSerializer,
      protobuf ? "application/x-protobuf" : "application/json",
      pulse,
    );
  }

  protected defaultUrl(): string {
    return "http://localhost:4318/v1/logs";
  }

  export(
    logs: ReadableLogRecord[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    this.exportBatch(logs, resultCallback);
  }
}

class PulseBrowserMetricExportAdapter
  extends PulseBrowserBatchExporter<
    ResourceMetrics,
    IExportMetricsServiceResponse
  >
  implements IOtlpExportDelegate<ResourceMetrics>
{
  constructor(
    config: OTLPExporterConfigBase | undefined,
    pulse: PulseBrowserExporterOptions,
  ) {
    const protobuf = pulse.useProtobuf;
    super(
      config,
      protobuf ? ProtobufMetricsSerializer : JsonMetricsSerializer,
      protobuf ? "application/x-protobuf" : "application/json",
      pulse,
    );
  }

  protected defaultUrl(): string {
    return "http://localhost:4318/v1/metrics";
  }

  export(
    resourceMetrics: ResourceMetrics,
    resultCallback: (result: ExportResult) => void,
  ): void {
    this.exportBatch(resourceMetrics, resultCallback);
  }
}

export function createPulseBrowserMetricExporter(
  config: OTLPExporterConfigBase | undefined,
  pulse: PulseBrowserExporterOptions,
): OTLPMetricExporterBase {
  const delegate = new PulseBrowserMetricExportAdapter(config, pulse);
  return new OTLPMetricExporterBase(
    delegate,
    config as OTLPMetricExporterOptions,
  );
}
