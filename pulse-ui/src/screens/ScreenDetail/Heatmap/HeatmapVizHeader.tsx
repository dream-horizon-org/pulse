import { Text } from "@mantine/core";
import graphClasses from "../components/EngagementGraph.module.css";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapVizHeaderProps {
  screenName?: string;
  signalLabel: string;
  totalTapsLabel?: string;
}

export function HeatmapVizHeader({
  screenName,
  signalLabel,
  totalTapsLabel,
}: HeatmapVizHeaderProps) {
  const title =
    screenName?.trim() !== ""
      ? `${screenName} · ${signalLabel} heatmap`
      : `${signalLabel.charAt(0).toUpperCase()}${signalLabel.slice(1)} heatmap`;

  return (
    <div className={classes.heatVizHeader}>
      <div className={graphClasses.graphTitle}>{title}</div>
      <Text size="sm" c="dimmed" mt="xs" className={classes.heatSubtitle}>
        Warmer colors mean more activity at that spot on the layout.
        {totalTapsLabel ? ` ${totalTapsLabel}.` : ""}
      </Text>
    </div>
  );
}
