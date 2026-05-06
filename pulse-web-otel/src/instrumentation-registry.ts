// M1: InstrumentationRegistry — holds all Instrumentation instances,
// calls installAll() during SDK.start() and uninstallAll() during shutdown().
// See: web-sdk-plan/v1/01-foundation/sdk-lifecycle.md

import type { InstrumentationConfig } from "./config";
import type { FeatureGate } from "./feature-gate";
import { SessionInstrumentation } from "./instrumentations/session";
import { WebVitalsInstrumentation } from "./instrumentations/web-vitals";
import { ErrorInstrumentation } from "./instrumentations/errors";
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
  /** Prevents duplicate `installAll()` without `uninstallAll()` (single owner for web-vitals listeners). */
  private installAllCompleted = false;

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
    if (this.installAllCompleted) {
      return;
    }
    this.installAllCompleted = true;
    // M1: Install session instrumentation
    if (this.shouldInstall(InstrumentationKeys.SESSION)) {
      this.registerAndInstall(new SessionInstrumentation());
    }

    this.registerAndInstall(
      new WebVitalsInstrumentation(),
      InstrumentationKeys.WEB_VITALS,
    );

    // M3: will install ErrorsInstrumentation, NetworkInstrumentation,
    // ClicksInstrumentation, NavigationInstrumentation, etc.
    if (this.shouldInstall(InstrumentationKeys.ERRORS)) {
      const errInstr = new ErrorInstrumentation();
      errInstr.install(this.sdk);
      this.installed.push(errInstr);
    }
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
    this.installAllCompleted = false;
  }
}
