// Session-level sampling rate resolution (Android PulseSessionConfigParser subset).

import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";

import type {
  PulseSdkConfig,
  PulseSdkName,
  PulseSessionSamplingRule,
  PulseSignalMatchCondition,
} from "../types/remote-config";

export function clamp01(r: number): number {
  if (Number.isNaN(r) || r <= 0) return 0;
  if (r >= 1) return 1;
  return r;
}

/**
 * Web subset of Android session rule matching: empty / UNKNOWN rule name matches
 * all contexts; otherwise require {@code rule.value} as regex against navigator.userAgent.
 */
export function sessionRuleMatchesWeb(rule: PulseSessionSamplingRule): boolean {
  const n = (rule.name ?? "").trim();
  if (n === "" || n === "UNKNOWN") return true;
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
): number {
  for (const rule of config.sampling.rules ?? []) {
    if (!rule.sdks?.includes(sdkName)) continue;
    if (sessionRuleMatchesWeb(rule)) return clamp01(rule.sessionSampleRate);
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
  const s = config.sampling;
  const a = s.criticalEventPolicies?.alwaysSend ?? [];
  const b = s.criticalSessionPolicies?.alwaysSend ?? [];
  const merged = [...a, ...b];
  const seen = new Set<string>();
  const out: PulseSignalMatchCondition[] = [];
  for (const c of merged) {
    const key = JSON.stringify(c);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(c);
  }
  return out;
}
