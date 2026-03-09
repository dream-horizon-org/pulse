import { TurboModuleRegistry, type TurboModule } from 'react-native';

/**
 * Data collection consent state for the Pulse SDK.
 * - PENDING: Telemetry is buffered in memory until consent is granted or denied.
 * - ALLOWED: Buffered signals are flushed and subsequent signals are exported normally.
 * - DENIED: Buffered data is cleared and the SDK is shut down.
 */
export enum PulseDataCollectionConsent {
  PENDING = 'PENDING',
  ALLOWED = 'ALLOWED',
  DENIED = 'DENIED',
}

export interface Spec extends TurboModule {
  /** Check if native SDK is initialized */
  isInitialized(): boolean;

  /** Track a custom event with properties. */
  trackEvent(
    event: string,
    observedTimeMs: number,
    properties?: Object
  ): boolean;

  /** Report a JS exception (message + stack, with fatal flag). */
  /** This function was made synchronous to attach current span context to the error event. Since span is created synchronously on mqt_js thread.*/
  reportException(
    errorMessage: string,
    observedTimeMs: number,
    stackTrace: string,
    isFatal: boolean,
    errorType: string,
    attributes?: Object
  ): boolean;

  /** Start an active span; returns spanId. */
  startSpan(name: string, inheritContext: boolean, attributes?: Object): string;

  /** End a span with optional status code. */
  endSpan(spanId: string, statusCode?: string): boolean;

  /** Add an event to a span. */
  addSpanEvent(spanId: string, name: string, attributes?: Object): boolean;

  /** Batch set attributes on a span. */
  setSpanAttributes(spanId: string, attributes?: Object): boolean;

  /** Record an exception on a span. */
  recordSpanException(
    spanId: string,
    errorMessage: string,
    stackTrace?: string
  ): boolean;

  /** Discard a span without sending it to backend. */
  discardSpan(spanId: string): boolean;

  /** Set user id for the session. Setting null will reset the id */
  setUserId(id: string | null): void;

  /** Set a single user property for this session */
  setUserProperty(name: string, value: string | null): void;

  /** Set multiple user properties for this session */
  setUserProperties(properties: Object): void;

  /** Trigger ANR test (freezes main thread for 6 seconds) */
  triggerAnr(): void;

  /** Set the current React Native screen name to sync active screen name on Android/iOS */
  setCurrentScreenName(screenName: string): boolean;

  /** Get all SDK Remote Config features */
  getAllFeatures(): {
    rn_screen_load: boolean;
    screen_session: boolean;
    rn_screen_interactive: boolean;
    network_instrumentation: boolean;
    custom_events: boolean;
    js_crash: boolean;
  } | null;

  /**
   * Update the data collection consent state.
   * - ALLOWED: flushes buffered signals and resumes normal export.
   * - DENIED: clears buffered data and shuts down the SDK.
   * - PENDING: no-op once the SDK is initialized.
   */
  setDataCollectionState(state: PulseDataCollectionConsent): void;

  /** Shut down the Pulse SDK. After this, re-init is not supported. */
  shutdown(): boolean;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PulseReactNativeOtel');
