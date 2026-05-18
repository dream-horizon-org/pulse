// ISS-I01: forwards app.click logs into the interaction tracker so real DOM
// clicks advance interaction sequences without manual Pulse.trackEvent calls.
// Mirrors Android InteractionLogListener (Branch A only — Branch B markers in ISS-I03).

import type { Context } from "@opentelemetry/api";
import { hrTimeToMilliseconds } from "@opentelemetry/core";
import type { LogRecordProcessor, SdkLogRecord } from "@opentelemetry/sdk-logs";
import { PulseWebSemconv } from "../semconv";
import { logRecordBodyAsString } from "../utils/session-sampling-rate";
import type { InteractionInstrumentation } from "../instrumentations/interaction";
import type { PulseAttributes } from "../types/attributes";

export class InteractionLogProcessor implements LogRecordProcessor {
  private instrumentation: InteractionInstrumentation | null = null;

  setInstrumentation(instr: InteractionInstrumentation | null): void {
    this.instrumentation = instr;
  }

  onEmit(logRecord: SdkLogRecord, _context?: Context): void {
    const instr = this.instrumentation;
    if (instr == null) return;

    const attrs = logRecord.attributes as unknown as PulseAttributes;
    const pulseType = attrs?.[PulseWebSemconv.AttributeKey.PULSE_TYPE];

    // Branch A — click bridge (APP_CLICK only; do not relax to generic string body).
    // Other pulse.types with string bodies (device.crash, non_fatal, session.start)
    // must NOT hit this branch — Branch B (ISS-I03) handles markers separately.
    if (pulseType === PulseWebSemconv.PulseType.APP_CLICK) {
      const body = logRecordBodyAsString(logRecord.body);
      if (!body) return;
      const timeMs = hrTimeToMilliseconds(logRecord.hrTime);
      instr.trackEvent(body, attrs, timeMs);
    }
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    this.instrumentation = null;
    return Promise.resolve();
  }
}
