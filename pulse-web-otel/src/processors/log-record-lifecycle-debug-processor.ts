// Dev-only: traces each log through the pre-batch processor chain (ingress vs pre-batch).

import type { LogRecord, LogRecordProcessor } from "@opentelemetry/sdk-logs";
import type { LogBody } from "@opentelemetry/api-logs";

let emitSeq = 0;

function bodyPreview(body: LogBody | undefined): string {
  if (body === undefined) return "";
  if (typeof body === "string") return body.slice(0, 120);
  if (typeof body === "number" || typeof body === "boolean")
    return String(body);
  try {
    return JSON.stringify(body).slice(0, 160);
  } catch {
    return String(body);
  }
}

function summarizeLogRecord(logRecord: LogRecord): Record<string, unknown> {
  const attrs = logRecord.attributes as Record<string, unknown>;
  return {
    body: bodyPreview(logRecord.body),
    pulseType: attrs["pulse.type"],
    eventName: attrs["event.name"],
  };
}

export type LogRecordLifecyclePhase = "ingress" | "pre_batch";

/**
 * ingress: first processor — log as emitted from the app (before GlobalAttributesProcessor).
 * pre_batch: last pre-batch processor — log after enrich/sample/filter, right before BatchLogRecordProcessor queues it.
 */
export class LogRecordLifecycleDebugProcessor implements LogRecordProcessor {
  constructor(private readonly phase: LogRecordLifecyclePhase) {}

  onEmit(logRecord: LogRecord): void {
    const n = ++emitSeq;
    console.log("[PulseWeb:logLifecycle]", {
      seq: n,
      phase: this.phase,
      note:
        this.phase === "ingress"
          ? "Logger pipeline entry (before global attrs)"
          : "Done pre-batch → BatchLogRecordProcessor will queue (in-memory; flush on timer or forceFlush)",
      ...summarizeLogRecord(logRecord),
    });
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    return Promise.resolve();
  }
}
