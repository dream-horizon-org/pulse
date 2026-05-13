// Polyfill crypto.randomUUID for Android WebView < Chrome 92 and other environments
// that expose crypto but not randomUUID (e.g. HTTP non-secure contexts).
if (typeof crypto !== "undefined" && typeof crypto.randomUUID !== "function") {
  (crypto as Crypto).randomUUID =
    (): `${string}-${string}-${string}-${string}-${string}` =>
      "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
      }) as `${string}-${string}-${string}-${string}-${string}`;
}

// M1: PulseSDK — minimal init sequence matching Android's public API surface.
// Endpoint URL, wire format, compression, and batch timing are fixed internally.
// `diskBuffering` mirrors Android `DiskBufferingConfig`: **on by default** (PulseSDK does not expose
// a disk toggle; OTel `DiskBufferingConfigurationSpec` defaults `isEnabled = true`). Pass
// `diskBuffering: { enabled: false }` to disable IndexedDB replay.

import { trace } from "@opentelemetry/api";
import type { Tracer } from "@opentelemetry/api";
import { logs, SeverityNumber } from "@opentelemetry/api-logs";
import type { Logger } from "@opentelemetry/api-logs";
import { metrics } from "@opentelemetry/api";
import type { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { MeterProvider } from "@opentelemetry/sdk-metrics";

import type { PulseWebConfig } from "./config";
import {
  resolveEndpointBaseUrl,
  validateConfig,
  PulseLogLevel,
  InstrumentationKeys,
} from "./config";
import { PulseWebLogger } from "./pulse-web-logger";
import {
  SessionProvider,
  getOrCreateInstallationId,
  getPersistedUserId,
  getPersistedUserProperties,
  persistUserId,
  persistUserProperties,
  clearPersistedUserIdentity,
  wasNewInstallation,
} from "./session";
import { buildMergedResource } from "./resource";
import { parseUserAgent, getOsVersionAsync } from "./utils/ua-parser";
import { SdkConfigFetcher, PulseFeature } from "./remote-config";
import { DEFAULT_SDK_CONFIG } from "./constants/default-sdk-config";
import {
  DomEventType,
  PulseOtelLoggerScope,
} from "./constants/pulse-otel-runtime";
import { FeatureGate } from "./feature-gate";
import { PulseGlobalAttributesProcessor } from "./processors/global-attrs-processor";
import { SignalFilterProcessor } from "./processors/signal-filter-processor";
import { LogRecordLifecycleDebugProcessor } from "./processors/log-record-lifecycle-debug-processor";
import { createProviders } from "./exporters";
import type { ExporterConfig, ProviderBundle } from "./exporters";
import { InstrumentationRegistry } from "./instrumentation-registry";
import type { SdkContext } from "./instrumentation-registry";
import { extractProjectId } from "./resource";
import { isDataCollectionAllowed } from "./consent";
import { PulseWebSemconv } from "./semconv";
import { errorFilenameFromStack } from "./utils/error-stack";
import { ExportSamplingGate } from "./sampling/export-sampling-gate";
import { drainBufferedOtlpExports } from "./persistence/drain-buffered-exports";
import {
  resolveDiskBufferMaxAgeMs,
  resolveDiskBufferMaxCacheSizeBytes,
} from "./constants/disk-buffer";
import { resolveBeforeSend } from "./before-send";
import { InteractionInstrumentation } from "./instrumentations/interaction";
import type { PulseAttributes } from "./types/attributes";

class PulseSDK implements SdkContext {
  private static _instance: PulseSDK | null = null;
  private _initialized = false;
  private _shuttingDown = false;
  private _initializing = false;

  endpointBaseUrl = "";
  sessionProvider!: SessionProvider;
  logger!: Logger;
  tracer!: Tracer;
  config!: PulseWebConfig;
  globalAttrsProcessor!: PulseGlobalAttributesProcessor;

  private _webTracerProvider?: WebTracerProvider;
  private _loggerProvider?: LoggerProvider;
  private meterProvider?: MeterProvider;
  private _prepareForDocumentUnload?: () => void;
  private _pagehideListener?: (e: PageTransitionEvent) => void;
  private registry?: InstrumentationRegistry;
  private configFetcher: SdkConfigFetcher = new SdkConfigFetcher("", "");
  gate: FeatureGate = new FeatureGate(DEFAULT_SDK_CONFIG);
  private _providerCleanup: () => void = () => {};
  private interactionInstrumentation?: InteractionInstrumentation;

  /** Promise for in-flight {@link init}; cleared when {@code finishInit} settles. */
  private _initSettled: Promise<void> | null = null;

  /** Exposed on {@link SdkContext} for instrumentations that must flush logs (Web Vitals). */
  get loggerProvider(): LoggerProvider | undefined {
    return this._loggerProvider;
  }

  /**
   * Notify the SDK that a soft (SPA) navigation just occurred so any buffered
   * vitals can be exported with the **departing** route's `screen.name`.
   *
   * Wired into the React / Next router-tracking hooks immediately after
   * {@link setScreenName}. Fire-and-forget — errors are swallowed.
   */
  notifySoftNavigation(): void {
    void this._loggerProvider?.forceFlush().catch(() => {});
  }

  /**
   * OTel {@link WebTracerProvider} — defined after {@link init}'s async bootstrap completes.
   * Await {@link whenReady} (or the promise returned from {@link init}) before use; until then
   * this getter may be undefined even though {@link init} was called.
   */
  get tracerProvider(): WebTracerProvider | undefined {
    return this._webTracerProvider;
  }

  static getInstance(): PulseSDK {
    if (!PulseSDK._instance) {
      PulseSDK._instance = new PulseSDK();
    }
    return PulseSDK._instance;
  }

  /**
   * Begins SDK initialization. Returns a promise that settles when async bootstrap
   * ({@code finishInit}) completes — same instant as {@link whenReady}. Safe to ignore
   * the return value unless you need {@link tracerProvider} immediately after.
   */
  init(config: PulseWebConfig): Promise<void> {
    if (this._initialized || this._shuttingDown) {
      return Promise.resolve();
    }
    if (this._initializing) {
      return this.whenReady();
    }
    // Step 1: Validate config
    validateConfig(config);
    PulseWebLogger.setLevel(config.logLevel ?? PulseLogLevel.NONE);
    // Step 1.5: Resolve endpointBaseUrl from apiKey; config.endpoint overrides for WebView dev
    const endpointBaseUrl = resolveEndpointBaseUrl(
      config.apiKey,
      config.endpoint,
    );
    this.endpointBaseUrl = endpointBaseUrl;

    // Consent gate — DENIED or PENDING → no-op, zero signals emitted
    if (!isDataCollectionAllowed(config.dataCollectionState)) {
      return Promise.resolve();
    }
    this.config = config;

    const meteringSessionId = crypto.randomUUID();

    // Set _initializing before the async finishInit so the singleton guard blocks
    // any duplicate init() calls that arrive during the 200ms OS-version await.
    this._initializing = true;
    const done = this.finishInit(
      config,
      endpointBaseUrl,
      meteringSessionId,
    ).finally(() => {
      this._initSettled = null;
    });
    this._initSettled = done;
    return done;
  }

  /**
   * Resolves when {@link init}'s async work has finished (or immediately if already
   * {@link isInitialized}). If consent blocked init or startup aborted, still resolves —
   * check {@link isInitialized} before using {@link tracerProvider}.
   */
  whenReady(): Promise<void> {
    if (this._initialized) {
      return Promise.resolve();
    }
    return this._initSettled ?? Promise.resolve();
  }

  private async finishInit(
    config: PulseWebConfig,
    endpointBaseUrl: string,
    meteringSessionId: string,
  ): Promise<void> {
    if (this.abortInitIfUnavailable()) return;

    this.initializeSessionContext();

    // Step 3: Resolve real OS version async (Client Hints, <200ms) then build Resource.
    const syncUA = parseUserAgent();
    const resolvedOsVersion = await getOsVersionAsync(syncUA.osVersion);
    if (this._shuttingDown) {
      this._initializing = false;
      return;
    }
    const resource = buildMergedResource(config, resolvedOsVersion);

    const { gate, sdkConfig, spanProcessors, logProcessors, samplingGate } =
      this.buildInitContext(config, endpointBaseUrl, meteringSessionId);
    const exporterConfig = this.buildExporterConfig(
      config,
      endpointBaseUrl,
      meteringSessionId,
      sdkConfig.signals.metricsToAdd,
      samplingGate,
    );
    const bundle = createProviders(
      exporterConfig,
      resource,
      spanProcessors,
      logProcessors,
    );

    this.drainBufferedExports(
      bundle,
      config,
      endpointBaseUrl,
      meteringSessionId,
    );
    this.assignProviders(bundle);
    this.bindPagehideFlush();
    this.bindGlobalProviders();
    this.emitSdkInitializationLogRecords(endpointBaseUrl);
    this.installInstrumentations(gate, config);

    // Step 10: Fetch fresh config in background.
    void this.configFetcher.fetchInBackground();

    this._initialized = true;
    this._initializing = false;
    this.emitInstallationStartIfNeeded();
  }

  private abortInitIfUnavailable(): boolean {
    // SSR / non-browser — skip init (TC-C16). Avoid `unknown`; `window` may be absent on globalThis.
    const w = (globalThis as typeof globalThis & { window?: Window }).window;
    if (w == null) {
      this._initializing = false;
      return true;
    }
    if (this._initialized || this._shuttingDown) {
      this._initializing = false;
      return true;
    }
    return false;
  }

  private initializeSessionContext(): void {
    // Step 2: SessionProvider
    this.sessionProvider = new SessionProvider(
      undefined,
      undefined,
      this.config.pageHiddenTimeoutMs,
    );
    // Step 2.5: Eagerly resolve installation ID so wasNewInstallation() is accurate.
    getOrCreateInstallationId();
  }

  private buildInitContext(
    config: PulseWebConfig,
    endpointBaseUrl: string,
    meteringSessionId: string,
  ) {
    // Step 4: Load cached SDK config
    const projectId = extractProjectId(config.apiKey);
    this.configFetcher = new SdkConfigFetcher(
      endpointBaseUrl,
      projectId,
      undefined,
      config.apiKey,
    );
    const sdkConfig = this.configFetcher.loadCached();

    const gate = new FeatureGate(sdkConfig);
    this.gate = gate;
    const samplingGate = new ExportSamplingGate(sdkConfig, "pulse_web_js", {
      serviceVersion: config.serviceVersion,
    });
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    this.globalAttrsProcessor = new PulseGlobalAttributesProcessor(
      this.sessionProvider,
      config,
      meteringSessionId,
    );
    this.globalAttrsProcessor.hydrateUserIdentity(
      getPersistedUserId(),
      getPersistedUserProperties(),
    );

    const spanProcessors = [this.globalAttrsProcessor, filterProcessor];

    const lifecycleDebug = PulseWebLogger.getLevel() <= PulseLogLevel.DEBUG;
    const ingressDebugProc = lifecycleDebug
      ? new LogRecordLifecycleDebugProcessor("ingress")
      : null;
    const preBatchDebugProc = lifecycleDebug
      ? new LogRecordLifecycleDebugProcessor("pre_batch")
      : null;
    const logProcessors = [
      ...(ingressDebugProc ? [ingressDebugProc] : []),
      this.globalAttrsProcessor,
      filterProcessor,
      ...(preBatchDebugProc ? [preBatchDebugProc] : []),
    ];

    return { gate, sdkConfig, samplingGate, spanProcessors, logProcessors };
  }

  private buildExporterConfig(
    config: PulseWebConfig,
    endpointBaseUrl: string,
    meteringSessionId: string,
    metricsToAdd: ExporterConfig["metricsToAdd"],
    samplingGate: ExportSamplingGate,
  ): ExporterConfig {
    const diskOn = config.diskBuffering?.enabled !== false;
    const disk = config.diskBuffering;
    const beforeSendResolved = resolveBeforeSend(config.beforeSendData);
    return {
      endpointBaseUrl,
      apiKey: config.apiKey,
      meteringSessionId,
      useProtobuf: config.export?.format === "protobuf",
      // Inject the same global attributes into metric data points at export time.
      getMetricGlobalAttrs: () =>
        this.globalAttrsProcessor.getCommonAttrsForMetrics(),
      samplingGate,
      metricsToAdd,
      metricsToAddSdkName: "pulse_web_js" as const,
      diskBuffering: diskOn
        ? {
            enabled: true,
            maxAgeMs: resolveDiskBufferMaxAgeMs(disk?.maxAgeMs),
            maxCacheSizeBytes: resolveDiskBufferMaxCacheSizeBytes(
              disk?.maxCacheSizeBytes,
            ),
          }
        : { enabled: false },
      ...(beforeSendResolved ? { beforeSendData: beforeSendResolved } : {}),
      ...(config.beaconRelayUrl
        ? { beaconRelayUrl: config.beaconRelayUrl }
        : {}),
    };
  }

  private drainBufferedExports(
    bundle: ProviderBundle,
    config: PulseWebConfig,
    endpointBaseUrl: string,
    meteringSessionId: string,
  ): void {
    if (!bundle.idbSignalBuffer) return;
    void drainBufferedOtlpExports({
      buffer: bundle.idbSignalBuffer,
      apiKey: config.apiKey,
      meteringSessionId,
      tracesUrl: `${endpointBaseUrl}/v1/traces`,
      logsUrl: `${endpointBaseUrl}/v1/logs`,
      metricsUrl: `${endpointBaseUrl}/v1/metrics`,
    });
  }

  private assignProviders(bundle: ProviderBundle): void {
    this._webTracerProvider = bundle.tracerProvider;
    this._loggerProvider = bundle.loggerProvider;
    this.meterProvider = bundle.meterProvider;
    this._prepareForDocumentUnload = bundle.prepareForDocumentUnload;
    this._providerCleanup = bundle.cleanup ?? (() => {});
  }

  private bindPagehideFlush(): void {
    if (typeof window === "undefined") return;
    this._pagehideListener = (e: PageTransitionEvent) => {
      if (!e.persisted && this._initialized) {
        this._prepareForDocumentUnload?.();
        void Promise.all([
          this._loggerProvider?.forceFlush(),
          this._webTracerProvider?.forceFlush(),
          this.meterProvider?.forceFlush(),
        ]).catch(() => {});
      }
    };
    window.addEventListener(DomEventType.PAGEHIDE, this._pagehideListener);
  }

  private bindGlobalProviders(): void {
    const tracerProvider = this._webTracerProvider;
    const loggerProvider = this._loggerProvider;
    const meterProvider = this.meterProvider;
    if (!tracerProvider || !loggerProvider || !meterProvider) return;

    trace.setGlobalTracerProvider(tracerProvider);
    logs.setGlobalLoggerProvider(loggerProvider);
    metrics.setGlobalMeterProvider(meterProvider);

    this.logger = loggerProvider.getLogger(PulseOtelLoggerScope.PULSE_WEB);
    this.tracer = tracerProvider.getTracer(PulseOtelLoggerScope.PULSE_WEB);
  }

  private installInstrumentations(
    gate: FeatureGate,
    config: PulseWebConfig,
  ): void {
    this.registry = new InstrumentationRegistry(
      this as SdkContext,
      gate,
      config.instrumentations,
    );
    this.interactionInstrumentation = new InteractionInstrumentation();
    this.registry.installAll();
    this.registry.registerAndInstall(
      this.interactionInstrumentation,
      InstrumentationKeys.INTERACTIONS,
    );
  }

  private emitInstallationStartIfNeeded(): void {
    // Emit app.installation.start on first-ever install — mirrors Android.
    if (!wasNewInstallation()) return;
    this.logger.emit({
      body: PulseWebSemconv.LogBody.APP_INSTALLATION_START,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.INSTALLATION_START,
        [PulseWebSemconv.AttributeKey.INSTALLATION_ID]:
          getOrCreateInstallationId(),
      },
    });
  }

  async shutdown(): Promise<void> {
    if (!this._initialized && !this._initializing) return;
    this._shuttingDown = true;
    this._initializing = false; // kill any pending async init

    if (this._pagehideListener && typeof window !== "undefined") {
      window.removeEventListener(DomEventType.PAGEHIDE, this._pagehideListener);
      this._pagehideListener = undefined;
    }

    this._providerCleanup();
    this.registry?.uninstallAll();
    this.interactionInstrumentation = undefined;
    this.sessionProvider?.shutdown();

    await Promise.all([
      this._webTracerProvider?.forceFlush(),
      this._loggerProvider?.forceFlush(),
      this.meterProvider?.forceFlush(),
    ]);

    this._webTracerProvider = undefined;
    this._loggerProvider = undefined;
    this.meterProvider = undefined;
    this._prepareForDocumentUnload = undefined;

    this._initialized = false;
    this._shuttingDown = false;
    PulseWebLogger.setLevel(PulseLogLevel.NONE);
    // _initializing already reset above
  }

  isInitialized(): boolean {
    return this._initialized;
  }

  setScreenName(name: string): void {
    if (!this._initialized || !this.globalAttrsProcessor) return;
    this.globalAttrsProcessor.setScreenName(name);
  }

  /**
   * Android parity: set logged-in user id on all signals (`user.id`). Persists to localStorage.
   * Emits `pulse.user.session.end` / `pulse.user.session.start` when the id changes.
   */
  setUserId(id: string | null): void {
    if (!this._initialized || !this.globalAttrsProcessor) return;
    const nextId = id === "" ? null : id;
    const oldId = this.globalAttrsProcessor.getUserId();
    if (oldId === nextId) return;
    this.globalAttrsProcessor.setUserId(nextId);
    persistUserId(nextId);
    if (oldId !== null) {
      this.emitUserSessionEndLog(oldId);
    }
    if (nextId !== null) {
      this.emitUserSessionStartLog(nextId, oldId !== null ? oldId : undefined);
    }
  }

  /** Android parity: custom user fields as `pulse.user.<name>`. Persists JSON to localStorage. */
  setUserProperty(key: string, value: string | null): void {
    if (!this._initialized || !this.globalAttrsProcessor) return;
    this.globalAttrsProcessor.setUserProperty(key, value);
    persistUserProperties(
      this.globalAttrsProcessor.getUserPropertiesSnapshot(),
    );
  }

  /** Batch update user properties; `null` removes a key. */
  setUserProperties(props: Record<string, string | null>): void {
    if (!this._initialized || !this.globalAttrsProcessor) return;
    this.globalAttrsProcessor.setUserProperties(props);
    persistUserProperties(
      this.globalAttrsProcessor.getUserPropertiesSnapshot(),
    );
  }

  /**
   * Clear the persisted user ID and all user properties.
   * Call on logout to prevent the next user from inheriting the identity.
   */
  clearUserIdentity(): void {
    clearPersistedUserIdentity();
    if (this.globalAttrsProcessor) {
      this.globalAttrsProcessor.setUserId(null);
      this.globalAttrsProcessor.setUserProperties(
        Object.fromEntries(
          Object.keys(
            this.globalAttrsProcessor.getUserPropertiesSnapshot(),
          ).map((k) => [k, null]),
        ),
      );
    }
  }

  private emitUserSessionEndLog(userId: string): void {
    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;
    const logBodies = PulseWebSemconv.LogBody;
    this.logger.emit({
      body: logBodies.USER_SESSION_END,
      attributes: {
        [attributeKeys.PULSE_TYPE]: pulseTypes.USER_SESSION_END,
        [attributeKeys.USER_ID]: userId,
      },
    });
  }

  private emitUserSessionStartLog(
    userId: string,
    previousUserId?: string,
  ): void {
    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;
    const logBodies = PulseWebSemconv.LogBody;
    const attrs: Record<string, string> = {
      [attributeKeys.PULSE_TYPE]: pulseTypes.USER_SESSION_START,
      [attributeKeys.USER_ID]: userId,
    };
    if (previousUserId !== undefined) {
      attrs[attributeKeys.PULSE_USER_PREVIOUS_ID] = previousUserId;
    }
    this.logger.emit({
      body: logBodies.USER_SESSION_START,
      attributes: attrs,
    });
  }

  trackEvent(
    name: string,
    attrs?: PulseAttributes,
    timestampMs: number = Date.now(),
  ): void {
    if (!this._initialized) return;

    if (this.gate.isEnabled(PulseFeature.CUSTOM_EVENTS)) {
      this.logger.emit({
        body: name,
        attributes: {
          [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
            PulseWebSemconv.PulseType.CUSTOM_EVENT,
          [PulseWebSemconv.AttributeKey.EVENT_NAME]:
            PulseWebSemconv.FixedValue.EVENT_NAME_CUSTOM_EVENT,
          ...(attrs ?? {}),
        },
      });
    }

    if (isDataCollectionAllowed(this.config.dataCollectionState)) {
      this.interactionInstrumentation?.trackEvent(name, attrs, timestampMs);
    }
  }

  /**
   * Manual non-fatal error report. Parameter is {@link unknown} because callers may pass
   * non-{@link Error} throws/rejections; values are normalised to {@link Error} internally.
   */
  reportException(error: unknown, attrs?: PulseAttributes): void {
    if (!this._initialized) return;
    const err = error instanceof Error ? error : new Error(String(error));
    this.logger.emit({
      eventName: PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
      body: err.message,
      timestamp: Date.now(),
      severityNumber: SeverityNumber.WARN,
      severityText: "WARN",
      attributes: {
        [PulseWebSemconv.AttributeKey.EVENT_NAME]:
          PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.NON_FATAL,
        [PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]: err.name,
        [PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]: err.message,
        [PulseWebSemconv.AttributeKey.EXCEPTION_STACKTRACE]: err.stack ?? "",
        [PulseWebSemconv.AttributeKey.NON_FATAL_IS_MANUAL]: true,
        [PulseWebSemconv.AttributeKey.URL_PATH]:
          typeof window !== "undefined" ? window.location.pathname : "",
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }

  /**
   * React render errors and similar fatals — `pulse.type` = device.crash (dashboard contract).
   */
  /** Fatals / render crashes — same {@link unknown} rationale as {@link reportException}. */
  reportDeviceCrash(error: unknown, attrs?: PulseAttributes): void {
    if (!this._initialized) return;
    const err = error instanceof Error ? error : new Error(String(error));
    const stack = err.stack ?? "";
    this.logger.emit({
      eventName: PulseWebSemconv.LogEventName.DEVICE_CRASH,
      body: err.message,
      timestamp: Date.now(),
      severityNumber: SeverityNumber.FATAL,
      severityText: "FATAL",
      attributes: {
        [PulseWebSemconv.AttributeKey.EVENT_NAME]:
          PulseWebSemconv.LogEventName.DEVICE_CRASH,
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.DEVICE_CRASH,
        [PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]: err.name,
        [PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]: err.message,
        [PulseWebSemconv.AttributeKey.EXCEPTION_STACKTRACE]: stack,
        [PulseWebSemconv.AttributeKey.ERROR_FILENAME]:
          errorFilenameFromStack(stack),
        [PulseWebSemconv.AttributeKey.URL_PATH]:
          typeof window !== "undefined" ? window.location.pathname : "",
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }

  trackNonFatal(name: string, attrs?: PulseAttributes): void {
    if (!this._initialized) return;
    this.logger.emit({
      eventName: PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
      body: name,
      attributes: {
        [PulseWebSemconv.AttributeKey.EVENT_NAME]:
          PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.NON_FATAL,
        [PulseWebSemconv.AttributeKey.NON_FATAL_TYPE]: name,
        [PulseWebSemconv.AttributeKey.NON_FATAL_IS_MANUAL]: true,
        ...(attrs ?? {}),
      },
    });
  }

  /**
   * Android parity: {@code SdkInitializationEvents} emits OTel log records named
   * {@code rum.sdk.init.*} via the {@code otel.initialization.events} logger scope.
   * Web emits a minimal subset after providers are registered, before instrumentations
   * install (so {@code session.start} follows these in the pipeline).
   */
  private emitSdkInitializationLogRecords(endpointBaseUrl: string): void {
    if (this._loggerProvider === undefined) return;

    const attributeKeys = PulseWebSemconv.AttributeKey;
    const rumSdkInit = PulseWebSemconv.RumSdkInit;
    const initLogger = this._loggerProvider.getLogger(
      PulseOtelLoggerScope.INITIALIZATION_EVENTS,
    );

    initLogger.emit({
      body: rumSdkInit.STARTED,
      attributes: {
        [attributeKeys.PULSE_TYPE]: rumSdkInit.STARTED,
      },
    });

    const spanExporterHint = [
      `OtlpHttpJson`,
      `traces=${endpointBaseUrl}/v1/traces`,
      `logs=${endpointBaseUrl}/v1/logs`,
      `metrics=${endpointBaseUrl}/v1/metrics`,
    ].join("; ");
    initLogger.emit({
      body: rumSdkInit.SPAN_EXPORTER,
      attributes: {
        [attributeKeys.PULSE_TYPE]: rumSdkInit.SPAN_EXPORTER,
        [attributeKeys.SPAN_EXPORTER]: spanExporterHint,
      },
    });
  }
}

export const Pulse = PulseSDK.getInstance();
