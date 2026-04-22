import { PulseDataCollectionConsent } from "./types/config";

export { PulseDataCollectionConsent } from "./types/config";

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

export interface PulseWebConfig {
  // Required
  apiKey: string;
  dataCollectionState: PulseDataCollectionConsent;

  // Optional — identity
  /** Defaults to window.location.hostname if absent. */
  serviceName?: string;
  serviceVersion?: string;

  // Optional — privacy
  beforeSend?: (signal: unknown) => unknown | null;

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
}

export function validateConfig(config: PulseWebConfig): void {
  if (!config.apiKey) throw new Error("[PulseWeb] apiKey is required");
}

const PULSE_PROD_ENDPOINT_URL = "https://pulse-otel-collector.pulse-ux.com";

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
