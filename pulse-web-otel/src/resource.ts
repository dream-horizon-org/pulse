// M1: Builds the OTEL Resource with 18 static browser attributes
// (platform, browser.name, screen.resolution, timezone, etc.).
// See: docs/sdk-core/data-contract/SPEC.md (resource / platform)

import {
  resourceFromAttributes,
  type Resource,
} from "@opentelemetry/resources";
import type { PulseWebConfig } from "./config";
import { getOrCreateInstallationId } from "./session";
import { SDK_VERSION } from "./version";
import { parseUserAgent } from "./utils/ua-parser";
import { PulseWebSemconv } from "./semconv";
import type { PulseAttributePrimitive } from "./types/attributes";

export function extractProjectId(apiKey: string): string {
  // Format: '<project_name>-<random_id>_<api_key_portion>' → '<project_name>-<random_id>'
  // Everything before the last underscore is the project ID
  const lastUnderscoreIdx = apiKey.lastIndexOf("_");
  if (lastUnderscoreIdx > 0) {
    return apiKey.substring(0, lastUnderscoreIdx);
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

export function buildResource(
  config: PulseWebConfig,
  osVersion: string,
): Resource {
  const resourceKeys = PulseWebSemconv.ResourceKey;
  const fixedValues = PulseWebSemconv.FixedValue;
  const parsedUA = parseUserAgent();
  const installationId = getOrCreateInstallationId();

  const serviceName =
    config.serviceName ||
    (typeof window !== "undefined"
      ? window.location.hostname || "web-app"
      : "web-app");

  const serviceVersion = config.serviceVersion ?? "0.0.0";

  const attrs: Record<string, PulseAttributePrimitive> = {
    [resourceKeys.SERVICE_NAME]: serviceName,
    [resourceKeys.SERVICE_VERSION]: serviceVersion,
    [resourceKeys.APP_BUILD_NAME]: serviceVersion,
    [resourceKeys.PLATFORM]: fixedValues.PLATFORM_WEB,
    [resourceKeys.TELEMETRY_SDK_NAME]: fixedValues.TELEMETRY_SDK_NAME,
    [resourceKeys.RUM_SDK_NAME]: fixedValues.RUM_SDK_NAME,
    [resourceKeys.RUM_SDK_VERSION]: SDK_VERSION,
    [resourceKeys.INSTALLATION_ID]: installationId,
    [resourceKeys.PROJECT_ID]: extractProjectId(config.apiKey),
    [resourceKeys.BROWSER_NAME]: parsedUA.browserName,
    [resourceKeys.BROWSER_VERSION]: parsedUA.browserVersion,
    /** Coarse RUM label; materializes ClickHouse {@code Platform} on logs/traces (Android/iOS parity). */
    [resourceKeys.OS_NAME]: fixedValues.PLATFORM_WEB,
    [resourceKeys.OS_VERSION]: osVersion,
    [resourceKeys.DEVICE_TYPE]: parsedUA.deviceType,
  };

  if (typeof window !== "undefined") {
    if (typeof screen !== "undefined") {
      const w = screen.width ?? 0;
      const h = screen.height ?? 0;
      attrs[resourceKeys.SCREEN_RESOLUTION] = `${w}x${h}`;
      attrs[resourceKeys.SCREEN_ASPECT_RATIO] = computeAspectRatio(w, h);
      attrs[resourceKeys.SCREEN_COLOR_DEPTH] = screen.colorDepth ?? 0;
    }

    if (typeof navigator !== "undefined") {
      attrs[resourceKeys.BROWSER_LANGUAGE] = navigator.language ?? "";
      attrs[resourceKeys.NETWORK_ONLINE] = navigator.onLine ?? true;
    }

    try {
      attrs[resourceKeys.TIMEZONE] =
        Intl.DateTimeFormat().resolvedOptions().timeZone ?? "";
    } catch {
      attrs[resourceKeys.TIMEZONE] = "";
    }
  }

  return resourceFromAttributes(attrs);
}

/**
 * Merges optional user {@link PulseWebConfig.resourceAttributes} with {@link buildResource}.
 * In OTel JS, {@code left.merge(right)} keeps {@code right} on attribute key conflicts — so
 * **Pulse-built attributes win** over user-supplied duplicates (e.g. {@code project.id},
 * {@code telemetry.sdk.name}, {@code rum.sdk.name}, {@code platform}, {@code app.build_name},
 * {@code service.version}).
 */
export function buildMergedResource(
  config: PulseWebConfig,
  osVersion: string,
): Resource {
  const userLayer = resourceFromAttributes(config.resourceAttributes ?? {});
  const pulseLayer = buildResource(config, osVersion);
  return userLayer.merge(pulseLayer);
}
