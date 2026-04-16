// M1: Session instrumentation — subscribes to SessionProvider events
// and emits session.start / session.end log records.

import { logs } from '@opentelemetry/api-logs';
import type { SdkContext, PulseInstrumentation } from '../instrumentation-registry';
import type { SessionChangeEvent } from '../session';

export class SessionInstrumentation implements PulseInstrumentation {
  readonly name = 'session';
  private unsubscribe?: () => void;

  install(sdk: SdkContext): void {
    this.unsubscribe = sdk.sessionProvider.onSessionChange((event: SessionChangeEvent) => {
      const logger = logs.getLogger('pulse-web-session');
      if (event.type === 'start') {
        logger.emit({
          body: 'session.start',
          attributes: {
            'pulse.type': 'session.start',
            'session.id': event.newSessionId ?? '',
            'session.previous_id': event.previousSessionId ?? '',
            'session.start_reason': event.reason,
          },
        });
      } else if (event.type === 'end') {
        logger.emit({
          body: 'session.end',
          attributes: {
            'pulse.type': 'session.end',
            'session.id': event.sessionId ?? '',
            'session.duration_ns': event.durationNs ?? 0,
            'session.end_reason': event.reason,
          },
        });
      }
    });

    // Trigger the initial session.start
    sdk.sessionProvider.emitInitialSession();
  }

  uninstall(): void {
    this.unsubscribe?.();
  }
}
