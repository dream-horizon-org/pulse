// Web Vitals — OTLP logs (Plan B). See web-sdk-plan/v2-web-vitals/PLAN-B-logs-events.md

import { logs } from "@opentelemetry/api-logs";
import { onCLS, onFCP, onFID, onINP, onLCP } from "web-vitals";
import type { Metric } from "web-vitals";

import type {
  PulseInstrumentation,
  SdkContext,
} from "../instrumentation-registry";
import { PulseWebSemconv } from "../semconv";

export class WebVitalsInstrumentation implements PulseInstrumentation {
  readonly name = "web-vitals";

  private onVisibilityChange?: () => void;
  private onPageShow?: (e: PageTransitionEvent) => void;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined") return;

    const logger = logs.getLogger("pulse-web-vitals");
    const wv = sdk.config.instrumentations?.webVitals;

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

    if (wv?.fid === true) {
      onFID(emit);
    }
    if (wv?.fcp === true) {
      onFCP(emit);
    }

    const flushLogs = (): void => {
      void sdk.loggerProvider?.forceFlush().catch(() => {});
    };

    this.onVisibilityChange = (): void => {
      if (document.visibilityState === "hidden") {
        flushLogs();
      }
    };
    document.addEventListener("visibilitychange", this.onVisibilityChange);

    this.onPageShow = (e: PageTransitionEvent): void => {
      if (e.persisted) {
        flushLogs();
      }
    };
    window.addEventListener("pageshow", this.onPageShow);
  }

  uninstall(): void {
    if (this.onVisibilityChange) {
      document.removeEventListener("visibilitychange", this.onVisibilityChange);
      this.onVisibilityChange = undefined;
    }
    if (this.onPageShow) {
      window.removeEventListener("pageshow", this.onPageShow);
      this.onPageShow = undefined;
    }
  }
}
