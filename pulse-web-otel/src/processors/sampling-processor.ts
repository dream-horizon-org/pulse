// M1: Sampling processor — makes a once-at-construction sampling decision.
// Marks unsampled spans/logs with pulse.sampled=false.

import type { Span, Context } from "@opentelemetry/api";
import type { SpanProcessor, ReadableSpan } from "@opentelemetry/sdk-trace-web";
import type { LogRecord, LogRecordProcessor } from "@opentelemetry/sdk-logs";
import type { PulseSdkConfig, PulseSdkName } from "../remote-config";
import { PulseWebSemconv } from "../semconv";

function computeSamplingDecision(
  config: PulseSdkConfig,
  sdkName: PulseSdkName,
): boolean {
  const sessionSampleRate = config.sampling.default.sessionSampleRate;

  // Check if any SDK-specific rule overrides the default
  for (const rule of config.sampling.rules) {
    if (rule.sdks.includes(sdkName)) {
      return Math.random() < rule.sessionSampleRate;
    }
  }

  return Math.random() < sessionSampleRate;
}

export class PulseSamplingProcessor
  implements SpanProcessor, LogRecordProcessor
{
  readonly shouldSample: boolean;

  constructor(config: PulseSdkConfig, sdkName: PulseSdkName) {
    this.shouldSample = computeSamplingDecision(config, sdkName);
  }

  onStart(span: Span, _parentContext: Context): void {
    if (!this.shouldSample) {
      span.setAttribute(PulseWebSemconv.AttributeKey.PULSE_SAMPLED, false);
    }
  }

  onEnd(_span: ReadableSpan): void {
    // No-op
  }

  onEmit(logRecord: LogRecord): void {
    if (!this.shouldSample) {
      logRecord.setAttribute(PulseWebSemconv.AttributeKey.PULSE_SAMPLED, false);
    }
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    return Promise.resolve();
  }
}
