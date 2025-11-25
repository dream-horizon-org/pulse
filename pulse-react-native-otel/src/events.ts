import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { mergeWithGlobalAttributes } from './globalAttributes';
import { isSupportedPlatform } from './initialization';
import type { PulseAttributes } from './pulse.interface';

export function trackEvent(event: string, attributes?: PulseAttributes): void {
  if (!isSupportedPlatform()) {
    return;
  }

  const observedTimeMs = Date.now();
  const mergedAttributes = mergeWithGlobalAttributes(attributes || {});

  PulseReactNativeOtel.trackEvent(event, observedTimeMs, mergedAttributes);
}
