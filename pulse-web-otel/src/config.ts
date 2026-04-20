export enum PulseDataCollectionConsent {
  ALLOWED = 'ALLOWED',
  DENIED  = 'DENIED',
  PENDING = 'PENDING',
}

export interface InstrumentationConfig {
  errors?:        { enabled: boolean };
  network?:       { enabled: boolean };
  clicks?:        { enabled: boolean };
  webVitals?:     { enabled: boolean };
  navigation?:    { enabled: boolean };
  session?:       {
    enabled: boolean;
    /** Rotate session after this many ms of inactivity. Default: 30 min. */
    inactivityTimeoutMs?: number;
    /** Hard max session lifetime in ms regardless of activity. Default: 4 hours. */
    maxSessionLifetimeMs?: number;
    /** Rotate session after page has been hidden for this many ms. Default: 15 min. */
    pageHiddenTimeoutMs?: number;
  };
  interactions?:  { enabled: boolean };
  sessionReplay?: { enabled: boolean };
}

export interface PulseWebConfig {
  // Required
  apiKey: string;
  serviceName: string;
  endpointBaseUrl?: string;

  // Optional — identity
  serviceVersion?: string;

  // Optional — privacy
  dataCollectionState?: PulseDataCollectionConsent;
  beforeSend?: (signal: unknown) => unknown | null;

  // Optional — custom attributes stamped on every signal
  globalAttributes?: Record<string, string | number | boolean>;

  // Optional — route → screen name mapping (used by navigation instrumentation)
  routePatterns?: Array<{ pattern: string; name: string }>;

  // Optional — remote config
  configEndpointUrl?: string;

  // Optional — export tuning
  export?: {
    format?: 'json' | 'protobuf';
    compression?: 'gzip' | 'none';
    batch?: {
      scheduledDelayMillis?: number;
      maxQueueSize?: number;
      maxExportBatchSize?: number;
    };
  };

  // Optional — offline / retry persistence
  diskBuffering?: {
    enabled?: boolean;
    maxSizeBytes?: number;
    maxAgeMs?: number;
  };

  // Optional — per-instrumentation toggles
  instrumentations?: InstrumentationConfig;
}

export function validateConfig(config: PulseWebConfig): void {
  if (!config.apiKey) throw new Error('[PulseWeb] apiKey is required');
  if (!config.serviceName) throw new Error('[PulseWeb] serviceName is required');
}

const PULSE_PROD_ENDPOINT_URL = 'https://pulse-otel-collector.pulse-ux.com';

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
 * Explicit override always wins.
 */
export function resolveEndpointBaseUrl(apiKey: string, provided?: string): string {
  if (provided) return provided;
  if (isLocalEnvironment(apiKey)) {
    return 'http://localhost:4318';
  }
  return PULSE_PROD_ENDPOINT_URL;
}
