import type { ReactNode } from "react";
import type { HeatmapBreakpoint } from "./heatmap.types";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapPhoneFrameProps {
  children: ReactNode;
  /** Viewport filter — tweaks inner frame width / aspect. */
  breakpoint?: string;
}

const INNER_CLASS: Partial<Record<HeatmapBreakpoint, string>> = {
  small_mobile: classes.phoneInnerSmallMobile,
  medium_folding: classes.phoneInnerMediumFolding,
  medium_mobile: classes.phoneInnerMediumMobile,
  medium_mobile_wide: classes.phoneInnerMediumWide,
  large_tablet: classes.phoneInnerLargeTablet,
  extra_large_web: classes.phoneInnerExtraLargeWeb,
};

export function HeatmapPhoneFrame({
  children,
  breakpoint = "",
}: HeatmapPhoneFrameProps) {
  const bp = breakpoint?.trim() as HeatmapBreakpoint;
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
