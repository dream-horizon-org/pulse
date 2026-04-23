import type { PulseWebBeforeSendConfig } from "../before-send";
import type { PulseLogLevel } from "../pulse-log-level";

/** Consent for telemetry collection (Android `dataCollectionState` parity). */
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
  session?: { enabled: boolean };
  interactions?: { enabled: boolean };
  sessionReplay?: { enabled: boolean };
}

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
  globalAttributes?: Record<string, string | number | boolean>;

  /**
   * Extra OTEL resource attributes (e.g. {@code deployment.environment}). Merged under the
   * built-in resource; **Pulse keys win on conflict** ({@code project.id}, {@code rum.sdk.name},
   * {@code platform}, etc.).
   */
  resourceAttributes?: Record<string, string | number | boolean>;

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
}
