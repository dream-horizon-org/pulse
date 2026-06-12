/**
 * Defaults for trace/log batch processors and metric export interval (`createProviders`).
 *
 * `scheduledDelayMillis` can be overridden by the internal env var
 * `VITE_PULSE_BATCH_DELAY_MS` — used only in E2E test environments to speed up
 * signal flushing. This is NOT a public config field.
 */

function resolveScheduledDelay(): number {
  // Internal test override — not part of public PulseWebConfig.
  // Vite (ecommerce-demo): VITE_PULSE_BATCH_DELAY_MS via import.meta.env
  // Next.js (nextjs-demo): NEXT_PUBLIC_PULSE_BATCH_DELAY_MS via process.env (baked at build time)
  try {
    const env = (import.meta as unknown as { env?: Record<string, string> })
      .env;
    if (env) {
      const override =
        env["VITE_PULSE_BATCH_DELAY_MS"] ??
        env["NEXT_PUBLIC_PULSE_BATCH_DELAY_MS"];
      if (override) {
        const parsed = Number(override);
        if (!isNaN(parsed) && parsed > 0) return parsed;
      }
    }
  } catch {
    // import.meta.env not available (e.g. Node / Jest)
  }
  // Next.js webpack inlines process.env.NEXT_PUBLIC_* at build time
  try {
    if (
      typeof process !== "undefined" &&
      process.env["NEXT_PUBLIC_PULSE_BATCH_DELAY_MS"]
    ) {
      const parsed = Number(process.env["NEXT_PUBLIC_PULSE_BATCH_DELAY_MS"]);
      if (!isNaN(parsed) && parsed > 0) return parsed;
    }
  } catch {
    // process not available
  }
  return 5000;
}

export const DEFAULT_BATCH_OPTIONS = {
  scheduledDelayMillis: resolveScheduledDelay(),
  maxQueueSize: 2048,
  maxExportBatchSize: 512,
  exportTimeoutMillis: 30000,
};
