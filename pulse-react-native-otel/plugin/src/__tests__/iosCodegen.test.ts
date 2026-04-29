import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';

import {
  PULSE_IOS_IMPORT,
  PULSE_IOS_OTEL_API_IMPORT,
  buildSwiftConfigurationArg,
  buildSwiftInstrumentationsArg,
  buildSwiftPulseSdkInitialization,
} from '../iosCodegen';
import { resolveIosProps } from '../resolvePluginProps';
import {
  PULSE_OBJC_PULSE_SWIFT_HEADER,
  buildObjcPulseSdkInitialization,
  getAppDelegatePrebuildKind,
} from '../iosObjcCodegen';
import type { PulsePluginProps } from '../types';

const EXPO_APP_DELEGATE_SNIPPET = `
import React
import ReactAppDependencyProvider

 reactNativeFactory = factory

#if os(iOS)
`;

const OBJC_APP_DELEGATE_FIXTURE = `
#import "AppDelegate.h"
#import <React/RCTBundleURLProvider.h>

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
  self.moduleName = @"X";
  self.initialProps = @{};

  return [super application:application didFinishLaunchingWithOptions:launchOptions];
}

@end
`;

describe('buildSwiftPulseSdkInitialization', () => {
  it('generates minimal call with defaults', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'key_1',
      dataCollectionState: 'PENDING',
    });
    expect(code).toContain('PulseSDK.initialize(');
    expect(code).toContain('apiKey: "key_1"');
    expect(code).toContain('dataCollectionState: .pending');
    expect(code).not.toContain('endpointBaseUrl');
    expect(code).not.toContain('configEndpointUrl');
    expect(code).not.toContain('endpointHeaders');
    expect(code).toContain('globalAttributes: nil');
    expect(code).toContain('configuration: nil');
    expect(code).toContain('instrumentations: nil');
    expect(code).not.toContain('logLevel:');
  });

  it('includes logLevel when provided', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
      logLevel: 2,
    });
    expect(code).toContain('logLevel: .info');
  });

  it('embeds ios.configuration into PulseKitConfiguration closure', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
      configuration: {
        includeScreenAttributes: false,
        includeNetworkAttributes: true,
        includeGlobalAttributes: true,
      },
    });
    expect(code).toContain('configuration: { kit in');
    expect(code).toContain('kit.includeScreenAttributes = false');
    expect(code).toContain('kit.includeNetworkAttributes = true');
    expect(code).toContain('kit.includeGlobalAttributes = true');
  });

  it('maps consent states', () => {
    const allowed = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'ALLOWED',
    });
    expect(allowed).toContain('dataCollectionState: .allowed');

    const denied = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'DENIED',
    });
    expect(denied).toContain('dataCollectionState: .denied');
  });

  it('escapes quotes in strings', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'k"y',
      dataCollectionState: 'PENDING',
    });
    expect(code).toContain('\\"');
  });

  it('includes globalAttributes with OpenTelemetry types', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
      globalAttributes: {
        s: 'a',
        n: 3,
        f: 1.5,
        b: true,
        sa: ['x', 'y'],
      },
    });
    expect(code).toContain('AttributeValue.string("a")');
    expect(code).toContain('AttributeValue.int(3)');
    expect(code).toContain('AttributeValue.double(1.5)');
    expect(code).toContain('AttributeValue.bool(true)');
    expect(code).toContain('AttributeValue.array(AttributeArray(values:');
  });

  it('embeds ios.instrumentation into PulseSDK.initialize', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
      instrumentation: {
        screenLifecycle: { enabled: true },
        interaction: { enabled: true },
      },
    });
    expect(code).toContain('instrumentations: { config in');
    expect(code).toContain('config.screenLifecycle { $0.enabled(true) }');
    expect(code).toContain('config.interaction { $0.enabled(true) }');
  });

  it('embeds sessionReplay configure flush and scale fields', () => {
    const code = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
      instrumentation: {
        sessionReplay: {
          enabled: true,
          screenshotScale: 0.75,
          flushIntervalSeconds: 30,
          flushAt: 5,
          maxBatchSize: 20,
        },
      },
    });
    expect(code).toContain('local.screenshotScale = 0.75');
    expect(code).toContain('local.flushIntervalSeconds = 30');
    expect(code).toContain('local.flushAt = 5');
    expect(code).toContain('local.maxBatchSize = 20');
  });
});

describe('buildSwiftInstrumentationsArg', () => {
  it('returns nil for undefined, empty object, or no effective fields', () => {
    expect(buildSwiftInstrumentationsArg(undefined)).toBe('nil');
    expect(buildSwiftInstrumentationsArg({})).toBe('nil');
  });

  it('emits screenLifecycle enabled closure', () => {
    const arg = buildSwiftInstrumentationsArg({
      screenLifecycle: { enabled: false },
    });
    expect(arg).toContain('{ config in');
    expect(arg).toContain('config.screenLifecycle { $0.enabled(false) }');
  });

  it('emits simple toggles from { enabled } objects', () => {
    const arg = buildSwiftInstrumentationsArg({
      urlSession: { enabled: true },
      crash: { enabled: false },
    });
    expect(arg).toContain('config.urlSession { $0.enabled(true) }');
    expect(arg).toContain('config.crash { $0.enabled(false) }');
  });

  it('emits urlSession enabled closure', () => {
    const arg = buildSwiftInstrumentationsArg({
      urlSession: { enabled: true },
    });
    expect(arg).toContain('config.urlSession { $0.enabled(true) }');
  });

  it('emits sessions maxLifetime and shouldPersist', () => {
    const arg = buildSwiftInstrumentationsArg({
      sessions: {
        enabled: true,
        maxLifetimeSeconds: 3600,
        backgroundInactivityTimeoutSeconds: 120,
        shouldPersist: false,
      },
    });
    expect(arg).toContain('config.sessions { s in');
    expect(arg).toContain('s.maxLifetime(3600)');
    expect(arg).toContain('s.backgroundInactivityTimeout(120)');
    expect(arg).toContain('s.shouldPersist(false)');
  });
});

describe('buildSwiftConfigurationArg', () => {
  it('returns nil for empty or undefined', () => {
    expect(buildSwiftConfigurationArg(undefined)).toBe('nil');
    expect(buildSwiftConfigurationArg({})).toBe('nil');
  });
});

describe('buildObjcPulseSdkInitialization', () => {
  it('emits pulseInitialize: with nil for empty optional args', () => {
    const code = buildObjcPulseSdkInitialization({
      apiKey: 'k1',
      dataCollectionState: 'PENDING',
    });
    expect(code).toMatch(/\[PulseSDK pulseInitialize:/);
    expect(code).toContain('@"k1"');
    expect(code).toContain('@"PENDING"');
    expect(code).toContain('globalAttributes:nil');
    expect(code).toContain('configuration:nil');
    expect(code).toContain('instrumentations:nil');
  });

  it('maps globalAttributes to NSDictionary of PulseAttributeValue', () => {
    const code = buildObjcPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'ALLOWED',
      globalAttributes: { env: 'prod', n: 2 },
    });
    expect(code).toContain('NSDictionary<NSString*, PulseAttributeValue*');
    expect(code).toContain('[PulseAttributeValue string:@"prod"]');
    expect(code).toContain('[PulseAttributeValue int:2]');
  });

  it('emits instrumentation toggles for ObjC', () => {
    const code = buildObjcPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
      instrumentation: {
        crash: { enabled: true },
        urlSession: { enabled: false },
      },
    });
    expect(code).toContain('[PulseObjcInstrumentations new]');
    expect(code).toContain('crash = [PulseObjcEnabledConfig enabled]');
    expect(code).toContain('urlSession = [PulseObjcEnabledConfig disabled]');
  });

  it('emits full ios.* from resolveIosProps (app.json-style client payload)', () => {
    const appJsonStyle: PulsePluginProps = {
      apiKey: 'default-project_devkey01',
      dataCollectionState: 'ALLOWED',
      ios: {
        instrumentation: {
          urlSession: { enabled: true },
          sessions: { enabled: true },
          interaction: { enabled: true },
          location: { enabled: true },
          crash: { enabled: true },
          appLifecycle: { enabled: true },
          screenLifecycle: { enabled: true },
          appStartup: { enabled: true },
          uiKitTap: { enabled: true, captureContext: true },
        },
        configuration: {
          includeScreenAttributes: true,
          includeNetworkAttributes: true,
          includeGlobalAttributes: true,
        },
        globalAttributes: {
          string: 'value',
          numberFloatNegative: -9.05,
          boolean: true,
          emptyString: '',
          emptyArray: [],
          stringArray: ['a', 'b', 'c'],
          numberArrayMixed: [1, 2.5, 3],
          booleanArray: [true, false, true],
          null: null,
          platform: 'ios',
        },
      },
    };
    const code = buildObjcPulseSdkInitialization(resolveIosProps(appJsonStyle));
    expect(code).toContain('NSDictionary<NSString*, PulseAttributeValue*');
    expect(code).toContain('pulseRNKitConfig');
    expect(code).toContain('includeScreenAttributes = @(YES)');
    expect(code).toContain('[PulseObjcInstrumentations new]');
    expect(code).toMatch(/globalAttributes:pulseRNGlobalAttributes/);
    expect(code).toMatch(/configuration:pulseRNKitConfig/);
    expect(code).toMatch(/instrumentations:pulseRNInstCfg/);
  });
});

describe('getAppDelegatePrebuildKind', () => {
  it('prefers file extension over language', () => {
    expect(
      getAppDelegatePrebuildKind({
        path: '/ios/AppDelegate.mm',
        language: 'swift',
        contents: '',
      })
    ).toBe('objc');
  });

  it('classifies Swift AppDelegate by path or markers', () => {
    expect(
      getAppDelegatePrebuildKind({
        path: '/ios/AppDelegate.swift',
        contents: '',
      })
    ).toBe('swift');
    expect(
      getAppDelegatePrebuildKind({
        contents: EXPO_APP_DELEGATE_SNIPPET,
      })
    ).toBe('swift');
  });
});

describe('Expo AppDelegate merge (fixtures)', () => {
  it('merges ObjC Swift header and init into RN AppDelegate template', () => {
    let src = mergeContents({
      src: OBJC_APP_DELEGATE_FIXTURE,
      newSrc: PULSE_OBJC_PULSE_SWIFT_HEADER,
      tag: 'pulse-ios-objc-pulse-swift-header',
      comment: '//',
      anchor: /^#import\s+["<]AppDelegate.h[>"]\s*$/m,
      offset: 1,
    }).contents;
    src = mergeContents({
      src,
      newSrc: buildObjcPulseSdkInitialization({
        apiKey: 'k',
        dataCollectionState: 'PENDING',
      }),
      tag: 'pulse-ios-objc-pulse-initialize',
      comment: '//',
      anchor: /^\s*self\.moduleName\s*=\s*@/m,
      offset: 0,
    }).contents;
    expect(src).toContain('#import <PulseReactNativeOtel-Swift.h>');
    expect(src).toContain('[PulseSDK pulseInitialize:');
  });

  it('merges Pulse import after ReactAppDependencyProvider', () => {
    const out = mergeContents({
      src: EXPO_APP_DELEGATE_SNIPPET,
      newSrc: PULSE_IOS_IMPORT,
      tag: 'pulse-ios-pulsereactnative-import',
      comment: '//',
      anchor: /import\s+ReactAppDependencyProvider/,
      offset: 1,
    });
    expect(out.didMerge).toBe(true);
    expect(out.contents).toContain('import PulseReactNativeOtel');
  });

  it('merges OpenTelemetryApi after PulseReactNativeOtel', () => {
    let src = mergeContents({
      src: EXPO_APP_DELEGATE_SNIPPET,
      newSrc: PULSE_IOS_IMPORT,
      tag: 'pulse-ios-pulsereactnative-import',
      comment: '//',
      anchor: /import\s+ReactAppDependencyProvider/,
      offset: 1,
    }).contents;
    src = mergeContents({
      src,
      newSrc: PULSE_IOS_OTEL_API_IMPORT,
      tag: 'pulse-ios-opentelemetry-api-import',
      comment: '//',
      anchor: /import\s+PulseReactNativeOtel/,
      offset: 1,
    }).contents;
    expect(src).toContain('import OpenTelemetryApi');
  });

  it('merges initialization after reactNativeFactory assignment', () => {
    const init = buildSwiftPulseSdkInitialization({
      apiKey: 'k',
      dataCollectionState: 'PENDING',
    });
    const out = mergeContents({
      src: EXPO_APP_DELEGATE_SNIPPET,
      newSrc: init,
      tag: 'pulse-ios-sdk-initialization',
      comment: '//',
      anchor: /reactNativeFactory\s*=\s*factory/,
      offset: 1,
    });
    expect(out.didMerge).toBe(true);
    expect(out.contents).toContain('PulseSDK.initialize(');
  });
});
