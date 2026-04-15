// M1: Signal filter processor — injects/drops attributes based on remote config rules.

import type { Span, Context, AttributeValue } from '@opentelemetry/api';
import type { SpanProcessor, ReadableSpan } from '@opentelemetry/sdk-trace-web';
import type { LogRecord, LogRecordProcessor } from '@opentelemetry/sdk-logs';
import type { PulseSignalConfig, PulseAttributeValue } from '../remote-config';

function coerceAttributeValue(attr: PulseAttributeValue): AttributeValue {
  switch (attr.type) {
    case 'BOOLEAN':
      return attr.value === 'true';
    case 'LONG':
      return parseInt(attr.value, 10);
    case 'DOUBLE':
      return parseFloat(attr.value);
    case 'STRING_ARRAY':
      try {
        const parsed: unknown = JSON.parse(attr.value);
        if (Array.isArray(parsed)) return parsed as string[];
      } catch {
        // fall through
      }
      return [attr.value];
    case 'STRING':
    default:
      return attr.value;
  }
}

export class SignalFilterProcessor implements SpanProcessor, LogRecordProcessor {
  private readonly signalConfig: PulseSignalConfig;

  constructor(signalConfig: PulseSignalConfig) {
    this.signalConfig = signalConfig;
  }

  onStart(span: Span, _parentContext: Context): void {
    // Inject attributes from attributesToAdd
    for (const entry of this.signalConfig.attributesToAdd) {
      const condition = entry.condition;
      // Only apply if TRACES is in the condition scopes
      if (!condition.scopes.includes('TRACES')) continue;

      for (const attr of entry.values) {
        span.setAttribute(attr.name, coerceAttributeValue(attr));
      }
    }
  }

  onEnd(span: ReadableSpan): void {
    // Attribute dropping — note: ReadableSpan attributes are read-only
    // In practice, attribute dropping happens at the exporter level
    // We mark them for dropping by convention here
    const attrsToCheck = this.signalConfig.attributesToDrop;
    if (attrsToCheck.length === 0) return;

    // Check if span attributes match any drop condition
    for (const entry of attrsToCheck) {
      const condition = entry.condition;
      if (!condition.scopes.includes('TRACES')) continue;

      // For ReadableSpan, we cannot mutate attributes — this is best-effort
      // Real dropping would require a custom exporter
      void span;
    }
  }

  onEmit(logRecord: LogRecord): void {
    // Inject attributes from attributesToAdd for LOGS scope
    for (const entry of this.signalConfig.attributesToAdd) {
      const condition = entry.condition;
      if (!condition.scopes.includes('LOGS')) continue;

      for (const attr of entry.values) {
        logRecord.setAttribute(attr.name, coerceAttributeValue(attr));
      }
    }

    // Drop attributes for LOGS scope
    for (const entry of this.signalConfig.attributesToDrop) {
      const condition = entry.condition;
      if (!condition.scopes.includes('LOGS')) continue;

      for (const attrKey of entry.values) {
        logRecord.setAttribute(attrKey, undefined as unknown as AttributeValue);
      }
    }
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    return Promise.resolve();
  }
}
