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

export interface PulseWebConfig {
  endpointBaseUrl: string;
  apiKey: string;
  serviceName: string;

  serviceVersion?: string;

  dataCollectionState?: PulseDataCollectionConsent;
  beforeSend?: (signal: unknown) => unknown | null;

  globalAttributes?: Record<string, string | number | boolean>;

  routePatterns?: Array<{ pattern: string; name: string }>;

  configEndpointUrl?: string;

  export?: {
    /** OTLP body: protobuf by default; set `"json"` for dev / readable exports. */
    format?: "json" | "protobuf";
    compression?: "gzip" | "none";
    batch?: {
      scheduledDelayMillis?: number;
      maxQueueSize?: number;
      maxExportBatchSize?: number;
    };
  };

  diskBuffering?: {
    enabled?: boolean;
    maxSizeBytes?: number;
    maxAgeMs?: number;
  };

  instrumentations?: InstrumentationConfig;

  /**
   * When true, logs each log record lifecycle: pipeline ingress, post pre-batch
   * (before BatchLogRecordProcessor queue), and each OTLP log batch at export.
   */
  debugLogRecordLifecycle?: boolean;
}
