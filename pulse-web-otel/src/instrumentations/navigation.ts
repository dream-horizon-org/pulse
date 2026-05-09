import { logs } from "@opentelemetry/api-logs";
import type {
  SdkContext,
  PulseInstrumentation,
} from "../instrumentation-registry";
import {
  PulseOtelLoggerScope,
  PulseInstrumentationName,
} from "../constants/pulse-otel-runtime";
import { PulseWebSemconv } from "../semconv";
import { PulseDataCollectionConsent } from "../config";

type NavigationTimingType = "cold" | "reload" | "back_forward";

interface TimingData {
  pageLoadTime?: number;
  ttfb?: number;
  dnsTime?: number;
  tcpTime?: number;
  domProcessingTime?: number;
  tti?: number;
}

export class NavigationInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.NAVIGATION;
  private installed = false;
  private lastNavigationTime = 0;
  private navigationRateLimitMs = 100;
  private currentScreenName = "";
  private screenStartTime = 0;

  private originalPushState?: typeof history.pushState;
  private originalReplaceState?: typeof history.replaceState;

  private sdkContext: SdkContext | null = null;
  private onPopStateBound?: () => void;
  private onPageHideBound?: () => void;

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
    this.sdkContext = sdk;

    // Initialize current screen
    this.currentScreenName = this.getCurrentScreenName(sdk);
    this.screenStartTime = Date.now();

    // Emit initial screen_load and screen_interactive on page load
    this.emitInitialLoadSignals(sdk);

    // Patch History API for SPA navigations
    this.patchHistoryAPI(sdk);

    // Browser back/forward (does not go through pushState)
    this.onPopStateBound = () => {
      if (this.sdkContext) this.onRouteChange(this.sdkContext);
    };
    window.addEventListener("popstate", this.onPopStateBound);

    // Final screen_session when document unloads (tab close / navigation away from document)
    this.onPageHideBound = () => {
      if (this.sdkContext) this.emitPageHideScreenSession(this.sdkContext);
    };
    window.addEventListener("pagehide", this.onPageHideBound);
  }

  uninstall(): void {
    if (typeof window !== "undefined") {
      if (this.onPopStateBound) {
        window.removeEventListener("popstate", this.onPopStateBound);
        this.onPopStateBound = undefined;
      }
      if (this.onPageHideBound) {
        window.removeEventListener("pagehide", this.onPageHideBound);
        this.onPageHideBound = undefined;
      }
    }

    if (this.originalPushState) {
      history.pushState = this.originalPushState;
    }
    if (this.originalReplaceState) {
      history.replaceState = this.originalReplaceState;
    }
    this.originalPushState = undefined;
    this.originalReplaceState = undefined;
    this.installed = false;
    this.sdkContext = null;
  }

  private patchHistoryAPI(sdk: SdkContext): void {
    this.originalPushState = history.pushState;
    this.originalReplaceState = history.replaceState;

    history.pushState = (...args: Parameters<typeof history.pushState>) => {
      const result = this.originalPushState!.apply(history, args);
      this.onRouteChange(sdk);
      return result;
    };

    history.replaceState = (
      ...args: Parameters<typeof history.replaceState>
    ) => {
      const result = this.originalReplaceState!.apply(history, args);
      this.onRouteChange(sdk);
      return result;
    };
  }

  /** Document-level attributes for navigation logs (FINAL-PLAN parity). */
  private buildDocAttrs(): Record<string, string> {
    if (typeof window === "undefined" || typeof document === "undefined") {
      return {};
    }
    const k = PulseWebSemconv.AttributeKey;
    return {
      [k.URL_PATH]: window.location.pathname,
      [k.PAGE_TITLE]: typeof document.title === "string" ? document.title : "",
    };
  }

  private emitPageHideScreenSession(sdk: SdkContext): void {
    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;
    const logBodies = PulseWebSemconv.LogBody;

    const logger = logs.getLogger(PulseOtelLoggerScope.PULSE_WEB_NAVIGATION);
    if (!logger) return;

    if (!this.currentScreenName) return;

    const now = Date.now();
    const sessionDurationMs =
      this.screenStartTime > 0 ? now - this.screenStartTime : 0;
    const sessionId = sdk.sessionProvider.getSessionId();

    logger.emit({
      body: logBodies.SCREEN_SESSION,
      attributes: {
        [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_SESSION,
        [attributeKeys.SCREEN_NAME]: this.currentScreenName,
        [attributeKeys.SESSION_ID]: sessionId ?? "",
        [attributeKeys.SESSION_DURATION_MS]: sessionDurationMs,
        [attributeKeys.SESSION_DURATION]: sessionDurationMs,
        ...this.buildDocAttrs(),
      },
    });
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
    const docAttrs = this.buildDocAttrs();

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
          [attributeKeys.SESSION_DURATION]: sessionDurationMs,
          ...docAttrs,
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
        ...docAttrs,
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

  private getNavigationTimingType(): NavigationTimingType {
    if (typeof window === "undefined" || !window.performance) {
      return "cold";
    }

    const navTiming = performance.getEntriesByType("navigation")[0] as
      | PerformanceNavigationTiming
      | undefined;

    if (!navTiming) {
      return "cold";
    }

    const type = navTiming.type;
    if (type === "reload") return "reload";
    if (type === "back_forward") return "back_forward";
    return "cold";
  }

  /** Raw PerformanceNavigationTiming.type (`navigate` | `reload` | `back_forward`). */
  private getBrowserNavigationType(): string | undefined {
    if (typeof window === "undefined" || !window.performance) return undefined;
    const navTiming = performance.getEntriesByType("navigation")[0] as
      | PerformanceNavigationTiming
      | undefined;
    return navTiming?.type;
  }

  private extractTimingData(): TimingData {
    if (typeof window === "undefined" || !window.performance) {
      return {};
    }

    const navTiming = performance.getEntriesByType("navigation")[0] as
      | PerformanceNavigationTiming
      | undefined;

    if (!navTiming) {
      return {};
    }

    const timing: TimingData = {};

    // page.load_time: loadEventEnd - fetchStart
    if (navTiming.loadEventEnd && navTiming.fetchStart) {
      const pageLoadTime = Math.round(
        navTiming.loadEventEnd - navTiming.fetchStart,
      );
      if (pageLoadTime > 0) {
        timing.pageLoadTime = pageLoadTime;
      }
    }

    // ttfb: responseStart - fetchStart
    if (navTiming.responseStart && navTiming.fetchStart) {
      const ttfb = Math.round(navTiming.responseStart - navTiming.fetchStart);
      if (ttfb > 0) {
        timing.ttfb = ttfb;
      }
    }

    // dns.time: domainLookupEnd - domainLookupStart
    if (navTiming.domainLookupEnd && navTiming.domainLookupStart) {
      const dnsTime = Math.round(
        navTiming.domainLookupEnd - navTiming.domainLookupStart,
      );
      if (dnsTime > 0) {
        timing.dnsTime = dnsTime;
      }
    }

    // tcp.time: connectEnd - connectStart
    if (navTiming.connectEnd && navTiming.connectStart) {
      const tcpTime = Math.round(navTiming.connectEnd - navTiming.connectStart);
      if (tcpTime > 0) {
        timing.tcpTime = tcpTime;
      }
    }

    // dom.processing_time: domComplete - domInteractive
    if (navTiming.domComplete && navTiming.domInteractive) {
      const domProcessingTime = Math.round(
        navTiming.domComplete - navTiming.domInteractive,
      );
      if (domProcessingTime > 0) {
        timing.domProcessingTime = domProcessingTime;
      }
    }

    // tti: domInteractive - fetchStart
    if (navTiming.domInteractive && navTiming.fetchStart) {
      const tti = Math.round(navTiming.domInteractive - navTiming.fetchStart);
      if (tti >= 0) {
        timing.tti = tti;
      }
    }

    return timing;
  }

  private emitInitialLoadSignals(sdk: SdkContext): void {
    if (typeof window === "undefined" || !document.readyState) {
      return;
    }

    const emitOnLoad = () => {
      const attributeKeys = PulseWebSemconv.AttributeKey;
      const pulseTypes = PulseWebSemconv.PulseType;
      const logBodies = PulseWebSemconv.LogBody;

      const logger = logs.getLogger(PulseOtelLoggerScope.PULSE_WEB_NAVIGATION);
      if (!logger) return;

      const screenName = this.getCurrentScreenName(sdk);
      const sessionId = sdk.sessionProvider.getSessionId();
      const navTimingType = this.getNavigationTimingType();
      const timing = this.extractTimingData();
      const navType = this.getBrowserNavigationType();
      const docAttrs = this.buildDocAttrs();

      // Emit screen_load first, then screen_interactive (FINAL-PLAN initial lifecycle order)
      const loadAttrs: Record<string, string | number> = {
        [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_LOAD,
        [attributeKeys.SCREEN_NAME]: screenName,
        [attributeKeys.SESSION_ID]: sessionId ?? "",
        [attributeKeys.START_TYPE]: navTimingType,
        ...docAttrs,
      };

      if (navType !== undefined) {
        loadAttrs[attributeKeys.NAVIGATION_TYPE] = navType;
      }

      if (timing.pageLoadTime !== undefined) {
        loadAttrs[attributeKeys.PAGE_LOAD_TIME] = timing.pageLoadTime;
      }
      if (timing.ttfb !== undefined) {
        loadAttrs[attributeKeys.TTFB] = timing.ttfb;
      }
      if (timing.dnsTime !== undefined) {
        loadAttrs[attributeKeys.DNS_TIME] = timing.dnsTime;
      }
      if (timing.tcpTime !== undefined) {
        loadAttrs[attributeKeys.TCP_TIME] = timing.tcpTime;
      }
      if (timing.domProcessingTime !== undefined) {
        loadAttrs[attributeKeys.DOM_PROCESSING_TIME] = timing.domProcessingTime;
      }

      logger.emit({
        body: logBodies.SCREEN_LOAD,
        attributes: loadAttrs,
      });

      const interactiveAttrs: Record<string, string | number> = {
        [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_INTERACTIVE,
        [attributeKeys.SCREEN_NAME]: screenName,
        [attributeKeys.SESSION_ID]: sessionId ?? "",
        [attributeKeys.START_TYPE]: navTimingType,
        ...docAttrs,
      };

      if (timing.tti !== undefined) {
        interactiveAttrs[attributeKeys.TTI] = timing.tti;
      }

      logger.emit({
        body: logBodies.SCREEN_INTERACTIVE,
        attributes: interactiveAttrs,
      });
    };

    // Emit on load event
    if (document.readyState === "loading") {
      window.addEventListener("load", emitOnLoad, { once: true });
    } else {
      // If already loaded, emit immediately
      emitOnLoad();
    }
  }
}
