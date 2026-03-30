import { Slider } from "@mantine/core";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapVizFooterProps {
  glowMapLength: number;
  displayCount: number;
  binBudgetMax: number;
  effectiveBudget: number;
  onBudgetChange: (v: number) => void;
}

export function HeatmapVizFooter({
  glowMapLength,
  displayCount,
  binBudgetMax,
  effectiveBudget,
  onBudgetChange,
}: HeatmapVizFooterProps) {
  return (
    <>
      <div className={classes.binBudgetRow}>
        <span className={classes.gradientCaption}>Fewer taps</span>
        <Slider
          className={classes.binBudgetSlider}
          min={32}
          max={binBudgetMax}
          step={1}
          value={effectiveBudget}
          onChange={onBudgetChange}
          disabled={glowMapLength === 0}
          size="xs"
          label={null}
        />
        <span className={classes.gradientCaption}>More taps</span>
      </div>
      <div className={classes.heatSubtitle}>
        Showing top {displayCount} of {glowMapLength} bins by weight (normalized
        0–1; underlay for alignment only).
      </div>

      <div className={classes.gradientLegend}>
        <span className={classes.gradientCaption}>Cooler</span>
        <div className={classes.gradientStrip} />
        <span className={classes.gradientCaption}>Hotter</span>
      </div>
      <div className={classes.heatSubtitle} style={{ marginTop: 4 }}>
        Normalized coordinates; screenshot is alignment only.
      </div>
    </>
  );
}
