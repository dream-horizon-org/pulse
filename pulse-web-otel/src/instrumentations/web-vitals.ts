// Web Vitals — OTLP logs (Plan B). See docs/instrumentations/web-vitals/SPEC.md

import { logs } from "@opentelemetry/api-logs";
import { onCLS, onFCP, onFID, onINP, onLCP, onTTFB } from "web-vitals";
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

export class WebVitalsInstrumentation implements PulseInstrumentation {
  readonly name = PulseInstrumentationName.WEB_VITALS;

  private onVisibilityChange?: () => void;
  private onPageShow?: (e: PageTransitionEvent) => void;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    const logger = logs.getLogger(PulseOtelLoggerScope.PULSE_WEB_VITALS);

    const emit = (metric: Metric): void => {
      const attrs: Record<string, string | number | boolean> = {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.WEB_VITAL,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_NAME]: metric.name,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_VALUE]: metric.value,
        [PulseWebSemconv.AttributeKey.WEB_VITAL_RATING]: metric.rating,
      };
      if (metric.navigationType !== undefined) {
        attrs[PulseWebSemconv.AttributeKey.WEB_VITAL_NAVIGATION_TYPE] =
          metric.navigationType;
      }
      logger.emit({
        body: PulseWebSemconv.LogBody.WEB_VITAL,
        attributes: attrs,
      });
    };

    onLCP(emit);
    onINP(emit);
    onCLS(emit);
    onFCP(emit);
    onFID(emit);
    onTTFB(emit);

    const flushLogs = (): void => {
      void sdk.loggerProvider?.forceFlush().catch(() => {});
    };

    this.onVisibilityChange = (): void => {
      if (document.visibilityState === DomVisibilityState.HIDDEN) {
        flushLogs();
      }
    };
    document.addEventListener(
      DomEventType.VISIBILITY_CHANGE,
      this.onVisibilityChange,
    );

    this.onPageShow = (e: PageTransitionEvent): void => {
      if (e.persisted) {
        flushLogs();
      }
    };
    window.addEventListener(DomEventType.PAGESHOW, this.onPageShow);
  }

  uninstall(): void {
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
