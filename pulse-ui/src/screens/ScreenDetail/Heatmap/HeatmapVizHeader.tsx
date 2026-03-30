import classes from "./HeatmapPanel.module.css";

export interface HeatmapVizHeaderProps {
  signalLabel: string;
  totalTapsLabel?: string;
}

export function HeatmapVizHeader({
  signalLabel,
  totalTapsLabel,
}: HeatmapVizHeaderProps) {
  return (
    <div>
      <div className={classes.heatTitle}>
        Sample {signalLabel} heatmap (aggregated)
      </div>
      <div className={classes.heatSubtitle}>
        heatmap.js canvas on normalized coordinates.
        {totalTapsLabel ? ` ${totalTapsLabel}.` : ""}
      </div>
    </div>
  );
}
