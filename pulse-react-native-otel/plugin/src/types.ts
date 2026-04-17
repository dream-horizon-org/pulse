/**
 * OpenTelemetry attribute value types.
 * Based on OpenTelemetry JavaScript SDK attribute types.
 * @see https://github.com/open-telemetry/opentelemetry-js/blob/main/api/src/common/Attributes.ts
 */
export type PulseAttributeValue =
  | string
  | number
  | boolean
  | string[]
  | number[]
  | boolean[];

export type PulseAttributes = Record<
  string,
  PulseAttributeValue | undefined | null
>;

export type PulseDataCollectionState = 'PENDING' | 'ALLOWED' | 'DENIED';

/** Simple on/off for `app.json` instrumentation (Android + iOS). */
export interface PulseInstrumentationEnabled {
  enabled?: boolean;
}

/** Android interaction block; `url` → Kotlin `setConfigUrl`. */
export interface PulseAndroidInteractionInstrumentation {
  enabled: boolean;
  url?: string;
}

/** Android `Pulse.initialize { … }` (under `android` only). */
export interface PulseAndroidInstrumentationProps {
  interaction?: PulseAndroidInteractionInstrumentation;
  activity?: PulseInstrumentationEnabled;
  network?: PulseInstrumentationEnabled;
  anr?: PulseInstrumentationEnabled;
  crash?: PulseInstrumentationEnabled;
  slowRendering?: PulseInstrumentationEnabled;
  fragment?: PulseInstrumentationEnabled;
}

/** iOS interaction; `configUrl` → Swift `setConfigUrl`. */
export interface PulseIosInteractionInstrumentation {
  enabled?: boolean;
  configUrl?: string;
}

export interface PulseIosUIKitTapRage {
  timeWindowMs?: number;
  rageThreshold?: number;
  radiusPt?: number;
}

export interface PulseIosUIKitTapInstrumentation {
  enabled?: boolean;
  captureContext?: boolean;
  rage?: PulseIosUIKitTapRage;
}

export type PulseIosSessionReplayTextPrivacy =
  | 'maskAll'
  | 'maskAllInputs'
  | 'maskSensitiveInputs';

export type PulseIosSessionReplayImagePrivacy = 'maskAll' | 'maskNone';

/** iOS session replay + `SessionReplayConfig` fields expressible from JSON. */
export interface PulseIosSessionReplayInstrumentation {
  enabled?: boolean;
  replayEndpointBaseUrl?: string;
  maskViewClasses?: string[];
  unmaskViewClasses?: string[];
  textAndInputPrivacy?: PulseIosSessionReplayTextPrivacy;
  imagePrivacy?: PulseIosSessionReplayImagePrivacy;
  captureIntervalMs?: number;
  compressionQuality?: number;
  screenshotScale?: number;
  flushIntervalSeconds?: number;
  flushAt?: number;
  maxBatchSize?: number;
}

/** iOS URL session instrumentation (`URLSessionInstrumentationConfig`). */
export interface PulseIosUrlSessionInstrumentation {
  enabled?: boolean;
  /**
   * When true, emits `excludeOtlpEndpoints(baseUrl:)` using merged init `endpointBaseUrl`
   * (PulseKit merges with per-request `shouldInstrument` internally).
   */
  excludeOtlpEndpoints?: boolean;
}

/** iOS sessions instrumentation (`SessionsInstrumentationConfig`); times are seconds. */
export interface PulseIosSessionsInstrumentation {
  enabled?: boolean;
  maxLifetimeSeconds?: number;
  backgroundInactivityTimeoutSeconds?: number;
  shouldPersist?: boolean;
}

/** iOS `ios.instrumentation` → PulseKit `InstrumentationConfiguration` (Swift `instrumentations:`). */
export interface PulseIosInstrumentationProps {
  urlSession?: PulseIosUrlSessionInstrumentation;
  sessions?: PulseIosSessionsInstrumentation;
  signPost?: PulseInstrumentationEnabled;
  interaction?: PulseIosInteractionInstrumentation;
  location?: PulseInstrumentationEnabled;
  crash?: PulseInstrumentationEnabled;
  appLifecycle?: PulseInstrumentationEnabled;
  screenLifecycle?: PulseInstrumentationEnabled;
  appStartup?: PulseInstrumentationEnabled;
  uiKitTap?: PulseIosUIKitTapInstrumentation;
  sessionReplay?: PulseIosSessionReplayInstrumentation;
}

/** iOS `PulseKitConfiguration` (`configuration: { kit in … }`). */
export interface PulseIosKitConfigurationProps {
  includeScreenAttributes?: boolean;
  includeNetworkAttributes?: boolean;
  includeGlobalAttributes?: boolean;
}

/** Per-platform overrides; merged with top-level init (endpoint + apiKey required after merge). */
export type PulseNativeInitFields = {
  endpointBaseUrl?: string;
  apiKey?: string;
  dataCollectionState?: PulseDataCollectionState;
  endpointHeaders?: Record<string, string>;
  configEndpointUrl?: string;
  /** Full URL for custom events (iOS `PulseSDK.initialize`; Android `HttpEndpointConnectivity`). */
  customEventCollectorUrl?: string;
  globalAttributes?: PulseAttributes;
};

export interface PulseAndroidSection extends PulseNativeInitFields {
  instrumentation?: PulseAndroidInstrumentationProps;
}

export interface PulseIosSection extends PulseNativeInitFields {
  instrumentation?: PulseIosInstrumentationProps;
  configuration?: PulseIosKitConfigurationProps;
}

/** Merged init passed to native codegen. */
export interface PulsePlatformInitProps {
  endpointBaseUrl: string;
  apiKey: string;
  dataCollectionState?: PulseDataCollectionState;
  endpointHeaders?: Record<string, string>;
  configEndpointUrl?: string;
  customEventCollectorUrl?: string;
  globalAttributes?: PulseAttributes;
}

export type ResolvedAndroidPulseProps = PulsePlatformInitProps & {
  instrumentation?: PulseAndroidInstrumentationProps;
};

export type ResolvedIosPulseProps = PulsePlatformInitProps & {
  configuration?: PulseIosKitConfigurationProps;
  instrumentation?: PulseIosInstrumentationProps;
};

/**
 * Expo config plugin props. Top-level `endpointBaseUrl` + `apiKey` required (non-empty).
 * `android` / `ios`: optional init overrides, `globalAttributes`, `instrumentation`; iOS also `configuration`.
 * Do not put `globalAttributes`, `instrumentation`, or `configuration` at the top level.
 */
export interface PulsePluginProps {
  endpointBaseUrl: string;
  apiKey: string;
  dataCollectionState?: PulseDataCollectionState;
  endpointHeaders?: Record<string, string>;
  configEndpointUrl?: string;
  customEventCollectorUrl?: string;

  android?: PulseAndroidSection;
  ios?: PulseIosSection;
}
