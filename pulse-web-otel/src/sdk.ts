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
import { validateConfig, resolveEndpointBaseUrl } from './config';
import { SessionProvider, getOrCreateInstallationId, wasNewInstallation } from './session';
import { buildResource } from './resource';
import { SdkConfigFetcher, DEFAULT_SDK_CONFIG } from './remote-config';
import { FeatureGate } from './feature-gate';
import { PulseGlobalAttributesProcessor } from './processors/global-attrs-processor';
import { PulseSamplingProcessor } from './processors/sampling-processor';
import { SignalFilterProcessor } from './processors/signal-filter-processor';
import { createProviders } from './exporters';
import { InstrumentationRegistry } from './instrumentation-registry';
import type { SdkContext } from './instrumentation-registry';
import { extractProjectId } from './resource';
import { isDataCollectionAllowed } from './consent';

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
  private gate: FeatureGate = new FeatureGate(DEFAULT_SDK_CONFIG);
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

    // Step 1.5: Resolve endpointBaseUrl from apiKey if not provided
    const endpointBaseUrl = resolveEndpointBaseUrl(config.apiKey, config.endpointBaseUrl);
    const configWithUrl: PulseWebConfig = {
      ...config,
      endpointBaseUrl: endpointBaseUrl,
    };

    // Consent gate — DENIED or PENDING → no-op, zero signals emitted
    if (!isDataCollectionAllowed(configWithUrl.dataCollectionState)) return;
    this.config = configWithUrl;

    // Step 2: SessionProvider
    this.sessionProvider = new SessionProvider();

    // Step 2.5: Eagerly resolve installation ID so wasNewInstallation() is accurate
    // before any signal is emitted (global-attrs-processor may call it later).
    getOrCreateInstallationId();

    // Step 3: Build OTEL Resource
    const resource = buildResource(config);

    // Step 4: Load cached SDK config
    const projectId = extractProjectId(configWithUrl.apiKey);
    this.configFetcher = new SdkConfigFetcher(
      endpointBaseUrl,
      projectId,
      undefined,
      configWithUrl.apiKey,
    );
    const sdkConfig = this.configFetcher.loadCached();

    // Step 5: FeatureGate + SamplingProcessor + FilterProcessor
    const gate = new FeatureGate(sdkConfig);
    this.gate = gate;
    const samplingProcessor = new PulseSamplingProcessor(sdkConfig, 'pulse_web_js');
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    // Step 6: GlobalAttributesProcessor
    this.globalAttrsProcessor = new PulseGlobalAttributesProcessor(
      this.sessionProvider,
      configWithUrl,
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

    // Generate a stable metering session ID for this SDK lifetime (page load).
    // Sent as X-Pulse-Metering-Session-ID on every OTLP request — mirrors Android.
    const meteringSessionId = crypto.randomUUID();

    const exporterConfig = {
      endpointBaseUrl: endpointBaseUrl,
      apiKey: configWithUrl.apiKey,
      meteringSessionId,
      format: configWithUrl.export?.format,
      compression: configWithUrl.export?.compression,
      batchOptions: configWithUrl.export?.batch,
      // Inject the same global attributes into metric data points at export time.
      getMetricGlobalAttrs: () => this.globalAttrsProcessor.getCommonAttrsForMetrics(),
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
      configWithUrl.instrumentations,
    );
    this.registry.installAll();

    // Step 10: Fetch fresh config in background.
    void this.configFetcher.fetchInBackground();

    this._initialized = true;

    // Defer sdk.init heartbeat until os.version async enrichment resolves (<200ms)
    // so the span carries the real OS version instead of the frozen Chrome UA value.
    void this.globalAttrsProcessor.enrichmentReady.then(() => {
      const initSpan = this.tracer.startSpan('sdk.init');
      initSpan.setAttribute('pulse.type', 'sdk.init');
      initSpan.setAttribute('platform', 'web');
      initSpan.end();

      // Emit app.installation.start on first-ever install — mirrors Android.
      if (wasNewInstallation()) {
        this.logger.emit({
          body: 'pulse.app.installation.start',
          attributes: {
            'pulse.type': 'pulse.app.installation.start',
            'installation.id': getOrCreateInstallationId(),
          },
        });
      }
    });
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
    // Gated by remote config — mirrors Android's isCustomEventEnabled check.
    if (!this.gate.isEnabled('custom_events')) return;
    this.logger.emit({
      body: name,
      attributes: {
        'pulse.type': 'custom_event',
        'event.name': 'pulse.custom_event',
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }

  reportException(error: unknown, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    const err = error instanceof Error ? error : new Error(String(error));
    // body = error message, matching Android's setBody(throwable.message) behaviour.
    this.logger.emit({
      body: err.message,
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

  /**
   * Report a named non-fatal event without an exception — mirrors Android's
   * trackNonFatal(name, params) overload. Use for custom error-category signals
   * (e.g. 'network_timeout', 'payment_declined') that don't have a stack trace.
   */
  trackNonFatal(name: string, attrs?: Record<string, unknown>): void {
    if (!this._initialized) return;
    this.logger.emit({
      body: name,
      attributes: {
        'pulse.type': 'non_fatal',
        'non_fatal.type': name,
        'non_fatal.is_manual': true,
        ...(attrs as Record<string, string | number | boolean>),
      },
    });
  }
}

export const PulseWeb = PulseWebSDK.getInstance();