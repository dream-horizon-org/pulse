import type { PulseWebBeforeSendConfig } from "../before-send";

export enum PulseDataCollectionConsent {
  ALLOWED = "ALLOWED",
  DENIED = "DENIED",
  PENDING = "PENDING",
}

export interface InstrumentationConfig {
  errors?: { enabled: boolean };
  network?: { enabled: boolean };
  clicks?: { enabled: boolean };
  webVitals?: { enabled: boolean };
  navigation?: { enabled: boolean };
  session?: {
    enabled: boolean;
    /** Rotate session after this many ms of inactivity. Default: 30 min. */
    inactivityTimeoutMs?: number;
    /** Hard max session lifetime in ms regardless of activity. Default: 4 hours. */
    maxSessionLifetimeMs?: number;
    /** Rotate session after page has been hidden for this many ms. Default: 15 min. */
    pageHiddenTimeoutMs?: number;
  };
  interactions?: { enabled: boolean };
  sessionReplay?: { enabled: boolean };
}

/** @see `PulseWebDiskBufferingConfig` in config.ts — duplicated for doc parity with Android. */
export interface PulseWebDiskBufferingConfig {
  /** Default true (matches Android OTel RUM disk spec default). */
  enabled?: boolean;
  maxAgeMs?: number;
  maxCacheSizeBytes?: number;
}

export interface PulseWebConfig {
  // Required — same as Android
  apiKey: string;
  dataCollectionState: PulseDataCollectionConsent;

  // Optional — same as Android
  /** Defaults to window.location.hostname if absent. */
  serviceName?: string;
  serviceVersion?: string;
  globalAttributes?: Record<string, string | number | boolean>;
  beforeSend?: PulseWebBeforeSendConfig;
  instrumentations?: InstrumentationConfig;

  // Web-specific only (no Android equivalent)
  routePatterns?: Array<{ pattern: string; name: string }>;

  /**
   * Wire format for OTLP export.
   * "json"  → application/json (DevTools-readable, default for dev keys)
   * "protobuf" → application/x-protobuf (more compact, default for prod keys)
   * When omitted, the SDK auto-selects based on the API key prefix.
   */
  export?: {
    format?: "json" | "protobuf";
  };

  /**
   * When true, logs each log record through the processor chain to the browser console.
   * Useful for debugging signal flow: API emit → processors → batch export.
   * Leave false (or omit) in production.
   */
  debugLogRecordLifecycle?: boolean;

  diskBuffering?: PulseWebDiskBufferingConfig;
}
