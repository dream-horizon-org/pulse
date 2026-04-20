import { mergeContents } from '@expo/config-plugins/build/utils/generateCode';

import {
  PULSE_IOS_IMPORT,
  PULSE_IOS_OTEL_API_IMPORT,
  buildSwiftConfigurationArg,
  buildSwiftInstrumentationsArg,
  buildSwiftPulseSdkInitialization,
} from '../iosCodegen';

const EXPO_APP_DELEGATE_SNIPPET = `
import React
import ReactAppDependencyProvider

 reactNativeFactory = factory

#if os(iOS)
`;

describe('buildSwiftPulseSdkInitialization', () => {
  it('generates minimal call with defaults', () => {
    const code = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://127.0.0.1:4318',
      apiKey: 'key_1',
    });
    expect(code).toContain('PulseSDK.initialize(');
    expect(code).toContain('endpointBaseUrl: "http://127.0.0.1:4318"');
    expect(code).toContain('apiKey: "key_1"');
    expect(code).toContain('dataCollectionState: .pending');
    expect(code).toContain('configEndpointUrl: nil');
    expect(code).toContain('endpointHeaders: nil');
    expect(code).toContain('globalAttributes: nil');
    expect(code).toContain('configuration: nil');
    expect(code).toContain('instrumentations: nil');
  });

  it('embeds ios.configuration into PulseKitConfiguration closure', () => {
    const code = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://x',
      apiKey: 'k',
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
      endpointBaseUrl: 'http://x',
      apiKey: 'k',
      dataCollectionState: 'ALLOWED',
    });
    expect(allowed).toContain('dataCollectionState: .allowed');

    const denied = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://x',
      apiKey: 'k',
      dataCollectionState: 'DENIED',
    });
    expect(denied).toContain('dataCollectionState: .denied');
  });

  it('escapes quotes in strings', () => {
    const code = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://host/path?q="x"',
      apiKey: 'k"y',
    });
    expect(code).toContain('\\"');
  });

  it('includes configEndpointUrl, customEventCollectorUrl, and endpointHeaders', () => {
    const code = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://localhost:4318',
      apiKey: 'p',
      configEndpointUrl: 'http://localhost:8080/v1/configs/active/',
      customEventCollectorUrl: 'http://localhost:4318/v1/logs',
      endpointHeaders: { 'X-Custom': '1' },
    });
    expect(code).toContain(
      'configEndpointUrl: "http://localhost:8080/v1/configs/active/"'
    );
    expect(code).toContain(
      'customEventCollectorUrl: "http://localhost:4318/v1/logs"'
    );
    expect(code).toContain('"X-Custom": "1"');
  });

  it('includes globalAttributes with OpenTelemetry types', () => {
    const code = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://x',
      apiKey: 'k',
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
      endpointBaseUrl: 'http://x',
      apiKey: 'k',
      instrumentation: {
        screenLifecycle: { enabled: true },
        interaction: { enabled: true, configUrl: 'https://cfg.example/v1' },
      },
    });
    expect(code).toContain('instrumentations: { config in');
    expect(code).toContain('config.screenLifecycle { $0.enabled(true) }');
    expect(code).toContain(
      'config.interaction { $0.enabled(true); $0.setConfigUrl { "https://cfg.example/v1" } }'
    );
  });

  it('embeds sessionReplay configure flush and scale fields', () => {
    const code = buildSwiftPulseSdkInitialization({
      endpointBaseUrl: 'http://x',
      apiKey: 'k',
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
  const base = 'http://otel.example';

  it('returns nil for undefined, empty object, or no effective fields', () => {
    expect(buildSwiftInstrumentationsArg(undefined, base)).toBe('nil');
    expect(buildSwiftInstrumentationsArg({}, base)).toBe('nil');
  });

  it('emits screenLifecycle enabled closure', () => {
    const arg = buildSwiftInstrumentationsArg(
      {
        screenLifecycle: { enabled: false },
      },
      base
    );
    expect(arg).toContain('{ config in');
    expect(arg).toContain('config.screenLifecycle { $0.enabled(false) }');
  });

  it('emits simple toggles from { enabled } objects', () => {
    const arg = buildSwiftInstrumentationsArg(
      {
        urlSession: { enabled: true },
        crash: { enabled: false },
      },
      base
    );
    expect(arg).toContain('config.urlSession { $0.enabled(true) }');
    expect(arg).toContain('config.crash { $0.enabled(false) }');
  });

  it('emits urlSession excludeOtlpEndpoints with merged base URL', () => {
    const arg = buildSwiftInstrumentationsArg(
      {
        urlSession: { enabled: true, excludeOtlpEndpoints: true },
      },
      'http://127.0.0.1:4318'
    );
    expect(arg).toContain('config.urlSession { u in');
    expect(arg).toContain('u.enabled(true)');
    expect(arg).toContain(
      'u.excludeOtlpEndpoints(baseUrl: "http://127.0.0.1:4318")'
    );
  });

  it('emits sessions maxLifetime and shouldPersist', () => {
    const arg = buildSwiftInstrumentationsArg(
      {
        sessions: {
          enabled: true,
          maxLifetimeSeconds: 3600,
          backgroundInactivityTimeoutSeconds: 120,
          shouldPersist: false,
        },
      },
      base
    );
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

describe('Expo AppDelegate merge (fixtures)', () => {
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
      endpointBaseUrl: 'http://127.0.0.1:4318',
      apiKey: 'k',
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
