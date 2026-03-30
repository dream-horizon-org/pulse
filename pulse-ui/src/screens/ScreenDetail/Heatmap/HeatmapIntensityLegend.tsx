import classes from "./HeatmapPanel.module.css";

export interface HeatmapIntensityLegendProps {
  /** Accessible description of what the gradient represents. */
  "aria-label"?: string;
}

/** Compact activity gradient — sits flush under the phone frame. */
export function HeatmapIntensityLegend({
  "aria-label": ariaLabel = "Activity level from cooler to warmer",
}: HeatmapIntensityLegendProps) {
  return (
    <div
      className={classes.intensityLegendCompact}
      role="img"
      aria-label={ariaLabel}
    >
      <div className={classes.intensityLegendGradientRow}>
        <span className={classes.intensityLegendEnd}>Less activity</span>
        <div className={classes.gradientStripHeatmap} />
        <span className={classes.intensityLegendEnd}>More activity</span>
      </div>
    </div>
  );
}
