// M1: InstrumentationRegistry — holds all Instrumentation instances,
// calls installAll() during SDK.start() and uninstallAll() during shutdown().
// See: web-sdk-plan/v1/01-foundation/sdk-lifecycle.md

import type { PulseWebConfig, InstrumentationConfig } from "./config";
import type { FeatureGate } from "./feature-gate";
import { SessionInstrumentation } from "./instrumentations/session";
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
    const featureMap: Record<keyof InstrumentationConfig, string> = {
      errors: "js_crash",
      network: "network_instrumentation",
      clicks: "click",
      webVitals: "web_vitals",
      navigation: "screen_session",
      session: "session",
      interactions: "interaction",
      sessionReplay: "session_replay",
    };

    const featureName = featureMap[key] as Parameters<
      FeatureGate["isEnabled"]
    >[0];
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
    if (this.shouldInstall("session")) {
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
