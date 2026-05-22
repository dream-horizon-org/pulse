/**
 * Helpers for E2E tests that need a specific merged PulseSdkConfig before Pulse.init() runs.
 * The SDK reads localStorage["pulse_sdk_config"] synchronously in SdkConfigFetcher.loadCached();
 * background fetch does not rebuild ExportSamplingGate / FeatureGate, so seeding storage is the
 * reliable way to drive export-time sampling and feature gates in Playwright.
 *
 * Always pair seedPulseSdkConfig with attachSdkConfigStub (already wired in the fixture) so
 * a lower version from the wire does not overwrite the seeded config.
 */

import type { Page } from "@playwright/test";

/** Must match SDK_CONFIG_CACHE_KEY in pulse-web-otel remote-config.ts. */
export const PULSE_SDK_CONFIG_STORAGE_KEY = "pulse_sdk_config";

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
    scheduleDurationMs: 5000,
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

/** Intercept the interaction-config fetch and return a custom payload.
 *  Routes both the local backend URL (/v1/interaction-configs/) and the
 *  production CDN URL (/config/projects/*\/interaction-config.json) so the
 *  same helper works with any apiKey format used in the demo.
 */
export async function seedInteractionConfig(
  page: Page,
  payload: unknown,
): Promise<void> {
  const fulfill = async (route: import("@playwright/test").Route): Promise<void> => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(payload),
    });
  };
  await page.route("**/v1/interaction-configs/", fulfill);
  await page.route("**/config/projects/*/interaction-config.json", fulfill);
}

/** Seed localStorage before navigation so loadCached() sees this config. */
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
