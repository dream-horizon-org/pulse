import type { HeatmapPlumePalette } from "./heatmapDomUtils";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapIntensityLegendProps {
  /** Accessible description of what the gradient represents. */
  "aria-label"?: string;
  /** Matches plume colors: thermal (tap) vs brand teal (rage/dead). */
  variant?: HeatmapPlumePalette;
}

/** Compact activity gradient — sits flush under the phone frame. */
export function HeatmapIntensityLegend({
  "aria-label": ariaLabel = "Activity level from cooler to warmer",
  variant = "thermal",
}: HeatmapIntensityLegendProps) {
  const stripClass =
    variant === "brand"
      ? classes.gradientStripHeatmap
      : classes.gradientStripHeatmapThermal;

  return (
    <div
      className={classes.intensityLegendCompact}
      role="img"
      aria-label={ariaLabel}
    >
      <div className={classes.intensityLegendGradientRow}>
        <span className={classes.intensityLegendEnd}>Less activity</span>
        <div className={stripClass} />
        <span className={classes.intensityLegendEnd}>More activity</span>
      </div>
    </div>
  );
}
