// M2: FeatureGate — reads remote SDK config to gate per-instrumentation installs.
// isEnabled(featureName) returns false if the feature is disabled remotely.
// See: web-sdk-plan/v1/01-foundation/sdk-config.md

import type { PulseSdkConfig, PulseFeatureName } from './remote-config';

const SDK_NAME = 'pulse_web_js' as const;

export class FeatureGate {
  private readonly config: PulseSdkConfig;

  constructor(config: PulseSdkConfig) {
    this.config = config;
  }

  isEnabled(feature: PulseFeatureName): boolean {
    const featureConfig = this.config.features.find(
      (f) => f.featureName === feature,
    );

    // Feature not in config list → default enabled
    if (!featureConfig) return true;

    // Only applies if this SDK is in the list
    if (!featureConfig.sdks.includes(SDK_NAME)) return true;

    // Disabled if sessionSampleRate is 0
    if (featureConfig.sessionSampleRate === 0) return false;

    return true;
  }

  getSampleRate(feature: PulseFeatureName): number {
    const featureConfig = this.config.features.find(
      (f) => f.featureName === feature && f.sdks.includes(SDK_NAME),
    );

    if (!featureConfig) return 1.0;

    return featureConfig.sessionSampleRate;
  }
}
