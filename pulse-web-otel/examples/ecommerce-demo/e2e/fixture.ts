/**
 * Shared Playwright fixture — OTLP capture + helpers.
 *
 * Every spec imports `test` and `expect` from here, NOT from @playwright/test directly.
 *
 * How it works:
 *   page.route('**\/v1\/{logs|traces|metrics}') intercepts every OTLP export call the
 *   browser makes, regardless of what endpointBaseUrl is configured. Each call is:
 *     1. Body decoded (gunzip → UTF-8 → JSON, with plain-JSON fallback)
 *     2. Stored in `captured`
 *     3. Responded to with 200 {"partialSuccess":{}} so the SDK doesn't retry
 *
 * The `waitFor*` helpers poll `captured` on a 100ms interval — no external server needed.
 */
import { test as base, expect, type Route } from "@playwright/test";
import { gunzipSync } from "zlib";

// ─── OTLP JSON types (minimal — add fields as needed) ────────────────────────

export interface OtlpAttr {
  key: string;
  value: {
    stringValue?: string;
    intValue?: number;
    doubleValue?: number;
    boolValue?: boolean;
    arrayValue?: { values: Array<{ stringValue?: string }> };
  };
}

export interface OtlpLogRecord {
  timeUnixNano?: string;
  severityText?: string;
  body?: { stringValue?: string };
  attributes: OtlpAttr[];
}

export interface OtlpSpan {
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  name: string;
  startTimeUnixNano?: string;
  endTimeUnixNano?: string;
  attributes: OtlpAttr[];
}

export interface OtlpDataPoint {
  attributes: OtlpAttr[];
  asDouble?: number;
  asInt?: number;
  startTimeUnixNano?: string;
  timeUnixNano?: string;
}

type LogsBody = { resourceLogs: ResourceLogs[] };
type TracesBody = { resourceSpans: ResourceSpans[] };
type MetricsBody = { resourceMetrics: ResourceMetrics[] };

interface ResourceLogs {
  resource?: { attributes: OtlpAttr[] };
  scopeLogs: { logRecords: OtlpLogRecord[] }[];
}
interface ResourceSpans {
  resource?: { attributes: OtlpAttr[] };
  scopeSpans: { spans: OtlpSpan[] }[];
}
interface ResourceMetrics {
  resource?: { attributes: OtlpAttr[] };
  scopeMetrics: { metrics: OtlpMetric[] }[];
}
interface OtlpMetric {
  name: string;
  gauge?: { dataPoints: OtlpDataPoint[] };
  sum?: { dataPoints: OtlpDataPoint[] };
}

export type CapturedRequest =
  | { type: "logs"; body: LogsBody }
  | { type: "traces"; body: TracesBody }
  | { type: "metrics"; body: MetricsBody };

// ─── Attribute helpers ────────────────────────────────────────────────────────

/** Read a scalar attribute value by key. Returns undefined if not found. */
export function getAttr(
  attrs: OtlpAttr[] | undefined,
  key: string,
): string | number | boolean | undefined {
  const a = (attrs ?? []).find((a) => a.key === key);
  if (!a) return undefined;
  const v = a.value;
  return v.stringValue ?? v.intValue ?? v.doubleValue ?? v.boolValue;
}

/** Find all logs matching a pulse.type value. */
export function findAllLogs(
  captured: CapturedRequest[],
  pulseType: string,
): OtlpLogRecord[] {
  const out: OtlpLogRecord[] = [];
  for (const c of captured) {
    if (c.type !== "logs") continue;
    for (const rl of c.body.resourceLogs) {
      for (const sl of rl.scopeLogs) {
        for (const lr of sl.logRecords) {
          if (getAttr(lr.attributes, "pulse.type") === pulseType) out.push(lr);
        }
      }
    }
  }
  return out;
}

/** Find all spans matching a pulse.type value (for SDK-defined signal types). */
export function findAllSpans(
  captured: CapturedRequest[],
  pulseType: string,
): OtlpSpan[] {
  const out: OtlpSpan[] = [];
  for (const c of captured) {
    if (c.type !== "traces") continue;
    for (const rs of c.body.resourceSpans) {
      for (const ss of rs.scopeSpans) {
        for (const sp of ss.spans) {
          if (getAttr(sp.attributes, "pulse.type") === pulseType) out.push(sp);
        }
      }
    }
  }
  return out;
}

/** Find all spans matching a span name (for trackEvent / custom spans that have no pulse.type). */
export function findAllSpansByName(
  captured: CapturedRequest[],
  spanName: string,
): OtlpSpan[] {
  const out: OtlpSpan[] = [];
  for (const c of captured) {
    if (c.type !== "traces") continue;
    for (const rs of c.body.resourceSpans) {
      for (const ss of rs.scopeSpans) {
        for (const sp of ss.spans) {
          if (sp.name === spanName) out.push(sp);
        }
      }
    }
  }
  return out;
}

/** Find all custom_event logs matching an event body (name). Used for trackEvent assertions. */
export function findAllLogsByBody(
  captured: CapturedRequest[],
  body: string,
): OtlpLogRecord[] {
  const out: OtlpLogRecord[] = [];
  for (const c of captured) {
    if (c.type !== "logs") continue;
    for (const rl of c.body.resourceLogs) {
      for (const sl of rl.scopeLogs) {
        for (const lr of sl.logRecords) {
          if (lr.body?.stringValue === body) out.push(lr);
        }
      }
    }
  }
  return out;
}

/** Find all metric data points by metric name. */
export function findAllMetricPoints(
  captured: CapturedRequest[],
  metricName: string,
): OtlpDataPoint[] {
  const out: OtlpDataPoint[] = [];
  for (const c of captured) {
    if (c.type !== "metrics") continue;
    for (const rm of c.body.resourceMetrics) {
      for (const sm of rm.scopeMetrics) {
        for (const m of sm.metrics) {
          if (m.name === metricName) {
            for (const dp of m.gauge?.dataPoints ?? m.sum?.dataPoints ?? []) {
              out.push(dp);
            }
          }
        }
      }
    }
  }
  return out;
}

/** Read a resource-level attribute from any captured payload. */
export function getResourceAttr(
  captured: CapturedRequest[],
  key: string,
): string | undefined {
  for (const c of captured) {
    const resourceList =
      c.type === "logs"
        ? c.body.resourceLogs.map((r) => r.resource)
        : c.type === "traces"
          ? c.body.resourceSpans.map((r) => r.resource)
          : c.body.resourceMetrics.map((r) => r.resource);
    for (const res of resourceList) {
      const val = getAttr(res?.attributes, key);
      if (val !== undefined) return String(val);
    }
  }
  return undefined;
}

// ─── Internal: body decode + poll ─────────────────────────────────────────────

function decodeBody(buf: Buffer | null): unknown {
  if (!buf) return {};
  try {
    return JSON.parse(gunzipSync(buf).toString("utf-8"));
  } catch {
    /* not gzip */
  }
  try {
    return JSON.parse(buf.toString("utf-8"));
  } catch {
    return {};
  }
}

async function pollUntil<T>(
  fn: () => T | undefined,
  timeoutMs: number,
  description: string,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = fn();
    if (result !== undefined) return result;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`Timeout (${timeoutMs}ms) waiting for ${description}`);
}

// ─── Fixture type ─────────────────────────────────────────────────────────────

export type OtlpFixture = {
  /** All OTLP request payloads captured since test start (or last reset). */
  captured: CapturedRequest[];
  /** Wait until a log with the given pulse.type arrives. Throws on timeout. */
  waitForLog(pulseType: string, timeoutMs?: number): Promise<OtlpLogRecord>;
  /** Wait until a span with the given pulse.type arrives (SDK signal types). Throws on timeout. */
  waitForSpan(pulseType: string, timeoutMs?: number): Promise<OtlpSpan>;
  /** Wait until a span with the given span.name arrives (SDK-internal spans only). Throws on timeout. */
  waitForSpanByName(spanName: string, timeoutMs?: number): Promise<OtlpSpan>;
  /** Wait until a log with the given body arrives (custom trackEvent logs). Throws on timeout. */
  waitForLogByBody(body: string, timeoutMs?: number): Promise<OtlpLogRecord>;
  /** Wait until any metric data point with the given name arrives. */
  waitForMetric(metricName: string, timeoutMs?: number): Promise<OtlpDataPoint>;
  /** Clear the captured array — useful between steps in multi-step tests. */
  reset(): void;
};

// ─── Fixture export ────────────────────────────────────────────────────────────

export const test = base.extend<{ otlp: OtlpFixture }>({
  otlp: async ({ page }, use) => {
    const captured: CapturedRequest[] = [];

    const corsHeaders: Record<string, string> = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers":
        "Content-Type, Content-Encoding, X-API-KEY, X-Pulse-Metering-Session-ID",
    };

    const intercept =
      (type: "logs" | "traces" | "metrics") => async (route: Route) => {
        if (route.request().method() === "OPTIONS") {
          await route.fulfill({ status: 204, headers: corsHeaders });
          return;
        }
        const body = decodeBody(route.request().postDataBuffer()) as never;
        captured.push({ type, body });
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          headers: corsHeaders,
          body: '{"partialSuccess":{}}',
        });
      };

    await page.route("**/v1/logs", intercept("logs"));
    await page.route("**/v1/traces", intercept("traces"));
    await page.route("**/v1/metrics", intercept("metrics"));

    await use({
      captured,
      waitForLog: (t, ms = 8_000) =>
        pollUntil(
          () => findAllLogs(captured, t)[0],
          ms,
          `log(pulse.type="${t}")`,
        ),
      waitForSpan: (t, ms = 8_000) =>
        pollUntil(
          () => findAllSpans(captured, t)[0],
          ms,
          `span(pulse.type="${t}")`,
        ),
      waitForSpanByName: (n, ms = 8_000) =>
        pollUntil(
          () => findAllSpansByName(captured, n)[0],
          ms,
          `span(name="${n}")`,
        ),
      waitForLogByBody: (b, ms = 8_000) =>
        pollUntil(
          () => findAllLogsByBody(captured, b)[0],
          ms,
          `log(body="${b}")`,
        ),
      waitForMetric: (n, ms = 15_000) =>
        pollUntil(
          () => findAllMetricPoints(captured, n)[0],
          ms,
          `metric(name="${n}")`,
        ),
      reset: () => {
        captured.length = 0;
      },
    });
  },
});

export { expect };
