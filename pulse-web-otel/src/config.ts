import { PulseDataCollectionConsent } from "./types/config";
import type { PulseWebBeforeSendConfig } from "./before-send";
import { validateBeforeSendConfig } from "./before-send";
import { PulseLogLevel } from "./pulse-log-level";

export { PulseDataCollectionConsent } from "./types/config";
export type {
  PulseWebBeforeSendCallbacks,
  PulseWebBeforeSendConfig,
} from "./before-send";
export { PulseLogLevel };

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

export function validateConfig(config: PulseWebConfig): void {
  if (!config.apiKey) throw new Error("[PulseWeb] apiKey is required");
  validateBeforeSendConfig(config.beforeSendData);
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

export const PULSE_PROD_ENDPOINT_URL =
  "https://pulse-otel-collector.pulse-ux.com";

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
