// M1: Session instrumentation — subscribes to SessionProvider events
// and emits session.start / session.end log records.

import { logs } from "@opentelemetry/api-logs";
import type {
  SdkContext,
  PulseInstrumentation,
} from "../instrumentation-registry";
import type { SessionChangeEvent } from "../session";
import {
  PulseInstrumentationName,
  PulseOtelLoggerScope,
} from "../constants/pulse-otel-runtime";
import { PulseWebSemconv } from "../semconv";

export class SessionInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.SESSION;
  private unsubscribe?: () => void;

  install(sdk: SdkContext): void {
    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;
    const logBodies = PulseWebSemconv.LogBody;
    this.unsubscribe = sdk.sessionProvider.onSessionChange(
      (event: SessionChangeEvent) => {
        const logger = logs.getLogger(PulseOtelLoggerScope.PULSE_WEB_SESSION);
        if (event.type === "start") {
          logger.emit({
            body: logBodies.SESSION_START,
            attributes: {
              [attributeKeys.PULSE_TYPE]: pulseTypes.SESSION_START,
              [attributeKeys.SESSION_ID]: event.newSessionId ?? "",
              [attributeKeys.SESSION_PREVIOUS_ID]:
                event.previousSessionId ?? "",
              [attributeKeys.SESSION_START_REASON]: event.reason,
            },
          });
        } else if (event.type === "end") {
          const durationMs =
            event.durationNs !== undefined
              ? Math.floor(event.durationNs / 1_000_000)
              : 0;
          logger.emit({
            body: logBodies.SESSION_END,
            attributes: {
              [attributeKeys.PULSE_TYPE]: pulseTypes.SESSION_END,
              [attributeKeys.SESSION_ID]: event.sessionId ?? "",
              [attributeKeys.SESSION_DURATION_MS]: durationMs,
              [attributeKeys.SESSION_END_REASON]: event.reason,
            },
          });
        }
      },
    );

    // Trigger the initial session.start
    sdk.sessionProvider.emitInitialSession();
  }

  uninstall(): void {
    this.unsubscribe?.();
  }
}
