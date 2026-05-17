import type { GradlePropertiesItem } from './androidJetifierGradlePropertiesMerge';

/**
 * Project property the SDK's `android/build.gradle` reads to opt in to the Kotlin-1.9 compat
 * dependency cap. When `true`, transitive Kotlin runtime artifacts (stdlib, coroutines,
 * serialization) are capped below the version whose `.kotlin_module` metadata a 1.9.x compiler
 * cannot read. Default behavior in the SDK is OFF; consumers on Kotlin 2.0+ should not set it.
 */
export const PULSE_RN_KOTLIN19_COMPAT_KEY =
  'PulseReactNativeOtel_kotlin19Compat';

/**
 * Idempotent merge into `gradle.properties`.
 *
 * - `enabled === true`  → upsert `PulseReactNativeOtel_kotlin19Compat=true`.
 * - `enabled === false` → remove the key entirely (so toggling the plugin prop off cleans up the
 *   file rather than leaving a stale `=true`/`=false` entry).
 *
 * Returns a new array; input is not mutated.
 */
export function mergeKotlin19CompatFlag(
  items: readonly GradlePropertiesItem[],
  enabled: boolean
): GradlePropertiesItem[] {
  const out = items.map((i) => ({ ...i }));
  const idx = out.findIndex(
    (i) => i.type === 'property' && i.key === PULSE_RN_KOTLIN19_COMPAT_KEY
  );

  if (enabled) {
    if (idx === -1) {
      return [
        ...out,
        { type: 'property', key: PULSE_RN_KOTLIN19_COMPAT_KEY, value: 'true' },
      ];
    }
    const cur = out[idx];
    if (cur && cur.value !== 'true') {
      out[idx] = { ...cur, value: 'true' };
    }
    return out;
  }

  if (idx === -1) {
    return out;
  }
  out.splice(idx, 1);
  return out;
}
