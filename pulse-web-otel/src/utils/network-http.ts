/**
 * Pulse HTTP client span helpers — stable semconv + product attrs for Fetch / XHR.
 * Spec: web-sdk-plan/v1/02-instrumentations/network.md
 */

import type { Span } from "@opentelemetry/api";
import { SpanStatusCode } from "@opentelemetry/api";

import { PulseWebSemconv } from "../semconv";

export type NetworkSpanPrivacy = {
  /** Default false — strips query string from {@code url.full}. */
  captureQueryParams: boolean;
};

export type NetworkSpanOptionalConfig = {
  peerServiceMap?: Record<string, string>;
  capturedRequestHeaders?: string[];
  capturedResponseHeaders?: string[];
};

/**
 * Android / ClickHouse parity: {@code pulse.type} is {@code network.<statusCode>}
 * (e.g. {@code network.200}, {@code network.404}). Missing or non-finite status → {@code network.0}.
 */
export function networkPulseType(statusCode: number | undefined): string {
  if (statusCode === undefined || !Number.isFinite(statusCode)) {
    return "network.0";
  }
  return `network.${Math.trunc(statusCode)}`;
}

/**
 * Parses OTel client span {@code name}: Fetch uses {@code "HTTP GET"}; XHR uses {@code "GET"}.
 */
export function methodFromOtelClientSpanName(
  spanName: string | undefined,
): string {
  const n = spanName?.trim() ?? "";
  const httpPref = n.match(/^HTTP\s+([A-Za-z]+)$/);
  if (httpPref?.[1]) {
    return httpPref[1].toUpperCase();
  }
  if (/^[A-Za-z]+$/.test(n)) {
    return n.toUpperCase();
  }
  return "GET";
}

/** Prefix-ignore OTLP and optional blocked URLs (OTel {@code ignoreUrls} rules). */
export function buildNetworkIgnoreUrls(
  endpointBaseUrl: string,
  blocked?: Array<string | RegExp>,
): Array<string | RegExp> {
  const escaped = endpointBaseUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return [new RegExp(`^${escaped}`), ...(blocked ?? [])];
}

export function sanitizeHttpUrl(
  rawUrl: string,
  privacy: NetworkSpanPrivacy,
): string {
  try {
    const u = new URL(
      rawUrl,
      typeof window !== "undefined" ? window.location.href : undefined,
    );
    if (!privacy.captureQueryParams) {
      u.search = "";
    }
    return u.toString();
  } catch {
    return rawUrl;
  }
}

export function extractGraphQlMeta(body: string): {
  operationName?: string;
  operationType?: string;
} {
  let parsed: { query?: string; operationName?: string };
  try {
    parsed = JSON.parse(body) as { query?: string; operationName?: string };
  } catch {
    return {};
  }
  if (typeof parsed.query !== "string") {
    return {};
  }
  const q = parsed.query.trim();
  if (
    !q.includes("query") &&
    !q.includes("mutation") &&
    !q.includes("subscription")
  ) {
    return {};
  }
  const typeMatch = q.match(/^(query|mutation|subscription)/);
  const namedMatch = q.match(
    /(?:query|mutation|subscription)\s+([_A-Za-z][_A-Za-z0-9]*)/,
  );
  const operationType = typeMatch?.[1];
  const operationName = namedMatch?.[1] ?? parsed.operationName ?? undefined;
  const out: { operationName?: string; operationType?: string } = {};
  if (operationName !== undefined && operationName !== "") {
    out.operationName = operationName;
  }
  if (operationType !== undefined && operationType !== "") {
    out.operationType = operationType;
  }
  return out;
}

export function resourceTimingDurationMs(
  resourceUrl: string,
): number | undefined {
  if (typeof performance === "undefined" || !performance.getEntriesByName) {
    return undefined;
  }
  const entries = performance.getEntriesByName(resourceUrl);
  for (let i = entries.length - 1; i >= 0; i--) {
    const e = entries[i];
    if (e instanceof PerformanceResourceTiming) {
      const ms = Math.round(e.duration);
      return Number.isFinite(ms) ? ms : undefined;
    }
  }
  return undefined;
}

function setOptionalString(
  span: Span,
  key: string,
  value: string | undefined,
): void {
  if (value === undefined || value === "") {
    return;
  }
  span.setAttribute(key, value);
}

function setOptionalLong(
  span: Span,
  key: string,
  value: number | undefined,
): void {
  if (value === undefined || !Number.isFinite(value)) {
    return;
  }
  span.setAttribute(key, Math.round(value));
}

/** Stable OTel HTTP semconv + Pulse additions; omits optional keys when absent. */
export function applyPulseHttpClientSpanAttributes(params: {
  span: Span;
  /** Fully resolved request URL (may include query — sanitized before storage). */
  resolvedUrl: string;
  method: string;
  statusCode: number | undefined;
  privacy: NetworkSpanPrivacy;
  optional: NetworkSpanOptionalConfig | undefined;
  /** Performance timing lookup key (usually {@link Response.url} / XHR responseURL). */
  perfLookupUrl?: string;
  requestHeaderGet?: (name: string) => string | null | undefined;
  responseHeaderGet?: (name: string) => string | null | undefined;
  /** Sync GraphQL JSON body when already materialized (Fetch body async — usually omitted). */
  graphqlRequestBody?: string | null;
}): void {
  const { span } = params;
  const ak = PulseWebSemconv.AttributeKey;

  if (!params.resolvedUrl.trim()) {
    span.setStatus({ code: SpanStatusCode.ERROR });
    setOptionalString(span, ak.ERROR_TYPE, "network_error");
    span.setAttribute(ak.PULSE_TYPE, networkPulseType(undefined));
    return;
  }

  const sanitized = sanitizeHttpUrl(params.resolvedUrl, params.privacy);
  let parsed: URL;
  try {
    parsed = new URL(
      sanitized,
      typeof window !== "undefined" ? window.location.href : undefined,
    );
  } catch {
    span.setStatus({ code: SpanStatusCode.ERROR });
    setOptionalString(span, ak.ERROR_TYPE, "network_error");
    span.setAttribute(ak.PULSE_TYPE, networkPulseType(undefined));
    return;
  }

  span.setAttribute(ak.HTTP_REQUEST_METHOD, params.method.toUpperCase());
  span.setAttribute(ak.URL_FULL, sanitized);
  span.setAttribute(ak.SERVER_ADDRESS, parsed.hostname);
  const port = parsed.port;
  if (port !== "" && port !== "80" && port !== "443") {
    const n = Number(port);
    if (Number.isFinite(n)) {
      span.setAttribute(ak.SERVER_PORT, n);
    }
  }

  const host = parsed.hostname;
  const peer = params.optional?.peerServiceMap?.[host];
  setOptionalString(span, ak.PEER_SERVICE, peer);

  const perfKey = params.perfLookupUrl ?? sanitized;
  const dur = resourceTimingDurationMs(perfKey);
  setOptionalLong(span, ak.HTTP_DURATION_MS, dur);

  const status = params.statusCode;
  if (status !== undefined) {
    span.setAttribute(ak.HTTP_RESPONSE_STATUS_CODE, status);
  }

  if (status === undefined) {
    span.setStatus({ code: SpanStatusCode.ERROR });
    setOptionalString(span, ak.ERROR_TYPE, "network_error");
  } else if (status === 0) {
    span.setStatus({ code: SpanStatusCode.ERROR });
    setOptionalString(span, ak.ERROR_TYPE, "cors_error");
  } else if (status >= 400) {
    span.setStatus({ code: SpanStatusCode.ERROR });
    setOptionalString(span, ak.ERROR_TYPE, status >= 500 ? "5xx" : "4xx");
  } else {
    span.setStatus({ code: SpanStatusCode.OK });
  }

  const reqLen = params.requestHeaderGet?.("content-length");
  if (reqLen !== null && reqLen !== undefined && reqLen !== "") {
    const n = Number(reqLen);
    if (Number.isFinite(n)) {
      span.setAttribute(ak.HTTP_REQUEST_BODY_SIZE, n);
    }
  }
  const resLen = params.responseHeaderGet?.("content-length");
  if (resLen !== null && resLen !== undefined && resLen !== "") {
    const n = Number(resLen);
    if (Number.isFinite(n)) {
      span.setAttribute(ak.HTTP_RESPONSE_BODY_SIZE, n);
    }
  }

  const capReq = params.optional?.capturedRequestHeaders;
  if (capReq && params.requestHeaderGet) {
    const g = params.requestHeaderGet;
    for (const name of capReq) {
      const v = g(name) ?? g(name.toLowerCase());
      if (v !== null && v !== undefined && v !== "") {
        span.setAttribute(`http.request.header.${name.toLowerCase()}`, v);
      }
    }
  }
  const capRes = params.optional?.capturedResponseHeaders;
  if (capRes && params.responseHeaderGet) {
    const g = params.responseHeaderGet;
    for (const name of capRes) {
      const v = g(name) ?? g(name.toLowerCase());
      if (v !== null && v !== undefined && v !== "") {
        span.setAttribute(`http.response.header.${name.toLowerCase()}`, v);
      }
    }
  }

  const gqlBody = params.graphqlRequestBody;
  if (
    gqlBody !== null &&
    gqlBody !== undefined &&
    gqlBody !== "" &&
    gqlBody.length <= 262_144
  ) {
    const gql = extractGraphQlMeta(gqlBody);
    setOptionalString(span, ak.GRAPHQL_OPERATION_NAME, gql.operationName);
    setOptionalString(span, ak.GRAPHQL_OPERATION_TYPE, gql.operationType);
  }

  span.setAttribute(ak.PULSE_TYPE, networkPulseType(status));
}
