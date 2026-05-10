/**
 * Click / tap instrumentation — OTLP **logs** (`pulse.type` = app.click, body = app.widget.click).
 * With default config, uses Android-parity {@link ClickEventBuffer} (rage clustering + delayed singleton taps).
 * See docs/instrumentations/clicks/SPEC.md
 */

import { logs } from "@opentelemetry/api-logs";

import type {
  PulseInstrumentation,
  SdkContext,
} from "../types/instrumentation-registry";
import { PulseWebSemconv } from "../semconv";
import {
  buildClickContextLabel,
  eventComposedPath,
  resolveInteractiveElement,
  widgetIdFromElement,
  widgetNameFromElement,
} from "./click-target";
import {
  ClickEventBuffer,
  type PendingClick,
  type RageEvent,
  resolveClickRageConfig,
} from "./click-rage-buffer";

const keys = PulseWebSemconv.AttributeKey;
const pulseTypes = PulseWebSemconv.PulseType;
const logBodies = PulseWebSemconv.LogBody;
const clickKind = PulseWebSemconv.ClickTypeValue;

export class ClicksInstrumentation implements PulseInstrumentation {
  readonly name = "clicks";
  private onClick?: (ev: Event) => void;
  private onVisibilityChange?: () => void;
  private buffer?: ClickEventBuffer;
  private rageImmediate = false;

  install(sdk: SdkContext): void {
    if (typeof window === "undefined" || typeof document === "undefined") {
      return;
    }

    const logger = logs.getLogger("pulse-web-clicks");
    const rageResolved = resolveClickRageConfig(
      sdk.config.instrumentations?.clicks?.rage,
    );
    this.rageImmediate = rageResolved === null;

    const flushLogs = (): void => {
      void sdk.loggerProvider?.forceFlush().catch(() => {});
    };

    const emitIndividual = (pending: PendingClick): void => {
      const clickType = pending.hasTarget ? clickKind.GOOD : clickKind.DEAD;
      const attrs: Record<string, string | number | boolean> = {
        [keys.PULSE_TYPE]: pulseTypes.APP_CLICK,
        [keys.CLICK_TYPE]: clickType,
        [keys.APP_SCREEN_COORDINATE_X]: pending.xPx,
        [keys.APP_SCREEN_COORDINATE_Y]: pending.yPx,
        [keys.DEVICE_SCREEN_WIDTH]: pending.viewportWidthPx,
        [keys.DEVICE_SCREEN_HEIGHT]: pending.viewportHeightPx,
      };
      const vpW = pending.viewportWidthPx;
      const vpH = pending.viewportHeightPx;
      if (vpW > 0 && vpH > 0) {
        attrs[keys.APP_SCREEN_COORDINATE_NX] = pending.xPx / vpW;
        attrs[keys.APP_SCREEN_COORDINATE_NY] = pending.yPx / vpH;
      }
      if (pending.hasTarget) {
        attrs[keys.APP_WIDGET_NAME] = pending.widgetName as string;
        if (pending.widgetId !== undefined) {
          attrs[keys.APP_WIDGET_ID] = pending.widgetId;
        }
        if (pending.clickContext !== undefined) {
          attrs[keys.APP_CLICK_CONTEXT] = pending.clickContext;
        }
      }
      logger.emit({
        body: logBodies.APP_WIDGET_CLICK,
        attributes: attrs,
      });
    };

    const emitRage = (rage: RageEvent): void => {
      const clickType = rage.hasTarget ? clickKind.GOOD : clickKind.DEAD;
      const attrs: Record<string, string | number | boolean> = {
        [keys.PULSE_TYPE]: pulseTypes.APP_CLICK,
        [keys.CLICK_TYPE]: clickType,
        [keys.CLICK_IS_RAGE]: true,
        [keys.CLICK_RAGE_COUNT]: rage.count,
        [keys.APP_SCREEN_COORDINATE_X]: rage.xPx,
        [keys.APP_SCREEN_COORDINATE_Y]: rage.yPx,
        [keys.DEVICE_SCREEN_WIDTH]: rage.viewportWidthPx,
        [keys.DEVICE_SCREEN_HEIGHT]: rage.viewportHeightPx,
      };
      const vpW = rage.viewportWidthPx;
      const vpH = rage.viewportHeightPx;
      if (vpW > 0 && vpH > 0) {
        attrs[keys.APP_SCREEN_COORDINATE_NX] = rage.xPx / vpW;
        attrs[keys.APP_SCREEN_COORDINATE_NY] = rage.yPx / vpH;
      }
      if (rage.hasTarget) {
        attrs[keys.APP_WIDGET_NAME] = rage.widgetName as string;
        if (rage.widgetId !== undefined) {
          attrs[keys.APP_WIDGET_ID] = rage.widgetId;
        }
        if (rage.clickContext !== undefined) {
          attrs[keys.APP_CLICK_CONTEXT] = rage.clickContext;
        }
      }
      logger.emit({
        body: logBodies.APP_WIDGET_CLICK,
        attributes: attrs,
      });
    };

    if (!this.rageImmediate && rageResolved !== null) {
      const density =
        typeof window !== "undefined" && window.devicePixelRatio > 0
          ? window.devicePixelRatio
          : 1;
      this.buffer = new ClickEventBuffer({
        densityScale: density,
        rageConfig: rageResolved,
        onRage: emitRage,
        onEmit: emitIndividual,
      });
    }

    this.onClick = (ev: Event) => {
      if (typeof window === "undefined") return;

      const path = eventComposedPath(ev);
      const targetEl = resolveInteractiveElement(path);
      const hasTarget = targetEl !== null;
      const me = ev as MouseEvent;
      const xPx = Math.round(me.clientX);
      const yPx = Math.round(me.clientY);
      const vpW = window.innerWidth;
      const vpH = window.innerHeight;
      const tsMono =
        typeof performance !== "undefined" &&
        typeof performance.now === "function"
          ? performance.now()
          : Date.now();
      const tapEpochMs = Date.now();

      let widgetName: string | undefined;
      let widgetId: string | undefined;
      let clickContext: string | undefined;
      if (targetEl !== null) {
        widgetName = widgetNameFromElement(targetEl);
        const wid = widgetIdFromElement(targetEl);
        if (wid !== undefined) widgetId = wid;
        const captureContext =
          sdk.config.instrumentations?.clicks?.captureContext !== false;
        const ctx = buildClickContextLabel(targetEl, captureContext);
        if (ctx !== undefined) clickContext = ctx;
      }

      const pending: PendingClick = {
        xPx,
        yPx,
        timestampMs: tsMono,
        tapEpochMs,
        hasTarget,
        widgetName,
        widgetId,
        clickContext,
        viewportWidthPx: vpW,
        viewportHeightPx: vpH,
      };

      if (this.buffer) {
        this.buffer.record(pending);
        return;
      }

      emitIndividual(pending);
    };

    if (this.buffer) {
      /** Buffer flush on tab backgrounding; SDK `pagehide` + `forceFlush` follows separately. */
      this.onVisibilityChange = (): void => {
        if (document.visibilityState === "hidden") {
          this.buffer?.flush();
          flushLogs();
        }
      };
      document.addEventListener("visibilitychange", this.onVisibilityChange);
    }

    document.addEventListener("click", this.onClick, true);
  }

  uninstall(): void {
    if (this.onVisibilityChange && typeof document !== "undefined") {
      document.removeEventListener("visibilitychange", this.onVisibilityChange);
    }
    this.onVisibilityChange = undefined;

    if (this.onClick && typeof document !== "undefined") {
      document.removeEventListener("click", this.onClick, true);
    }
    this.onClick = undefined;

    this.buffer?.dispose();
    this.buffer = undefined;
  }
}
