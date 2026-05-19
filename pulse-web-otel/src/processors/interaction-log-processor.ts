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

    const timeMs = hrTimeToMilliseconds(logRecord.hrTime);

    // Branch B — log-based markers (crash / non_fatal → addMarkerToAll).
    // Evaluated first so crash logs never also hit Branch A.
    if (
      pulseType === PulseWebSemconv.PulseType.DEVICE_CRASH ||
      pulseType === PulseWebSemconv.PulseType.NON_FATAL
    ) {
      const body = logRecordBodyAsString(logRecord.body);
      if (!body) return;
      instr.addMarkerToAll(body, attrs, timeMs);
      return;
    }

    // Branch A — click bridge (APP_CLICK only; do not relax to generic string body).
    if (pulseType === PulseWebSemconv.PulseType.APP_CLICK) {
      const body = logRecordBodyAsString(logRecord.body);
      if (!body) return;
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
