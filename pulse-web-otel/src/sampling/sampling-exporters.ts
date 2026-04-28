// Export-time sampling wrappers — Android SampledSpanExporter / SampledLogExporter / SampledMetricExporter.

import type { Meter } from "@opentelemetry/api";
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

import type {
  PulseMetricsToAddEntry,
  PulseSdkName,
} from "../types/remote-config";
import type { ExportSamplingGate } from "./export-sampling-gate";
import {
  applyMetricsToAddToLogs,
  applyMetricsToAddToSpans,
  buildMetricsToAddPairs,
} from "./metrics-to-add-apply";

/** Runs `signals.metricsToAdd` on the full batch, then delegates (Android order: before session filter). */
export class MetricsToAddSpanExporter implements SpanExporter {
  private readonly pairs: ReturnType<typeof buildMetricsToAddPairs>;
  private readonly sdkName: PulseSdkName;

  constructor(
    private readonly delegate: SpanExporter,
    opts: {
      entries: PulseMetricsToAddEntry[];
      sdkName: PulseSdkName;
      getMeter: () => Meter;
    },
  ) {
    this.sdkName = opts.sdkName;
    this.pairs = buildMetricsToAddPairs(
      opts.entries,
      "TRACES",
      opts.sdkName,
      opts.getMeter,
    );
  }

  export(
    spans: ReadableSpan[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    applyMetricsToAddToSpans(this.pairs, this.sdkName, spans);
    this.delegate.export(spans, resultCallback);
  }

  shutdown(): Promise<void> {
    return this.delegate.shutdown();
  }

  forceFlush(): Promise<void> {
    return this.delegate.forceFlush?.() ?? Promise.resolve();
  }
}

export class MetricsToAddLogRecordExporter implements LogRecordExporter {
  private readonly pairs: ReturnType<typeof buildMetricsToAddPairs>;
  private readonly sdkName: PulseSdkName;

  constructor(
    private readonly delegate: LogRecordExporter,
    opts: {
      entries: PulseMetricsToAddEntry[];
      sdkName: PulseSdkName;
      getMeter: () => Meter;
    },
  ) {
    this.sdkName = opts.sdkName;
    this.pairs = buildMetricsToAddPairs(
      opts.entries,
      "LOGS",
      opts.sdkName,
      opts.getMeter,
    );
  }

  export(
    logs: ReadableLogRecord[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    applyMetricsToAddToLogs(this.pairs, this.sdkName, logs);
    this.delegate.export(logs, resultCallback);
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
