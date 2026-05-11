import {
  ROOT_CONTEXT,
  SpanKind,
  SpanStatusCode,
  type Span,
} from "@opentelemetry/api";
import type {
  SdkContext,
  PulseInstrumentation,
} from "../instrumentation-registry";
import { PulseInstrumentationName } from "../constants/pulse-otel-runtime";
import { PulseWebSemconv } from "../semconv";
import { PulseDataCollectionConsent } from "../config";
import { resolveScreenNameFromUrl } from "../processors/global-attrs-processor";

/** OTLP span names — fixed literals for ClickHouse `SpanName` queries (not route strings). */
const SPAN_SCREEN_LOAD = "screen_load";
const SPAN_SCREEN_SESSION = "screen_session";

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
  /** Debounces duplicate History signals — see `docs/instrumentations/screen-signals/SPEC.md` (R2a). */
  private navigationRateLimitMs = 100;
  private currentScreenName = "";
  private screenStartTime = 0;
  /** Screen we navigated from before entering {@link currentScreenName} — maps to `last.screen.name` on exit spans. */
  private enteredFromScreenName = "";
  /** Document URL/title snapshot for the screen covered by {@link activeSessionSpan}. */
  private sessionDocPath = "";
  private sessionDocTitle = "";
  private activeSessionSpan: Span | undefined;

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
    this.enteredFromScreenName = "";

    // Initial page load: screen_load span + screen_session span for dwell time
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
    if (this.sdkContext && this.activeSessionSpan) {
      const now = Date.now();
      this.endActiveSessionSpan(
        this.sdkContext,
        now,
        this.enteredFromScreenName,
        this.sessionDocPath,
        this.sessionDocTitle,
      );
    }

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
    this.activeSessionSpan = undefined;
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

  private captureDocSnapshot(): { path: string; title: string } {
    if (typeof window === "undefined" || typeof document === "undefined") {
      return { path: "", title: "" };
    }
    return {
      path: window.location.pathname,
      title: typeof document.title === "string" ? document.title : "",
    };
  }

  private buildDocAttrsFromSnapshot(
    path: string,
    title: string,
    lastScreenName: string,
  ): Record<string, string | number | boolean> {
    const k = PulseWebSemconv.AttributeKey;
    const out: Record<string, string | number | boolean> = {
      [k.URL_PATH]: path,
      [k.PAGE_TITLE]: title,
    };
    if (lastScreenName) {
      out[k.LAST_SCREEN_NAME] = lastScreenName;
    }
    return out;
  }

  private endActiveSessionSpan(
    sdk: SdkContext,
    endMs: number,
    lastScreenNameAttr: string,
    docPath: string,
    docTitle: string,
  ): void {
    if (!this.activeSessionSpan || !this.currentScreenName) {
      this.activeSessionSpan = undefined;
      return;
    }

    const span = this.activeSessionSpan;
    const durationMs = Math.max(0, endMs - this.screenStartTime);
    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;

    span.setAttributes({
      [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_SESSION,
      [attributeKeys.SCREEN_NAME]: this.currentScreenName,
      [attributeKeys.SESSION_ID]: sdk.sessionProvider.getSessionId() ?? "",
      [attributeKeys.SESSION_DURATION_MS]: durationMs,
      [attributeKeys.SESSION_DURATION]: durationMs,
      ...this.buildDocAttrsFromSnapshot(docPath, docTitle, lastScreenNameAttr),
    });
    span.setStatus({ code: SpanStatusCode.OK });
    span.end(endMs);
    this.activeSessionSpan = undefined;
  }

  private emitPageHideScreenSession(sdk: SdkContext): void {
    if (!this.currentScreenName) return;

    const now = Date.now();
    this.endActiveSessionSpan(
      sdk,
      now,
      this.enteredFromScreenName,
      this.sessionDocPath,
      this.sessionDocTitle,
    );
  }

  private onRouteChange(sdk: SdkContext): void {
    const now = Date.now();
    if (now - this.lastNavigationTime < this.navigationRateLimitMs) {
      return;
    }
    this.lastNavigationTime = now;

    const attributeKeys = PulseWebSemconv.AttributeKey;
    const pulseTypes = PulseWebSemconv.PulseType;

    // URL + config only — History fires synchronously here; React Router / Next
    // typically calls Pulse.setScreenName in useEffect after paint. Using the
    // processor's full getCurrentScreenName() would keep a stale manual override
    // until pathname changes (same-pathname query tweaks) or lag frame behind URL.
    const newScreenName = resolveScreenNameFromUrl(sdk.config);
    const sessionId = sdk.sessionProvider.getSessionId();
    const exitedDocPath = this.sessionDocPath;
    const exitedDocTitle = this.sessionDocTitle;
    const exitedScreen = this.currentScreenName;
    const lastNameForExitedSession = this.enteredFromScreenName;

    // Emit screen_session for the previous screen (if it exists and is different)
    if (exitedScreen && exitedScreen !== newScreenName) {
      this.endActiveSessionSpan(
        sdk,
        now,
        lastNameForExitedSession,
        exitedDocPath,
        exitedDocTitle,
      );

      const spaSnapshot = this.captureDocSnapshot();
      const spaSpan = sdk.tracer.startSpan(
        SPAN_SCREEN_LOAD,
        {
          kind: SpanKind.INTERNAL,
          startTime: now,
        },
        ROOT_CONTEXT,
      );
      spaSpan.setAttributes({
        [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_LOAD,
        [attributeKeys.SCREEN_NAME]: newScreenName,
        [attributeKeys.START_TYPE]: "spa",
        [attributeKeys.SESSION_ID]: sessionId ?? "",
        ...this.buildDocAttrsFromSnapshot(
          spaSnapshot.path,
          spaSnapshot.title,
          exitedScreen,
        ),
      });
      spaSpan.setStatus({ code: SpanStatusCode.OK });
      spaSpan.end(now);

      this.enteredFromScreenName = exitedScreen;
      this.currentScreenName = newScreenName;
      this.screenStartTime = now;
      const dwellSnapshot = this.captureDocSnapshot();
      this.sessionDocPath = dwellSnapshot.path;
      this.sessionDocTitle = dwellSnapshot.title;

      this.activeSessionSpan = sdk.tracer.startSpan(
        SPAN_SCREEN_SESSION,
        {
          kind: SpanKind.INTERNAL,
          startTime: now,
        },
        ROOT_CONTEXT,
      );

      if (typeof sdk.globalAttrsProcessor.setScreenName === "function") {
        sdk.globalAttrsProcessor.setScreenName(newScreenName);
      }
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

  /**
   * Cold load: span times follow Navigation Timing (epoch ms).
   * Fallback when entry missing: marker span (~0 duration) at emit time.
   */
  private resolveColdLoadSpanTimes(): { startMs: number; endMs: number } {
    const navTiming = performance.getEntriesByType("navigation")[0] as
      | PerformanceNavigationTiming
      | undefined;

    const origin = performance.timeOrigin;
    if (
      navTiming &&
      typeof navTiming.loadEventEnd === "number" &&
      navTiming.loadEventEnd > 0
    ) {
      return {
        startMs: origin,
        endMs: origin + navTiming.loadEventEnd,
      };
    }

    const now = Date.now();
    if (
      typeof performance.timeOrigin === "number" &&
      performance.timeOrigin > 0
    ) {
      return {
        startMs: origin,
        endMs: Math.max(origin, now),
      };
    }

    return { startMs: now, endMs: now };
  }

  private emitInitialLoadSignals(sdk: SdkContext): void {
    if (typeof window === "undefined" || !document.readyState) {
      return;
    }

    const emitOnLoad = () => {
      const attributeKeys = PulseWebSemconv.AttributeKey;
      const pulseTypes = PulseWebSemconv.PulseType;

      const screenName = this.getCurrentScreenName(sdk);
      const sessionId = sdk.sessionProvider.getSessionId();
      const navTimingType = this.getNavigationTimingType();
      const timing = this.extractTimingData();
      const navType = this.getBrowserNavigationType();
      const docSnap = this.captureDocSnapshot();

      const { startMs: loadStartMs, endMs: loadEndMs } =
        this.resolveColdLoadSpanTimes();

      const loadAttrs: Record<string, string | number | boolean> = {
        [attributeKeys.PULSE_TYPE]: pulseTypes.SCREEN_LOAD,
        [attributeKeys.SCREEN_NAME]: screenName,
        [attributeKeys.SESSION_ID]: sessionId ?? "",
        [attributeKeys.START_TYPE]: navTimingType,
        ...this.buildDocAttrsFromSnapshot(docSnap.path, docSnap.title, ""),
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
      if (timing.tti !== undefined) {
        loadAttrs[attributeKeys.TTI] = timing.tti;
      }

      const loadSpan = sdk.tracer.startSpan(
        SPAN_SCREEN_LOAD,
        {
          kind: SpanKind.INTERNAL,
          startTime: loadStartMs,
        },
        ROOT_CONTEXT,
      );
      loadSpan.setAttributes(loadAttrs);
      loadSpan.setStatus({ code: SpanStatusCode.OK });
      loadSpan.end(loadEndMs);

      const dwellSnapshot = this.captureDocSnapshot();
      this.sessionDocPath = dwellSnapshot.path;
      this.sessionDocTitle = dwellSnapshot.title;
      this.screenStartTime = loadEndMs;
      this.enteredFromScreenName = "";

      this.activeSessionSpan = sdk.tracer.startSpan(
        SPAN_SCREEN_SESSION,
        {
          kind: SpanKind.INTERNAL,
          startTime: loadEndMs,
        },
        ROOT_CONTEXT,
      );
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
