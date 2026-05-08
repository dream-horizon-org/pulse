import type { PulseWebBeforeSendConfig } from "./before-send";
import type { PulseLogLevel } from "../pulse-log-level";
import type { PulseAttributes } from "./attributes";

/** Consent for telemetry collection (Android `dataCollectionState` parity). */
export enum PulseDataCollectionConsent {
  ALLOWED = "ALLOWED",
  DENIED = "DENIED",
  PENDING = "PENDING",
}

/** OTel browser trace header propagation allowlist — same shape as {@code propagateTraceHeaderCorsUrls}. */
export type PulseNetworkPropagateCorsUrls =
  | string
  | RegExp
  | Array<string | RegExp>;

export interface InstrumentationConfig {
  errors?: { enabled: boolean };
  network?: {
    enabled?: boolean;
    peerServiceMap?: Record<string, string>;
    blockedUrls?: Array<string | RegExp>;
    propagateTraceHeaderCorsUrls?: PulseNetworkPropagateCorsUrls;
    /** Names of request headers to copy onto spans. `PulseWebSemconv.SensitiveCapturedHeaderName` keys are never emitted (see `isSensitiveCapturedHeaderName` in `utils/network-http.ts`). */
    capturedRequestHeaders?: string[];
    /** Names of response headers to copy onto spans. Same denylist as request headers. */
    capturedResponseHeaders?: string[];
    /**
     * Default false — strips query string from {@code url.full}. When true, query is kept but
     * values for keys in {@code PulseWebSemconv.SensitiveQueryParamName} are replaced with
     * {@code ***} (see {@code isSensitiveQueryParamName} in {@code utils/network-http.ts}).
     */
    captureQueryParams?: boolean;
    /**
     * Reserved for PLAN-C §P3.5 — emit OTel stable histogram {@code http.client.request.duration}
     * (seconds). **Not implemented yet**; default ignored until wired in {@code NetworkInstrumentation}.
     */
    emitRequestDurationMetric?: boolean;
  };
  /**
   * `captureContext` defaults to true when omitted (Android parity).
   * `rage` defaults on (Android `ClickEventBuffer`); set `rage.enabled: false` for immediate per-click emit.
   */
  clicks?: {
    enabled: boolean;
    captureContext?: boolean;
    rage?: {
      enabled?: boolean;
      timeWindowMs?: number;
      threshold?: number;
      radiusDp?: number;
    };
  };
  webVitals?: {
    enabled?: boolean;
  };
  navigation?: { enabled: boolean };
  session?: { enabled: boolean };
  interactions?: { enabled: boolean };
  sessionReplay?: { enabled: boolean };
}

/** Runtime map of instrumentation toggles → {@link InstrumentationConfig} keys (string values). */
export const InstrumentationKeys = {
  ERRORS: "errors",
  NETWORK: "network",
  CLICKS: "clicks",
  WEB_VITALS: "webVitals",
  NAVIGATION: "navigation",
  SESSION: "session",
  INTERACTIONS: "interactions",
  SESSION_REPLAY: "sessionReplay",
} as const;

/** Allowed keys for {@link InstrumentationConfig} / remote feature wiring — not the const object itself. */
export type InstrumentationKey =
  (typeof InstrumentationKeys)[keyof typeof InstrumentationKeys];

/**
 * Durable buffering for failed OTLP exports (IndexedDB).
 * Matches Android OTel RUM: {@code DiskBufferingConfigurationSpec} defaults {@code isEnabled = true},
 * and {@code PulseSDK.initialize} does not pass a disk lambda — so disk buffering is **on by default**.
 * Set {@code enabled: false} to disable (no IndexedDB writes / drain).
 */
export interface PulseWebDiskBufferingConfig {
  /** Default {@code true}. Set {@code false} to turn off disk buffering entirely. */
  enabled?: boolean;
  /** Max row age before prune (ms). Default 24h. */
  maxAgeMs?: number;
  /** Approximate cap on total buffered payload bytes. Default 10 MiB. */
  maxCacheSizeBytes?: number;
}

export interface PulseWebConfig {
  // Required
  apiKey: string;
  dataCollectionState: PulseDataCollectionConsent;

  // Optional — identity
  /** Defaults to window.location.hostname if absent. */
  serviceName?: string;
  serviceVersion?: string;

  // Optional — custom attributes stamped on every signal
  globalAttributes?: PulseAttributes;

  /**
   * Extra OTEL resource attributes (e.g. {@code deployment.environment}). Merged under the
   * built-in resource; **Pulse keys win on conflict** ({@code project.id}, {@code rum.sdk.name},
   * {@code platform}, etc.).
   */
  resourceAttributes?: PulseAttributes;

  // Optional — privacy (Android `beforeSendData` / PulseBeforeSendData parity — see before-send.ts)
  beforeSendData?: PulseWebBeforeSendConfig;

  // Optional — per-instrumentation toggles
  instrumentations?: InstrumentationConfig;

  // Optional — route → screen name mapping (used by navigation instrumentation)
  routePatterns?: Array<{ pattern: string; name: string }>;

  /**
   * Wire format for OTLP export.
   * "json"  → application/json (DevTools-readable)
   * "protobuf" → application/x-protobuf (more compact)
   */
  export?: {
    format?: "json" | "protobuf";
  };

  /**
   * SDK internal diagnostics (Android / RN parity). Omitted or {@link PulseLogLevel.NONE} → no
   * Pulse console output. Use {@link PulseLogLevel.DEBUG} for sampling + remote-config traces.
   */
  logLevel?: PulseLogLevel;

  /**
   * Buffer failed OTLP exports in IndexedDB and retry on next load (Android disk buffering parity).
   * On by default; set {@code enabled: false} to turn off. Tune with {@code maxAgeMs} /
   * {@code maxCacheSizeBytes}. In Vite builds, {@code VITE_PULSE_DISK_BUFFER_MAX_AGE_MS} and
   * {@code VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES} can override defaults when buffering is active.
   */
  diskBuffering?: PulseWebDiskBufferingConfig;

  /**
   * Same-origin relay URL for sendBeacon unload delivery.
   *
   * `navigator.sendBeacon` cannot carry custom headers, so by default the API
   * key is embedded as a `?apiKey=` query parameter — visible in server access
   * logs and browser tooling. Providing a `beaconRelayUrl` on your own origin
   * (e.g. `/api/pulse-relay`) lets a server-side handler forward the payload
   * with a proper `X-API-KEY` header, keeping the key out of the URL entirely.
   *
   * If omitted, the SDK falls back to the query-parameter approach and logs a
   * one-time warning to the console.
   */
  beaconRelayUrl?: string;

  /**
   * Override the OTLP collector base URL (e.g. `http://192.168.1.x:4318`).
   * Useful in native WebView (Capacitor/Cordova) where `localhost` resolves to
   * the device, not the dev host. When omitted, the SDK auto-resolves from
   * `apiKey` (local dev → `http://localhost:4318`, prod → Pulse cloud).
   */
  endpoint?: string;
}
