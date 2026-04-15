// M1: InstrumentationRegistry — holds all Instrumentation instances,
// calls installAll() during SDK.start() and uninstallAll() during shutdown().
// See: web-sdk-plan/v1/01-foundation/sdk-lifecycle.md

import type { Logger } from '@opentelemetry/api-logs';
import type { Tracer } from '@opentelemetry/api';
import type { PulseWebConfig, InstrumentationConfig } from './config';
import type { SessionProvider } from './session';
import type { PulseGlobalAttributesProcessor } from './processors/global-attrs-processor';
import type { FeatureGate } from './feature-gate';
import { SessionInstrumentation } from './instrumentations/session';

export interface SdkContext {
  sessionProvider: SessionProvider;
  logger: Logger;
  tracer: Tracer;
  config: PulseWebConfig;
  globalAttrsProcessor: PulseGlobalAttributesProcessor;
}

export interface PulseInstrumentation {
  readonly name: string;
  install(sdk: SdkContext): void;
  uninstall(): void;
}

export class InstrumentationRegistry {
  private installed: PulseInstrumentation[] = [];

  constructor(
    private readonly sdk: SdkContext,
    private readonly gate: FeatureGate,
    private readonly instrConfig: InstrumentationConfig | undefined,
  ) {}

  private shouldInstall(key: keyof InstrumentationConfig): boolean {
    const featureMap: Record<keyof InstrumentationConfig, string> = {
      errors: 'js_crash',
      network: 'network_instrumentation',
      clicks: 'click',
      webVitals: 'web_vitals',
      navigation: 'screen_session',
      session: 'session',
      interactions: 'interaction',
      sessionReplay: 'session_replay',
    };

    const featureName = featureMap[key] as Parameters<FeatureGate['isEnabled']>[0];
    const gateEnabled = this.gate.isEnabled(featureName);
    const configEnabled = this.instrConfig?.[key]?.enabled ?? true;

    return gateEnabled && configEnabled;
  }

  installAll(): void {
    // M1: Install session instrumentation
    if (this.shouldInstall('session')) {
      const sessionInstr = new SessionInstrumentation();
      sessionInstr.install(this.sdk);
      this.installed.push(sessionInstr);
    }

    // M3: will install ErrorsInstrumentation, NetworkInstrumentation,
    // ClicksInstrumentation, WebVitalsInstrumentation, NavigationInstrumentation, etc.
  }

  uninstallAll(): void {
    // Uninstall in reverse order
    for (let i = this.installed.length - 1; i >= 0; i--) {
      const instr = this.installed[i];
      if (instr) {
        try {
          instr.uninstall();
        } catch {
          // ignore errors during uninstall
        }
      }
    }
    this.installed = [];
  }
}
