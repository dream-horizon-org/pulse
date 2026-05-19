/**
 * Defaults and internal Vite env overrides for IndexedDB disk buffering
 * (`PulseWebConfig.diskBuffering`). Buffering is **on by default** (Android OTel RUM parity);
 * optional `maxAgeMs` / `maxCacheSizeBytes` on the config object tune the store. Same pattern as
 * `DEFAULT_BATCH_OPTIONS` + `VITE_PULSE_BATCH_DELAY_MS` in `exporters.ts` for env-only overrides.
 */

export const DEFAULT_DISK_BUFFER_MAX_AGE_MS = 24 * 60 * 60 * 1000;
/** ~10 MiB — matches `docs/sdk-core/config-and-public-api/SPEC.md` and `.env.example` (`10485760`). */
export const DEFAULT_DISK_BUFFER_MAX_CACHE_SIZE_BYTES = 10 * 1024 * 1024;

function readEnvPositiveInt(key: string): number | undefined {
  try {
    const env = (import.meta as unknown as { env?: Record<string, string> })
      .env;
    if (!env) return undefined;
    const raw = env[key];
    if (raw === undefined || String(raw).trim() === "") return undefined;
    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed <= 0) return undefined;
    return parsed;
  } catch {
    return undefined;
  }
}

/**
 * `configValue` wins; else `VITE_PULSE_DISK_BUFFER_MAX_AGE_MS` if valid; else default.
 */
export function resolveDiskBufferMaxAgeMs(configValue?: number): number {
  if (
    configValue !== undefined &&
    Number.isFinite(configValue) &&
    configValue > 0
  ) {
    return configValue;
  }
  return (
    readEnvPositiveInt("VITE_PULSE_DISK_BUFFER_MAX_AGE_MS") ??
    DEFAULT_DISK_BUFFER_MAX_AGE_MS
  );
}

/**
 * `configValue` wins; else `VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES` if valid; else default.
 */
export function resolveDiskBufferMaxCacheSizeBytes(
  configValue?: number,
): number {
  if (
    configValue !== undefined &&
    Number.isFinite(configValue) &&
    configValue > 0
  ) {
    return configValue;
  }
  return (
    readEnvPositiveInt("VITE_PULSE_DISK_BUFFER_MAX_SIZE_BYTES") ??
    DEFAULT_DISK_BUFFER_MAX_CACHE_SIZE_BYTES
  );
}
