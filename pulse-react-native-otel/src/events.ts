import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { getFeaturesFromRemoteConfig, getIsShutdown } from './config';
import { mergeWithGlobalAttributes } from './globalAttributes';
import { isSupportedPlatform } from './initialization';
import type { PulseAttributes } from './pulse.interface';

export function trackEvent(event: string, attributes?: PulseAttributes): void {
  if (!isSupportedPlatform() || getIsShutdown()) {
    return;
  }
  const features = getFeaturesFromRemoteConfig();
  const customEventsEnabled = features?.custom_events ?? true;

  if (!customEventsEnabled) {
    return;
  }

  const observedTimeMs = Date.now();
  const mergedAttributes = mergeWithGlobalAttributes(attributes || {});

  PulseReactNativeOtel.trackEvent(event, observedTimeMs, mergedAttributes);
}
