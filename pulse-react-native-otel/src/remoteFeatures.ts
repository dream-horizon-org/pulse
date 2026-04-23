import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import type { PulseFeatureConfig } from './pulse.interface';

let cachedFeatures: PulseFeatureConfig;

/**
 * Feature flags from native remote config. Cached after first read.
 * Lives outside `config` so navigation/events can import without pulling the full config graph.
 */
export function getFeaturesFromRemoteConfig(): PulseFeatureConfig {
  if (cachedFeatures !== undefined) {
    return cachedFeatures;
  }

  cachedFeatures = PulseReactNativeOtel.getAllFeatures();
  return cachedFeatures;
}
