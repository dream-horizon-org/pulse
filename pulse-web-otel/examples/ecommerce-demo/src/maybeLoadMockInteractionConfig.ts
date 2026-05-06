/**
 * When VITE_PULSE_MOCK_INTERACTION_CONFIG=true, fetches a mock interaction
 * config list into localStorage before React mounts so
 * InteractionConfigFetcher.loadFromCache() starts with deterministic flows.
 *
 * Background refresh still calls the runtime interaction-config endpoint.
 * If that endpoint returns valid data, it can replace this mock cache later.
 */

const STORAGE_KEY = "pulse_interaction_config";

function mockInteractionConfigPath(): string {
  const raw = import.meta.env["VITE_PULSE_MOCK_INTERACTION_CONFIG_PATH"] as
    | string
    | undefined;
  const path = (
    raw && raw.trim() !== "" ? raw : "/interaction-config.mock.json"
  ).trim();
  return path.startsWith("/") ? path : `/${path}`;
}

export async function maybeLoadMockInteractionConfig(): Promise<void> {
  if (import.meta.env["VITE_PULSE_MOCK_INTERACTION_CONFIG"] !== "true") return;

  const path = mockInteractionConfigPath();
  const res = await fetch(path, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(
      `[ecommerce-demo] VITE_PULSE_MOCK_INTERACTION_CONFIG=true but fetch ${path} failed: ${res.status}`,
    );
  }
  const text = await res.text();
  try {
    localStorage.setItem(STORAGE_KEY, text);
  } catch (e) {
    console.warn(
      "[ecommerce-demo] mock interaction config: localStorage.setItem failed",
      e,
    );
  }
}
