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

/**
 * Numeric log verbosity for native init from Expo config (JSON-friendly).
 * Matches JS `PulseLogLevel` / native ordinals: 0 = VERBOSE … 5 = NONE.
 */
export type PulseLogLevelValue = 0 | 1 | 2 | 3 | 4 | 5;

/** Simple on/off for `app.json` instrumentation (Android + iOS). */
export interface PulseInstrumentationEnabled {
  enabled?: boolean;
}

/** Android interaction block; `url` → Kotlin `setConfigUrl`. */
export interface PulseAndroidInteractionInstrumentation {
  enabled: boolean;
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

/** iOS interaction toggles; interaction config URL comes from remote SDK config. */
export interface PulseIosInteractionInstrumentation {
  enabled?: boolean;
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

/** Per-platform overrides; merged with top-level `apiKey` + `dataCollectionState` (both required after merge). */
export type PulseNativeInitFields = {
  apiKey?: string;
  dataCollectionState?: PulseDataCollectionState;
  globalAttributes?: PulseAttributes;
  /** Override top-level `logLevel` for this platform when set. */
  logLevel?: PulseLogLevelValue;
};

export interface PulseAndroidCoreLibraryDesugaring {
  enabled?: boolean;
  /** `com.android.tools:desugar_jdk_libs` version; used only when `enabled` is true. Default `2.1.4`. */
  version?: string;
}

export interface PulseAndroidSection extends PulseNativeInitFields {
  instrumentation?: PulseAndroidInstrumentationProps;
  coreLibraryDesugaring?: PulseAndroidCoreLibraryDesugaring;
}

export interface PulseIosSection extends PulseNativeInitFields {
  instrumentation?: PulseIosInstrumentationProps;
  configuration?: PulseIosKitConfigurationProps;
}

/** Merged init passed to native codegen. */
export interface PulsePlatformInitProps {
  apiKey: string;
  dataCollectionState: PulseDataCollectionState;
  globalAttributes?: PulseAttributes;
  logLevel?: PulseLogLevelValue;
}

export type ResolvedAndroidPulseProps = PulsePlatformInitProps & {
  instrumentation?: PulseAndroidInstrumentationProps;
  coreLibraryDesugaring: {
    enabled: boolean;
    /** Meaningful when `enabled`; always set for stable plugin internals. */
    version: string;
  };
};

export type ResolvedIosPulseProps = PulsePlatformInitProps & {
  configuration?: PulseIosKitConfigurationProps;
  instrumentation?: PulseIosInstrumentationProps;
};

/**
 * Expo config plugin props. Top-level `apiKey` and `dataCollectionState` are required.
 * `android` / `ios`: optional init overrides, `globalAttributes`, `instrumentation`; iOS also `configuration`.
 * Do not put `globalAttributes`, `instrumentation`, or `configuration` at the top level.
 * Optional `logLevel` (0–5) may be set at the top level and/or overridden per platform.
 */
export interface PulsePluginProps {
  apiKey: string;
  dataCollectionState: PulseDataCollectionState;
  logLevel?: PulseLogLevelValue;

  android?: PulseAndroidSection;
  ios?: PulseIosSection;
}
