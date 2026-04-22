/**
 * Helpers for E2E that need a specific merged {@link PulseSdkConfig} **before**
 * {@code PulseWeb.start()} runs. The SDK reads {@code localStorage.pulse_sdk_config}
 * synchronously in {@code SdkConfigFetcher.loadCached()}; background fetch does not
 * rebuild {@code ExportSamplingGate} / {@code FeatureGate}, so seeding storage is the
 * reliable way to drive export-time sampling and {@code signals.*} in Playwright.
 *
 * Always block or lose the active-config HTTP fetch in tests that seed storage, so a
 * lower version from the wire does not overwrite the seeded config.
 */

import type { Page } from "@playwright/test";

/** Must match {@code SDK_CONFIG_CACHE_KEY} in pulse-web-otel {@code remote-config.ts}. */
export const PULSE_SDK_CONFIG_STORAGE_KEY = "pulse_sdk_config";

/**
 * Must match {@code signals.scheduleDurationMs} in {@link minimalPulseSdkConfig} default
 * {@code signals}. Tests that need to wait past the first logs batch should use
 * {@link waitPastSeededSignalsBatchWindow}.
 */
export const MINIMAL_SIGNAL_SCHEDULE_DURATION_MS = 5000;

/**
 * WHITELIST entries so the ecommerce demo can boot and emit core lifecycle + sdk.init when
 * {@code signals.filters.mode} is {@code WHITELIST}. Keep in sync with demo-required signals.
 */
export function demoE2eWhitelistFilterValues(): Array<{
  name: string;
  props: unknown[];
  scopes: string[];
  sdks: string[];
}> {
  return [
    {
      name: "^session\\.start$",
      props: [],
      scopes: ["logs"],
      sdks: ["pulse_web_js"],
    },
    {
      name: "^session\\.end$",
      props: [],
      scopes: ["logs"],
      sdks: ["pulse_web_js"],
    },
    {
      name: "^sdk\\.init$",
      props: [],
      scopes: ["traces"],
      sdks: ["pulse_web_js"],
    },
  ];
}

/** Wait past one default seeded batch window so log exports can flush. */
export async function waitPastSeededSignalsBatchWindow(
  page: Page,
): Promise<void> {
  await page.waitForTimeout(MINIMAL_SIGNAL_SCHEDULE_DURATION_MS + 750);
}

/** Valid {@code PulseSdkConfig} shape for {@code isValidSdkConfig} + {@code mergePulseSdkConfig}. */
export type PulseSdkConfigSeed = Record<string, unknown>;

export function minimalPulseSdkConfig(
  overrides: Partial<{
    version: number;
    description: string;
    sampling: Record<string, unknown>;
    signals: Record<string, unknown>;
    features: unknown[];
  }> = {},
): PulseSdkConfigSeed {
  const {
    version = 900,
    description = "e2e-seeded",
    sampling: samplingOverride,
    signals: signalsOverride,
    features: featuresOverride,
  } = overrides;

  const sampling = {
    default: { sessionSampleRate: 1.0 },
    rules: [] as unknown[],
    signalsToSample: [] as unknown[],
    ...(samplingOverride ?? {}),
  };

  const signals = {
    scheduleDurationMs: MINIMAL_SIGNAL_SCHEDULE_DURATION_MS,
    attributesToDrop: [] as unknown[],
    attributesToAdd: [] as unknown[],
    filters: { mode: "BLACKLIST", values: [] as unknown[] },
    metricsToAdd: [] as unknown[],
    ...(signalsOverride ?? {}),
  };

  return {
    version,
    description,
    sampling,
    signals,
    interaction: { beforeInitQueueSize: 5000 },
    features: featuresOverride ?? [],
  };
}

/** Seed localStorage before navigation so {@code loadCached()} sees this config. */
export async function seedPulseSdkConfig(
  page: Page,
  config: PulseSdkConfigSeed,
): Promise<void> {
  const json = JSON.stringify(config);
  await page.addInitScript(
    ({ key, payload }: { key: string; payload: string }) => {
      try {
        localStorage.setItem(key, payload);
      } catch {
        /* ignore */
      }
    },
    { key: PULSE_SDK_CONFIG_STORAGE_KEY, payload: json },
  );
}

/** Block active-config fetch so seeded localStorage is not overwritten. */
export async function blockActiveConfigFetch(page: Page): Promise<void> {
  await page.route("**/v1/configs/active/**", (route) =>
    route.fulfill({ status: 404, body: "{}" }),
  );
  await page.route("**/v1/configs/active", (route) =>
    route.fulfill({ status: 404, body: "{}" }),
  );
}
