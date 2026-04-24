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
      assertPulsePluginProps({
        apiKey: 'k',
        dataCollectionState: 'PENDING',
      })
    ).not.toThrow();
  });

  it('accepts per-platform overrides when top-level apiKey is set', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'default-key',
        dataCollectionState: 'PENDING',
        android: { apiKey: 'ka' },
        ios: { apiKey: 'ki' },
      })
    ).not.toThrow();
  });

  it('rejects missing top-level apiKey', () => {
    expect(() =>
      assertPulsePluginProps({
        dataCollectionState: 'PENDING',
        android: { apiKey: 'ka' },
        ios: { apiKey: 'ki' },
      } as PulsePluginProps)
    ).toThrow(/apiKey/);
  });

  it('rejects blank top-level apiKey', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: '   ',
        dataCollectionState: 'PENDING',
      })
    ).toThrow(/apiKey/);
  });

  it('rejects invalid top-level dataCollectionState', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'k',
        dataCollectionState: 'maybe',
      } as unknown as PulsePluginProps)
    ).toThrow(/dataCollectionState/);
  });

  it('rejects configuration at top level', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'k',
        dataCollectionState: 'PENDING',
        configuration: {},
      })
    ).toThrow(/configuration/);
  });

  it('rejects globalAttributes at top level', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'k',
        dataCollectionState: 'PENDING',
        globalAttributes: {},
      })
    ).toThrow(/globalAttributes/);
  });

  it('rejects instrumentation at top level', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'k',
        dataCollectionState: 'PENDING',
        instrumentation: {},
      })
    ).toThrow(/instrumentation/);
  });

  it('rejects non-object android', () => {
    expect(() =>
      assertPulsePluginProps({
        apiKey: 'k',
        dataCollectionState: 'PENDING',
        android: 'bad',
      })
    ).toThrow(/android/);
  });
});

describe('resolveAndroidProps / resolveIosProps', () => {
  it('merges top-level defaults with platform overrides', () => {
    const props: PulsePluginProps = {
      apiKey: 'key',
      dataCollectionState: 'PENDING',
      android: {
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
    expect(a.apiKey).toBe('key');
    expect(a.dataCollectionState).toBe('PENDING');
    expect(a.globalAttributes).toEqual({ p: 'a' });
    expect(a.instrumentation).toEqual({ activity: { enabled: true } });
    expect(a.coreLibraryDesugaring).toEqual({
      enabled: false,
      version: '2.1.4',
    });

    const i = resolveIosProps(props);
    expect(i.apiKey).toBe('key');
    expect(i.globalAttributes).toEqual({ p: 'i' });
    expect(i.configuration).toEqual({ includeScreenAttributes: false });
    expect(i.instrumentation).toEqual({
      screenLifecycle: { enabled: true },
    });
  });

  it('throws when merge leaves android without apiKey', () => {
    expect(() =>
      resolveAndroidProps({
        ios: { apiKey: 'ki' },
      } as PulsePluginProps)
    ).toThrow(PluginError);
  });

  it('throws when merge leaves ios without apiKey', () => {
    expect(() =>
      resolveIosProps({
        android: { apiKey: 'ka' },
      } as PulsePluginProps)
    ).toThrow(PluginError);
  });

  it('uses android.coreLibraryDesugaring when enabled with optional version', () => {
    const props: PulsePluginProps = {
      apiKey: 'key',
      dataCollectionState: 'PENDING',
      android: { coreLibraryDesugaring: { enabled: true, version: '2.0.4' } },
    };
    expect(resolveAndroidProps(props).coreLibraryDesugaring).toEqual({
      enabled: true,
      version: '2.0.4',
    });
  });

  it('defaults desugar version when enabled without version', () => {
    const props: PulsePluginProps = {
      apiKey: 'key',
      dataCollectionState: 'PENDING',
      android: { coreLibraryDesugaring: { enabled: true } },
    };
    expect(resolveAndroidProps(props).coreLibraryDesugaring).toEqual({
      enabled: true,
      version: '2.1.4',
    });
  });
});
