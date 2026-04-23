// M1: PulseWebSDK — minimal init sequence matching Android's public API surface.
// Endpoint URL, wire format, compression, and batch timing are fixed internally.
// `diskBuffering` mirrors Android `DiskBufferingConfig`: **on by default** (PulseSDK does not expose
// a disk toggle; OTel `DiskBufferingConfigurationSpec` defaults `isEnabled = true`). Pass
// `diskBuffering: { enabled: false }` to disable IndexedDB replay.

import { trace } from "@opentelemetry/api";
import type { Tracer } from "@opentelemetry/api";
import { logs } from "@opentelemetry/api-logs";
import type { Logger } from "@opentelemetry/api-logs";
import { metrics } from "@opentelemetry/api";
import type { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { MeterProvider } from "@opentelemetry/sdk-metrics";

import type { PulseWebConfig } from "./config";
import { resolveEndpointBaseUrl, validateConfig } from "./config";
import {
  SessionProvider,
  getOrCreateInstallationId,
  wasNewInstallation,
} from "./session";
import { buildResource } from "./resource";
import { parseUserAgent, getOsVersionAsync } from "./utils/ua-parser";
import { SdkConfigFetcher, DEFAULT_SDK_CONFIG } from "./remote-config";
import { FeatureGate } from "./feature-gate";
import { PulseGlobalAttributesProcessor } from "./processors/global-attrs-processor";
import { SignalFilterProcessor } from "./processors/signal-filter-processor";
import { LogRecordLifecycleDebugProcessor } from "./processors/log-record-lifecycle-debug-processor";
import { createProviders } from "./exporters";
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

class PulseWebSDK implements SdkContext {
  private static _instance: PulseWebSDK | null = null;
  private _initialized = false;
  private _shuttingDown = false;
  private _starting = false;

  sessionProvider!: SessionProvider;
  logger!: Logger;
  tracer!: Tracer;
  config!: PulseWebConfig;
  globalAttrsProcessor!: PulseGlobalAttributesProcessor;

  private tracerProvider?: WebTracerProvider;
  private loggerProvider?: LoggerProvider;
  private meterProvider?: MeterProvider;
  private registry?: InstrumentationRegistry;
  private configFetcher: SdkConfigFetcher = new SdkConfigFetcher("", "");
  private gate: FeatureGate = new FeatureGate(DEFAULT_SDK_CONFIG);
  private _providerCleanup: () => void = () => {};

  static getInstance(): PulseWebSDK {
    if (!PulseWebSDK._instance) {
      PulseWebSDK._instance = new PulseWebSDK();
    }
    return PulseWebSDK._instance;
  }

  start(config: PulseWebConfig): void {
    if (this._initialized || this._shuttingDown || this._starting) return;
    // Step 1: Validate config
    validateConfig(config);
    // Step 1.5: Resolve endpointBaseUrl from apiKey (internal — not a public config field)
    const endpointBaseUrl = resolveEndpointBaseUrl(config.apiKey);

    // Consent gate — DENIED or PENDING → no-op, zero signals emitted
    if (!isDataCollectionAllowed(config.dataCollectionState)) return;
    this.config = config;

    const meteringSessionId = crypto.randomUUID();

    // Set _starting before the async finishStart so the singleton guard blocks
    // any duplicate start() calls that arrive during the 200ms OS-version await.
    this._starting = true;
    void this.finishStart(config, endpointBaseUrl, meteringSessionId);
  }

  private async finishStart(
    config: PulseWebConfig,
    endpointBaseUrl: string,
    meteringSessionId: string,
  ): Promise<void> {
    if (this._initialized || this._shuttingDown) {
      this._starting = false;
      return;
    }
    // Step 2: SessionProvider
    this.sessionProvider = new SessionProvider();

    // Step 2.5: Eagerly resolve installation ID so wasNewInstallation() is accurate
    // before any signal is emitted (global-attrs-processor may call it later).
    getOrCreateInstallationId();

    // Step 3: Resolve real OS version async (Client Hints, <200ms) then build Resource.
    // This matches Android which puts os.version in the Resource via Build.VERSION.RELEASE.
    const syncUA = parseUserAgent();
    const resolvedOsVersion = await getOsVersionAsync(syncUA.osVersion);
    if (this._shuttingDown) {
      this._starting = false;
      return;
    }
    const resource = buildResource(config, resolvedOsVersion);

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

    const spanProcessors = [this.globalAttrsProcessor, filterProcessor];

    const debugLifecycle = config.debugLogRecordLifecycle === true;
    const ingressDebugProc = debugLifecycle
      ? new LogRecordLifecycleDebugProcessor("ingress")
      : null;
    const preBatchDebugProc = debugLifecycle
      ? new LogRecordLifecycleDebugProcessor("pre_batch")
      : null;
    const logProcessors = [
      ...(ingressDebugProc ? [ingressDebugProc] : []),
      this.globalAttrsProcessor,
      filterProcessor,
      ...(preBatchDebugProc ? [preBatchDebugProc] : []),
    ];

    const diskOn = config.diskBuffering?.enabled !== false;
    const disk = config.diskBuffering;
    const beforeSendResolved = resolveBeforeSend(config.beforeSendData);
    const exporterConfig = {
      endpointBaseUrl,
      apiKey: config.apiKey,
      meteringSessionId,
      useProtobuf: config.export?.format === "protobuf",
      // Inject the same global attributes into metric data points at export time.
      getMetricGlobalAttrs: () =>
        this.globalAttrsProcessor.getCommonAttrsForMetrics(),
      samplingGate,
      metricsToAdd: sdkConfig.signals.metricsToAdd,
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
    };

    const bundle = createProviders(
      exporterConfig,
      resource,
      spanProcessors,
      logProcessors,
    );

    if (bundle.idbSignalBuffer) {
      void drainBufferedOtlpExports({
        buffer: bundle.idbSignalBuffer,
        apiKey: config.apiKey,
        meteringSessionId,
        tracesUrl: `${endpointBaseUrl}/v1/traces`,
        logsUrl: `${endpointBaseUrl}/v1/logs`,
        metricsUrl: `${endpointBaseUrl}/v1/metrics`,
      });
    }

    this.tracerProvider = bundle.tracerProvider;
    this.loggerProvider = bundle.loggerProvider;
    this.meterProvider = bundle.meterProvider;
    this._providerCleanup = bundle.cleanup ?? (() => {});

    trace.setGlobalTracerProvider(this.tracerProvider);
    logs.setGlobalLoggerProvider(this.loggerProvider);
    metrics.setGlobalMeterProvider(this.meterProvider);

    this.logger = this.loggerProvider.getLogger("pulse-web");
    this.tracer = this.tracerProvider.getTracer("pulse-web");

    this.emitSdkInitializationLogRecords(endpointBaseUrl);

    this.registry = new InstrumentationRegistry(
      this as SdkContext,
      gate,
      config.instrumentations,
    );
    this.registry.installAll();

    // Step 10: Fetch fresh config in background.
    void this.configFetcher.fetchInBackground();

    this._initialized = true;
    this._starting = false;

    // Emit app.installation.start on first-ever install — mirrors Android.
    if (wasNewInstallation()) {
      this.logger.emit({
        body: "pulse.app.installation.start",
        attributes: {
          "pulse.type": "pulse.app.installation.start",
          "installation.id": getOrCreateInstallationId(),
        },
      });
    }
  }

  async shutdown(): Promise<void> {
    if (!this._initialized && !this._starting) return;
    this._shuttingDown = true;
    this._starting = false; // kill any pending async init

    this._providerCleanup();
    this.registry?.uninstallAll();
    this.sessionProvider?.shutdown();

    await Promise.all([
      this.tracerProvider?.forceFlush(),
      this.loggerProvider?.forceFlush(),
      this.meterProvider?.forceFlush(),
    ]);

    this._initialized = false;
    this._shuttingDown = false;
    // _starting already reset above
  }

  isInitialized(): boolean {
    return this._initialized;
  }

  setScreenName(name: string): void {
    this.globalAttrsProcessor?.setScreenName(name);
  }

  trackEvent(name: string, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    if (!this.gate.isEnabled("custom_events")) return;
    this.logger.emit({
      body: name,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.CUSTOM_EVENT,
        [PulseWebSemconv.AttributeKey.EVENT_NAME]:
          PulseWebSemconv.FixedValue.EVENT_NAME_CUSTOM_EVENT,
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }

  reportException(error: unknown, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    const err = error instanceof Error ? error : new Error(String(error));
    this.logger.emit({
      body: err.message,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.NON_FATAL,
        [PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]: err.name,
        [PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]: err.message,
        [PulseWebSemconv.AttributeKey.EXCEPTION_STACKTRACE]: err.stack ?? "",
        [PulseWebSemconv.AttributeKey.NON_FATAL_IS_MANUAL]: true,
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }

  /**
   * React render errors and similar fatals — `pulse.type` = device.crash (dashboard contract).
   */
  reportDeviceCrash(error: unknown, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    const err = error instanceof Error ? error : new Error(String(error));
    const stack = err.stack ?? "";
    this.logger.emit({
      body: err.message,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.DEVICE_CRASH,
        [PulseWebSemconv.AttributeKey.EXCEPTION_TYPE]: err.name,
        [PulseWebSemconv.AttributeKey.EXCEPTION_MESSAGE]: err.message,
        [PulseWebSemconv.AttributeKey.EXCEPTION_STACKTRACE]: stack,
        [PulseWebSemconv.AttributeKey.ERROR_FILENAME]:
          errorFilenameFromStack(stack),
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }

  trackNonFatal(name: string, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    this.logger.emit({
      body: name,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.NON_FATAL,
        [PulseWebSemconv.AttributeKey.NON_FATAL_TYPE]: name,
        [PulseWebSemconv.AttributeKey.NON_FATAL_IS_MANUAL]: true,
        ...(attrs as Record<string, string | number | boolean>),
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
    if (this.loggerProvider === undefined) return;

    const K = PulseWebSemconv.AttributeKey;
    const R = PulseWebSemconv.RumSdkInit;
    const initLogger = this.loggerProvider.getLogger(
      "otel.initialization.events",
    );

    initLogger.emit({
      body: R.STARTED,
      attributes: {
        [K.PULSE_TYPE]: R.STARTED,
      },
    });

    const spanExporterHint = [
      `OtlpHttpJson`,
      `traces=${endpointBaseUrl}/v1/traces`,
      `logs=${endpointBaseUrl}/v1/logs`,
      `metrics=${endpointBaseUrl}/v1/metrics`,
    ].join("; ");
    initLogger.emit({
      body: R.SPAN_EXPORTER,
      attributes: {
        [K.PULSE_TYPE]: R.SPAN_EXPORTER,
        "span.exporter": spanExporterHint,
      },
    });
  }
}

export const PulseWeb = PulseWebSDK.getInstance();
