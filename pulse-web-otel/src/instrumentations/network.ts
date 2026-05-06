import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";

import type {
  PulseInstrumentation,
  SdkContext,
} from "../instrumentation-registry";
import {
  applyPulseHttpClientSpanAttributes,
  buildNetworkIgnoreUrls,
  getOtelHttpRequestMethodFromSpan,
  methodFromOtelClientSpanName,
  requestHeaderGetter,
  resolveFetchMethod,
  resolveFetchStatus,
  resolveFetchUrl,
  type NetworkSpanOptionalConfig,
} from "../utils/network-http";

/** Never throws — each upstream {@code disable()} runs even if a sibling fails. */
function disableInstrumentationBestEffort(
  instr: { disable(): void } | undefined,
): void {
  if (!instr) {
    return;
  }
  try {
    instr.disable();
  } catch {
    /* OTel unpatch can throw in odd test/env setups */
  }
}

export class NetworkInstrumentation implements PulseInstrumentation {
  readonly name = "network";

  private fetchInstr?: FetchInstrumentation;
  private xhrInstr?: XMLHttpRequestInstrumentation;
  /** True after Fetch + XHR instrumentations are enabled; avoids double `disable()` noise. */
  private instrumentsActive = false;

  install(sdk: SdkContext): void {
    if (this.instrumentsActive || typeof window === "undefined") {
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
        const method =
          getOtelHttpRequestMethodFromSpan(span) ?? resolveFetchMethod(request);
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
        const method =
          getOtelHttpRequestMethodFromSpan(span) ??
          methodFromOtelClientSpanName(opaque.name);
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
    try {
      disableInstrumentationBestEffort(this.fetchInstr);
      disableInstrumentationBestEffort(this.xhrInstr);
    } finally {
      this.fetchInstr = undefined;
      this.xhrInstr = undefined;
    }
  }
}
