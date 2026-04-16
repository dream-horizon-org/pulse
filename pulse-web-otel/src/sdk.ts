// M1: PulseWebSDK — init sequence (singleton). Optional async IndexedDB drain when diskBuffering enabled.

import { trace } from "@opentelemetry/api";
import type { Tracer } from "@opentelemetry/api";
import { logs } from "@opentelemetry/api-logs";
import type { Logger } from "@opentelemetry/api-logs";
import { metrics } from "@opentelemetry/api";
import type { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { MeterProvider } from "@opentelemetry/sdk-metrics";

import type { PulseWebConfig } from "./config";
import { validateConfig } from "./config";
import {
  SessionProvider,
  getOrCreateInstallationId,
  wasNewInstallation,
} from "./session";
import { buildResource } from "./resource";
import { SdkConfigFetcher, DEFAULT_SDK_CONFIG } from "./remote-config";
import { FeatureGate } from "./feature-gate";
import { PulseGlobalAttributesProcessor } from "./processors/global-attrs-processor";
import { PulseSamplingProcessor } from "./processors/sampling-processor";
import { SignalFilterProcessor } from "./processors/signal-filter-processor";
import { LogRecordLifecycleDebugProcessor } from "./processors/log-record-lifecycle-debug-processor";
import { createProviders } from "./exporters";
import { InstrumentationRegistry } from "./instrumentation-registry";
import type { SdkContext } from "./instrumentation-registry";
import { extractProjectId } from "./resource";
import { isDataCollectionAllowed } from "./consent";
import { IdbSignalBuffer } from "./persistence/indexed-db";
import { drainBufferedOtlpExports } from "./persistence/drain-buffered-exports";
import { PulseWebSemconv } from "./semconv";
import { errorFilenameFromStack } from "./utils/error-stack";

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

    validateConfig(config);

    if (!isDataCollectionAllowed(config.dataCollectionState)) return;
    this.config = config;

    const disk = config.diskBuffering;
    const diskEnabled = disk?.enabled === true;
    const idbBuffer = new IdbSignalBuffer(disk?.maxAgeMs, disk?.maxSizeBytes);
    const meteringSessionId = crypto.randomUUID();

    if (diskEnabled) {
      this._starting = true;
      void drainBufferedOtlpExports({
        tracesUrl: `${config.endpointBaseUrl}/v1/traces`,
        logsUrl: `${config.endpointBaseUrl}/v1/logs`,
        metricsUrl: `${config.endpointBaseUrl}/v1/metrics`,
        apiKey: config.apiKey,
        meteringSessionId,
        buffer: idbBuffer,
      })
        .catch(() => {})
        .finally(() => {
          this._starting = false;
          this.finishStart(config, idbBuffer, meteringSessionId);
        });
      return;
    }

    this.finishStart(config, idbBuffer, meteringSessionId);
  }

  private finishStart(
    config: PulseWebConfig,
    idbBuffer: IdbSignalBuffer,
    meteringSessionId: string,
  ): void {
    if (this._initialized || this._shuttingDown) return;

    const sessionInactivityMs =
      config.instrumentations?.session?.inactivityTimeoutMs;
    this.sessionProvider = new SessionProvider(sessionInactivityMs);

    getOrCreateInstallationId();

    const resource = buildResource(config);

    const projectId = extractProjectId(config.apiKey);
    this.configFetcher = new SdkConfigFetcher(
      config.endpointBaseUrl,
      projectId,
      config.configEndpointUrl,
      config.apiKey,
    );
    const sdkConfig = this.configFetcher.loadCached();

    const gate = new FeatureGate(sdkConfig);
    this.gate = gate;
    const samplingProcessor = new PulseSamplingProcessor(
      sdkConfig,
      "pulse_web_js",
    );
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    this.globalAttrsProcessor = new PulseGlobalAttributesProcessor(
      this.sessionProvider,
      config,
    );

    const spanProcessors = [
      this.globalAttrsProcessor,
      samplingProcessor,
      filterProcessor,
    ];
    const logLifecycle = config.debugLogRecordLifecycle === true;
    const logProcessors = [
      ...(logLifecycle
        ? [new LogRecordLifecycleDebugProcessor("ingress")]
        : []),
      this.globalAttrsProcessor,
      samplingProcessor,
      filterProcessor,
      ...(logLifecycle
        ? [new LogRecordLifecycleDebugProcessor("pre_batch")]
        : []),
    ];

    const diskEnabled = config.diskBuffering?.enabled === true;

    const exporterConfig = {
      endpointBaseUrl: config.endpointBaseUrl,
      apiKey: config.apiKey,
      meteringSessionId,
      format: config.export?.format,
      compression: config.export?.compression,
      batchOptions: config.export?.batch,
      debugLogRecordLifecycle: config.debugLogRecordLifecycle,
      diskBuffer: {
        enabled: diskEnabled,
        buffer: idbBuffer,
      },
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
      config.instrumentations,
    );
    this.registry.installAll();

    void this.configFetcher.fetchInBackground();

    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;
    const B = PulseWebSemconv.LogBody;
    const F = PulseWebSemconv.FixedValue;
    const initSpan = this.tracer.startSpan(PulseWebSemconv.SpanName.SDK_INIT);
    initSpan.setAttribute(K.PULSE_TYPE, T.SDK_INIT);
    initSpan.setAttribute(K.PLATFORM, F.PLATFORM_WEB);
    initSpan.end();

    if (wasNewInstallation()) {
      this.logger.emit({
        body: B.APP_INSTALLATION_START,
        attributes: {
          [K.PULSE_TYPE]: T.INSTALLATION_START,
          [K.INSTALLATION_ID]: getOrCreateInstallationId(),
        },
      });
    }

    this._initialized = true;
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
