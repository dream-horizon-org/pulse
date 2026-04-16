import { useEffect, useRef } from "react";
import h337 from "heatmap.js";
import type { HeatmapGlowPoint } from "./heatmap.types";
import { buildHeatmapJsPayload } from "./heatmapDisplay";
import { HEATMAP_JS_GRADIENT } from "./heatmapViz.constants";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapJsCanvasProps {
  displayGlow: HeatmapGlowPoint[];
  sharedWeightMax?: number;
}

const LAYOUT_RETRY_FRAMES = 90;

/** Fraction of min(canvas w,h) used as heatmap.js kernel radius — smaller = tighter bins. */
const RADIUS_FACTOR_FEW = 0.072;
const RADIUS_FACTOR_MANY = 0.04;
const RADIUS_MIN_PX = 10;
/** `BLUR_FEW`: softer falloff when point count is low. `BLUR_MANY`: slightly tighter for dense maps. */
const BLUR_FEW = 0.58;
const BLUR_MANY = 0.64;

export function HeatmapJsCanvas({
  displayGlow,
  sharedWeightMax,
}: HeatmapJsCanvasProps) {
  const outerRef = useRef<HTMLDivElement | null>(null);
  const innerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const outer = outerRef.current;
    const inner = innerRef.current;
    if (!outer || !inner) return undefined;

    let cancelled = false;
    let rafId = 0;
    let frame = 0;

    const doPaint = (w: number, h: number) => {
      if (cancelled || !inner) return;

      inner.innerHTML = "";
      const n = displayGlow.length;
      const radiusFactor =
        n > 0 && n < 140 ? RADIUS_FACTOR_FEW : RADIUS_FACTOR_MANY;
      const radius = Math.max(
        RADIUS_MIN_PX,
        Math.round(Math.min(w, h) * radiusFactor),
      );

      const inst = h337.create({
        container: inner,
        width: w,
        height: h,
        radius,
        /** Keep low-intensity (cool) tail faint — avoids a sheet of gray/blue between peaks. */
        minOpacity: 0.04,
        maxOpacity: 0.48,
        blur: n < 100 ? BLUR_FEW : BLUR_MANY,
        gradient: HEATMAP_JS_GRADIENT,
        backgroundColor: "rgba(0,0,0,0)",
      });

      const { max, data } = buildHeatmapJsPayload(
        displayGlow,
        w,
        h,
        sharedWeightMax,
      );
      inst.setData({ max, data });
    };

    const paintWhenReady = () => {
      if (cancelled || !outer) return;
      const w = outer.clientWidth;
      const h = outer.clientHeight;
      if (w < 8 || h < 8) {
        if (frame++ < LAYOUT_RETRY_FRAMES) {
          rafId = requestAnimationFrame(paintWhenReady);
        }
        return;
      }
      frame = 0;
      doPaint(w, h);
    };

    /** Double rAF: host in flex/aspect-ratio boxes often reports 0×0 on first frame */
    rafId = requestAnimationFrame(() => {
      rafId = requestAnimationFrame(paintWhenReady);
    });

    const ro = new ResizeObserver(() => {
      cancelAnimationFrame(rafId);
      frame = 0;
      paintWhenReady();
    });
    ro.observe(outer);

    return () => {
      cancelled = true;
      cancelAnimationFrame(rafId);
      ro.disconnect();
      inner.innerHTML = "";
    };
  }, [displayGlow, sharedWeightMax]);

  return (
    <div ref={outerRef} className={classes.heatCanvasHost} aria-hidden>
      <div ref={innerRef} className={classes.heatCanvasInner} />
    </div>
  );
}
