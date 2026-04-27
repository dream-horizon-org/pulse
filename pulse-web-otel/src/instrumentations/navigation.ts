// M4: Navigation instrumentation — tracks initial page load performance and
// SPA route changes as spans. Owns screen_load, screen_interactive, screen_session
// signal types. Android parity: ActivityInstrumentation, FragmentInstrumentation.

import { SpanKind, ROOT_CONTEXT } from "@opentelemetry/api";
import type { Tracer } from "@opentelemetry/api";
import type { PulseInstrumentation, SdkContext } from "../instrumentation-registry";
import { PulseWebSemconv } from "../semconv";

export class NavigationInstrumentation implements PulseInstrumentation {
  readonly name = "navigation";

  private currentRoute = "";
  private currentScreenName = "";
  private lastScreenName = "";
  private routeStartTime = 0; // performance.now() at route start
  private sdk?: SdkContext;
  private tracer?: Tracer;

  // Bound handler refs for cleanup
  private onPopState?: () => void;
  private onPageHide?: () => void;
  private origPushState?: typeof history.pushState;
  private origReplaceState?: typeof history.replaceState;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;
    this.sdk = sdk;
    this.tracer = sdk.tracer;

    this.currentRoute = window.location.pathname;
    this.currentScreenName = this.resolveScreenName(this.currentRoute);
    this.routeStartTime = performance.now();

    // Update global attrs processor with initial last screen name (empty on first load)
    sdk.globalAttrsProcessor?.setLastScreenName?.("");

    // 1. Initial page load spans
    this.capturePageLoad();

    // 2. SPA: patch History API
    this.patchHistoryApi();

    // 3. Browser back/forward
    this.onPopState = () => this.onRouteChange(window.location.pathname);
    window.addEventListener("popstate", this.onPopState);

    // 4. Tab close / unload — emit final session
    this.onPageHide = () => this.endCurrentSession();
    window.addEventListener("pagehide", this.onPageHide);
  }

  uninstall(): void {
    // Restore patched history methods
    if (this.origPushState) {
      history.pushState = this.origPushState;
      this.origPushState = undefined;
    }
    if (this.origReplaceState) {
      history.replaceState = this.origReplaceState;
      this.origReplaceState = undefined;
    }
    if (this.onPopState) {
      window.removeEventListener("popstate", this.onPopState);
      this.onPopState = undefined;
    }
    if (this.onPageHide) {
      window.removeEventListener("pagehide", this.onPageHide);
      this.onPageHide = undefined;
    }
    this.sdk = undefined;
  }

  /** Called by framework integrations (useRouterTracking hook, Next.js) */
  onRouteChange(newPathname: string): void {
    this.endCurrentSession();
    this.lastScreenName = this.currentScreenName;
    this.currentRoute = newPathname;
    this.currentScreenName = this.resolveScreenName(newPathname);
    this.routeStartTime = performance.now();

    // Update global attrs processor with the previous screen name
    this.sdk?.globalAttrsProcessor?.setLastScreenName?.(this.lastScreenName);
    // Clear manual screen name override by having globalAttrsProcessor resolve fresh from URL
    // (getCurrentScreenName() detects URL change and auto-clears when path differs)
  }

  private capturePageLoad(): void {
    const emit = () => {
      const entries = performance.getEntriesByType("navigation");
      const nav = entries[0] as PerformanceNavigationTiming | undefined;
      if (!nav || nav.loadEventEnd === 0) {
        // Not ready — wait for load
        window.addEventListener("load", emit, { once: true });
        return;
      }
      this.emitPageLoadSpans(nav);
    };

    if (document.readyState === "complete") {
      // Use setTimeout to ensure span is created after SDK is fully set up
      setTimeout(emit, 0);
    } else {
      window.addEventListener("load", emit, { once: true });
    }
  }

  private emitPageLoadSpans(nav: PerformanceNavigationTiming): void {
    if (!this.sdk || !this.tracer) return;
    const tracer = this.tracer;
    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;

    const origin = performance.timeOrigin;
    const pathname = window.location.pathname;
    const screenName = this.resolveScreenName(pathname);

    // screen_load span
    const loadStart = origin + nav.startTime;
    const loadEnd = origin + nav.loadEventEnd;

    const loadSpan = tracer.startSpan(
      "screen_load",
      {
        kind: SpanKind.INTERNAL,
        startTime: loadStart,
        attributes: {
          [K.PULSE_TYPE]: T.SCREEN_LOAD,
          [K.SCREEN_NAME]: screenName,
          [K.URL_PATH]: pathname,
          [K.PAGE_TITLE]: document.title,
          [K.NAVIGATION_TYPE]: nav.type,
          [K.START_TYPE]: nav.type === "navigate" ? "cold" : nav.type,
          [K.PAGE_LOAD_TIME]: Math.round(nav.loadEventEnd - nav.startTime),
          [K.DNS_TIME]: Math.round(nav.domainLookupEnd - nav.domainLookupStart),
          [K.TCP_TIME]: Math.round(nav.connectEnd - nav.connectStart),
          [K.TTFB]: Math.round(nav.responseStart - nav.requestStart),
          [K.DOM_PROCESSING_TIME]: Math.round(nav.domComplete - nav.domInteractive),
        },
      },
      ROOT_CONTEXT,
    );
    loadSpan.end(loadEnd);

    // screen_interactive span
    const interactiveEnd = origin + nav.domInteractive;
    const interactiveSpan = tracer.startSpan(
      "screen_interactive",
      {
        kind: SpanKind.INTERNAL,
        startTime: loadStart,
        attributes: {
          [K.PULSE_TYPE]: T.SCREEN_INTERACTIVE,
          [K.SCREEN_NAME]: screenName,
          [K.URL_PATH]: pathname,
          [K.TTI]: Math.round(nav.domInteractive - nav.startTime),
        },
      },
      ROOT_CONTEXT,
    );
    interactiveSpan.end(interactiveEnd);
  }

  private endCurrentSession(): void {
    if (!this.sdk || !this.tracer || !this.currentRoute) return;
    const duration = performance.now() - this.routeStartTime;
    if (duration < 100) return; // ignore sub-100ms accidental navigations

    const tracer = this.tracer;
    const K = PulseWebSemconv.AttributeKey;
    const T = PulseWebSemconv.PulseType;

    const sessionStart = performance.timeOrigin + this.routeStartTime;
    const sessionEnd = performance.timeOrigin + performance.now();

    const span = tracer.startSpan(
      "screen_session",
      {
        kind: SpanKind.INTERNAL,
        startTime: sessionStart,
        attributes: {
          [K.PULSE_TYPE]: T.SCREEN_SESSION,
          [K.SCREEN_NAME]: this.currentScreenName,
          [K.LAST_SCREEN_NAME]: this.lastScreenName,
          // previous_screen.name = the screen the user came from (before this screen)
          [K.PREVIOUS_SCREEN_NAME]: this.lastScreenName,
          [K.URL_PATH]: this.currentRoute,
          [K.SESSION_DURATION]: Math.round(duration),
        },
      },
      ROOT_CONTEXT,
    );
    span.end(sessionEnd);
  }

  private patchHistoryApi(): void {
    // Save original references (not bound — restore by reference equality)
    this.origPushState = history.pushState;
    this.origReplaceState = history.replaceState;

    const self = this;
    const origPush = this.origPushState;
    const origReplace = this.origReplaceState;

    history.pushState = function (...args: Parameters<typeof history.pushState>) {
      origPush.apply(history, args);
      // Only treat as a route change if the pathname actually changed.
      // Same-route pushState (e.g. query-param updates) must not split the session.
      if (window.location.pathname !== self.currentRoute) {
        self.onRouteChange(window.location.pathname);
      }
    };

    history.replaceState = function (...args: Parameters<typeof history.replaceState>) {
      origReplace.apply(history, args);
      // replaceState = URL cleanup (e.g. removing auth tokens from query string)
      // Don't start a new session — just update the tracked route silently
      self.currentRoute = window.location.pathname;
      self.currentScreenName = self.resolveScreenName(self.currentRoute);
    };
  }

  /** 4-step screen name resolution chain — delegates to globalAttrsProcessor */
  private resolveScreenName(_pathname: string): string {
    if (this.sdk?.globalAttrsProcessor) {
      return this.sdk.globalAttrsProcessor.getCurrentScreenName();
    }
    // Fallback if processor not available
    return _pathname;
  }
}
