import { Platform } from 'react-native';
import PulseReactNativeOtel from './NativePulseReactNativeOtel';

let cachedInitStatus: boolean = false;

export function isInitialized(): boolean {
  if (!isSupportedPlatform()) {
    return false;
  }
  if (!cachedInitStatus) {
    cachedInitStatus = PulseReactNativeOtel.isInitialized();
  }
  return cachedInitStatus;
}

export function isSupportedPlatform(): boolean {
  return Platform.OS === 'android' || Platform.OS === 'ios';
}
