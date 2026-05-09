import { logs } from "@opentelemetry/api-logs";
import type { SdkContext, PulseInstrumentation } from "../instrumentation-registry";
import { PulseOtelLoggerScope, PulseInstrumentationName } from "../constants/pulse-otel-runtime";
import { PulseWebSemconv } from "../semconv";
import { PulseDataCollectionConsent } from "../config";

export class NavigationInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.NAVIGATION;
  private unsubscribe?: () => void;
  private installed = false;
  private lastNavigationTime = 0;
  private navigationRateLimitMs = 100;
  private currentScreenName = "";
  private screenStartTime = 0;

  private originalPushState?: typeof history.pushState;
  private originalReplaceState?: typeof history.replaceState;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") {
      return;
    }

    if (this.installed) {
      return;
    }

    // Don't install if consent is denied
    if (sdk.config.dataCollectionState === PulseDataCollectionConsent.DENIED) {
      return;
    }

    this.installed = true;

    // Initialize current screen
    this.currentScreenName = this.getCurrentScreenName(sdk);
    this.screenStartTime = Date.now();

    // Patch History API
    this.patchHistoryAPI(sdk);
  }

  uninstall(): void {
    if (this.originalPushState) {
      history.pushState = this.originalPushState;
    }
    if (this.originalReplaceState) {
      history.replaceState = this.originalReplaceState;
    }
    this.installed = false;
  }

  private patchHistoryAPI(sdk: SdkContext): void {
    this.originalPushState = history.pushState;
    this.originalReplaceState = history.replaceState;

    history.pushState = (...args: Parameters<typeof history.pushState>) => {
      const result = this.originalPushState!.apply(history, args);
      this.onRouteChange(sdk);
      return result;
    };

    history.replaceState = (...args: Parameters<typeof history.replaceState>) => {
      const result = this.originalReplaceState!.apply(history, args);
      this.onRouteChange(sdk);
      return result;
    };
  }

  private onRouteChange(sdk: SdkContext): void {
    const now = Date.now();
    if (now - this.lastNavigationTime < this.navigationRateLimitMs) {
      return;
    }
    this.lastNavigationTime = now;

    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;
    const logBodies = PulseWebSemconv.LogBody;

    const logger = logs.getLogger(PulseOtelLoggerScope.PULSE_WEB_NAVIGATION);
    if (!logger) return;

    const newScreenName = this.getCurrentScreenName(sdk);
    const sessionId = sdk.sessionProvider.getSessionId();

    // Emit screen_session for the previous screen (if it exists and is different)
    if (this.currentScreenName && this.currentScreenName !== newScreenName) {
      const sessionDurationMs =
        this.screenStartTime > 0 ? now - this.screenStartTime : 0;
      logger.emit({
        body: logBodies.SCREEN_SESSION,
        attributes: {
          [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_SESSION,
          [attributeKeys.SCREEN_NAME]: this.currentScreenName,
          [attributeKeys.SESSION_ID]: sessionId ?? "",
          [attributeKeys.SESSION_DURATION_MS]: sessionDurationMs,
        },
      });
    }

    // Emit screen_load for the new screen
    logger.emit({
      body: logBodies.SCREEN_LOAD,
      attributes: {
        [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_LOAD,
        [attributeKeys.SCREEN_NAME]: newScreenName,
        [attributeKeys.START_TYPE]: "spa",
        [attributeKeys.SESSION_ID]: sessionId ?? "",
      },
    });

    // Update current screen tracking
    this.currentScreenName = newScreenName;
    this.screenStartTime = now;

    // Update current screen in global attributes processor
    if (typeof sdk.globalAttrsProcessor.setScreenName === "function") {
      sdk.globalAttrsProcessor.setScreenName(newScreenName);
    }
  }

  getCurrentScreenName(sdk: SdkContext): string {
    if (typeof window === "undefined") {
      return "";
    }

    return sdk.globalAttrsProcessor.getCurrentScreenName();
  }
}
