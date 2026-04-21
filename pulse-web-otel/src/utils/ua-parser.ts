// M1: Parses User-Agent string + navigator.userAgentData (Client Hints API)
// to extract browser.name, browser.version, os.name, device.type.

import type { ParsedUA } from "../types/ua";

export type { ParsedUA } from "../types/ua";

interface NavigatorUAData {
  brands?: Array<{ brand: string; version: string }>;
  mobile?: boolean;
  platform?: string;
  getHighEntropyValues?: (hints: string[]) => Promise<{
    brands?: Array<{ brand: string; version: string }>;
    mobile?: boolean;
    platform?: string;
    platformVersion?: string;
    uaFullVersion?: string;
  }>;
}

function parseBrowserFromUA(ua: string): { name: string; version: string } {
  // Order matters: Edge before Chrome, OPR before Chrome
  const rules: Array<{ regex: RegExp; name: string }> = [
    { regex: /Edg(?:e|A|iOS)?\/([0-9.]+)/i, name: "Edge" },
    { regex: /OPR\/([0-9.]+)/i, name: "Opera" },
    { regex: /Firefox\/([0-9.]+)/i, name: "Firefox" },
    { regex: /Chrome\/([0-9.]+)/i, name: "Chrome" },
    { regex: /Version\/([0-9.]+).*Safari/i, name: "Safari" },
    { regex: /Safari\/([0-9.]+)/i, name: "Safari" },
  ];

  for (const rule of rules) {
    const match = ua.match(rule.regex);
    if (match && match[1]) {
      return { name: rule.name, version: match[1].split(".")[0] ?? match[1] };
    }
  }

  return { name: "Unknown", version: "" };
}

function parseOSFromUA(ua: string): { name: string; version: string } {
  if (/Android ([0-9.]+)/i.test(ua)) {
    const match = ua.match(/Android ([0-9.]+)/i);
    return { name: "Android", version: match?.[1]?.split(".")[0] ?? "" };
  }
  if (/iPhone OS ([0-9_]+)/i.test(ua)) {
    const match = ua.match(/iPhone OS ([0-9_]+)/i);
    const version = match?.[1]?.replace(/_/g, ".") ?? "";
    return { name: "iOS", version: version.split(".")[0] ?? version };
  }
  if (/iPad.*OS ([0-9_]+)/i.test(ua)) {
    const match = ua.match(/iPad.*OS ([0-9_]+)/i);
    const version = match?.[1]?.replace(/_/g, ".") ?? "";
    return { name: "iOS", version: version.split(".")[0] ?? version };
  }
  if (/Windows NT ([0-9.]+)/i.test(ua)) {
    const match = ua.match(/Windows NT ([0-9.]+)/i);
    const ntVersion = match?.[1] ?? "";
    const versionMap: Record<string, string> = {
      "10.0": "10",
      "6.3": "8.1",
      "6.2": "8",
      "6.1": "7",
      "6.0": "Vista",
      "5.2": "XP",
      "5.1": "XP",
    };
    return { name: "Windows", version: versionMap[ntVersion] ?? ntVersion };
  }
  if (/Mac OS X ([0-9_]+)/i.test(ua)) {
    const match = ua.match(/Mac OS X ([0-9_]+)/i);
    const version = match?.[1]?.replace(/_/g, ".") ?? "";
    return { name: "macOS", version: version.split(".")[0] ?? version };
  }
  if (/Linux/i.test(ua)) {
    return { name: "Linux", version: "" };
  }

  return { name: "Unknown", version: "" };
}

function parseDeviceTypeFromUA(ua: string): "desktop" | "mobile" | "tablet" {
  if (/iPad|Tablet/i.test(ua)) return "tablet";
  if (/Mobi|Android/i.test(ua)) return "mobile";
  return "desktop";
}

/**
 * Enriches os.version via the Client Hints high-entropy API (Chrome 90+).
 * Returns the real major OS version (e.g. "15" for macOS Sequoia), or the
 * sync fallback if Client Hints is unavailable or times out.
 */
export async function getOsVersionAsync(syncFallback: string): Promise<string> {
  if (typeof navigator === "undefined") return syncFallback;
  const nav = navigator as Navigator & { userAgentData?: NavigatorUAData };
  const fn = nav.userAgentData?.getHighEntropyValues;
  if (typeof fn !== "function") return syncFallback;
  try {
    const result = (await Promise.race([
      fn.call(nav.userAgentData, ["platformVersion"]),
      new Promise<null>((r) => setTimeout(() => r(null), 200)),
    ])) as { platformVersion?: string } | null;
    const v = result?.platformVersion;
    if (v) return v.split(".")[0] ?? v;
  } catch {
    // getHighEntropyValues can throw if the browser blocks it
  }
  return syncFallback;
}

export function parseUserAgent(): ParsedUA {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return {
      browserName: "Unknown",
      browserVersion: "",
      osName: "Unknown",
      osVersion: "",
      deviceType: "desktop",
    };
  }

  const nav = navigator as Navigator & { userAgentData?: NavigatorUAData };
  const uaData = nav.userAgentData;

  if (uaData) {
    const brands = uaData.brands ?? [];
    const significantBrands = brands.filter(
      (b) => !b.brand.includes("Not") && !b.brand.includes("Chromium"),
    );
    const primaryBrand = significantBrands[0] ?? brands[0];

    const browserName = primaryBrand?.brand ?? "Unknown";
    const browserVersion = primaryBrand?.version?.split(".")[0] ?? "";

    const platform = uaData.platform ?? "Unknown";
    const deviceType = uaData.mobile ? "mobile" : "desktop";

    // Map platform string to OS name
    let osName = platform;
    if (platform === "macOS") osName = "macOS";
    else if (platform === "Windows") osName = "Windows";
    else if (platform === "Linux") osName = "Linux";
    else if (platform === "Android") osName = "Android";
    else if (platform === "iOS") osName = "iOS";

    // Client Hints doesn't expose platformVersion without an async getHighEntropyValues()
    // call. Fall back to UA string for the version component only.
    const osVersion = parseOSFromUA(navigator.userAgent).version;

    return {
      browserName,
      browserVersion,
      osName,
      osVersion,
      deviceType,
    };
  }

  // Fall back to UA string parsing
  const ua = navigator.userAgent;
  const browser = parseBrowserFromUA(ua);
  const os = parseOSFromUA(ua);
  const deviceType = parseDeviceTypeFromUA(ua);

  return {
    browserName: browser.name,
    browserVersion: browser.version,
    osName: os.name,
    osVersion: os.version,
    deviceType,
  };
}
