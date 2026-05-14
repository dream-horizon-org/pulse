/**
 * Pulse HTTP client span helpers — stable semconv + product attrs for Fetch / XHR.
 * Spec: docs/instrumentations/network/SPEC.md
 */

import type { Span } from "@opentelemetry/api";
import { SpanStatusCode } from "@opentelemetry/api";

import { PulseWebSemconv } from "../semconv";

/**
 * Reads URL the upstream client span may have set before the custom callback.
 * Tries stable {@code url.full} first, then legacy {@code http.url} (OTel / older
 * instrumentations). On {@code fetch(url, opts)} without {@link Request}, the URL
 * often only exists on the span; on failure/abort, {@link Response#url} is absent.
 */
export function getOtelHttpUrlFromSpan(span: Span): string {
  const store = span as unknown as { attributes?: Record<string, unknown> };
  const attrs = store.attributes;
  if (!attrs) {
    return "";
  }
  const ak = PulseWebSemconv.AttributeKey;
  const full = attrs[ak.URL_FULL];
  if (typeof full === "string" && full.trim().length > 0) {
    return full.trim();
  }
  const legacy = attrs["http.url"];
  if (typeof legacy === "string" && legacy.trim().length > 0) {
    return legacy.trim();
  }
  return "";
}

/**
 * HTTP method already on the span (stable {@code http.request.method}), if upstream set it.
 * Prefer over parsing {@link Span#name} — names are not a stable contract across OTel versions.
 */
export function getOtelHttpRequestMethodFromSpan(
  span: Span,
): string | undefined {
  const store = span as unknown as { attributes?: Record<string, unknown> };
  const attrs = store.attributes;
  if (!attrs) {
    return undefined;
  }
  const ak = PulseWebSemconv.AttributeKey.HTTP_REQUEST_METHOD;
  const m = attrs[ak] ?? attrs["http.request.method"];
  if (typeof m === "string") {
    const t = m.trim();
    return t.length > 0 ? t.toUpperCase() : undefined;
  }
  return undefined;
}

/**
 * FetchInstrumentation callback helpers — resolve URL/method/status and header getter for
 * {@link Request} vs {@link RequestInit}.
 */
export function resolveFetchUrl(
  span: Span,
  request: Request | RequestInit,
  result: Response | unknown,
): string {
  if (result instanceof Response && result.url) {
    return result.url;
  }
  if (request instanceof Request) {
    return request.url;
  }
  return getOtelHttpUrlFromSpan(span);
}

export function resolveFetchMethod(request: Request | RequestInit): string {
  if (request instanceof Request) {
    return request.method;
  }
  const m = request.method;
  return typeof m === "string" ? m : "GET";
}

export function resolveFetchStatus(result: unknown): number | undefined {
  if (result instanceof Response) {
    return result.status;
  }
  if (typeof result === "object" && result !== null && "status" in result) {
    const s = (result as { status?: number }).status;
    return typeof s === "number" ? s : undefined;
  }
  return undefined;
}

/**
 * Case-insensitive header lookup for {@link Request} or {@link RequestInit} (including plain
 * {@code Record} and {@code string[][]} shapes — the dominant {@code fetch(url, { headers: { ... } })} pattern).
 */
export function requestHeaderGetter(
  request: Request | RequestInit,
): ((name: string) => string | null | undefined) | undefined {
  if (request instanceof Request) {
    return (name: string) => request.headers.get(name);
  }
  const h = request.headers;
  if (h === undefined || h === null) {
    return undefined;
  }
  if (h instanceof Headers) {
    return (name: string) => h.get(name);
  }
  if (Array.isArray(h)) {
    const map = new Map<string, string>();
    for (const pair of h) {
      if (!Array.isArray(pair) || pair.length < 2) {
        continue;
      }
      map.set(String(pair[0]).toLowerCase(), String(pair[1]));
    }
    return (name: string) => map.get(name.toLowerCase()) ?? undefined;
  }
  const rec = h as Record<string, string | string[] | undefined>;
  const map = new Map<string, string>();
  for (const [k, v] of Object.entries(rec)) {
    if (v === undefined) {
      continue;
    }
    const val = Array.isArray(v) ? v.map(String).join(", ") : String(v);
    map.set(k.toLowerCase(), val);
  }
  return (name: string) => map.get(name.toLowerCase()) ?? undefined;
}

/**
 * Names that must never be copied from {@code capturedRequestHeaders} /
 * {@code capturedResponseHeaders} onto spans — even if remote config lists them.
 * Case-insensitive match. Prevents credential/session leakage via OTLP on misconfiguration.
 */
const SENSITIVE_HEADER_CAPTURE_DENYLIST = new Set<string>(
  Object.values(PulseWebSemconv.SensitiveCapturedHeaderName),
);

/** Returns true if this header name must not be emitted on spans (case-insensitive). */
export function isSensitiveCapturedHeaderName(name: string): boolean {
  const n = name.trim().toLowerCase();
  return SENSITIVE_HEADER_CAPTURE_DENYLIST.has(n);
}

const SENSITIVE_QUERY_PARAM_DENYLIST = new Set<string>(
  Object.values(PulseWebSemconv.SensitiveQueryParamName),
);

/** True when this query param name must have its value redacted (case-insensitive key). */
export function isSensitiveQueryParamName(name: string): boolean {
  const n = name.trim().toLowerCase();
  return SENSITIVE_QUERY_PARAM_DENYLIST.has(n);
}

export type NetworkSpanPrivacy = {
  /** Default false — strips query string from {@code url.full}. */
  captureQueryParams: boolean;
};

export type NetworkSpanOptionalConfig = {
  peerServiceMap?: Record<string, string>;
  capturedRequestHeaders?: string[];
  capturedResponseHeaders?: string[];
};

/** RFC 9110 + PATCH (RFC 5789) — methods outside this set use `_OTHER` + `http.request.method_original`. */
const KNOWN_HTTP_METHODS = new Set([
  "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "CONNECT", "TRACE",
]);

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

function escapeRegexFragment(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Local dev stack: OTLP on :4318, pulse-server REST on :8080 (sdk config + interaction-config).
 * Same rewrite as {@link resolveConfigUrl} / {@link resolveInteractionConfigRequest}.
 */
function pulseServerRestBaseForNetworkIgnore(
  endpointBaseUrl: string,
): string | null {
  if (
    !endpointBaseUrl.includes("localhost") &&
    !endpointBaseUrl.includes("10.0.2.2") &&
    !endpointBaseUrl.includes("127.0.0.1")
  ) {
    return null;
  }
  return endpointBaseUrl.replace(/:4318\b/, ":8080").replace(/\/$/, "");
}

/**
 * Prefix-ignore OTLP export URLs, Pulse internal REST on local dev (:8080 when OTLP is :4318),
 * and optional {@code blocked}. Prod: sdk + interaction JSON share the OTLP host (see
 * {@code resolveConfigUrl} / interaction URLs), so the OTLP prefix already excludes them.
 */
export function buildNetworkIgnoreUrls(
  endpointBaseUrl: string,
  blocked?: Array<string | RegExp>,
): Array<string | RegExp> {
  const normalizedBase = endpointBaseUrl.replace(/\/$/, "");
  const patterns: Array<string | RegExp> = [
    new RegExp(`^${escapeRegexFragment(normalizedBase)}`),
  ];

  const restBase = pulseServerRestBaseForNetworkIgnore(endpointBaseUrl);
  if (restBase && restBase !== normalizedBase) {
    patterns.push(new RegExp(`^${escapeRegexFragment(restBase)}`));
  }

  if (blocked?.length) {
    patterns.push(...blocked);
  }
  return patterns;
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
    } else {
      for (const key of Array.from(u.searchParams.keys())) {
        if (isSensitiveQueryParamName(key)) {
          u.searchParams.set(key, "***");
        }
      }
    }
    /** OTel `url.full` MUST NOT contain credentials (user:password@…). */
    u.username = "";
    u.password = "";
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

function getLastPerformanceResourceTiming(
  resourceUrl: string,
): PerformanceResourceTiming | undefined {
  if (typeof performance === "undefined" || !performance.getEntriesByName) {
    return undefined;
  }
  const entries = performance.getEntriesByName(resourceUrl);
  for (let i = entries.length - 1; i >= 0; i--) {
    const e = entries[i];
    if (e instanceof PerformanceResourceTiming) {
      return e;
    }
  }
  return undefined;
}

export function resourceTimingDurationMs(
  resourceUrl: string,
): number | undefined {
  const e = getLastPerformanceResourceTiming(resourceUrl);
  if (!e) {
    return undefined;
  }
  const ms = Math.round(e.duration);
  return Number.isFinite(ms) ? ms : undefined;
}

/**
 * Maps {@link PerformanceResourceTiming#nextHopProtocol} to OTel
 * {@code network.protocol.version} (`1.1`, `2`, `3`).
 */
export function networkProtocolVersionFromNextHop(
  nextHopProtocol: string,
): string | undefined {
  const n = nextHopProtocol.trim().toLowerCase();
  if (n === "http/1.1") {
    return "1.1";
  }
  if (n === "http/1.0") {
    return "1.0";
  }
  if (n === "h2" || n === "http/2" || n === "http2") {
    return "2";
  }
  if (n === "h3" || n === "http/3" || n === "http3") {
    return "3";
  }
  return undefined;
}

/** Recommended OTel attr — set only when {@link PerformanceResourceTiming} exists. */
export function resourceTimingProtocolVersion(
  resourceUrl: string,
): string | undefined {
  const e = getLastPerformanceResourceTiming(resourceUrl);
  if (!e?.nextHopProtocol) {
    return undefined;
  }
  return networkProtocolVersionFromNextHop(e.nextHopProtocol);
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

/**
 * Stable OTel HTTP semconv + Pulse additions; omits optional keys when absent.
 *
 * Optional {@code graphqlRequestBody}: reserved for callers that already have a sync JSON body.
 * {@code NetworkInstrumentation} does not pass it (Fetch body is async; see network.md Done Criteria).
 * {@link extractGraphQlMeta} runs only when that field is set.
 */
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
  /** Sync GraphQL JSON body when already materialized (optional; not wired from Fetch/XHR yet). */
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

  const upperMethod = params.method.toUpperCase();
  if (KNOWN_HTTP_METHODS.has(upperMethod)) {
    span.setAttribute(ak.HTTP_REQUEST_METHOD, upperMethod);
  } else {
    span.setAttribute(ak.HTTP_REQUEST_METHOD, "_OTHER");
    span.setAttribute(ak.HTTP_REQUEST_METHOD_ORIGINAL, upperMethod);
  }
  span.setAttribute(ak.URL_FULL, sanitized);
  span.setAttribute(ak.SERVER_ADDRESS, parsed.hostname);
  let serverPort: number | undefined;
  if (parsed.port !== "") {
    serverPort = Number(parsed.port);
  } else if (parsed.protocol === "https:") {
    serverPort = 443;
  } else if (parsed.protocol === "http:") {
    serverPort = 80;
  }
  if (serverPort !== undefined && Number.isFinite(serverPort)) {
    span.setAttribute(ak.SERVER_PORT, Math.trunc(serverPort));
  }

  const host = parsed.hostname;
  const peer = params.optional?.peerServiceMap?.[host];
  setOptionalString(span, ak.PEER_SERVICE, peer);

  const perfKey = params.perfLookupUrl ?? sanitized;
  const dur = resourceTimingDurationMs(perfKey);
  setOptionalLong(span, ak.HTTP_DURATION_MS, dur);
  setOptionalString(
    span,
    ak.NETWORK_PROTOCOL_VERSION,
    resourceTimingProtocolVersion(perfKey),
  );

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
      if (isSensitiveCapturedHeaderName(name)) {
        continue;
      }
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
      if (isSensitiveCapturedHeaderName(name)) {
        continue;
      }
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
