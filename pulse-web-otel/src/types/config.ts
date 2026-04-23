import type { PulseWebBeforeSendConfig } from "../before-send";
import type { PulseLogLevel } from "../pulse-log-level";

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

  /**
   * Extra OTEL resource attributes. Merged before the SDK resource; Pulse reserved keys win.
   */
  resourceAttributes?: Record<string, string | number | boolean>;

  beforeSendData?: PulseWebBeforeSendConfig;
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

  /** SDK internal log verbosity (Android / RN parity). */
  logLevel?: PulseLogLevel;

  diskBuffering?: PulseWebDiskBufferingConfig;
}
