import { PluginError } from '@expo/config-plugins';

import {
  assertPulsePluginProps,
  resolveAndroidProps,
  resolveIosProps,
} from '../resolvePluginProps';
import type { PulsePluginProps } from '../types';

describe('assertPulsePluginProps', () => {
  it('accepts top-level defaults only', () => {
    expect(() =>
      assertPulsePluginProps({ endpointBaseUrl: 'http://x', apiKey: 'k' })
    ).not.toThrow();
  });

  it('accepts per-platform overrides when top-level endpoint + apiKey are set', () => {
    expect(() =>
      assertPulsePluginProps({
        endpointBaseUrl: 'http://default',
        apiKey: 'default-key',
        android: { endpointBaseUrl: 'http://a', apiKey: 'ka' },
        ios: { endpointBaseUrl: 'http://i', apiKey: 'ki' },
      })
    ).not.toThrow();
  });

  it('rejects missing top-level endpointBaseUrl', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'k',
        android: { endpointBaseUrl: 'http://a', apiKey: 'ka' },
        ios: { endpointBaseUrl: 'http://i', apiKey: 'ki' },
      } as PulsePluginProps)
    ).toThrow(/endpointBaseUrl/);
  });

  it('rejects blank top-level apiKey', () => {
    expect(() =>
      assertPulsePluginProps({
        endpointBaseUrl: 'http://x',
        apiKey: '   ',
      })
    ).toThrow(/apiKey/);
  });

  it('rejects configuration at top level', () => {
    expect(() =>
      assertPulsePluginProps({
        endpointBaseUrl: 'http://x',
        apiKey: 'k',
        configuration: {},
      })
    ).toThrow(/configuration/);
  });

  it('rejects globalAttributes at top level', () => {
    expect(() =>
      assertPulsePluginProps({
        endpointBaseUrl: 'http://x',
        apiKey: 'k',
        globalAttributes: {},
      })
    ).toThrow(/globalAttributes/);
  });

  it('rejects instrumentation at top level', () => {
    expect(() =>
      assertPulsePluginProps({
        endpointBaseUrl: 'http://x',
        apiKey: 'k',
        instrumentation: {},
      })
    ).toThrow(/instrumentation/);
  });

  it('rejects non-object android', () => {
    expect(() =>
      assertPulsePluginProps({
        endpointBaseUrl: 'http://x',
        apiKey: 'k',
        android: 'bad',
      })
    ).toThrow(/android/);
  });
});

describe('resolveAndroidProps / resolveIosProps', () => {
  it('merges top-level defaults with platform overrides', () => {
    const props: PulsePluginProps = {
      endpointBaseUrl: 'http://default',
      apiKey: 'key',
      dataCollectionState: 'PENDING',
      android: {
        endpointBaseUrl: 'http://android',
        globalAttributes: { p: 'a' },
        instrumentation: { activity: { enabled: true } },
      },
      ios: {
        globalAttributes: { p: 'i' },
        configuration: { includeScreenAttributes: false },
        instrumentation: { screenLifecycle: { enabled: true } },
      },
    };
    const a = resolveAndroidProps(props);
    expect(a.endpointBaseUrl).toBe('http://android');
    expect(a.apiKey).toBe('key');
    expect(a.dataCollectionState).toBe('PENDING');
    expect(a.globalAttributes).toEqual({ p: 'a' });
    expect(a.instrumentation).toEqual({ activity: { enabled: true } });

    const i = resolveIosProps(props);
    expect(i.endpointBaseUrl).toBe('http://default');
    expect(i.apiKey).toBe('key');
    expect(i.globalAttributes).toEqual({ p: 'i' });
    expect(i.configuration).toEqual({ includeScreenAttributes: false });
    expect(i.instrumentation).toEqual({
      screenLifecycle: { enabled: true },
    });
  });

  it('merges customEventCollectorUrl with ios override', () => {
    const props: PulsePluginProps = {
      endpointBaseUrl: 'http://d',
      apiKey: 'k',
      customEventCollectorUrl: 'http://root/v1/logs',
      ios: { customEventCollectorUrl: 'http://ios/v1/logs' },
    };
    expect(resolveIosProps(props).customEventCollectorUrl).toBe(
      'http://ios/v1/logs'
    );
    expect(resolveAndroidProps(props).customEventCollectorUrl).toBe(
      'http://root/v1/logs'
    );
  });

  it('throws when merge leaves android without keys', () => {
    expect(() =>
      resolveAndroidProps({
        ios: { endpointBaseUrl: 'http://i', apiKey: 'ki' },
      } as PulsePluginProps)
    ).toThrow(PluginError);
  });

  it('throws when merge leaves ios without keys', () => {
    expect(() =>
      resolveIosProps({
        android: { endpointBaseUrl: 'http://a', apiKey: 'ka' },
      } as PulsePluginProps)
    ).toThrow(PluginError);
  });
});
