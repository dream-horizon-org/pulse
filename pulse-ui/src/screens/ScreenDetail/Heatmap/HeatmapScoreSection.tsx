import { Text } from "@mantine/core";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import type { HeatmapFocusLens } from "./heatmapPanelUtils";
import type { HeatmapDataResponse } from "./heatmap.types";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapScoreSectionProps {
  singlePayload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
  focusLens: HeatmapFocusLens;
}

export function HeatmapScoreSection({
  singlePayload,
  qualityMetrics,
  focusLens,
}: HeatmapScoreSectionProps) {
  return (
    <div className={classes.scoreRow}>
      <div className={classes.scoreCard}>
        <Text className={classes.scoreCardLabel}>Heatmap quality</Text>
        <Text className={classes.scoreCardValue}>
          {singlePayload && qualityMetrics.score != null
            ? `${qualityMetrics.score} · ${qualityMetrics.label}`
            : "—"}
        </Text>
        <Text className={classes.scoreCardSub}>
          Touch density vs. sampled sessions.
          {focusLens === "key" ? " Key actions lens." : ""}
        </Text>
      </div>
      <div className={classes.scoreCard}>
        <Text className={classes.scoreCardLabel}>Pulse Interaction Score</Text>
        <Text className={classes.scoreCardValue}>
          {focusLens === "key" ? "0.86" : "—"}
        </Text>
        <Text className={classes.scoreCardSub}>
          {focusLens === "key"
            ? "Screen-level composite (key actions)."
            : "Select Key actions to compute."}
        </Text>
      </div>
    </div>
  );
}
