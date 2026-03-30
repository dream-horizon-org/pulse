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
    <>
      <div className={classes.scoreRow}>
        <div className={classes.scoreCard}>
          <Text className={classes.scoreCardLabel}>Heatmap quality</Text>
          <Text className={classes.scoreCardValue}>
            {singlePayload && qualityMetrics.score != null
              ? `${qualityMetrics.score} · ${qualityMetrics.label}`
              : "—"}
          </Text>
          <Text className={classes.scoreCardSub}>
            0–100 from glow bin weights vs total events (telemetry).{" "}
            {focusLens === "key" ? "Key actions lens." : ""}
          </Text>
        </div>
        <div className={classes.scoreCard}>
          <Text className={classes.scoreCardLabel}>
            Pulse Interaction Score
          </Text>
          <Text className={classes.scoreCardValue}>
            {focusLens === "key" ? "0.86" : "—"}
          </Text>
          <Text className={classes.scoreCardSub}>
            {focusLens === "key"
              ? "Starts on this screen (Apdex-style composite)"
              : "Switch to Key actions to preview"}
          </Text>
        </div>
      </div>

      <div className={classes.scoreLegend}>
        <span className={classes.scoreLegendLabel}>
          Heatmap quality color examples:
        </span>
        <span
          className={`${classes.legendChip} ${classes.legendGood}${qualityMetrics.band === "good" ? ` ${classes.legendChipActive}` : ""}`}
        >
          Good
        </span>
        <span
          className={`${classes.legendChip} ${classes.legendAvg}${qualityMetrics.band === "average" ? ` ${classes.legendChipActive}` : ""}`}
        >
          Average
        </span>
        <span
          className={`${classes.legendChip} ${classes.legendPoor}${qualityMetrics.band === "poor" ? ` ${classes.legendChipActive}` : ""}`}
        >
          Poor
        </span>
      </div>
    </>
  );
}
