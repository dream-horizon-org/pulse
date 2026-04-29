// Apply `signals.metricsToAdd` at export time — ordering matches Android (before session filter).

import type { Attributes, Meter } from "@opentelemetry/api";
import type { ReadableLogRecord } from "@opentelemetry/sdk-logs";
import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";

import type {
  PulseMetricsToAddEntry,
  PulseSdkName,
  PulseSignalMatchCondition,
} from "../types/remote-config";
import type { PulseSignalScope } from "../types/sampling";
import {
  attrsToStringMap,
  pulsePickAttrKeyMatches,
  pulseSignalConditionMatches,
  pulseTargetPropMatchesConfig,
} from "../utils/sampling-signal-match";

import {
  createMeterRecorderFactory,
  type DataRecorderFactory,
} from "./metrics-to-add-recorder";
import { sanitizeInstrumentationName } from "./sanitize-instrumentation-name";

export interface MetricsToAddPair {
  entry: PulseMetricsToAddEntry;
  factory: DataRecorderFactory;
}

function buildAttributesFromPick(
  signalAttrs: Readonly<Attributes> | undefined,
  attributesToPick: PulseSignalMatchCondition[] | undefined,
): Attributes {
  if (!attributesToPick?.length) return {};
  const keyPatterns = attributesToPick.flatMap((c) =>
    (c.props ?? []).map((p) => p.key),
  );
  if (keyPatterns.length === 0) return {};
  const out: Record<string, string | number | boolean> = {};
  for (const [attrKey, attrVal] of Object.entries(
    attrsToStringMap(signalAttrs),
  )) {
    if (!keyPatterns.some((pat) => pulsePickAttrKeyMatches(attrKey, pat)))
      continue;
    out[attrKey] = attrVal;
  }
  return out as Attributes;
}

export function buildMetricsToAddPairs(
  entries: PulseMetricsToAddEntry[],
  scope: PulseSignalScope,
  sdkName: PulseSdkName,
  getMeter: () => Meter,
): MetricsToAddPair[] {
  return entries
    .filter(
      (e) =>
        e.condition.scopes.includes(scope) &&
        e.condition.sdks.includes(sdkName),
    )
    .map((entry) => ({
      entry,
      factory: createMeterRecorderFactory(entry, getMeter),
    }));
}

function logBodyAsName(body: ReadableLogRecord["body"]): string {
  if (body === undefined || body === null) return "";
  if (typeof body === "string") return body;
  if (typeof body === "number" || typeof body === "boolean")
    return String(body);
  return String(body);
}

export function applyMetricsToAddToSpans(
  pairs: MetricsToAddPair[],
  sdkName: PulseSdkName,
  spans: ReadableSpan[],
): void {
  if (pairs.length === 0) return;
  for (const span of spans) {
    const name = span.name;
    const attrs = span.attributes;
    for (const { entry, factory } of pairs) {
      if (
        !pulseSignalConditionMatches(
          "TRACES",
          name,
          attrs,
          entry.condition,
          sdkName,
        )
      ) {
        continue;
      }
      const picked = buildAttributesFromPick(attrs, entry.attributesToPick);
      const baseMetricName = entry.name;
      const target = entry.target;
      if (target.type === "name") {
        factory(sanitizeInstrumentationName(baseMetricName))(name, picked);
      } else {
        for (const [sk, sv] of Object.entries(attrsToStringMap(attrs))) {
          const hit = (target.condition.props ?? []).some((p) =>
            pulseTargetPropMatchesConfig(p.key, p.value, sk, sv),
          );
          if (!hit) continue;
          const metricName = target.shouldAddPropNameAsSuffix
            ? `${baseMetricName}.${sk}`
            : baseMetricName;
          factory(sanitizeInstrumentationName(metricName))(sv, picked);
        }
      }
    }
  }
}

export function applyMetricsToAddToLogs(
  pairs: MetricsToAddPair[],
  sdkName: PulseSdkName,
  logs: ReadableLogRecord[],
): void {
  if (pairs.length === 0) return;
  for (const log of logs) {
    const name = logBodyAsName(log.body);
    const attrs = log.attributes as unknown as Attributes;
    for (const { entry, factory } of pairs) {
      if (
        !pulseSignalConditionMatches(
          "LOGS",
          name,
          attrs,
          entry.condition,
          sdkName,
        )
      ) {
        continue;
      }
      const picked = buildAttributesFromPick(attrs, entry.attributesToPick);
      const baseMetricName = entry.name;
      const target = entry.target;
      if (target.type === "name") {
        factory(sanitizeInstrumentationName(baseMetricName))(name, picked);
      } else {
        for (const [sk, sv] of Object.entries(attrsToStringMap(attrs))) {
          const hit = (target.condition.props ?? []).some((p) =>
            pulseTargetPropMatchesConfig(p.key, p.value, sk, sv),
          );
          if (!hit) continue;
          const metricName = target.shouldAddPropNameAsSuffix
            ? `${baseMetricName}.${sk}`
            : baseMetricName;
          factory(sanitizeInstrumentationName(metricName))(sv, picked);
        }
      }
    }
  }
}
