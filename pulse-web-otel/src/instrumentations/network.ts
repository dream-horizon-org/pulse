import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";

import type {
  PulseInstrumentation,
  SdkContext,
} from "../instrumentation-registry";
import type { Span } from "@opentelemetry/api";
import {
  applyPulseHttpClientSpanAttributes,
  buildNetworkIgnoreUrls,
  getOtelHttpUrlFromSpan,
  methodFromOtelClientSpanName,
  type NetworkSpanOptionalConfig,
} from "../utils/network-http";

function resolveFetchUrl(
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

function resolveFetchMethod(request: Request | RequestInit): string {
  if (request instanceof Request) {
    return request.method;
  }
  const m = request.method;
  return typeof m === "string" ? m : "GET";
}

function resolveFetchStatus(result: unknown): number | undefined {
  if (result instanceof Response) {
    return result.status;
  }
  if (typeof result === "object" && result !== null && "status" in result) {
    const s = (result as { status?: number }).status;
    return typeof s === "number" ? s : undefined;
  }
  return undefined;
}

function requestHeaderGetter(
  request: Request | RequestInit,
): ((name: string) => string | null) | undefined {
  if (request instanceof Request) {
    return (name: string) => request.headers.get(name);
  }
  const h = request.headers;
  if (h instanceof Headers) {
    return (name: string) => h.get(name);
  }
  return undefined;
}

export class NetworkInstrumentation implements PulseInstrumentation {
  readonly name = "network";

  private fetchInstr?: FetchInstrumentation;
  private xhrInstr?: XMLHttpRequestInstrumentation;
  /** True after Fetch + XHR instrumentations are enabled; avoids double `disable()` noise. */
  private instrumentsActive = false;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") {
      return;
    }
    const provider = sdk.tracerProvider;
    if (!provider) {
      return;
    }

    const net = sdk.config.instrumentations?.network;
    const optional: NetworkSpanOptionalConfig | undefined = net
      ? {
          peerServiceMap: net.peerServiceMap,
          capturedRequestHeaders: net.capturedRequestHeaders,
          capturedResponseHeaders: net.capturedResponseHeaders,
        }
      : undefined;

    const privacy = { captureQueryParams: net?.captureQueryParams === true };

    const ignoreUrls = buildNetworkIgnoreUrls(
      sdk.endpointBaseUrl,
      net?.blockedUrls,
    );

    const propagate = net?.propagateTraceHeaderCorsUrls;

    this.fetchInstr = new FetchInstrumentation({
      ignoreUrls,
      ...(propagate !== undefined
        ? { propagateTraceHeaderCorsUrls: propagate }
        : {}),
      applyCustomAttributesOnSpan: (span, request, result) => {
        const resolvedUrl =
          resolveFetchUrl(span, request, result) ||
          (request instanceof Request ? request.url : "");
        const method = resolveFetchMethod(request);
        const statusCode = resolveFetchStatus(result);
        const perfLookup =
          result instanceof Response ? result.url : resolvedUrl;

        applyPulseHttpClientSpanAttributes({
          span,
          resolvedUrl,
          method,
          statusCode,
          privacy,
          optional,
          perfLookupUrl: perfLookup,
          requestHeaderGet: requestHeaderGetter(request),
          responseHeaderGet:
            result instanceof Response
              ? (name: string) => result.headers.get(name)
              : undefined,
          graphqlRequestBody: undefined,
        });
      },
    });

    this.xhrInstr = new XMLHttpRequestInstrumentation({
      ignoreUrls,
      ...(propagate !== undefined
        ? { propagateTraceHeaderCorsUrls: propagate }
        : {}),
      applyCustomAttributesOnSpan: (span, xhr) => {
        // Upstream invokes this when ending the span (request finished); guard so we never
        // stamp `network_error` from `undefined` status if a hypothetical early hook fired.
        if (xhr.readyState !== XMLHttpRequest.DONE) {
          return;
        }
        const url = xhr.responseURL || "";
        const opaque = span as unknown as { name?: string };
        const method = methodFromOtelClientSpanName(opaque.name);
        const statusCode = xhr.status;

        applyPulseHttpClientSpanAttributes({
          span,
          resolvedUrl: url,
          method,
          statusCode,
          privacy,
          optional,
          perfLookupUrl: url || undefined,
          requestHeaderGet: undefined,
          responseHeaderGet: (name) => xhr.getResponseHeader(name),
          graphqlRequestBody: undefined,
        });
      },
    });

    this.fetchInstr.setTracerProvider(provider);
    this.fetchInstr.enable();
    this.xhrInstr.setTracerProvider(provider);
    this.xhrInstr.enable();
    this.instrumentsActive = true;
  }

  uninstall(): void {
    if (!this.instrumentsActive) {
      return;
    }
    this.instrumentsActive = false;
    this.fetchInstr?.disable();
    this.xhrInstr?.disable();
    this.fetchInstr = undefined;
    this.xhrInstr = undefined;
  }
}
