// M1: PulseWebSDK — full 10-step init sequence.
// Singleton via PulseWebSDK.getInstance().

import { trace } from '@opentelemetry/api';
import type { Tracer } from '@opentelemetry/api';
import { logs } from '@opentelemetry/api-logs';
import type { Logger } from '@opentelemetry/api-logs';
import { metrics } from '@opentelemetry/api';
import type { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import type { LoggerProvider } from '@opentelemetry/sdk-logs';
import type { MeterProvider } from '@opentelemetry/sdk-metrics';

import type { PulseWebConfig } from './config';
import { validateConfig } from './config';
import { SessionProvider } from './session';
import { buildResource } from './resource';
import { SdkConfigFetcher } from './remote-config';
import { FeatureGate } from './feature-gate';
import { PulseGlobalAttributesProcessor } from './processors/global-attrs-processor';
import { PulseSamplingProcessor } from './processors/sampling-processor';
import { SignalFilterProcessor } from './processors/signal-filter-processor';
import { createProviders } from './exporters';
import { InstrumentationRegistry } from './instrumentation-registry';
import type { SdkContext } from './instrumentation-registry';
import { extractProjectId } from './resource';

class PulseWebSDK implements SdkContext {
  private static _instance: PulseWebSDK | null = null;
  private _initialized = false;
  private _shuttingDown = false;

  // SdkContext fields (populated in start())
  sessionProvider!: SessionProvider;
  logger!: Logger;
  tracer!: Tracer;
  config!: PulseWebConfig;
  globalAttrsProcessor!: PulseGlobalAttributesProcessor;

  // Private providers
  private tracerProvider?: WebTracerProvider;
  private loggerProvider?: LoggerProvider;
  private meterProvider?: MeterProvider;
  private registry?: InstrumentationRegistry;
  private configFetcher: SdkConfigFetcher = new SdkConfigFetcher('', '');
  private pagehideHandler?: () => void;

  static getInstance(): PulseWebSDK {
    if (!PulseWebSDK._instance) {
      PulseWebSDK._instance = new PulseWebSDK();
    }
    return PulseWebSDK._instance;
  }

  start(config: PulseWebConfig): void {
    if (this._initialized || this._shuttingDown) return;

    // Step 1: Validate config
    validateConfig(config);
    this.config = config;

    // Step 2: SessionProvider
    const sessionInactivityMs = config.instrumentations?.session?.inactivityTimeoutMs;
    this.sessionProvider = new SessionProvider(sessionInactivityMs);

    // Step 3: Build OTEL Resource
    const resource = buildResource(config);

    // Step 4: Load cached SDK config
    const projectId = extractProjectId(config.apiKey);
    this.configFetcher = new SdkConfigFetcher(
      config.configEndpointUrl ?? config.endpointBaseUrl,
      projectId,
    );
    const sdkConfig = this.configFetcher.loadCached();

    // Step 5: FeatureGate + SamplingProcessor + FilterProcessor
    const gate = new FeatureGate(sdkConfig);
    const samplingProcessor = new PulseSamplingProcessor(sdkConfig, 'pulse_web_js');
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    // Step 6: GlobalAttributesProcessor
    this.globalAttrsProcessor = new PulseGlobalAttributesProcessor(
      this.sessionProvider,
      config,
    );

    // Step 7: Create providers
    const spanProcessors = [
      this.globalAttrsProcessor,
      samplingProcessor,
      filterProcessor,
    ];
    const logProcessors = [
      this.globalAttrsProcessor,
      samplingProcessor,
      filterProcessor,
    ];

    const exporterConfig = {
      endpointBaseUrl: config.endpointBaseUrl,
      apiKey: config.apiKey,
      batchOptions: config.export?.batch,
    };

    const bundle = createProviders(exporterConfig, resource, spanProcessors, logProcessors);
    this.tracerProvider = bundle.tracerProvider;
    this.loggerProvider = bundle.loggerProvider;
    this.meterProvider = bundle.meterProvider;

    // Step 8: Register providers globally
    trace.setGlobalTracerProvider(this.tracerProvider);
    logs.setGlobalLoggerProvider(this.loggerProvider);
    metrics.setGlobalMeterProvider(this.meterProvider);

    this.logger = this.loggerProvider.getLogger('pulse-web');
    this.tracer = this.tracerProvider.getTracer('pulse-web');

    // Step 9: InstrumentationRegistry.installAll()
    this.registry = new InstrumentationRegistry(
      this as SdkContext,
      gate,
      config.instrumentations,
    );
    this.registry.installAll();

    // Step 10: Fetch fresh config in background + emit sdk.init heartbeat
    void this.configFetcher.fetchInBackground();

    // Emit sdk.init heartbeat
    const initSpan = this.tracer.startSpan('sdk.init');
    initSpan.setAttribute('pulse.type', 'sdk.init');
    initSpan.setAttribute('platform', 'web');
    initSpan.end();

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

    if (this.pagehideHandler && typeof window !== 'undefined') {
      window.removeEventListener('pagehide', this.pagehideHandler);
    }

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
    const span = this.tracer.startSpan(name);
    if (attrs) {
      for (const [k, v] of Object.entries(attrs)) {
        span.setAttribute(k, v as string);
      }
    }
    span.end();
  }

  reportException(error: unknown, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    const err = error instanceof Error ? error : new Error(String(error));
    this.logger.emit({
      body: 'non_fatal',
      attributes: {
        'pulse.type': 'non_fatal',
        'exception.type': err.name,
        'exception.message': err.message,
        'exception.stacktrace': err.stack ?? '',
        'non_fatal.is_manual': true,
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }
}

export const PulseWeb = PulseWebSDK.getInstance();
