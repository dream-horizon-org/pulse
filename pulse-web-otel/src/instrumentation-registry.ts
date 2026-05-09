// M1: InstrumentationRegistry — holds all Instrumentation instances,
// calls installAll() during SDK.init() and uninstallAll() during shutdown().
// See: web-sdk-plan/v1/01-foundation/sdk-lifecycle.md

import { diag } from "@opentelemetry/api";

import type { InstrumentationConfig } from "./config";
import type { FeatureGate } from "./feature-gate";
import { SessionInstrumentation } from "./instrumentations/session";
import { ClicksInstrumentation } from "./instrumentations/clicks";
import { WebVitalsInstrumentation } from "./instrumentations/web-vitals";
import { NetworkInstrumentation } from "./instrumentations/network";
import { ErrorInstrumentation } from "./instrumentations/errors";
import { NavigationInstrumentation } from "./instrumentations/navigation";
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
      [InstrumentationKeys.NAVIGATION]: PulseFeature.SCREEN_NAVIGATION,
      [InstrumentationKeys.SESSION]: PulseFeature.SESSION,
      [InstrumentationKeys.INTERACTIONS]: PulseFeature.INTERACTION,
      [InstrumentationKeys.SESSION_REPLAY]: PulseFeature.SESSION_REPLAY,
    };

    const featureName = featureMap[key];
    const gateEnabled = this.gate.isEnabled(featureName);
    const configEnabled = this.instrConfig?.[key]?.enabled;

    // Local `enabled: false` is a kill switch — remote gate cannot turn it back on.
    // Omitted or `true`: only the FeatureGate decides (`gateEnabled`).
    // NOTE: `configEnabled && gateEnabled` would be wrong: omitted `enabled` is
    // `undefined`, and `undefined && gate` is falsy — would never install.
    return configEnabled !== false && gateEnabled;
  }

  registerAndInstall(
    instrumentation: PulseInstrumentation,
    key?: keyof InstrumentationConfig,
  ): boolean {
    if (key !== undefined && !this.shouldInstall(key)) {
      return false;
    }
    // Per-instrumentation try/catch: a transient error in one must not skip
    // the rest, and must not poison `installAllCompleted` (single-owner gate
    // is set only after the full sweep — see `installAll`).
    try {
      instrumentation.install(this.sdk);
      this.installed.push(instrumentation);
      return true;
    } catch (err) {
      diag.error(
        `[Pulse] instrumentation install failed${key ? ` (${key})` : ""}`,
        err,
      );
      return false;
    }
  }

  installAll(): void {
    if (this.installAllCompleted) {
      return;
    }

    this.registerAndInstall(
      new SessionInstrumentation(),
      InstrumentationKeys.SESSION,
    );
    this.registerAndInstall(
      new ClicksInstrumentation(),
      InstrumentationKeys.CLICKS,
    );
    this.registerAndInstall(
      new WebVitalsInstrumentation(),
      InstrumentationKeys.WEB_VITALS,
    );
    this.registerAndInstall(
      new NetworkInstrumentation(),
      InstrumentationKeys.NETWORK,
    );
    this.registerAndInstall(
      new NavigationInstrumentation(),
      InstrumentationKeys.NAVIGATION,
    );
    this.registerAndInstall(
      new ErrorInstrumentation(),
      InstrumentationKeys.ERRORS,
    );

    // Set the single-owner flag only after the full sweep completes.
    // If we set it earlier and a sync throw escaped one install, future
    // installAll() calls would silently no-op until uninstallAll() ran.
    // Per-instrumentation try/catch above ensures one failure does not
    // skip the others; setting the flag here makes the gate idempotent
    // without hiding partial failures.
    this.installAllCompleted = true;
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
