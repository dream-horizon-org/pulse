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

import type { ResolvedBeforeSend } from "../before-send";
import {
  isReadableLogRecord,
  isReadableSpan,
  isResourceMetrics,
} from "../before-send";

function applyGeneric(
  hooks: ResolvedBeforeSend,
  signal: unknown,
): unknown | null {
  if (hooks.beforeSend) return hooks.beforeSend(signal);
  return signal;
}

/** Outer exporter — batch processor calls this first (Android {@code PulseBeforeSendSpanExporter}). */
export class BeforeSendSpanExporter implements SpanExporter {
  constructor(
    private readonly delegate: SpanExporter,
    private readonly hooks: ResolvedBeforeSend,
  ) {}

  export(
    spans: ReadableSpan[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    const out: ReadableSpan[] = [];
    for (const span of spans) {
      let current: unknown = span;
      current = applyGeneric(this.hooks, current);
      if (current === null) continue;
      if (!isReadableSpan(current)) continue;
      if (this.hooks.beforeSendSpan) {
        const next = this.hooks.beforeSendSpan(current);
        if (next === null) continue;
        out.push(next);
      } else {
        out.push(current);
      }
    }
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

export class BeforeSendLogRecordExporter implements LogRecordExporter {
  constructor(
    private readonly delegate: LogRecordExporter,
    private readonly hooks: ResolvedBeforeSend,
  ) {}

  export(
    logs: ReadableLogRecord[],
    resultCallback: (result: ExportResult) => void,
  ): void {
    const out: ReadableLogRecord[] = [];
    for (const log of logs) {
      let current: unknown = log;
      current = applyGeneric(this.hooks, current);
      if (current === null) continue;
      if (!isReadableLogRecord(current)) continue;
      if (this.hooks.beforeSendLog) {
        const next = this.hooks.beforeSendLog(current);
        if (next === null) continue;
        out.push(next);
      } else {
        out.push(current);
      }
    }
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

export class BeforeSendMetricExporter implements PushMetricExporter {
  selectAggregationTemporality: PushMetricExporter["selectAggregationTemporality"];

  selectAggregation: PushMetricExporter["selectAggregation"];

  constructor(
    private readonly delegate: PushMetricExporter,
    private readonly hooks: ResolvedBeforeSend,
  ) {
    this.selectAggregationTemporality =
      delegate.selectAggregationTemporality?.bind(delegate);
    this.selectAggregation = delegate.selectAggregation?.bind(delegate);
  }

  export(
    metrics: ResourceMetrics,
    resultCallback: (result: ExportResult) => void,
  ): void {
    let current: unknown = metrics;
    current = applyGeneric(this.hooks, current);
    if (current === null) {
      resultCallback({ code: ExportResultCode.SUCCESS });
      return;
    }
    if (!isResourceMetrics(current)) {
      resultCallback({ code: ExportResultCode.SUCCESS });
      return;
    }
    let toExport = current;
    if (this.hooks.beforeSendMetric) {
      const next = this.hooks.beforeSendMetric(toExport);
      if (next === null) {
        resultCallback({ code: ExportResultCode.SUCCESS });
        return;
      }
      toExport = next;
    }
    this.delegate.export(toExport, resultCallback);
  }

  shutdown(): Promise<void> {
    return this.delegate.shutdown();
  }

  forceFlush(): Promise<void> {
    return this.delegate.forceFlush();
  }
}
