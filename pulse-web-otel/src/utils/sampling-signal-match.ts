// Mirrors Android PulseSignalsAttrMatcher + PulseProp.matches (regex on name/value).

import type { Attributes } from "@opentelemetry/api";
import { diag } from "@opentelemetry/api";

import type {
  PulseSignalMatchCondition,
  PulseSdkName,
} from "../types/remote-config";
import type { PulseSignalScope } from "../types/sampling";

/** Invalid server patterns must not silence all traffic (avoid `/^$/` trap). */
function safeRegex(pattern: string): RegExp {
  try {
    return new RegExp(pattern);
  } catch {
    diag.warn(
      `[Pulse] invalid regex in sampling config; using literal fallback: ${String(pattern).slice(0, 120)}`,
    );
    try {
      const escaped = pattern.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      return new RegExp(`^${escaped}$`);
    } catch {
      return /^$/;
    }
  }
}

function matchesRegex(haystack: string, pattern: string): boolean {
  return safeRegex(pattern).test(haystack);
}

/**
 * Android {@code attributesToDrop.values}: each entry is a regex matched against
 * attribute keys (not only literals).
 */
export function attributeKeyMatchesAnyDropPattern(
  attributeKey: string,
  dropPatterns: readonly string[],
): boolean {
  for (const pattern of dropPatterns) {
    if (matchesRegex(attributeKey, pattern)) return true;
  }
  return false;
}

/** Flatten OTEL attributes to string values for Pulse-style prop matching. */
export function attrsToStringMap(
  attrs: Attributes | Readonly<Attributes> | undefined,
): Record<string, string> {
  if (!attrs) return {};
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null) continue;
    if (typeof v === "string") out[k] = v;
    else if (typeof v === "number" || typeof v === "boolean")
      out[k] = String(v);
    else if (Array.isArray(v)) out[k] = v.map(String).join(",");
  }
  return out;
}

function propConfigMatchesSignalAttr(
  propNamePattern: string,
  propValuePattern: string | undefined,
  signalKey: string,
  signalValue: string,
): boolean {
  if (!matchesRegex(signalKey, propNamePattern)) return false;
  if (propValuePattern == null || propValuePattern === "") return true;
  return matchesRegex(signalValue, propValuePattern);
}

/** `metricsToAdd` attribute target: any config prop matches this signal attribute (regex). */
export function pulseTargetPropMatchesConfig(
  propKeyPattern: string,
  propValuePattern: string | undefined,
  signalAttrKey: string,
  signalAttrValue: string,
): boolean {
  return propConfigMatchesSignalAttr(
    propKeyPattern,
    propValuePattern,
    signalAttrKey,
    signalAttrValue,
  );
}

/** `attributesToPick`: attribute key must match one of these patterns (Android `buildAttributesFromPick`). */
export function pulsePickAttrKeyMatches(
  attrKey: string,
  pattern: string,
): boolean {
  return matchesRegex(attrKey, pattern);
}

/**
 * Same contract as Android {@code PulseSignalsAttrMatcher}:
 * sdks, scopes, name regex on signal name, and every condition prop must be
 * satisfied by some signal attribute (key + value regex).
 */
export function pulseSignalConditionMatches(
  scope: PulseSignalScope,
  signalName: string,
  signalAttrs: Attributes | Readonly<Attributes> | undefined,
  condition: PulseSignalMatchCondition,
  sdkName: PulseSdkName,
): boolean {
  if (!condition.sdks.includes(sdkName)) return false;
  if (!condition.scopes.includes(scope)) return false;
  if (!matchesRegex(signalName, condition.name)) return false;

  const props = condition.props ?? [];
  if (props.length === 0) return true;

  const entries = Object.entries(attrsToStringMap(signalAttrs));
  for (const p of props) {
    let hit = false;
    for (const [sk, sv] of entries) {
      if (propConfigMatchesSignalAttr(p.key, p.value, sk, sv)) {
        hit = true;
        break;
      }
    }
    if (!hit) return false;
  }
  return true;
}
