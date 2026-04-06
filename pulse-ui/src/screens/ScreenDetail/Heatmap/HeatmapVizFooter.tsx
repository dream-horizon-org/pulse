import { Slider } from "@mantine/core";
import { isHeatmapMockServerEnabled } from "./heatmapMockDev";
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
  if (!isHeatmapMockServerEnabled()) {
    return null;
  }

  return (
    <>
      <div className={classes.binBudgetRow}>
        <span className={classes.gradientCaption}>Fewer taps</span>
        <Slider
          className={classes.binBudgetSlider}
          min={binBudgetMax > 0 ? 1 : 0}
          max={Math.max(binBudgetMax, 1)}
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
    </>
  );
}
