import type { BufferedSignalRow, IdbSignalBuffer } from "./indexed-db";
import { base64ToUint8 } from "../exporters/otlp-transport";

export interface DrainBufferedExportsParams {
  tracesUrl: string;
  logsUrl: string;
  metricsUrl: string;
  apiKey: string;
  meteringSessionId: string;
  buffer: IdbSignalBuffer;
}

function urlForRow(
  row: BufferedSignalRow,
  p: DrainBufferedExportsParams,
): string {
  switch (row.signalType) {
    case "trace":
      return p.tracesUrl;
    case "log":
      return p.logsUrl;
    case "metric":
      return p.metricsUrl;
    default:
      return p.logsUrl;
  }
}

/**
 * Replay buffered OTLP payloads from a prior failed session. Deletes each row on HTTP 2xx.
 */
export async function drainBufferedOtlpExports(
  params: DrainBufferedExportsParams,
): Promise<void> {
  const rows = await params.buffer.readAll();
  if (rows.length === 0) return;

  for (const row of rows) {
    const env = row.envelope;
    if (!env?.bodyB64 || !env.contentType) continue;

    const url = urlForRow(row, params);
    const body = base64ToUint8(env.bodyB64);
    const headers: Record<string, string> = {
      "X-API-KEY": params.apiKey,
      "X-Pulse-Metering-Session-ID": params.meteringSessionId,
      "Content-Type": env.contentType,
    };
    if (env.contentEncoding) {
      headers["Content-Encoding"] = env.contentEncoding;
    }

    try {
      const res = await fetch(url, {
        method: "POST",
        headers,
        body: body as BodyInit,
      });
      if (res.ok && row.id != null) {
        await params.buffer.delete(row.id);
      }
    } catch {
      // keep row for a future drain
    }
  }
}
