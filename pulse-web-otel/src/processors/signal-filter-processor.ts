// M1: Signal filter processor — injects/drops attributes based on remote config rules.

import type {
  Attributes,
  Span,
  Context,
  AttributeValue,
} from "@opentelemetry/api";
import type { SpanProcessor, ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { LogRecordProcessor, SdkLogRecord } from "@opentelemetry/sdk-logs";
import type {
  PulseSdkName,
  PulseSignalConfig,
  PulseAttributeValue,
} from "../remote-config";
import {
  attributeKeyMatchesAnyDropPattern,
  pulseSignalConditionMatches,
} from "../utils/sampling-signal-match";
import { logRecordBodyAsString } from "../utils/session-sampling-rate";
import { PulseWebSemconv } from "../semconv";

const PULSE_WEB_SDK: PulseSdkName = PulseWebSemconv.FixedValue.RUM_SDK_NAME;

function coerceAttributeValue(attr: PulseAttributeValue): AttributeValue {
  switch (attr.type) {
    case "BOOLEAN":
      return attr.value === "true";
    case "LONG":
      return parseInt(attr.value, 10);
    case "DOUBLE":
      return parseFloat(attr.value);
    case "STRING_ARRAY":
      try {
        const parsed: unknown = JSON.parse(attr.value);
        if (Array.isArray(parsed)) return parsed as string[];
      } catch {
        // fall through
      }
      return [attr.value];
    case "STRING":
    default:
      return attr.value;
  }
}

/** SDK Span / LogRecord attribute bags — drop keys matching Android-style regex patterns. */
function deleteMutableAttributesMatchingDropPatterns(
  holder: unknown,
  dropKeyPatterns: readonly string[],
): void {
  const attrs = (holder as { attributes?: Record<string, unknown> })
    ?.attributes;
  if (!attrs) return;
  for (const key of Object.keys(attrs)) {
    if (attributeKeyMatchesAnyDropPattern(key, dropKeyPatterns)) {
      delete attrs[key];
    }
  }
}

function spanNameForMatch(span: Span): string {
  const opaque = span as unknown as { name?: string };
  return typeof opaque.name === "string" ? opaque.name : "";
}

export class SignalFilterProcessor
  implements SpanProcessor, LogRecordProcessor
{
  private readonly signalConfig: PulseSignalConfig;

  constructor(signalConfig: PulseSignalConfig) {
    this.signalConfig = signalConfig;
  }

  onStart(span: Span, _parentContext: Context): void {
    const spanName = spanNameForMatch(span);
    const spanAttrs = (span as unknown as { attributes?: Attributes })
      .attributes;

    for (const entry of this.signalConfig.attributesToAdd) {
      const condition = entry.condition;
      if (!condition.scopes.includes("TRACES")) continue;
      if (
        !pulseSignalConditionMatches(
          "TRACES",
          spanName,
          spanAttrs,
          condition,
          PULSE_WEB_SDK,
        )
      ) {
        continue;
      }
      for (const attr of entry.values) {
        span.setAttribute(attr.name, coerceAttributeValue(attr));
      }
    }

    for (const entry of this.signalConfig.attributesToDrop) {
      const condition = entry.condition;
      if (!condition.scopes.includes("TRACES")) continue;
      if (
        !pulseSignalConditionMatches(
          "TRACES",
          spanName,
          spanAttrs,
          condition,
          PULSE_WEB_SDK,
        )
      ) {
        continue;
      }
      deleteMutableAttributesMatchingDropPatterns(span, entry.values);
    }
  }

  onEnd(_span: ReadableSpan): void {
    // Trace drops run in onStart while the Span is still mutable (after global attrs).
  }

  onEmit(logRecord: SdkLogRecord): void {
    const logName = logRecordBodyAsString(logRecord.body);
    const logAttrs = logRecord.attributes as unknown as Attributes;

    for (const entry of this.signalConfig.attributesToAdd) {
      const condition = entry.condition;
      if (!condition.scopes.includes("LOGS")) continue;
      if (
        !pulseSignalConditionMatches(
          "LOGS",
          logName,
          logAttrs,
          condition,
          PULSE_WEB_SDK,
        )
      ) {
        continue;
      }
      for (const attr of entry.values) {
        logRecord.setAttribute(attr.name, coerceAttributeValue(attr));
      }
    }

    for (const entry of this.signalConfig.attributesToDrop) {
      const condition = entry.condition;
      if (!condition.scopes.includes("LOGS")) continue;
      if (
        !pulseSignalConditionMatches(
          "LOGS",
          logName,
          logAttrs,
          condition,
          PULSE_WEB_SDK,
        )
      ) {
        continue;
      }
      deleteMutableAttributesMatchingDropPatterns(logRecord, entry.values);
    }
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    return Promise.resolve();
  }
}
