// Web Vitals — OTLP log records (pulse.type web_vital). See docs/instrumentations/web-vitals/SPEC.md

import { logs } from "@opentelemetry/api-logs";
import { onCLS, onFCP, onINP, onLCP, onTTFB } from "web-vitals";
import type { Metric } from "web-vitals";

import type {
  PulseInstrumentation,
  SdkContext,
} from "../instrumentation-registry";
import {
  DomEventType,
  DomVisibilityState,
  PulseInstrumentationName,
  PulseOtelLoggerScope,
} from "../constants/pulse-otel-runtime";
import { PulseWebSemconv } from "../semconv";

/** Exported for unit tests — maps `Metric.navigationType` to Pulse `web_vital.context`. */
export function webVitalContextFromNavigationType(
  navigationType: string,
): "pageload" | "navigation" {
  return navigationType === "soft-navigation" ? "navigation" : "pageload";
}

export class WebVitalsInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.WEB_VITALS;

  private onVisibilityChange?: () => void;
  private onPageShow?: (e: PageTransitionEvent) => void;

  /**
   * When false, metric callbacks from `web-vitals` must not call `logger.emit`.
   * The `web-vitals` library does not expose unsubscribe handles; we guard at the emit boundary.
   */
  private reportingEnabled = false;

  /**
   * Incremented on each `install` and `uninstall`. Each `install` captures the value in metric
   * callbacks so stale `web-vitals` registrations from a prior install cannot emit after reinstall.
   */
  private callbackEpoch = 0;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    const myEpoch = ++this.callbackEpoch;
    this.reportingEnabled = true;

    const logger = logs.getLogger(PulseOtelLoggerScope.PULSE_WEB_VITALS);

    const emit = (metric: Metric): void => {
      if (this.callbackEpoch !== myEpoch) {
        return;
      }
      if (!this.reportingEnabled) {
        return;
      }
      const attributeKeys = PulseWebSemconv.AttributeKey;
      const attrs: Record<string, string | number | boolean> = {
        [attributeKeys.PULSE_TYPE]: PulseWebSemconv.PulseType.WEB_VITAL,
        [attributeKeys.WEB_VITAL_NAME]: metric.name,
        [attributeKeys.WEB_VITAL_VALUE]: metric.value,
        [attributeKeys.WEB_VITAL_RATING]: metric.rating,
        [attributeKeys.WEB_VITAL_NAVIGATION_TYPE]: metric.navigationType,
        [attributeKeys.WEB_VITAL_CONTEXT]: webVitalContextFromNavigationType(
          metric.navigationType,
        ),
        [attributeKeys.WEB_VITAL_DELTA]: metric.delta,
      };
      logger.emit({
        body: PulseWebSemconv.LogBody.WEB_VITAL,
        attributes: attrs,
      });
    };

    onLCP(emit);
    onINP(emit, { reportAllChanges: true });
    onCLS(emit, { reportAllChanges: true });
    onFCP(emit);
    onTTFB(emit);

    const flushLogs = (): void => {
      if (this.callbackEpoch !== myEpoch) {
        return;
      }
      void sdk.loggerProvider?.forceFlush().catch(() => {});
    };

    this.onVisibilityChange = (): void => {
      if (this.callbackEpoch !== myEpoch) {
        return;
      }
      if (document.visibilityState === DomVisibilityState.HIDDEN) {
        flushLogs();
      }
    };
    document.addEventListener(
      DomEventType.VISIBILITY_CHANGE,
      this.onVisibilityChange,
    );

    this.onPageShow = (e: PageTransitionEvent): void => {
      if (this.callbackEpoch !== myEpoch) {
        return;
      }
      if (e.persisted) {
        flushLogs();
      }
    };
    window.addEventListener(DomEventType.PAGESHOW, this.onPageShow);
  }

  uninstall(): void {
    this.reportingEnabled = false;
    this.callbackEpoch++;
    if (this.onVisibilityChange) {
      document.removeEventListener(
        DomEventType.VISIBILITY_CHANGE,
        this.onVisibilityChange,
      );
      this.onVisibilityChange = undefined;
    }
    if (this.onPageShow) {
      window.removeEventListener(DomEventType.PAGESHOW, this.onPageShow);
      this.onPageShow = undefined;
    }
  }
}
