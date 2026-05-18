import type { GradlePropertiesItem } from '../androidJetifierGradlePropertiesMerge';
import {
  mergeKotlin19CompatFlag,
  PULSE_RN_KOTLIN19_COMPAT_KEY,
} from '../androidKotlin19CompatGradlePropertiesMerge';

const flag = (value: string): GradlePropertiesItem => ({
  type: 'property',
  key: PULSE_RN_KOTLIN19_COMPAT_KEY,
  value,
});

describe('mergeKotlin19CompatFlag', () => {
  it('appends the flag when absent and enabled', () => {
    const out = mergeKotlin19CompatFlag([], true);
    expect(out).toEqual([flag('true')]);
  });

  it('is idempotent when the flag is already present and enabled', () => {
    const input = [flag('true')];
    const out = mergeKotlin19CompatFlag(input, true);
    expect(out).toEqual([flag('true')]);
    expect(out).not.toBe(input);
  });

  it('upgrades stale value to true when enabled', () => {
    const out = mergeKotlin19CompatFlag([flag('false')], true);
    expect(out).toEqual([flag('true')]);
  });

  it('removes the flag when disabled and present', () => {
    const out = mergeKotlin19CompatFlag([flag('true')], false);
    expect(out).toEqual([]);
  });

  it('is a no-op when disabled and absent', () => {
    const out = mergeKotlin19CompatFlag([], false);
    expect(out).toEqual([]);
  });

  it('preserves other entries and ordering', () => {
    const other: GradlePropertiesItem = {
      type: 'property',
      key: 'android.useAndroidX',
      value: 'true',
    };
    const out = mergeKotlin19CompatFlag([other], true);
    expect(out).toEqual([other, flag('true')]);
  });

  it('does not mutate the input array', () => {
    const input: GradlePropertiesItem[] = [flag('true')];
    const snapshot = JSON.parse(JSON.stringify(input));
    mergeKotlin19CompatFlag(input, false);
    expect(input).toEqual(snapshot);
  });
});
