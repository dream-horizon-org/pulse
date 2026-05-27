// Counts device.crash and non_fatal log records within a session and attaches
// the totals to the session.end log record before it is exported.
//
// Mirrors Android PulseSdkSignalProcessors.PulseLogTypeAttributesAppender:
//   recordedRelevantLogEvents[CRASH] / [NON_FATAL] → attached to "session.end"
//   → recordedRelevantLogEvents.clear()
//
// Attributes attached (omitted when 0 — matches Android ?.let pattern):
//   pulse.session.crash.count     (device.crash logs)
//   pulse.session.non_fatal.count (non_fatal logs)

import type { Context } from "@opentelemetry/api";
import type { LogRecordProcessor, SdkLogRecord } from "@opentelemetry/sdk-logs";
import { PulseWebSemconv } from "../semconv";

const K = PulseWebSemconv.AttributeKey;
const T = PulseWebSemconv.PulseType;

export class SessionCrashCountProcessor implements LogRecordProcessor {
  private crashCount = 0;
  private nonFatalCount = 0;

  onEmit(logRecord: SdkLogRecord, _context?: Context): void {
    const attrs = logRecord.attributes as Record<string, unknown>;
    const pulseType = attrs?.[K.PULSE_TYPE] as string | undefined;

    switch (pulseType) {
      case T.DEVICE_CRASH:
        this.crashCount++;
        break;

      case T.NON_FATAL:
        this.nonFatalCount++;
        break;

      case T.SESSION_END:
        // Attach counts only when > 0 — mirrors Android ?.let pattern
        if (this.crashCount > 0) {
          logRecord.setAttribute(K.SESSION_CRASH_COUNT, this.crashCount);
        }
        if (this.nonFatalCount > 0) {
          logRecord.setAttribute(K.SESSION_NON_FATAL_COUNT, this.nonFatalCount);
        }
        // Reset for next session
        this.crashCount = 0;
        this.nonFatalCount = 0;
        break;
    }
  }

  /** Expose for testing — returns snapshot of current counters. */
  getCounters(): { crashCount: number; nonFatalCount: number } {
    return { crashCount: this.crashCount, nonFatalCount: this.nonFatalCount };
  }

  /** Reset counters — used by uninstall / SDK shutdown. */
  reset(): void {
    this.crashCount = 0;
    this.nonFatalCount = 0;
  }

  forceFlush(): Promise<void> {
    return Promise.resolve();
  }

  shutdown(): Promise<void> {
    this.reset();
    return Promise.resolve();
  }
}
