/**
 * Click / tap instrumentation — OTLP **logs** (`pulse.type` = app.click, body = app.widget.click).
 *
 * Rage clicks and backend `rage.*` config keys are not consumed until Phase D
 * (Android `ClickEventBuffer` parity).
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

const keys = PulseWebSemconv.AttributeKey;
const pulseTypes = PulseWebSemconv.PulseType;
const logBodies = PulseWebSemconv.LogBody;
const clickKind = PulseWebSemconv.ClickTypeValue;

export class ClicksInstrumentation implements PulseInstrumentation {
  readonly name = "clicks";
  private onClick?: (ev: Event) => void;

  install(sdk: SdkContext): void {
    if (typeof document === "undefined") return;

    const logger = logs.getLogger("pulse-web-clicks");

    /**
     * Mobile Safari note: relying on the `click` event's `clientX`/`clientY` is correct for
     * mouse and keyboard-activated controls; some mobile browsers report `(0,0)` for certain
     * synthetic taps — optional follow-up: cache last `pointerdown` position.
     */
    this.onClick = (ev: Event) => {
      if (typeof window === "undefined") return;

      const path = eventComposedPath(ev);
      const targetEl = resolveInteractiveElement(path);
      const clickType = targetEl === null ? clickKind.DEAD : clickKind.GOOD;

      const me = ev as MouseEvent;
      const xPx = Math.round(me.clientX);
      const yPx = Math.round(me.clientY);
      const vpW = window.innerWidth;
      const vpH = window.innerHeight;

      const attrs: Record<string, string | number | boolean> = {
        [keys.PULSE_TYPE]: pulseTypes.APP_CLICK,
        [keys.CLICK_TYPE]: clickType,
        [keys.APP_SCREEN_COORDINATE_X]: xPx,
        [keys.APP_SCREEN_COORDINATE_Y]: yPx,
        [keys.DEVICE_SCREEN_WIDTH]: vpW,
        [keys.DEVICE_SCREEN_HEIGHT]: vpH,
      };

      if (vpW > 0 && vpH > 0) {
        attrs[keys.APP_SCREEN_COORDINATE_NX] = xPx / vpW;
        attrs[keys.APP_SCREEN_COORDINATE_NY] = yPx / vpH;
      }

      if (targetEl !== null) {
        attrs[keys.APP_WIDGET_NAME] = widgetNameFromElement(targetEl);
        const wid = widgetIdFromElement(targetEl);
        if (wid !== undefined) attrs[keys.APP_WIDGET_ID] = wid;

        const captureContext =
          sdk.config.instrumentations?.clicks?.captureContext !== false;
        const ctx = buildClickContextLabel(targetEl, captureContext);
        if (ctx !== undefined) attrs[keys.APP_CLICK_CONTEXT] = ctx;
      }

      logger.emit({
        body: logBodies.APP_WIDGET_CLICK,
        attributes: attrs,
      });
    };

    document.addEventListener("click", this.onClick, true);
  }

  uninstall(): void {
    if (this.onClick && typeof document !== "undefined") {
      document.removeEventListener("click", this.onClick, true);
    }
    this.onClick = undefined;
  }
}
