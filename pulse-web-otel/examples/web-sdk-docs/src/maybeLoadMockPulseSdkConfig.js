/**
 * When VITE_PULSE_MOCK_SDK_CONFIG=true, fetches mock JSON into localStorage before
 * Pulse.start so SdkConfigFetcher matches ecommerce-demo / Playwright patterns.
 */

const STORAGE_KEY = "pulse_sdk_config";

function mockConfigPath() {
  const raw = import.meta.env["VITE_PULSE_MOCK_SDK_CONFIG_PATH"];
  const path =
    raw && String(raw).trim() !== ""
      ? String(raw).trim()
      : "/pulse-sdk-config.mock.json";
  return path.startsWith("/") ? path : `/${path}`;
}

export async function maybeLoadMockPulseSdkConfig() {
  if (import.meta.env["VITE_PULSE_MOCK_SDK_CONFIG"] !== "true") return;

  const path = mockConfigPath();
  const res = await fetch(path, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(
      `[web-sdk-docs] VITE_PULSE_MOCK_SDK_CONFIG=true but fetch ${path} failed: ${res.status}`,
    );
  }
  const text = await res.text();
  try {
    localStorage.setItem(STORAGE_KEY, text);
  } catch (e) {
    console.warn(
      "[web-sdk-docs] mock SDK config: localStorage.setItem failed",
      e,
    );
  }
}
