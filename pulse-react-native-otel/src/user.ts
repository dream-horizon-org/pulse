import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { isSupportedPlatform } from './initialization';
import type { PulseAttributes } from './pulse.interface';

export function setUserId(id: string | null): void {
  if (!isSupportedPlatform()) {
    return;
  }
  PulseReactNativeOtel.setUserId(id);
}

export function setUserProperty(name: string, value: string | null): void {
  if (!isSupportedPlatform()) {
    return;
  }
  PulseReactNativeOtel.setUserProperty(name, value);
}

export function setUserProperties(properties: PulseAttributes): void {
  if (!isSupportedPlatform()) {
    return;
  }
  PulseReactNativeOtel.setUserProperties(properties);
}
