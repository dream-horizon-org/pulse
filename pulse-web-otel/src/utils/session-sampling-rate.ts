// Session-level sampling rate resolution (Android PulseSessionConfigParser subset).

import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";

import { PulseWebSemconv } from "../semconv";
import type { ParsedUA } from "../types/ua";
import type {
  PulseSdkConfig,
  PulseSdkName,
  PulseSessionSamplingRule,
  PulseSignalMatchCondition,
} from "../types/remote-config";
import { parseUserAgent } from "./ua-parser";

const RUM_PLATFORM_WEB = PulseWebSemconv.FixedValue.PLATFORM_WEB;

type NetworkSnapshot = {
  type?: string;
  effectiveType?: string;
};

/** Same source as signal `network.connection.*` (see global-attrs processor). */
function readNavigatorConnection(): NetworkSnapshot {
  if (typeof navigator === "undefined") return {};
  const nav = navigator as unknown as { connection?: NetworkSnapshot };
  return nav.connection ?? {};
}

/** Match {@code actual} against {@code pattern} as RegExp, or literal equality if pattern is invalid. */
function valuePatternMatches(actual: string, pattern: string): boolean {
  const p = pattern.trim();
  if (p === "") return true;
  try {
    return new RegExp(p).test(actual);
  } catch {
    return actual === p;
  }
}

export function clamp01(r: number): number {
  if (Number.isNaN(r) || r <= 0) return 0;
  if (r >= 1) return 1;
  return r;
}

/** Values aligned with RUM resource / global attrs where applicable (snapshot at match time). */
export interface SessionSamplingRuleMatchContext {
  serviceVersion: string;
  parsedUa: ParsedUA;
  networkType: string;
  networkEffectiveType: string;
}

export function buildSessionSamplingRuleMatchContext(
  serviceVersion?: string,
): SessionSamplingRuleMatchContext {
  const parsedUa = parseUserAgent();
  const net = readNavigatorConnection();
  return {
    serviceVersion: serviceVersion ?? "0.0.0",
    parsedUa,
    networkType: net.type ?? "unknown",
    networkEffectiveType: net.effectiveType ?? "unknown",
  };
}

function osVersionMatchTarget(ctx: SessionSamplingRuleMatchContext): string {
  const slice = [ctx.parsedUa.osName, ctx.parsedUa.osVersion]
    .filter(Boolean)
    .join(" ")
    .trim();
  if (slice !== "") return slice;
  if (typeof navigator !== "undefined") return navigator.userAgent;
  return "";
}

function networkMatchTarget(ctx: SessionSamplingRuleMatchContext): string {
  return `${ctx.networkType}/${ctx.networkEffectiveType}`;
}

/**
 * Web subset of Android session rule matching:
 * - Empty / UNKNOWN {@code name} → matches all contexts (for allowed {@code sdks}).
 * - {@code platform} → RUM {@code web} vs {@code rule.value} (regex or literal fallback).
 * - {@code app_version} → {@code service.version} string (from config / resource).
 * - {@code os_version} → parsed OS name + version (UA / Client Hints snapshot); if empty, full UA.
 * - {@code network} → {@code network.connection.type}/{@code effectiveType} (defaults {@code unknown}).
 * - {@code device} → parsed {@code device.type} ({@code desktop|mobile|tablet}).
 * - Other names → legacy: {@code rule.value} as regex on {@code navigator.userAgent}.
 */
export function sessionRuleMatchesWeb(
  rule: PulseSessionSamplingRule,
  ctx?: SessionSamplingRuleMatchContext,
): boolean {
  const context = ctx ?? buildSessionSamplingRuleMatchContext();
  const n = (rule.name ?? "").trim();
  if (n === "" || n === "UNKNOWN") return true;

  const nameLower = n.toLowerCase();
  if (nameLower === "platform") {
    return valuePatternMatches(RUM_PLATFORM_WEB, rule.value ?? "");
  }
  if (nameLower === "app_version") {
    return valuePatternMatches(context.serviceVersion, rule.value ?? "");
  }
  if (nameLower === "os_version") {
    return valuePatternMatches(osVersionMatchTarget(context), rule.value ?? "");
  }
  if (nameLower === "network") {
    return valuePatternMatches(networkMatchTarget(context), rule.value ?? "");
  }
  if (nameLower === "device") {
    return valuePatternMatches(context.parsedUa.deviceType, rule.value ?? "");
  }

  if (typeof navigator === "undefined" || !rule.value) return false;
  try {
    return new RegExp(rule.value).test(navigator.userAgent);
  } catch {
    return false;
  }
}

export function resolveSessionSamplingRate(
  config: PulseSdkConfig,
  sdkName: PulseSdkName,
  options?: { serviceVersion?: string },
): number {
  const ctx = buildSessionSamplingRuleMatchContext(options?.serviceVersion);
  for (const rule of config.sampling.rules ?? []) {
    if (!rule.sdks?.includes(sdkName)) continue;
    if (sessionRuleMatchesWeb(rule, ctx))
      return clamp01(rule.sessionSampleRate);
  }
  return clamp01(config.sampling.default.sessionSampleRate);
}

/** Log record body as string for sampling match key (Android log signal name). */
export function logRecordBodyAsString(body: ReadableLogRecord["body"]): string {
  if (body === undefined || body === null) return "";
  if (typeof body === "string") return body;
  if (typeof body === "number" || typeof body === "boolean")
    return String(body);
  if (typeof body === "object" && body !== null && "stringValue" in body) {
    const v = (body as { stringValue?: string }).stringValue;
    return v ?? "";
  }
  return String(body);
}

export function getCriticalAlwaysSendConditions(
  config: PulseSdkConfig,
): PulseSignalMatchCondition[] {
  const list = config.sampling.criticalSessionPolicies?.alwaysSend ?? [];
  const seen = new Set<string>();
  const out: PulseSignalMatchCondition[] = [];
  for (const c of list) {
    const key = JSON.stringify(c);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(c);
  }
  return out;
}
