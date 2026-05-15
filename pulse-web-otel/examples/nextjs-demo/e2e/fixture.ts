/**
 * Shared Playwright fixture for nextjs-demo E2E tests.
 *
 * Intercepts OTLP exports (v1/logs, v1/traces, v1/metrics) and stores them
 * in `captured` for assertion. Mirrors the ecommerce-demo fixture pattern
 * but is self-contained for port 3003.
 */
import {
  test as base,
  expect,
  type BrowserContext,
  type Page,
  type Route,
} from "@playwright/test";
import { gunzipSync } from "zlib";

// ─── OTLP types ───────────────────────────────────────────────────────────────

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
  severityNumber?: number;
  eventName?: string;
  body?: { stringValue?: string };
  attributes: OtlpAttr[];
}

export interface OtlpSpanStatus {
  code?: number;
  message?: string;
}

export interface OtlpSpan {
  name: string;
  attributes: OtlpAttr[];
  status?: OtlpSpanStatus;
}

type LogsBody = {
  resourceLogs: Array<{
    resource?: { attributes: OtlpAttr[] };
    scopeLogs: Array<{ logRecords: OtlpLogRecord[] }>;
  }>;
};
type TracesBody = {
  resourceSpans: Array<{
    resource?: { attributes: OtlpAttr[] };
    scopeSpans: Array<{ spans: OtlpSpan[] }>;
  }>;
};

export type CapturedRequest =
  | { type: "logs"; body: LogsBody }
  | { type: "traces"; body: TracesBody };

// ─── Attribute helpers ────────────────────────────────────────────────────────

export function getAttr(
  attrs: OtlpAttr[] | undefined,
  key: string,
): string | number | boolean | string[] | undefined {
  const a = (attrs ?? []).find((a) => a.key === key);
  if (!a) return undefined;
  const v = a.value;
  if (v.arrayValue) {
    return v.arrayValue.values.map((item) => item.stringValue ?? "");
  }
  return v.stringValue ?? v.intValue ?? v.doubleValue ?? v.boolValue;
}

export function findAllLogs(
  captured: CapturedRequest[],
  pulseType: string,
): OtlpLogRecord[] {
  const out: OtlpLogRecord[] = [];
  for (const c of captured) {
    if (c.type !== "logs") continue;
    for (const rl of c.body.resourceLogs ?? []) {
      for (const sl of rl.scopeLogs ?? []) {
        for (const lr of sl.logRecords ?? []) {
          if (getAttr(lr.attributes, "pulse.type") === pulseType) out.push(lr);
        }
      }
    }
  }
  return out;
}

/** Spans whose {@code pulse.type} equals {@code pulseType} (e.g. {@code screen_load}). */
export function findAllSpans(
  captured: CapturedRequest[],
  pulseType: string,
): OtlpSpan[] {
  const out: OtlpSpan[] = [];
  for (const c of captured) {
    if (c.type !== "traces") continue;
    for (const rs of c.body.resourceSpans ?? []) {
      for (const ss of rs.scopeSpans ?? []) {
        for (const sp of ss.spans ?? []) {
          if (getAttr(sp.attributes, "pulse.type") === pulseType) out.push(sp);
        }
      }
    }
  }
  return out;
}

const SCREEN_NAME_KEY = "screen.name";

/** Any OTLP log or span whose {@code screen.name} equals {@code name}. */
export function capturedHasScreenName(
  captured: CapturedRequest[],
  name: string,
): boolean {
  for (const c of captured) {
    if (c.type === "logs") {
      for (const rl of c.body.resourceLogs ?? []) {
        for (const sl of rl.scopeLogs ?? []) {
          for (const lr of sl.logRecords ?? []) {
            if (getAttr(lr.attributes, SCREEN_NAME_KEY) === name) return true;
          }
        }
      }
    } else if (c.type === "traces") {
      for (const rs of c.body.resourceSpans ?? []) {
        for (const ss of rs.scopeSpans ?? []) {
          for (const sp of ss.spans ?? []) {
            if (getAttr(sp.attributes, SCREEN_NAME_KEY) === name) return true;
          }
        }
      }
    }
  }
  return false;
}

/** Non-empty {@code screen.name} values from every log record and span. */
export function allScreenNamesInCaptured(
  captured: CapturedRequest[],
): string[] {
  const out: string[] = [];
  for (const c of captured) {
    if (c.type === "logs") {
      for (const rl of c.body.resourceLogs ?? []) {
        for (const sl of rl.scopeLogs ?? []) {
          for (const lr of sl.logRecords ?? []) {
            const sn = getAttr(lr.attributes, SCREEN_NAME_KEY);
            if (typeof sn === "string" && sn) out.push(sn);
          }
        }
      }
    } else if (c.type === "traces") {
      for (const rs of c.body.resourceSpans ?? []) {
        for (const ss of rs.scopeSpans ?? []) {
          for (const sp of ss.spans ?? []) {
            const sn = getAttr(sp.attributes, SCREEN_NAME_KEY);
            if (typeof sn === "string" && sn) out.push(sn);
          }
        }
      }
    }
  }
  return out;
}

export function getResourceAttr(
  captured: CapturedRequest[],
  key: string,
): string | undefined {
  for (const c of captured) {
    const list =
      c.type === "logs"
        ? (c.body.resourceLogs ?? []).map((r) => r.resource)
        : (c.body.resourceSpans ?? []).map((r) => r.resource);
    for (const res of list) {
      const val = getAttr(res?.attributes, key);
      if (val !== undefined) return String(val);
    }
  }
  return undefined;
}

/** Numeric OTLP status code, or undefined if missing / malformed. */
export function getOtlpSpanStatusCode(span: OtlpSpan): number | undefined {
  const c = span.status?.code;
  return typeof c === "number" && Number.isFinite(c) ? c : undefined;
}

/**
 * HTTP client spans: `pulse.type` is `network.<statusCode>`.
 * Prefix-matches `network.` but excludes `network.change`.
 */
export function findAllNetworkSpans(captured: CapturedRequest[]): OtlpSpan[] {
  const out: OtlpSpan[] = [];
  for (const c of captured) {
    if (c.type !== "traces") continue;
    for (const rs of c.body.resourceSpans ?? []) {
      for (const ss of rs.scopeSpans ?? []) {
        for (const sp of ss.spans ?? []) {
          const pt = getAttr(sp.attributes, "pulse.type");
          const s = typeof pt === "string" ? pt : "";
          if (s.startsWith("network.") && s !== "network.change") {
            out.push(sp);
          }
        }
      }
    }
  }
  return out;
}

// ─── Internal helpers ─────────────────────────────────────────────────────────

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

export async function attachOtlpCapture(
  target: Page | BrowserContext,
  captured: CapturedRequest[],
): Promise<void> {
  const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers":
      "Content-Type, Content-Encoding, X-API-KEY, X-Pulse-Metering-Session-ID",
  };
  const intercept = (type: "logs" | "traces") => async (route: Route) => {
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
  await target.route("**/v1/logs", intercept("logs"));
  await target.route("**/v1/traces", intercept("traces"));
  await target.route("**/v1/metrics", async (route) => {
    if (route.request().method() === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      headers: corsHeaders,
      body: '{"partialSuccess":{}}',
    });
  });
}

export async function attachSdkConfigStub(
  target: Page | BrowserContext,
): Promise<void> {
  const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, X-API-KEY",
  };
  await target.route("**/v1/configs/active**", async (route) => {
    if (route.request().method() === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders });
      return;
    }
    await route.fulfill({
      status: 404,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      body: "{}",
    });
  });
}

// ─── Fixture ──────────────────────────────────────────────────────────────────

export type OtlpFixture = {
  captured: CapturedRequest[];
  waitForLog(pulseType: string, timeoutMs?: number): Promise<OtlpLogRecord>;
  reset(): void;
};

export const test = base.extend<{ otlp: OtlpFixture }>({
  otlp: async ({ page }, use) => {
    const captured: CapturedRequest[] = [];
    await attachSdkConfigStub(page);
    await attachOtlpCapture(page, captured);

    await use({
      captured,
      waitForLog: (t, ms = 10_000) =>
        pollUntil(
          () => findAllLogs(captured, t)[0],
          ms,
          `log(pulse.type="${t}")`,
        ),
      reset: () => {
        captured.length = 0;
      },
    });
  },
});

export { expect };
