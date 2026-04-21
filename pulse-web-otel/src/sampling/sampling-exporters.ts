// Export-time sampling wrappers — Android SampledSpanExporter / SampledLogExporter / SampledMetricExporter.

import type { ExportResult } from "@opentelemetry/core";
import { ExportResultCode } from "@opentelemetry/core";
import type { ReadableSpan, SpanExporter } from "@opentelemetry/sdk-trace-web";
import type {
  LogRecordExporter,
  ReadableLogRecord,
} from "@opentelemetry/sdk-logs";
import type {
  PushMetricExporter,
  ResourceMetrics,
} from "@opentelemetry/sdk-metrics";

import type { ExportSamplingGate } from "./export-sampling-gate";

export class SampledSpanExporter implements SpanExporter {
  constructor(
    private readonly delegate: SpanExporter,
    private readonly gate: ExportSamplingGate,
  ) {}

  export(
    spans: ReadableSpan[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    const out = this.gate.filterReadableSpans(spans);
    if (out.length === 0) {
      resultCallback({ code: ExportResultCode.SUCCESS });
      return;
    }
    this.delegate.export(out, resultCallback);
  }

  shutdown(): Promise<void> {
    return this.delegate.shutdown();
  }

  forceFlush(): Promise<void> {
    return this.delegate.forceFlush?.() ?? Promise.resolve();
  }
}

export class SampledLogRecordExporter implements LogRecordExporter {
  constructor(
    private readonly delegate: LogRecordExporter,
    private readonly gate: ExportSamplingGate,
  ) {}

  export(
    logs: ReadableLogRecord[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    const out = this.gate.filterReadableLogs(logs);
    if (out.length === 0) {
      resultCallback({ code: ExportResultCode.SUCCESS });
      return;
    }
    this.delegate.export(out, resultCallback);
  }

  shutdown(): Promise<void> {
    return this.delegate.shutdown();
  }

  forceFlush(): Promise<void> {
    const d = this.delegate as LogRecordExporter & {
      forceFlush?: () => Promise<void>;
    };
    return d.forceFlush?.() ?? Promise.resolve();
  }
}

export class SampledPushMetricExporter implements PushMetricExporter {
  selectAggregationTemporality: PushMetricExporter["selectAggregationTemporality"];

  selectAggregation: PushMetricExporter["selectAggregation"];

  constructor(
    private readonly delegate: PushMetricExporter,
    private readonly gate: ExportSamplingGate,
  ) {
    this.selectAggregationTemporality =
      delegate.selectAggregationTemporality?.bind(delegate);
    this.selectAggregation = delegate.selectAggregation?.bind(delegate);
  }

  export(
    metrics: ResourceMetrics,
    resultCallback: (result: ExportResult) => void,
  ): void {
    const out = this.gate.filterResourceMetrics(metrics);
    if (out.scopeMetrics.length === 0) {
      resultCallback({ code: ExportResultCode.SUCCESS });
      return;
    }
    this.delegate.export(out, resultCallback);
  }

  shutdown(): Promise<void> {
    return this.delegate.shutdown();
  }

  forceFlush(): Promise<void> {
    return this.delegate.forceFlush();
  }
}
