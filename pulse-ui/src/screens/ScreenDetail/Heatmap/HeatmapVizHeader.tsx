import type { HeatmapRendererMode } from "./heatmapViz.types";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapVizHeaderProps {
  signalLabel: string;
  renderer: HeatmapRendererMode;
  totalTapsLabel?: string;
}

export function HeatmapVizHeader({
  signalLabel,
  renderer,
  totalTapsLabel,
}: HeatmapVizHeaderProps) {
  const rendererHint =
    renderer === "heatmapjs"
      ? "heatmap.js canvas on normalized points."
      : "DOM radial plumes (large point sets are bucketed for performance).";

  return (
    <div>
      <div className={classes.heatTitle}>
        Sample {signalLabel} heatmap (aggregated)
      </div>
      <div className={classes.heatSubtitle}>
        {rendererHint}
        {totalTapsLabel ? ` ${totalTapsLabel}.` : ""}
      </div>
    </div>
  );
}
