import type { ExportResult } from "@opentelemetry/core";
import type {
  LogRecordExporter,
  ReadableLogRecord,
} from "@opentelemetry/sdk-logs";
import type { LogBody } from "@opentelemetry/api-logs";

function bodyPreview(body: LogBody | undefined): string {
  if (body === undefined) return "";
  if (typeof body === "string") return body.slice(0, 80);
  if (typeof body === "number" || typeof body === "boolean")
    return String(body);
  try {
    return JSON.stringify(body).slice(0, 120);
  } catch {
    return String(body);
  }
}

/** Wraps the OTLP log exporter to log each batch right before HTTP send. */
export function wrapLogExporterLifecycleDebug(
  inner: LogRecordExporter,
): LogRecordExporter {
  return {
    export(
      logs: ReadableLogRecord[],
      resultCallback: (r: ExportResult) => void,
    ): void {
      const maxPreview = 24;
      const records = logs.slice(0, maxPreview).map((lr, i) => ({
        i,
        body: bodyPreview(lr.body),
        pulseType: (lr.attributes as Record<string, unknown>)["pulse.type"],
      }));
      console.log("[PulseWeb:logLifecycle]", {
        phase: "export",
        recordCount: logs.length,
        previewFirstN: records,
        truncatedList: logs.length > maxPreview,
      });
      inner.export(logs, resultCallback);
    },
    shutdown(): Promise<void> {
      return inner.shutdown();
    },
  };
}
