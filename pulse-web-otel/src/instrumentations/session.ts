// M1: Session instrumentation — subscribes to SessionProvider events
// and emits session.start / session.end log records.

import { logs } from "@opentelemetry/api-logs";
import type {
  SdkContext,
  PulseInstrumentation,
} from "../instrumentation-registry";
import type { SessionChangeEvent } from "../session";
import { PulseWebSemconv } from "../semconv";

export class SessionInstrumentation implements PulseInstrumentation {
  readonly name = "session";
  private unsubscribe?: () => void;

  install(sdk: SdkContext): void {
    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;
    const B = PulseWebSemconv.LogBody;
    this.unsubscribe = sdk.sessionProvider.onSessionChange(
      (event: SessionChangeEvent) => {
        const logger = logs.getLogger("pulse-web-session");
        if (event.type === "start") {
          logger.emit({
            body: B.SESSION_START,
            attributes: {
              [K.PULSE_TYPE]: T.SESSION_START,
              [K.SESSION_ID]: event.newSessionId ?? "",
              [K.SESSION_PREVIOUS_ID]: event.previousSessionId ?? "",
              [K.SESSION_START_REASON]: event.reason,
            },
          });
        } else if (event.type === "end") {
          logger.emit({
            body: B.SESSION_END,
            attributes: {
              [K.PULSE_TYPE]: T.SESSION_END,
              [K.SESSION_ID]: event.sessionId ?? "",
              [K.SESSION_DURATION_MS]: event.durationMs ?? 0,
              [K.SESSION_END_REASON]: event.reason,
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
