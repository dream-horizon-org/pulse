import type { ReactNode } from "react";
import { useEffect, useRef } from "react";
import type { HeatmapBreakpoint } from "./heatmap.types";
import { canonicalHeatmapBreakpoint } from "./heatmapLocalFilters";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapPhoneFrameProps {
  children: ReactNode;
  /** Viewport filter — tweaks inner frame width / aspect. */
  breakpoint?: string;
  /** Report laid-out size (CSS px) of the inner phone slot — matches on-screen frame. */
  onInnerLayout?: (width: number, height: number) => void;
}

const INNER_CLASS: Partial<Record<HeatmapBreakpoint, string>> = {
  Mobile_Small: classes.phoneInnerSmallMobile,
  Mobile_Medium: classes.phoneInnerMediumMobile,
  Tablet_Large: classes.phoneInnerLargeTablet,
  Web_Extra_Large: classes.phoneInnerExtraLargeWeb,
};

export function HeatmapPhoneFrame({
  children,
  breakpoint = "",
  onInnerLayout,
}: HeatmapPhoneFrameProps) {
  const innerRef = useRef<HTMLDivElement | null>(null);
  const raw = breakpoint?.trim() ?? "";
  const bp = (canonicalHeatmapBreakpoint(raw) || raw) as HeatmapBreakpoint;
  const innerExtra =
    bp && Object.prototype.hasOwnProperty.call(INNER_CLASS, bp)
      ? INNER_CLASS[bp]!
      : "";

  useEffect(() => {
    const el = innerRef.current;
    if (!el || !onInnerLayout) return undefined;
    const report = () => {
      const r = el.getBoundingClientRect();
      const w = Math.round(r.width);
      const h = Math.round(r.height);
      if (w > 0 && h > 0) onInnerLayout(w, h);
    };
    report();
    const ro = new ResizeObserver(report);
    ro.observe(el);
    return () => ro.disconnect();
  }, [onInnerLayout, breakpoint]);

  return (
    <div className={classes.phoneFrame}>
      <div
        ref={innerRef}
        className={
          innerExtra
            ? `${classes.phoneInner} ${innerExtra}`
            : classes.phoneInner
        }
      >
        {children}
      </div>
    </div>
  );
}
