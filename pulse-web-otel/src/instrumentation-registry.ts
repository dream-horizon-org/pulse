// M1: InstrumentationRegistry — holds all Instrumentation instances,
// calls installAll() during SDK.start() and uninstallAll() during shutdown().
// See: web-sdk-plan/v1/01-foundation/sdk-lifecycle.md

import type { InstrumentationConfig } from "./config";
import type { FeatureGate } from "./feature-gate";
import { SessionInstrumentation } from "./instrumentations/session";
import { InstrumentationKeys } from "./config";
import { PulseFeature } from "./remote-config";
import type { PulseFeatureName } from "./remote-config";
import type {
  PulseInstrumentation,
  SdkContext,
} from "./types/instrumentation-registry";

export type {
  PulseInstrumentation,
  SdkContext,
} from "./types/instrumentation-registry";

export class InstrumentationRegistry {
  private installed: PulseInstrumentation[] = [];

  constructor(
    private readonly sdk: SdkContext,
    private readonly gate: FeatureGate,
    private readonly instrConfig: InstrumentationConfig | undefined,
  ) {}

  private shouldInstall(key: keyof InstrumentationConfig): boolean {
    const featureMap: Record<keyof InstrumentationConfig, PulseFeatureName> = {
      [InstrumentationKeys.ERRORS]: PulseFeature.JS_CRASH,
      [InstrumentationKeys.NETWORK]: PulseFeature.NETWORK_INSTRUMENTATION,
      [InstrumentationKeys.CLICKS]: PulseFeature.CLICK,
      [InstrumentationKeys.WEB_VITALS]: PulseFeature.WEB_VITALS,
      [InstrumentationKeys.NAVIGATION]: PulseFeature.SCREEN_SESSION,
      [InstrumentationKeys.SESSION]: PulseFeature.SESSION,
      [InstrumentationKeys.INTERACTIONS]: PulseFeature.INTERACTION,
      [InstrumentationKeys.SESSION_REPLAY]: PulseFeature.SESSION_REPLAY,
    };

    const featureName = featureMap[key];
    const gateEnabled = this.gate.isEnabled(featureName);
    const configEnabled = this.instrConfig?.[key]?.enabled ?? true;

    return gateEnabled && configEnabled;
  }

  registerAndInstall(
    instrumentation: PulseInstrumentation,
    key?: keyof InstrumentationConfig,
  ): boolean {
    if (key !== undefined && !this.shouldInstall(key)) {
      return false;
    }
    instrumentation.install(this.sdk);
    this.installed.push(instrumentation);
    return true;
  }

  installAll(): void {
    // M1: Install session instrumentation
    if (this.shouldInstall(InstrumentationKeys.SESSION)) {
      this.registerAndInstall(new SessionInstrumentation());
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
