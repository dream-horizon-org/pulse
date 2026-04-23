/**
 * When VITE_PULSE_MOCK_SDK_CONFIG=true, fetches the mock JSON into localStorage
 * before React mounts so {@code SdkConfigFetcher.loadCached()} and export gates
 * see the same config as Playwright {@code seedPulseSdkConfig}.
 *
 * Background refresh still calls the real {@code /v1/configs/active/} URL
 * (gates are not rebuilt mid-session). For strict mock-only fetches, block
 * that route in Playwright or accept a one-time server merge into localStorage.
 */

const STORAGE_KEY = "pulse_sdk_config";

function mockConfigPath(): string {
  const raw = import.meta.env["VITE_PULSE_MOCK_SDK_CONFIG_PATH"] as
    | string
    | undefined;
  const path = (
    raw && raw.trim() !== "" ? raw : "/pulse-sdk-config.mock.json"
  ).trim();
  return path.startsWith("/") ? path : `/${path}`;
}

export function mockSdkConfigAbsoluteUrl(): string {
  if (typeof window === "undefined") return "";
  return new URL(mockConfigPath(), window.location.origin).href;
}

export async function maybeLoadMockPulseSdkConfig(): Promise<void> {
  if (import.meta.env["VITE_PULSE_MOCK_SDK_CONFIG"] !== "true") return;

  const path = mockConfigPath();
  const res = await fetch(path, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(
      `[ecommerce-demo] VITE_PULSE_MOCK_SDK_CONFIG=true but fetch ${path} failed: ${res.status}`,
    );
  }
  const text = await res.text();
  try {
    localStorage.setItem(STORAGE_KEY, text);
  } catch (e) {
    console.warn(
      "[ecommerce-demo] mock SDK config: localStorage.setItem failed",
      e,
    );
  }
}
