/** Gradle key used by AGP to skip Jetifier for matching artifacts (comma-separated patterns). */
export const PULSE_ANDROID_JETIFIER_IGNORELIST_KEY =
  'android.jetifier.ignorelist';

/** Project property that turns Jetifier on; when missing or not `true`, AGP does not run Jetifier. */
export const PULSE_ANDROID_ENABLE_JETIFIER_KEY = 'android.enableJetifier';

/** Skip jetifying Byte Buddy JARs — required when `android.enableJetifier=true` with modern Byte Buddy. */
export const PULSE_JETIFIER_IGNORE_NET_BYTEBUDDY = 'net.bytebuddy';

export type GradlePropertiesItem = {
  type: string;
  key?: string;
  value?: string;
};

/**
 * `true` if `gradle.properties` explicitly sets {@link PULSE_ANDROID_ENABLE_JETIFIER_KEY} to `true`
 * (case-insensitive). If the key is absent, Jetifier is off for typical AGP defaults — we do not treat
 * unknown `-P` / `~/.gradle` overrides as enabled here.
 */
export function isAndroidEnableJetifierTrue(
  items: readonly GradlePropertiesItem[]
): boolean {
  const entry = items.find(
    (i) => i.type === 'property' && i.key === PULSE_ANDROID_ENABLE_JETIFIER_KEY
  );
  if (!entry?.value) {
    return false;
  }
  return String(entry.value).trim().toLowerCase() === 'true';
}

/**
 * When Jetifier is enabled (`android.enableJetifier=true`), ensures `android.jetifier.ignorelist`
 * includes `net.bytebuddy` so Jetifier does not rewrite Byte Buddy artifacts (can fail with
 * unsupported class file versions or module-info issues). No-op when Jetifier is off or unset.
 */
export function mergeJetifierIgnorelistForNetByteBuddy(
  items: readonly GradlePropertiesItem[]
): GradlePropertiesItem[] {
  if (!isAndroidEnableJetifierTrue(items)) {
    return items.map((i) => ({ ...i }));
  }

  const key = PULSE_ANDROID_JETIFIER_IGNORELIST_KEY;
  const token = PULSE_JETIFIER_IGNORE_NET_BYTEBUDDY;
  const out = items.map((i) => ({ ...i }));
  const idx = out.findIndex((i) => i.type === 'property' && i.key === key);

  if (idx === -1) {
    return [...out, { type: 'property', key, value: token }];
  }

  const cur = out[idx];
  const raw = String(cur.value ?? '').trim();
  const parts = raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

  const hasByteBuddy = parts.some(
    (p) => p === token || p.endsWith('bytebuddy')
  );
  if (!hasByteBuddy) {
    const next = [...parts, token].join(',');
    out[idx] = { ...cur, value: next };
  }

  return out;
}
