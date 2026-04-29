import {
  isAndroidEnableJetifierTrue,
  mergeJetifierIgnorelistForNetByteBuddy,
  PULSE_ANDROID_ENABLE_JETIFIER_KEY,
  PULSE_ANDROID_JETIFIER_IGNORELIST_KEY,
  PULSE_JETIFIER_IGNORE_NET_BYTEBUDDY,
} from '../androidJetifierGradlePropertiesMerge';

describe('isAndroidEnableJetifierTrue', () => {
  it('is false when key missing', () => {
    expect(isAndroidEnableJetifierTrue([])).toBe(false);
  });

  it('is true for true (any case)', () => {
    expect(
      isAndroidEnableJetifierTrue([
        {
          type: 'property',
          key: PULSE_ANDROID_ENABLE_JETIFIER_KEY,
          value: 'TRUE',
        },
      ])
    ).toBe(true);
  });

  it('is false for false', () => {
    expect(
      isAndroidEnableJetifierTrue([
        {
          type: 'property',
          key: PULSE_ANDROID_ENABLE_JETIFIER_KEY,
          value: 'false',
        },
      ])
    ).toBe(false);
  });
});

describe('mergeJetifierIgnorelistForNetByteBuddy', () => {
  const jetifierOn = {
    type: 'property' as const,
    key: PULSE_ANDROID_ENABLE_JETIFIER_KEY,
    value: 'true',
  };

  it('no-op when enableJetifier is unset', () => {
    const out = mergeJetifierIgnorelistForNetByteBuddy([]);
    expect(out).toEqual([]);
  });

  it('no-op when enableJetifier is false', () => {
    const initial = [
      {
        type: 'property',
        key: PULSE_ANDROID_ENABLE_JETIFIER_KEY,
        value: 'false',
      },
      {
        type: 'property',
        key: PULSE_ANDROID_JETIFIER_IGNORELIST_KEY,
        value: 'foo',
      },
    ];
    const out = mergeJetifierIgnorelistForNetByteBuddy(initial);
    expect(out).toEqual([
      {
        type: 'property',
        key: PULSE_ANDROID_ENABLE_JETIFIER_KEY,
        value: 'false',
      },
      {
        type: 'property',
        key: PULSE_ANDROID_JETIFIER_IGNORELIST_KEY,
        value: 'foo',
      },
    ]);
  });

  it('appends ignorelist when missing and jetifier on', () => {
    const out = mergeJetifierIgnorelistForNetByteBuddy([jetifierOn]);
    expect(out).toContainEqual({
      type: 'property',
      key: PULSE_ANDROID_JETIFIER_IGNORELIST_KEY,
      value: PULSE_JETIFIER_IGNORE_NET_BYTEBUDDY,
    });
  });

  it('appends token to existing comma-separated list when jetifier on', () => {
    const out = mergeJetifierIgnorelistForNetByteBuddy([
      jetifierOn,
      {
        type: 'property',
        key: PULSE_ANDROID_JETIFIER_IGNORELIST_KEY,
        value: 'foo',
      },
    ]);
    expect(out).toHaveLength(2);
    const ignoreRow = out.find(
      (i) => i.key === PULSE_ANDROID_JETIFIER_IGNORELIST_KEY
    );
    expect(ignoreRow?.value).toBe('foo,net.bytebuddy');
  });

  it('does not duplicate net.bytebuddy when jetifier on', () => {
    const initial = [
      jetifierOn,
      {
        type: 'property',
        key: PULSE_ANDROID_JETIFIER_IGNORELIST_KEY,
        value: 'net.bytebuddy',
      },
    ];
    const out = mergeJetifierIgnorelistForNetByteBuddy(initial);
    expect(out).toEqual(initial.map((i) => ({ ...i })));
  });

  it('preserves other properties when jetifier on', () => {
    const out = mergeJetifierIgnorelistForNetByteBuddy([
      jetifierOn,
      { type: 'property', key: 'android.useAndroidX', value: 'true' },
    ]);
    expect(out).toHaveLength(3);
    expect(out).toContainEqual({
      type: 'property',
      key: 'android.useAndroidX',
      value: 'true',
    });
  });
});
