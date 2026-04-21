// M1: Builds the OTEL Resource with 18 static browser attributes
// (platform, browser.name, screen.resolution, timezone, etc.).
// See: web-sdk-plan/v1/01-foundation/resource.md

import { Resource } from "@opentelemetry/resources";
import type { PulseWebConfig } from "./config";
import { getOrCreateInstallationId } from "./session";
import { PulseWebSemconv } from "./semconv";
import { SDK_VERSION } from "./version";
import { parseUserAgent } from "./utils/ua-parser";

export function extractProjectId(apiKey: string): string {
  // Format: 'proj_XXXX_...' → 'proj_XXXX' (first two segments)
  const parts = apiKey.split("_");
  if (parts.length >= 2 && parts[0] === "proj") {
    return `${parts[0]}_${parts[1]}`;
  }
  return apiKey;
}

function gcd(a: number, b: number): number {
  while (b !== 0) {
    const t = b;
    b = a % b;
    a = t;
  }
  return a;
}

export function computeAspectRatio(w: number, h: number): string {
  if (w === 0 || h === 0) return "0:0";
  const divisor = gcd(w, h);
  return `${w / divisor}:${h / divisor}`;
}

export function buildResource(config: PulseWebConfig): Resource {
  const parsedUA = parseUserAgent();
  const installationId = getOrCreateInstallationId();

  const R = PulseWebSemconv.ResourceKey;
  const F = PulseWebSemconv.FixedValue;
  const attrs: Record<string, string | number | boolean> = {
    [R.SERVICE_NAME]: config.serviceName,
    [R.SERVICE_VERSION]: config.serviceVersion ?? "0.0.0",
    [R.PLATFORM]: F.PLATFORM_WEB,
    [R.RUM_SDK_NAME]: F.RUM_SDK_NAME,
    [R.RUM_SDK_VERSION]: SDK_VERSION,
    [R.INSTALLATION_ID]: installationId,
    [R.PROJECT_ID]: extractProjectId(config.apiKey),
    [R.BROWSER_NAME]: parsedUA.browserName,
    [R.BROWSER_VERSION]: parsedUA.browserVersion,
    [R.OS_NAME]: parsedUA.osName,
    [R.OS_VERSION]: parsedUA.osVersion,
    [R.DEVICE_TYPE]: parsedUA.deviceType,
  };

  if (typeof window !== "undefined") {
    if (typeof screen !== "undefined") {
      const w = screen.width ?? 0;
      const h = screen.height ?? 0;
      attrs[R.SCREEN_RESOLUTION] = `${w}x${h}`;
      attrs[R.SCREEN_ASPECT_RATIO] = computeAspectRatio(w, h);
      attrs[R.SCREEN_COLOR_DEPTH] = screen.colorDepth ?? 0;
    }

    if (typeof navigator !== "undefined") {
      attrs[R.BROWSER_LANGUAGE] = navigator.language ?? "";
      attrs[R.NETWORK_ONLINE] = navigator.onLine ?? true;
    }

    try {
      attrs[R.TIMEZONE] =
        Intl.DateTimeFormat().resolvedOptions().timeZone ?? "";
    } catch {
      attrs[R.TIMEZONE] = "";
    }
  }

  return new Resource(attrs);
}
