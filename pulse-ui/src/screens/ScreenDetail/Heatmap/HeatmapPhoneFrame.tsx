import type { ReactNode } from "react";
import type { HeatmapBreakpoint } from "./heatmap.types";
import { canonicalHeatmapBreakpoint } from "./heatmapLocalFilters";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapPhoneFrameProps {
  children: ReactNode;
  /** Viewport filter — tweaks inner frame width / aspect. */
  breakpoint?: string;
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
}: HeatmapPhoneFrameProps) {
  const raw = breakpoint?.trim() ?? "";
  const bp = (canonicalHeatmapBreakpoint(raw) || raw) as HeatmapBreakpoint;
  const innerExtra =
    bp && Object.prototype.hasOwnProperty.call(INNER_CLASS, bp)
      ? INNER_CLASS[bp]!
      : "";

  return (
    <div className={classes.phoneFrame}>
      <div
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
