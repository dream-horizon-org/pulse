import { diag } from "@opentelemetry/api";
import { baggageUtils, getEnv } from "@opentelemetry/core";
import {
  OTLPExporterBase,
  OTLPExporterError,
  appendResourcePathToUrl,
  appendRootPathToUrlIfNeeded,
  parseHeaders,
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
import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";
import type { ResourceMetrics } from "@opentelemetry/sdk-metrics";
import type { IExportTraceServiceResponse } from "@opentelemetry/otlp-transformer";
import type { IExportLogsServiceResponse } from "@opentelemetry/otlp-transformer";
import type { IExportMetricsServiceResponse } from "@opentelemetry/otlp-transformer";
import {
  OTLPMetricExporterBase,
  type OTLPMetricExporterOptions,
} from "@opentelemetry/exporter-metrics-otlp-http";

import { isGzipSupported } from "../utils/otlp-gzip";
import type { IdbSignalBuffer } from "../persistence/indexed-db";
import { buildBrowserExportTransport, type BrowserExportTransport } from "./otlp-transport";
import type { PersistMeta } from "../types/otlp-transport";
import type { PulseBrowserExporterOptions } from "../types/browser-exporter";

export type { PulseBrowserExporterOptions } from "../types/browser-exporter";

abstract class PulseBrowserOtelExporter<
  ExportItem,
  ServiceResponse,
> extends OTLPExporterBase<OTLPExporterConfigBase, ExportItem> { 
  private _serializer!: ISerializer<ExportItem[], ServiceResponse>;
  private _transport?: BrowserExportTransport;
  /** Set in onInit (runs during super()) before subclass ctor assigns _pulse. */
  private _otlpExporterConfig?: OTLPExporterConfigBase;
  private readonly _pulse: PulseBrowserExporterOptions & {
    contentType: string;
  };

  protected constructor(
    config: OTLPExporterConfigBase | undefined,
    serializer: ISerializer<ExportItem[], ServiceResponse>,
    contentType: string,
    pulse: PulseBrowserExporterOptions,
  ) {
    super(config);
    this._serializer = serializer;
    this._pulse = { ...pulse, contentType };
  }

  onInit(config: OTLPExporterConfigBase | undefined): void {
    this._otlpExporterConfig = config;
  }

  onShutdown(): void {
    this._transport?.shutdown();
  }

  switchToKeepalive(): void {
    this._transport?.switchToKeepalive();
  }

  switchToBeacon(apiKey?: string): void {
    this._transport?.switchToBeacon({
      apiKey,
      contentType: this._pulse.contentType,
    });
  }

  private ensureTransport(): void {
    if (this._transport) return;
    const pulse = this._pulse;
    const cfg = this._otlpExporterConfig;
    const useGzip = pulse.useGzip && isGzipSupported();
    const headers: Record<string, string> = {
      ...parseHeaders(cfg?.headers),
      ...baggageUtils.parseKeyPairsIntoRecord(
        getEnv().OTEL_EXPORTER_OTLP_HEADERS,
      ),
      "Content-Type": pulse.contentType,
    };
    if (useGzip) {
      headers["Content-Encoding"] = "gzip";
    }
    const meta: PersistMeta = {
      contentType: pulse.contentType,
      ...(useGzip ? { contentEncoding: "gzip" as const } : {}),
    };
    this._transport = buildBrowserExportTransport(
      { url: this.url, headers },
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

  send(
    objects: ExportItem[],
    onSuccess: () => void,
    onError: (error: OTLPExporterError | Error) => void,
  ): void {
    if (this._shutdownOnce.isCalled) {
      diag.debug("Shutdown already started. Cannot send objects");
      return;
    }
    this.ensureTransport();
    const data = this._serializer.serializeRequest(objects);
    if (data == null) {
      onError(new OTLPExporterError("Could not serialize message"));
      return;
    }
    const promise = this._transport!.send(data, this.timeoutMillis).then(
      (response) => {
        if (response.status === "success") {
          onSuccess();
        } else if (response.status === "failure" && response.error) {
          onError(response.error);
        } else if (response.status === "retryable") {
          onError(new OTLPExporterError("Export failed with retryable status"));
        } else {
          onError(new OTLPExporterError("Export failed with unknown status"));
        }
      },
      onError,
    );
    this._sendingPromises.push(promise);
    const popPromise = () => {
      const i = this._sendingPromises.indexOf(promise);
      this._sendingPromises.splice(i, 1);
    };
    promise.then(popPromise, popPromise);
  }
}

const DEFAULT_TRACES_PATH = "v1/traces";

export class PulseBrowserTraceExporter extends PulseBrowserOtelExporter<
  ReadableSpan,
  IExportTraceServiceResponse
> {
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

  getDefaultUrl(config: OTLPExporterConfigBase): string {
    if (typeof config.url === "string") return config.url;
    return `http://localhost:4318/${DEFAULT_TRACES_PATH}`;
  }
}

export class PulseBrowserLogExporter extends PulseBrowserOtelExporter<
  ReadableLogRecord,
  IExportLogsServiceResponse
> {
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

  getDefaultUrl(config: OTLPExporterConfigBase): string {
    if (typeof config.url === "string") return config.url;
    const env = getEnv();
    if (env.OTEL_EXPORTER_OTLP_LOGS_ENDPOINT.length > 0) {
      return appendRootPathToUrlIfNeeded(env.OTEL_EXPORTER_OTLP_LOGS_ENDPOINT);
    }
    if (env.OTEL_EXPORTER_OTLP_ENDPOINT.length > 0) {
      return appendResourcePathToUrl(
        env.OTEL_EXPORTER_OTLP_ENDPOINT,
        "v1/logs",
      );
    }
    return `http://localhost:4318/v1/logs`;
  }
}

class PulseMetricsBrowserProxy extends PulseBrowserOtelExporter<
  ResourceMetrics,
  IExportMetricsServiceResponse
> {
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

  getDefaultUrl(config: OTLPExporterConfigBase): string {
    if (typeof config.url === "string") return config.url;
    return `http://localhost:4318/v1/metrics`;
  }
}

export function createPulseBrowserMetricExporter(
  config: OTLPExporterConfigBase | undefined,
  pulse: PulseBrowserExporterOptions,
): OTLPMetricExporterBase<PulseMetricsBrowserProxy> {
  return new OTLPMetricExporterBase(
    new PulseMetricsBrowserProxy(config, pulse),
    config as OTLPMetricExporterOptions,
  );
}
