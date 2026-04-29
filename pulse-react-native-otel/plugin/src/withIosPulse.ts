import type { ConfigPlugin } from '@expo/config-plugins';
import { WarningAggregator, withAppDelegate } from '@expo/config-plugins';
import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';

import {
  PULSE_OBJC_PULSE_SWIFT_HEADER,
  buildObjcPulseSdkInitialization,
  getAppDelegatePrebuildKind,
} from './iosObjcCodegen';
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

const ANCHOR_OBJC_IMPLEMENTATION_APP_DELEGATE =
  /^\s*@implementation\s+AppDelegate\b/m;
const ANCHOR_OBJC_IMPORT_APP_DELEGATE = /^#import\s+["<]AppDelegate.h[>"]\s*$/m;
const ANCHOR_OBJC_SELF_MODULE = /^\s*self\.moduleName\s*=\s*@/m;
const ANCHOR_OBJC_SELF_PROPS = /^\s*self\.initialProps\s*=\s*@/m;
const ANCHOR_OBJC_SUPER_RETURN =
  /^\s*return\s+\[super\s+application:application\s+didFinishLaunchingWithOptions:launchOptions\];/m;

function mergeWithAnchorAttempts(
  src: string,
  newSrc: string,
  tag: string,
  comment: string,
  attempts: ReadonlyArray<{ anchor: string | RegExp; offset: number }>
): string {
  let last: unknown;
  for (const { anchor, offset } of attempts) {
    try {
      return mergeContents({
        src,
        newSrc,
        tag,
        comment,
        anchor,
        offset,
      }).contents;
    } catch (e) {
      last = e;
      if (
        e &&
        typeof e === 'object' &&
        (e as { code?: string }).code === 'ERR_NO_MATCH'
      ) {
        continue;
      }
      throw e;
    }
  }
  const msg =
    last && typeof last === 'object' && 'message' in (last as object)
      ? (last as Error).message
      : 'no anchor match';
  throw new Error(
    `Pulse: could not find an AppDelegate anchor to merge (${tag}): ${msg}`
  );
}

function patchObjCAppDelegate(
  original: string,
  props: ResolvedIosPulseProps
): string {
  const headerTag = 'pulse-ios-objc-pulse-swift-header';
  let out = mergeWithAnchorAttempts(
    original,
    PULSE_OBJC_PULSE_SWIFT_HEADER,
    headerTag,
    '//',
    [
      { anchor: ANCHOR_OBJC_IMPORT_APP_DELEGATE, offset: 1 },
      { anchor: ANCHOR_OBJC_IMPLEMENTATION_APP_DELEGATE, offset: 0 },
    ]
  );
  out = mergeWithAnchorAttempts(
    out,
    buildObjcPulseSdkInitialization(props),
    'pulse-ios-objc-pulse-initialize',
    '//',
    [
      { anchor: ANCHOR_OBJC_SELF_MODULE, offset: 0 },
      { anchor: ANCHOR_OBJC_SELF_PROPS, offset: 0 },
      { anchor: ANCHOR_OBJC_SUPER_RETURN, offset: 0 },
    ]
  );
  return out;
}

function patchSwiftAppDelegate(
  contents: string,
  props: ResolvedIosPulseProps
): string {
  let c = contents;
  c = mergeContents({
    src: c,
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
    c = mergeContents({
      src: c,
      newSrc: PULSE_IOS_OTEL_API_IMPORT,
      tag: 'pulse-ios-opentelemetry-api-import',
      comment: '//',
      anchor: /import\s+PulseReactNativeOtel/,
      offset: 1,
    }).contents;
  }
  c = mergeContents({
    src: c,
    newSrc: buildSwiftPulseSdkInitialization(props),
    tag: 'pulse-ios-sdk-initialization',
    comment: '//',
    anchor: ANCHOR_REACT_NATIVE_FACTORY,
    offset: 1,
  }).contents;
  return c;
}

export const withIosPulse: ConfigPlugin<ResolvedIosPulseProps> = (
  config,
  props: ResolvedIosPulseProps
) => {
  return withAppDelegate(config, (modConfig) => {
    const kind = getAppDelegatePrebuildKind(modConfig.modResults);
    try {
      if (kind === 'objc') {
        const contents = patchObjCAppDelegate(
          modConfig.modResults.contents,
          props
        );
        modConfig.modResults.contents = contents;
        return modConfig;
      }
      if (kind === 'swift') {
        modConfig.modResults.contents = patchSwiftAppDelegate(
          modConfig.modResults.contents,
          props
        );
        return modConfig;
      }
      WarningAggregator.addWarningIOS(
        'withPulse',
        'Pulse Expo plugin could not detect a Swift or Objective-C AppDelegate. Add [PulseSDK pulseInitialize:...] in AppDelegate, or `import PulseReactNativeOtel` + PulseSDK.initialize in Swift, before React Native starts.',
        'https://github.com/dream-horizon-org/pulse/blob/main/pulse-react-native-otel/ios/README-OBJC.md'
      );
      return modConfig;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error('[Pulse] Failed to patch iOS AppDelegate:', error);
      WarningAggregator.addWarningIOS(
        'withPulse',
        `Pulse iOS AppDelegate injection failed (${message}). Ensure your AppDelegate matches a supported template, or initialize PulseSDK manually.`
      );
      return modConfig;
    }
  });
};
