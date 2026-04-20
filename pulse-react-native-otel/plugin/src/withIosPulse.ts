import type { ConfigPlugin } from '@expo/config-plugins';
import { WarningAggregator, withAppDelegate } from '@expo/config-plugins';
import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';

import {
  PULSE_IOS_IMPORT,
  PULSE_IOS_OTEL_API_IMPORT,
  buildSwiftPulseSdkInitialization,
} from './iosCodegen';
import type { ResolvedIosPulseProps } from './types';

/** Expo / RN new-architecture Swift templates set this before `startReactNative`. */
const ANCHOR_REACT_NATIVE_FACTORY = /reactNativeFactory\s*=\s*factory/;

/** Present in Expo prebuild `AppDelegate.swift` (SDK 51+). */
const ANCHOR_REACT_APP_DEPENDENCY_PROVIDER =
  /import\s+ReactAppDependencyProvider/;

export const withIosPulse: ConfigPlugin<ResolvedIosPulseProps> = (
  config,
  props: ResolvedIosPulseProps
) => {
  return withAppDelegate(config, (modConfig) => {
    try {
      if (modConfig.modResults.language !== 'swift') {
        WarningAggregator.addWarningIOS(
          'withPulse',
          'Pulse Expo plugin only auto-injects into Swift AppDelegate. Add PulseSDK.initialize(...) in your iOS entry before React Native starts.',
          'https://github.com/expo/expo/tree/main/templates/expo-template-bare-minimum/ios'
        );
        return modConfig;
      }

      let contents = modConfig.modResults.contents;

      contents = mergeContents({
        src: contents,
        newSrc: PULSE_IOS_IMPORT,
        tag: 'pulse-ios-pulsereactnative-import',
        comment: '//',
        anchor: ANCHOR_REACT_APP_DEPENDENCY_PROVIDER,
        offset: 1,
      }).contents;

      if (
        props.globalAttributes &&
        Object.keys(props.globalAttributes).length > 0
      ) {
        contents = mergeContents({
          src: contents,
          newSrc: PULSE_IOS_OTEL_API_IMPORT,
          tag: 'pulse-ios-opentelemetry-api-import',
          comment: '//',
          anchor: /import\s+PulseReactNativeOtel/,
          offset: 1,
        }).contents;
      }

      contents = mergeContents({
        src: contents,
        newSrc: buildSwiftPulseSdkInitialization(props),
        tag: 'pulse-ios-sdk-initialization',
        comment: '//',
        anchor: ANCHOR_REACT_NATIVE_FACTORY,
        offset: 1,
      }).contents;

      modConfig.modResults.contents = contents;
      return modConfig;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error('[Pulse] Failed to patch iOS AppDelegate:', error);
      WarningAggregator.addWarningIOS(
        'withPulse',
        `Pulse iOS AppDelegate injection failed (${message}). Ensure your AppDelegate matches the Expo Swift template, or call PulseSDK.initialize manually before startReactNative.`
      );
      return modConfig;
    }
  });
};
