// M1: PulseWebSDK — full 10-step init sequence (singleton). Optional async IndexedDB drain when diskBuffering enabled.

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
import { createProviders } from "./exporters";
import { InstrumentationRegistry } from "./instrumentation-registry";
import type { SdkContext } from "./instrumentation-registry";
import { extractProjectId } from "./resource";
import { isDataCollectionAllowed } from "./consent";
import { drainBufferedOtlpExports } from "./persistence/drain-buffered-exports";
import { IdbSignalBuffer } from "./persistence/indexed-db";
import { LogRecordLifecycleDebugProcessor } from "./processors/log-record-lifecycle-debug-processor";
import { PulseWebSemconv } from "./semconv";
import { errorFilenameFromStack } from "./utils/error-stack";
import { ExportSamplingGate } from "./sampling/export-sampling-gate";

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

    // Step 1.5: Resolve endpointBaseUrl from apiKey if not provided
    const endpointBaseUrl = resolveEndpointBaseUrl(
      config.apiKey,
      config.endpointBaseUrl,
    );
    const configWithUrl: PulseWebConfig = {
      ...config,
      endpointBaseUrl: endpointBaseUrl,
    };

    // Consent gate — DENIED or PENDING → no-op, zero signals emitted
    if (!isDataCollectionAllowed(configWithUrl.dataCollectionState)) return;
    this.config = configWithUrl;

    const disk = configWithUrl.diskBuffering;
    const diskEnabled = disk?.enabled === true;
    const idbBuffer = new IdbSignalBuffer(disk?.maxAgeMs, disk?.maxSizeBytes);
    const meteringSessionId = crypto.randomUUID();

    if (diskEnabled) {
      this._starting = true;
      void drainBufferedOtlpExports({
        tracesUrl: `${endpointBaseUrl}/v1/traces`,
        logsUrl: `${endpointBaseUrl}/v1/logs`,
        metricsUrl: `${endpointBaseUrl}/v1/metrics`,
        apiKey: configWithUrl.apiKey,
        meteringSessionId,
        buffer: idbBuffer,
      })
        .catch(() => {})
        .finally(() => {
          this._starting = false;
          void this.finishStart(
            configWithUrl,
            endpointBaseUrl,
            idbBuffer,
            meteringSessionId,
          );
        });
      return;
    }

    // Set _starting before the async finishStart so the singleton guard blocks
    // any duplicate start() calls that arrive during the 200ms OS-version await.
    this._starting = true;
    void this.finishStart(
      configWithUrl,
      endpointBaseUrl,
      idbBuffer,
      meteringSessionId,
    );
  }

  private async finishStart(
    configWithUrl: PulseWebConfig,
    endpointBaseUrl: string,
    idbBuffer: IdbSignalBuffer,
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
    const resource = buildResource(configWithUrl, resolvedOsVersion);

    // Step 4: Load cached SDK config
    const projectId = extractProjectId(configWithUrl.apiKey);
    this.configFetcher = new SdkConfigFetcher(
      endpointBaseUrl,
      projectId,
      configWithUrl.configEndpointUrl,
      configWithUrl.apiKey,
    );
    const sdkConfig = this.configFetcher.loadCached();

    const gate = new FeatureGate(sdkConfig);
    this.gate = gate;
    const samplingGate = new ExportSamplingGate(sdkConfig, "pulse_web_js", {
      serviceVersion: configWithUrl.serviceVersion,
    });
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    this.globalAttrsProcessor = new PulseGlobalAttributesProcessor(
      this.sessionProvider,
      configWithUrl,
    );

    const spanProcessors = [this.globalAttrsProcessor, filterProcessor];
    const logLifecycle = configWithUrl.debugLogRecordLifecycle === true;
    const logProcessors = [
      ...(logLifecycle
        ? [new LogRecordLifecycleDebugProcessor("ingress")]
        : []),
      this.globalAttrsProcessor,
      filterProcessor,
      ...(logLifecycle
        ? [new LogRecordLifecycleDebugProcessor("pre_batch")]
        : []),
    ];

    const diskEnabled = configWithUrl.diskBuffering?.enabled === true;

    const exporterConfig = {
      endpointBaseUrl,
      apiKey: configWithUrl.apiKey,
      meteringSessionId,
      format: configWithUrl.export?.format,
      compression: configWithUrl.export?.compression,
      batchOptions: configWithUrl.export?.batch,
      // Inject the same global attributes into metric data points at export time.
      getMetricGlobalAttrs: () =>
        this.globalAttrsProcessor.getCommonAttrsForMetrics(),
      samplingGate,
      metricsToAdd: sdkConfig.signals.metricsToAdd,
      metricsToAddSdkName: "pulse_web_js" as const,
      ...(diskEnabled
        ? { diskBuffer: { enabled: true, buffer: idbBuffer } }
        : {}),
      debugLogRecordLifecycle: configWithUrl.debugLogRecordLifecycle === true,
    };

    const bundle = createProviders(
      exporterConfig,
      resource,
      spanProcessors,
      logProcessors,
    );
    this.tracerProvider = bundle.tracerProvider;
    this.loggerProvider = bundle.loggerProvider;
    this.meterProvider = bundle.meterProvider;

    trace.setGlobalTracerProvider(this.tracerProvider);
    logs.setGlobalLoggerProvider(this.loggerProvider);
    metrics.setGlobalMeterProvider(this.meterProvider);

    this.logger = this.loggerProvider.getLogger("pulse-web");
    this.tracer = this.tracerProvider.getTracer("pulse-web");

    this.registry = new InstrumentationRegistry(
      this as SdkContext,
      gate,
      configWithUrl.instrumentations,
    );
    this.registry.installAll();

    // Step 10: Fetch fresh config in background.
    void this.configFetcher.fetchInBackground();

    this._initialized = true;
    this._starting = false;

    // os.version is already resolved before we built the Resource, so emit immediately.
    const initSpan = this.tracer.startSpan("sdk.init");
    initSpan.setAttribute("pulse.type", "sdk.init");
    initSpan.setAttribute("platform", "web");
    initSpan.end();

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
    if (!this._initialized) return;
    this._shuttingDown = true;

    this.registry?.uninstallAll();
    this.sessionProvider?.shutdown();

    await Promise.all([
      this.tracerProvider?.forceFlush(),
      this.loggerProvider?.forceFlush(),
      this.meterProvider?.forceFlush(),
    ]);

    this._initialized = false;
    this._shuttingDown = false;
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
    if (this.config.debugLogRecordLifecycle === true) {
      console.log("[PulseWeb:logLifecycle]", {
        phase: "api",
        where: "PulseWeb.trackEvent → logger.emit",
        body: name,
        attrs,
      });
    }
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
}

export const PulseWeb = PulseWebSDK.getInstance();
