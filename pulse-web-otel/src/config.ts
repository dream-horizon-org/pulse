import { PulseDataCollectionConsent } from "./types/config";
import type { PulseWebBeforeSendConfig } from "./before-send";
import { validateBeforeSendConfig } from "./before-send";

export { PulseDataCollectionConsent } from "./types/config";
export type {
  PulseWebBeforeSendCallbacks,
  PulseWebBeforeSendConfig,
} from "./before-send";

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

  // Optional — privacy (Android PulseBeforeSendData parity — see before-send.ts)
  beforeSend?: PulseWebBeforeSendConfig;

  // Optional — custom attributes stamped on every signal
  globalAttributes?: Record<string, string | number | boolean>;

  // Optional — route → screen name mapping (used by navigation instrumentation)
  routePatterns?: Array<{ pattern: string; name: string }>;

  // Optional — per-instrumentation toggles
  instrumentations?: InstrumentationConfig;

  /**
   * Wire format for OTLP export.
   * "json"  → application/json (DevTools-readable)
   * "protobuf" → application/x-protobuf (more compact)
   */
  export?: {
    format?: "json" | "protobuf";
  };

  /**
   * When true, logs each log record through the processor chain to the browser console.
   * Leave false (or omit) in production.
   */
  debugLogRecordLifecycle?: boolean;

  /**
   * Failed exports may be written to IndexedDB and replayed on the next load (same role as
   * Android {@code DiskBufferingConfig}). **Default is on** (omit this field or omit {@code enabled}).
   * Set {@code enabled: false} to disable. Optional {@code maxAgeMs} / {@code maxCacheSizeBytes} tune the store.
   *
   * **Vite (internal):** optional {@code VITE_PULSE_DISK_BUFFER_MAX_AGE_MS} and
   * {@code VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES} override defaults when buffering is active (same
   * pattern as {@code VITE_PULSE_BATCH_DELAY_MS} for batching).
   */
  diskBuffering?: PulseWebDiskBufferingConfig;
}

export function validateConfig(config: PulseWebConfig): void {
  if (!config.apiKey) throw new Error("[PulseWeb] apiKey is required");
  validateBeforeSendConfig(config.beforeSend);
  const diskOn = config.diskBuffering?.enabled !== false;
  const disk = config.diskBuffering;
  if (diskOn && disk !== undefined) {
    if (
      disk.maxAgeMs !== undefined &&
      (!Number.isFinite(disk.maxAgeMs) || disk.maxAgeMs <= 0)
    ) {
      throw new Error(
        "[PulseWeb] diskBuffering.maxAgeMs must be a positive finite number",
      );
    }
    if (
      disk.maxCacheSizeBytes !== undefined &&
      (!Number.isFinite(disk.maxCacheSizeBytes) || disk.maxCacheSizeBytes <= 0)
    ) {
      throw new Error(
        "[PulseWeb] diskBuffering.maxCacheSizeBytes must be a positive finite number",
      );
    }
  }
}

export const PULSE_PROD_ENDPOINT_URL = "https://pulse-otel-collector.pulse-ux.com";

/**
 * Mirrors Android's PulseSDKInternal.isApiLocalDev().
 * Matches: default-project_* OR Test-*_*
 */
export function isLocalEnvironment(apiKey: string): boolean {
  return /^default-project_.*|^Test-.*_.*/.test(apiKey);
}

/**
 * Mirrors Android's PulseEndpointUtils.getBaseUrl().
 * Local: http://localhost:4318
 * Prod: https://pulse-otel-collector.pulse-ux.com
 * Internal only — not part of the public config surface.
 */
export function resolveEndpointBaseUrl(
  apiKey: string,
  provided?: string,
): string {
  if (provided) return provided;
  if (isLocalEnvironment(apiKey)) {
    return "http://localhost:4318";
  }
  return PULSE_PROD_ENDPOINT_URL;
}
