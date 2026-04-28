// M5: Network instrumentation — captures outbound HTTP (Fetch + XHR) as spans.
// Owns the `http` pulse.type. Android parity: OkHttpInstrumentation.
// Uses stable OTel HTTP semconv (http.request.method, url.full, http.response.status_code, server.address).

import type { Context, Span } from "@opentelemetry/api";
import { SpanStatusCode, trace } from "@opentelemetry/api";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";
import type { FetchError } from "@opentelemetry/instrumentation-fetch/build/src/types";
import type { ReadableSpan, SpanProcessor } from "@opentelemetry/sdk-trace-base";

import type { PulseInstrumentation, SdkContext } from "../instrumentation-registry";
import { PulseWebSemconv } from "../semconv";
import { resolveEndpointBaseUrl } from "../config";

// ─── NetworkInstrumentation config (extends base { enabled: boolean }) ──────

export interface NetworkConfig {
  enabled?: boolean;
  /** Hostnames blocked from tracing — in addition to the OTLP endpoint. */
  blockedUrls?: Array<string | RegExp>;
  /** hostname → friendly service name mapping. Mirrors Android setPeerServiceMapping(). */
  peerServiceMap?: Record<string, string>;
  /** Request header names to capture as span attributes (http.request.header.<name>). */
  capturedRequestHeaders?: string[];
  /** Response header names to capture as span attributes (http.response.header.<name>). */
  capturedResponseHeaders?: string[];
  /** Privacy controls. */
  privacy?: {
    /** If true, query params are preserved in url.full. Default false (stripped). */
    captureQueryParams?: boolean;
  };
  /**
   * Origins that should receive trace context propagation headers (traceparent).
   * Default: all origins (/.* /).  Mirror of Android's context propagation behaviour.
   */
  propagateTraceHeaderCorsUrls?: Array<string | RegExp>;
}

// ─── Deprecated HTTP semconv keys emitted by OTel instrumentation-fetch ─────
// These are set by @opentelemetry/instrumentation-fetch v0.53 using the old
// semconv. The NetworkSemconvFixupProcessor removes them on span end so only
// stable keys reach the exporter.

const DEPRECATED_HTTP_KEYS = [
  "http.url",
  "http.method",
  "http.status_code",
  "http.host",
  "http.scheme",
  "http.status_text",
  "http.request_content_length",
  "http.response_content_length",
  "net.peer.name",
] as const;

// ─── SpanProcessor: upgrade deprecated HTTP semconv keys ────────────────────

/**
 * Removes deprecated HTTP semconv attributes from network spans after they end.
 * The attributes have already been read by `applyCustomAttributesOnSpan` callback
 * and re-emitted as stable keys (http.request.method, url.full, etc.) — so it is
 * safe to delete the deprecated counterparts here.
 */
class NetworkSemconvFixupProcessor implements SpanProcessor {
  onStart(_span: Span, _ctx: Context): void {
    /* no-op */
  }

  onEnd(span: ReadableSpan): void {
    if (span.attributes[PulseWebSemconv.AttributeKey.PULSE_TYPE] !== PulseWebSemconv.PulseType.HTTP) return;
    // Mutate the attributes bag to remove deprecated keys before export
    const attrs = span.attributes as Record<string, unknown>;
    for (const key of DEPRECATED_HTTP_KEYS) {
      delete attrs[key];
    }
  }

  shutdown(): Promise<void> {
    return Promise.resolve();
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }
}

// ─── Public class ────────────────────────────────────────────────────────────

export class NetworkInstrumentation implements PulseInstrumentation {
  readonly name = "network";

  private fetchInstr?: FetchInstrumentation;
  private xhrInstr?: XMLHttpRequestInstrumentation;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    const networkCfg = (
      sdk.config.instrumentations?.network as unknown as NetworkConfig | undefined
    ) ?? {};

    const endpointBaseUrl = resolveEndpointBaseUrl(sdk.config.apiKey);
    // Use a regex prefix-match so paths like /v1/traces are also excluded.
    // OTel's isUrlIgnored uses exact === for strings, so we must use RegExp.
    const endpointEscaped = endpointBaseUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const endpointRegex = new RegExp(`^${endpointEscaped}`);
    const ignored: Array<string | RegExp> = [
      endpointRegex,
      ...(networkCfg.blockedUrls ?? []),
    ];
    const propagateCorsUrls = networkCfg.propagateTraceHeaderCorsUrls ?? [/.*/];
    const captureQueryParams = networkCfg.privacy?.captureQueryParams ?? false;

    this.fetchInstr = new FetchInstrumentation({
      ignoreUrls: ignored,
      propagateTraceHeaderCorsUrls: propagateCorsUrls,
      applyCustomAttributesOnSpan: (
        span: Span,
        request: Request | RequestInit,
        result: Response | FetchError,
      ) => {
        this.applyFetchAttrs(span, request, result, networkCfg, captureQueryParams);
      },
    });

    this.xhrInstr = new XMLHttpRequestInstrumentation({
      ignoreUrls: ignored,
      applyCustomAttributesOnSpan: (span: Span, xhr: XMLHttpRequest) => {
        this.applyXhrAttrs(span, xhr, networkCfg);
      },
    });

    // Ensure spans go through our provider (already set as global at this point)
    const rawProvider = trace.getTracerProvider();
    // ProxyTracerProvider.getDelegate() returns the actual WebTracerProvider
    const actualProvider =
      (rawProvider as unknown as { getDelegate?(): unknown }).getDelegate?.() ?? rawProvider;

    this.fetchInstr.setTracerProvider(rawProvider);
    this.xhrInstr.setTracerProvider(rawProvider);

    // Register the semconv upgrade processor to strip deprecated HTTP keys
    if (actualProvider && "addSpanProcessor" in (actualProvider as object)) {
      (actualProvider as { addSpanProcessor(p: SpanProcessor): void }).addSpanProcessor(
        new NetworkSemconvFixupProcessor(),
      );
    }

    this.fetchInstr.enable();
    this.xhrInstr.enable();
  }

  uninstall(): void {
    this.fetchInstr?.disable();
    this.xhrInstr?.disable();
    this.fetchInstr = undefined;
    this.xhrInstr = undefined;
  }

  // ─── Fetch span enrichment ─────────────────────────────────────────────────

  private applyFetchAttrs(
    span: Span,
    request: Request | RequestInit,
    result: Response | FetchError,
    config: NetworkConfig,
    captureQueryParams: boolean,
  ): void {
    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;

    span.setAttribute(K.PULSE_TYPE, T.HTTP);

    // ── http.request.method ──────────────────────────────────────────────────
    // OTel's instrumentation-fetch sets the deprecated `http.method`.
    // We read it from the span attrs (already set at span start) and emit the
    // stable `http.request.method` key. Fallback: read from RequestInit.method.
    const deprecatedMethod = getSpanAttr(span, "http.method");
    const requestMethod = request instanceof Request
      ? request.method
      : (request as RequestInit).method;
    const method = (deprecatedMethod ?? requestMethod ?? "GET").toUpperCase();
    span.setAttribute(K.HTTP_REQUEST_METHOD, method);

    // ── url.full + server.address ────────────────────────────────────────────
    // OTel sets `http.url` at span start — use it as fallback when `getRequestUrl`
    // returns undefined (which happens for `fetch(urlString)` calls, where OTel
    // passes `options = {}` to the callback rather than a `Request` object).
    const rawUrl = getRequestUrl(request) ?? getSpanAttr(span, "http.url");
    if (rawUrl) {
      const sanitized = sanitizeUrl(rawUrl, captureQueryParams);
      span.setAttribute(K.URL_FULL, sanitized);

      // server.address — hostname extracted from URL (excludes port)
      try {
        const parsed = new URL(rawUrl);
        span.setAttribute(K.SERVER_ADDRESS, parsed.hostname);
      } catch {
        /* relative URL or non-standard — skip */
      }

      // http.duration from PerformanceResourceTiming (best-effort)
      const dur = getResourceDuration(rawUrl);
      if (dur !== undefined) span.setAttribute(K.HTTP_DURATION, dur);

      // peer.service mapping (mirrors Android setPeerServiceMapping())
      try {
        const host = new URL(rawUrl).hostname;
        const svc = config.peerServiceMap?.[host];
        if (svc) span.setAttribute(K.PEER_SERVICE, svc);
      } catch {
        /* non-parseable URL — skip */
      }
    }

    // ── Span status + error.type + http.response.status_code ────────────────
    if (isResponse(result)) {
      const code = result.status;

      // Always emit stable http.response.status_code (OTel only emits deprecated http.status_code)
      if (code > 0) span.setAttribute(K.HTTP_RESPONSE_STATUS_CODE, code);

      if (code === 0) {
        span.setStatus({ code: SpanStatusCode.ERROR });
        span.setAttribute(K.ERROR_TYPE, "cors_error");
      } else if (code >= 400 && code < 500) {
        span.setStatus({ code: SpanStatusCode.ERROR });
        span.setAttribute(K.ERROR_TYPE, "4xx");
      } else if (code >= 500) {
        span.setStatus({ code: SpanStatusCode.ERROR });
        span.setAttribute(K.ERROR_TYPE, "5xx");
      }

      // Response body size
      const resLen = result.headers?.get("content-length");
      if (resLen !== null && resLen !== undefined) {
        span.setAttribute(K.HTTP_RESPONSE_BODY_SIZE, Number(resLen));
      }
      // Custom response headers
      config.capturedResponseHeaders?.forEach((h) => {
        const v = result.headers?.get(h);
        if (v !== null && v !== undefined) {
          span.setAttribute(`http.response.header.${h.toLowerCase()}`, v);
        }
      });
    } else {
      // FetchError — network failure (timeout, DNS failure, CORS pre-flight block, etc.)
      span.setStatus({ code: SpanStatusCode.ERROR });
      span.setAttribute(K.ERROR_TYPE, "network_error");
    }

    // Request body size
    const reqLen = getRequestHeader(request, "content-length");
    if (reqLen !== null) span.setAttribute(K.HTTP_REQUEST_BODY_SIZE, Number(reqLen));

    // GraphQL operation extraction from POST body
    const body = getRequestBody(request);
    if (body !== null && isGraphQL(body)) {
      const opName = extractOpName(body);
      const opType = extractOpType(body);
      if (opName) span.setAttribute(K.GRAPHQL_OPERATION_NAME, opName);
      if (opType) span.setAttribute(K.GRAPHQL_OPERATION_TYPE, opType);
    }

    // Custom request headers
    config.capturedRequestHeaders?.forEach((h) => {
      const v = getRequestHeader(request, h);
      if (v !== null) span.setAttribute(`http.request.header.${h.toLowerCase()}`, v);
    });
  }

  // ─── XHR span enrichment ───────────────────────────────────────────────────

  private applyXhrAttrs(
    span: Span,
    xhr: XMLHttpRequest,
    config: NetworkConfig,
  ): void {
    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;

    span.setAttribute(K.PULSE_TYPE, T.HTTP);

    // ── http.request.method ──────────────────────────────────────────────────
    const deprecatedMethod = getSpanAttr(span, "http.method");
    if (deprecatedMethod) span.setAttribute(K.HTTP_REQUEST_METHOD, deprecatedMethod);

    // ── url.full + server.address ────────────────────────────────────────────
    // OTel's XMLHttpRequestInstrumentation sets deprecated `http.url` at span start.
    // Read it here (before onEnd removes it) and re-emit as stable `url.full`.
    const rawUrl = getSpanAttr(span, "http.url") ?? xhr.responseURL;
    if (rawUrl) {
      span.setAttribute(K.URL_FULL, sanitizeUrl(rawUrl));
      try {
        const parsed = new URL(rawUrl);
        span.setAttribute(K.SERVER_ADDRESS, parsed.hostname);
      } catch {
        /* skip */
      }
    }

    // Span status for 4xx/5xx
    const code = xhr.status;
    if (code >= 400 && code < 500) {
      span.setStatus({ code: SpanStatusCode.ERROR });
      span.setAttribute(K.ERROR_TYPE, "4xx");
    } else if (code >= 500) {
      span.setStatus({ code: SpanStatusCode.ERROR });
      span.setAttribute(K.ERROR_TYPE, "5xx");
    }

    // Response body size
    const resLen = xhr.getResponseHeader("content-length");
    if (resLen !== null) span.setAttribute(K.HTTP_RESPONSE_BODY_SIZE, Number(resLen));

    // Custom response headers
    config.capturedResponseHeaders?.forEach((h) => {
      const v = xhr.getResponseHeader(h);
      if (v !== null) span.setAttribute(`http.response.header.${h.toLowerCase()}`, v);
    });
  }
}

// ─── URL helpers ─────────────────────────────────────────────────────────────

/**
 * Strip query params from URL unless captureQueryParams=true.
 * Falls back to original string if URL cannot be parsed.
 */
export function sanitizeUrl(url: string, captureQueryParams = false): string {
  try {
    const u = new URL(url, typeof window !== "undefined" ? window.location.origin : undefined);
    if (!captureQueryParams) {
      u.search = "";
    }
    return u.toString();
  } catch {
    return url;
  }
}

/** Extract the URL string from a Request or RequestInit object. */
export function getRequestUrl(request: Request | RequestInit): string | undefined {
  if (request instanceof Request) return request.url;
  // RequestInit has no url property — caller should fall back to span's http.url attr
  return undefined;
}

/**
 * Read an attribute value from a span's attributes bag.
 * Works because SdkSpan (which implements ReadableSpan) exposes `attributes` publicly.
 */
function getSpanAttr(span: Span, key: string): string | undefined {
  const attrs = (span as unknown as { attributes?: Record<string, unknown> }).attributes;
  const val = attrs?.[key];
  return val !== undefined && val !== null ? String(val) : undefined;
}

/**
 * Get the request body as a string.
 * Returns null for FormData, Blob, ArrayBuffer, ReadableStream, or unparseable bodies.
 */
export function getRequestBody(request: Request | RequestInit): string | null {
  // For Request objects, .body is a ReadableStream (consumed) — not readable as string
  if (request instanceof Request) return null;
  const body = (request as RequestInit).body;
  if (typeof body === "string") return body;
  return null;
}

/** Get a request header value (case-insensitive). Returns null if not found. */
export function getRequestHeader(
  request: Request | RequestInit,
  name: string,
): string | null {
  if (request instanceof Request) {
    return request.headers.get(name);
  }
  const headers = (request as RequestInit).headers;
  if (!headers) return null;
  if (headers instanceof Headers) return headers.get(name);
  if (Array.isArray(headers)) {
    const lower = name.toLowerCase();
    const found = headers.find(([k]) => k.toLowerCase() === lower);
    return found ? found[1]! : null;
  }
  // Plain Record<string, string>
  const lower = name.toLowerCase();
  for (const [k, v] of Object.entries(headers as Record<string, string>)) {
    if (k.toLowerCase() === lower) return v;
  }
  return null;
}

/** Type guard — distinguishes Response from FetchError. */
function isResponse(result: Response | FetchError): result is Response {
  return typeof (result as Response).headers !== "undefined";
}

// ─── GraphQL helpers ──────────────────────────────────────────────────────────

/** Returns true if the body looks like a GraphQL request (has a `query` field). */
export function isGraphQL(body: string): boolean {
  if (!body) return false;
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    return "query" in parsed && typeof parsed["query"] === "string";
  } catch {
    return false;
  }
}

/**
 * Extract operation name from a GraphQL request body.
 * Prefers `operationName` field; falls back to regex on the query string.
 */
export function extractOpName(body: string): string | null {
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    if (typeof parsed["operationName"] === "string" && parsed["operationName"]) {
      return parsed["operationName"];
    }
    const q = typeof parsed["query"] === "string" ? parsed["query"] : "";
    // Match: query GetUser { ... }  or  mutation CreateOrder { ... }
    const match = q.match(/(?:query|mutation|subscription)\s+(\w+)/);
    return match?.[1] ?? null;
  } catch {
    return null;
  }
}

/**
 * Extract operation type (query | mutation | subscription) from a GraphQL request body.
 * Defaults to "query" if the query field starts with a selection set (anonymous query).
 */
export function extractOpType(body: string): string | null {
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    const q = typeof parsed["query"] === "string" ? parsed["query"] : "";
    const trimmed = q.trimStart();
    const match = trimmed.match(/^(query|mutation|subscription)\b/);
    return match?.[1] ?? (trimmed.startsWith("{") ? "query" : null);
  } catch {
    return null;
  }
}

// ─── PerformanceResourceTiming duration helper ────────────────────────────────

/**
 * Look up the most recent PerformanceResourceTiming entry for the given URL
 * and return the duration in ms. Returns undefined if no entry found.
 */
function getResourceDuration(url: string): number | undefined {
  if (typeof performance === "undefined" || !performance.getEntriesByName) return undefined;
  const entries = performance.getEntriesByName(url, "resource") as PerformanceResourceTiming[];
  if (entries.length === 0) return undefined;
  const entry = entries[entries.length - 1]!;
  return Math.round(entry.responseEnd - entry.startTime);
}
