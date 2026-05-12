import { diag } from "@opentelemetry/api";
import type {
  IExporterTransport,
  ExportResponse,
} from "@opentelemetry/otlp-exporter-base";
import { createPulseRetryingTransport } from "./pulse-retrying-transport";
import { gzipUint8Array, isGzipSupported } from "../utils/otlp-gzip";
import type { IdbSignalBuffer } from "../persistence/indexed-db";
import type { OtlpSignalKind, PersistMeta } from "../types/otlp-transport";

export type { OtlpSignalKind, PersistMeta } from "../types/otlp-transport";

const RETRYABLE = new Set([429, 502, 503, 504]);

function parseRetryAfterToMillis(retryAfter: string | null): number {
  if (retryAfter == null) return -1;
  const seconds = Number.parseInt(retryAfter, 10);
  if (Number.isInteger(seconds)) {
    return seconds > 0 ? seconds * 1000 : -1;
  }
  const delay = new Date(retryAfter).getTime() - Date.now();
  if (delay >= 0) return delay;
  return 0;
}

/**
 * XHR transport matching @opentelemetry/otlp-exporter-base browser behaviour.
 */
export function createPulseXhrTransport(parameters: {
  url: string;
  headers: Record<string, string>;
}): IExporterTransport {
  return {
    send(data: Uint8Array, timeoutMillis: number): Promise<ExportResponse> {
      return new Promise((resolve) => {
        const xhr = new XMLHttpRequest();
        xhr.timeout = timeoutMillis;
        xhr.open("POST", parameters.url);
        Object.entries(parameters.headers).forEach(([k, v]) => {
          xhr.setRequestHeader(k, v);
        });
        xhr.ontimeout = () => {
          resolve({
            status: "failure",
            error: new Error("XHR request timed out"),
          });
        };
        xhr.onreadystatechange = () => {
          if (xhr.readyState !== XMLHttpRequest.DONE) return;
          if (xhr.status >= 200 && xhr.status <= 299) {
            diag.debug("OTLP XHR success");
            resolve({ status: "success" });
          } else if (xhr.status && RETRYABLE.has(xhr.status)) {
            resolve({
              status: "retryable",
              retryInMillis: parseRetryAfterToMillis(
                xhr.getResponseHeader("Retry-After"),
              ),
            });
          } else if (xhr.status !== 0) {
            resolve({
              status: "failure",
              error: new Error("XHR request failed with non-retryable status"),
            });
          } else {
            resolve({
              status: "failure",
              error: new Error(
                "XHR completed with status 0 (network/CORS or mixed content)",
              ),
            });
          }
        };
        xhr.onabort = () => {
          resolve({
            status: "failure",
            error: new Error("XHR request aborted"),
          });
        };
        xhr.onerror = () => {
          resolve({
            status: "failure",
            error: new Error("XHR request errored"),
          });
        };
        // Send as Uint8Array (Content-Type already set via setRequestHeader above).
        // Avoid wrapping in Blob — Playwright webkit cannot read postDataBuffer()
        // for Blob-bodied XHR requests, breaking E2E test interception.
        xhr.send(data as XMLHttpRequestBodyInit | null | undefined);
      });
    },
    shutdown() {},
  };
}

export function wrapTransportWithGzip(
  inner: IExporterTransport,
): IExporterTransport {
  if (!isGzipSupported()) return inner;
  return {
    async send(
      data: Uint8Array,
      timeoutMillis: number,
    ): Promise<ExportResponse> {
      const gzipped = await gzipUint8Array(data);
      return inner.send(gzipped, timeoutMillis);
    },
    shutdown() {
      inner.shutdown();
    },
  };
}

export function wrapTransportWithDiskPersistence(
  inner: IExporterTransport,
  options: {
    enabled: boolean;
    buffer: IdbSignalBuffer;
    signalKind: OtlpSignalKind;
    meta: PersistMeta;
  },
): IExporterTransport {
  if (!options.enabled) return inner;
  const { buffer, signalKind, meta } = options;
  return {
    async send(
      data: Uint8Array,
      timeoutMillis: number,
    ): Promise<ExportResponse> {
      const response = await inner.send(data, timeoutMillis);
      if (response.status === "failure" && response.error) {
        const bodyB64 = uint8ToBase64(data);
        void buffer.write(signalKind, {
          bodyB64,
          contentType: meta.contentType,
          contentEncoding: meta.contentEncoding,
        });
      }
      return response;
    },
    shutdown() {
      inner.shutdown();
    },
  };
}

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

export function base64ToUint8(b64: string): Uint8Array {
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    out[i] = binary.charCodeAt(i);
  }
  return out;
}

export function createPulseFetchTransport(parameters: {
  url: string;
  headers: Record<string, string>;
  keepalive?: boolean;
}): IExporterTransport {
  return {
    send(data: Uint8Array, _timeoutMillis: number): Promise<ExportResponse> {
      return fetch(parameters.url, {
        method: "POST",
        headers: parameters.headers,
        body: data as BodyInit,
        keepalive: parameters.keepalive ?? false,
      })
        .then((res) => {
          if (res.ok) return { status: "success" as const };
          if (RETRYABLE.has(res.status))
            return { status: "retryable" as const };
          return {
            status: "failure" as const,
            error: new Error(`Fetch failed: ${res.status}`),
          };
        })
        .catch((err: unknown) => ({
          status: "failure" as const,
          error: err instanceof Error ? err : new Error(String(err)),
        }));
    },
    shutdown() {},
  };
}

/**
 * Recommended max body size for navigator.sendBeacon.
 * The spec soft limit is 64 KiB; payloads above this fall back to keepalive fetch.
 */
export const BEACON_BODY_LIMIT_BYTES = 64 * 1024;

/**
 * SendBeacon transport — most reliable for page-unload delivery.
 *
 * `navigator.sendBeacon` cannot carry custom request headers, so auth must be
 * handled out-of-band.  Two modes:
 *
 * 1. **Relay URL (preferred)** — set `beaconRelayUrl` to a same-origin endpoint
 *    (e.g. `/api/pulse-relay`) that forwards the payload with a server-side
 *    `X-API-KEY` header.  The API key never appears in the URL.
 *
 * 2. **Query-param fallback** — when no relay URL is provided the API key is
 *    appended as `?apiKey=<key>`.  This is visible in server access logs,
 *    browser DevTools network panel, and reverse-proxy logs.  A one-time
 *    `console.warn` is emitted to flag the exposure.
 *
 * The OTLP Content-Type is conveyed via the Blob type so the collector can
 * still deserialise the payload correctly.
 *
 * Returns `{status:"failure"}` when sendBeacon is unavailable or the browser
 * rejects the beacon (quota exceeded).
 */

let _beaconKeyWarnEmitted = false;
/** Reset warning gate — for unit tests only. */
export function _resetBeaconKeyWarnForTesting(): void {
  _beaconKeyWarnEmitted = false;
}

export function createPulseSendBeaconTransport(params: {
  url: string;
  apiKey?: string;
  /** Same-origin relay URL. When provided the apiKey is NOT appended to the URL. */
  beaconRelayUrl?: string;
  contentType: string;
}): IExporterTransport {
  return {
    send(data: Uint8Array, _timeoutMillis: number): Promise<ExportResponse> {
      if (
        typeof navigator === "undefined" ||
        typeof navigator.sendBeacon !== "function"
      ) {
        return Promise.resolve({
          status: "failure",
          error: new Error("sendBeacon not available in this environment"),
        });
      }

      let url: string;
      if (params.beaconRelayUrl) {
        // Relay endpoint handles auth server-side — key stays out of the URL.
        url = params.beaconRelayUrl;
      } else if (params.apiKey != null && params.apiKey !== "") {
        // Fallback: embed key in query string. Warn once about exposure risk.
        if (!_beaconKeyWarnEmitted) {
          _beaconKeyWarnEmitted = true;
          console.warn(
            "[Pulse] sendBeacon: API key sent as URL query parameter — " +
              "visible in server access logs and browser tooling. " +
              "Set PulseWebConfig.beaconRelayUrl to route through a same-origin proxy instead.",
          );
        }
        url = `${params.url}${params.url.includes("?") ? "&" : "?"}apiKey=${encodeURIComponent(params.apiKey)}`;
      } else {
        url = params.url;
      }
      // Cast: TS 5.7+ types Uint8Array as Uint8Array<ArrayBufferLike>; Blob's
      // BlobPart constraint accepts Uint8Array<ArrayBuffer>. Runtime is fine —
      // sendBeacon copies the buffer synchronously before resolving.
      const blob = new Blob([data as BlobPart], { type: params.contentType });
      const queued = navigator.sendBeacon(url, blob);
      return Promise.resolve(
        queued
          ? ({ status: "success" } as const)
          : ({
              status: "failure",
              error: new Error(
                "sendBeacon rejected by browser (quota exceeded or unload not in progress)",
              ),
            } as const),
      );
    },
    shutdown() {},
  };
}

export type BrowserExportTransport = IExporterTransport & {
  switchToKeepalive(): void;
  /**
   * Switch the inner transport to an unload-safe mode:
   * - payloads ≤ {@link BEACON_BODY_LIMIT_BYTES} → sendBeacon (most reliable)
   * - payloads > limit → keepalive fetch (handles larger batches)
   * Falls back to keepalive fetch if sendBeacon is not available.
   */
  switchToBeacon(params: {
    apiKey?: string;
    beaconRelayUrl?: string;
    contentType: string;
  }): void;
};

/**
 * Outermost: retrying. Inner chain should be: persist → gzip → xhr (or persist → xhr).
 */
export function buildBrowserExportTransport(
  xhrParams: { url: string; headers: Record<string, string> },
  options: {
    useGzip: boolean;
    diskPersistence: {
      enabled: boolean;
      buffer: IdbSignalBuffer;
      signalKind: OtlpSignalKind;
      meta: PersistMeta;
    };
  },
): BrowserExportTransport {
  // Mutable inner transport — starts as XHR, can switch to keepalive fetch on pagehide.
  let innerXhr: IExporterTransport = createPulseXhrTransport(xhrParams);

  const switcher: IExporterTransport = {
    send(data, timeout) {
      return innerXhr.send(data, timeout);
    },
    shutdown() {
      innerXhr.shutdown();
    },
  };

  let t: IExporterTransport = switcher;
  if (options.useGzip) {
    t = wrapTransportWithGzip(t);
  }
  t = wrapTransportWithDiskPersistence(t, options.diskPersistence);
  const retrying = createPulseRetryingTransport({ transport: t });

  return {
    send: retrying.send.bind(retrying),
    shutdown: retrying.shutdown.bind(retrying),
    switchToKeepalive() {
      innerXhr = createPulseFetchTransport({
        ...xhrParams,
        keepalive: true,
      });
    },
    switchToBeacon({
      apiKey,
      beaconRelayUrl,
      contentType,
    }: {
      apiKey?: string;
      beaconRelayUrl?: string;
      contentType: string;
    }) {
      const beacon = createPulseSendBeaconTransport({
        url: xhrParams.url,
        apiKey,
        beaconRelayUrl,
        contentType,
      });
      const keepalive = createPulseFetchTransport({
        ...xhrParams,
        keepalive: true,
      });

      innerXhr = {
        send(data: Uint8Array, timeout: number): Promise<ExportResponse> {
          // For small payloads, prefer sendBeacon (survives page close).
          // For larger payloads that exceed the beacon limit, fall back to keepalive fetch.
          if (data.byteLength <= BEACON_BODY_LIMIT_BYTES) {
            return beacon.send(data, timeout).then((res) => {
              if (res.status === "failure") {
                // sendBeacon failed (unavailable or quota) → try keepalive fetch
                return keepalive.send(data, timeout);
              }
              return res;
            });
          }
          return keepalive.send(data, timeout);
        },
        shutdown() {},
      };
    },
  };
}
