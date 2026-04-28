// M5: Network instrumentation — captures outbound HTTP (Fetch + XHR) as spans.
// Owns the `http` pulse.type. Android parity: OkHttpInstrumentation.
// Uses stable OTel HTTP semconv (http.request.method, url.full, http.response.status_code, server.address).

import type { Span } from "@opentelemetry/api";
import { SpanStatusCode, trace } from "@opentelemetry/api";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";
import type { FetchError } from "@opentelemetry/instrumentation-fetch/build/src/types";

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
    const ignored: Array<string | RegExp> = [
      endpointBaseUrl,
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
    const provider = trace.getTracerProvider();
    this.fetchInstr.setTracerProvider(provider);
    this.xhrInstr.setTracerProvider(provider);

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

    // URL sanitization — override url.full set by OTel with stripped version
    const rawUrl = getRequestUrl(request);
    if (rawUrl) {
      const sanitized = sanitizeUrl(rawUrl, captureQueryParams);
      span.setAttribute(K.URL_FULL, sanitized);

      // http.duration from PerformanceResourceTiming (best-effort)
      const dur = getResourceDuration(rawUrl);
      if (dur !== undefined) span.setAttribute(K.HTTP_DURATION, dur);

      // peer.service mapping (mirrors Android setPeerServiceMapping())
      try {
        const host = new URL(rawUrl).hostname;
        const svc = config.peerServiceMap?.[host];
        if (svc) span.setAttribute(K.PEER_SERVICE, svc);
      } catch {
        /* relative URL or non-standard — skip */
      }
    }

    // Span status + error.type
    if (isResponse(result)) {
      const code = result.status;
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
  // RequestInit has no url — the instrumentation passes Request for fetch()
  return undefined;
}

/**
 * Get the request body as a string.
 * Returns null for FormData, Blob, ArrayBuffer, ReadableStream, or unparseable bodies.
 */
export function getRequestBody(request: Request | RequestInit): string | null {
  const body = request instanceof Request ? request.body : (request as RequestInit).body;
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
