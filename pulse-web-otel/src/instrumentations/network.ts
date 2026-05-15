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

/**
 * Module-scoped store for XHR request headers captured via the
 * setRequestHeader monkey-patch. Must outlive individual spans so the
 * applyCustomAttributesOnSpan callback (fired after send()) can still read
 * headers that were set before send(). WeakMap prevents leaking XHR
 * references — entries are deleted after the span callback runs.
 *
 * Exported for testing only; not part of the public API.
 */
export const xhrHeaderStore = new WeakMap<XMLHttpRequest, Record<string, string>>();

/** Original setRequestHeader kept for call-through and teardown. */
let _origSetRequestHeader:
  | ((this: XMLHttpRequest, name: string, value: string) => void)
  | undefined;

/**
 * Install the setRequestHeader monkey-patch when capturedRequestHeaders is
 * non-empty. Idempotent — a second call while already patched is a no-op.
 */
function installXhrHeaderPatch(): void {
  if (_origSetRequestHeader !== undefined) {
    return;
  }
  const orig = XMLHttpRequest.prototype.setRequestHeader;
  _origSetRequestHeader = orig;
  XMLHttpRequest.prototype.setRequestHeader = function (
    name: string,
    value: string,
  ): void {
    const stored = xhrHeaderStore.get(this) ?? {};
    stored[name.toLowerCase()] = value;
    xhrHeaderStore.set(this, stored);
    try {
      return orig.call(this, name, value);
    } catch {
      // best-effort: don't break XHR if original call throws (e.g. Firefox quirks)
    }
  };
}

/**
 * Remove the setRequestHeader monkey-patch and clear the store.
 * Called from NetworkInstrumentation.uninstall().
 */
function uninstallXhrHeaderPatch(): void {
  if (_origSetRequestHeader === undefined) {
    return;
  }
  XMLHttpRequest.prototype.setRequestHeader = _origSetRequestHeader;
  _origSetRequestHeader = undefined;
}

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

    // Install the setRequestHeader patch only when the caller explicitly wants
    // request header capture on XHR. Keeps the monkey-patch surface zero when
    // the feature is unused.
    const capturedRequestHeaders = net?.capturedRequestHeaders;
    if (
      capturedRequestHeaders &&
      capturedRequestHeaders.length > 0 &&
      typeof XMLHttpRequest !== "undefined"
    ) {
      installXhrHeaderPatch();
    }

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

        // Read headers captured by the setRequestHeader monkey-patch (installed
        // when capturedRequestHeaders is non-empty). Browser hides sent headers
        // after send(), so the WeakMap is the only source of truth here.
        const storedHeaders = xhrHeaderStore.get(xhr) ?? {};
        const requestHeaderGet =
          Object.keys(storedHeaders).length > 0
            ? (name: string): string | undefined =>
                storedHeaders[name.toLowerCase()]
            : undefined;

        applyPulseHttpClientSpanAttributes({
          span,
          resolvedUrl: url,
          method,
          statusCode,
          privacy,
          optional,
          perfLookupUrl: url || undefined,
          requestHeaderGet,
          responseHeaderGet: (name) => xhr.getResponseHeader(name),
        });

        // Cleanup: remove the XHR entry now that the span has been finalized.
        xhrHeaderStore.delete(xhr);
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
      uninstallXhrHeaderPatch();
    } finally {
      this.fetchInstr = undefined;
      this.xhrInstr = undefined;
    }
  }
}
