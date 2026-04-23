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
};

export interface PulseAndroidCoreLibraryDesugaring {
  enabled?: boolean;
  /** `com.android.tools:desugar_jdk_libs` version; used only when `enabled` is true. Default `2.1.4`. */
  version?: string;
}

/** Android OkHttp / Byte Buddy Gradle wiring (under `android` only), parallel to `coreLibraryDesugaring`. */
export interface PulseAndroidOkHttpInstrumentation {
  enabled?: boolean;
  /** okhttp3-library / okhttp3-agent version (both); default when omitted: see `androidBuildConstants`. */
  libraryVersion?: string;
  /** `net.bytebuddy:byte-buddy-gradle-plugin` on root `buildscript` classpath; default when omitted: see `androidBuildConstants`. */
  byteBuddyGradlePluginVersion?: string;
}

export interface PulseAndroidSection extends PulseNativeInitFields {
  instrumentation?: PulseAndroidInstrumentationProps;
  coreLibraryDesugaring?: PulseAndroidCoreLibraryDesugaring;
  okHttpInstrumentation?: PulseAndroidOkHttpInstrumentation;
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
}

export type ResolvedAndroidPulseProps = PulsePlatformInitProps & {
  instrumentation?: PulseAndroidInstrumentationProps;
  coreLibraryDesugaring: {
    enabled: boolean;
    /** Meaningful when `enabled`; always set for stable plugin internals. */
    version: string;
  };
  okHttpInstrumentation: {
    enabled: boolean;
    /** Meaningful when `enabled`; always set for stable plugin internals (defaults from androidBuildConstants). */
    libraryVersion: string;
    /** Meaningful when `enabled`; always set for stable plugin internals (defaults from androidBuildConstants). */
    byteBuddyGradlePluginVersion: string;
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
 */
export interface PulsePluginProps {
  apiKey: string;
  dataCollectionState: PulseDataCollectionState;

  android?: PulseAndroidSection;
  ios?: PulseIosSection;
}
