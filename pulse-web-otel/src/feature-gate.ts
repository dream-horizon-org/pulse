// M2: FeatureGate — reads remote SDK config to gate per-instrumentation installs.
// isEnabled(featureName) returns false if the feature is disabled remotely.
// See: web-sdk-plan/v1/01-foundation/sdk-config.md

import type { PulseSdkConfig, PulseFeatureName } from "./remote-config";

const SDK_NAME = "pulse_web_js" as const;

export class FeatureGate {
  private readonly config: PulseSdkConfig;

  constructor(config: PulseSdkConfig) {
    this.config = config;
  }

  /**
   * Mirrors Android {@code PulseSamplingSignalProcessors.getEnabledFeatures()}:
   * a feature is on for instrumentation only when {@code sessionSampleRate === 1}
   * for this SDK. Absent from config → default enabled (local/dev).
   */
  isEnabled(feature: PulseFeatureName): boolean {
    const featureConfig = this.config.features.find(
      (f) => f.featureName === feature,
    );

    if (!featureConfig) return true;

    if (!featureConfig.sdks.includes(SDK_NAME)) return true;

    return featureConfig.sessionSampleRate === 1;
  }

  getSampleRate(feature: PulseFeatureName): number {
    const featureConfig = this.config.features.find(
      (f) => f.featureName === feature && f.sdks.includes(SDK_NAME),
    );

    if (!featureConfig) return 1.0;

    return featureConfig.sessionSampleRate;
  }
}
