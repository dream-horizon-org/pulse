import { Group, Paper, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import {
  HEATMAP_QUALITY_AVERAGE_MIN,
  HEATMAP_QUALITY_GOOD_MIN,
  type HeatmapQualityMetrics,
} from "./heatmapQuality";
import type { HeatmapDataResponse } from "./heatmap.types";
import classes from "./HeatmapPanel.module.css";

const tealValue = "#0ec9c2";

export interface HeatmapAggregatesQualityCardProps {
  payload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
}

/** Map quality as a compact card matching other aggregate tiles (right panel). */
export function HeatmapAggregatesQualityCard({
  payload,
  qualityMetrics,
}: HeatmapAggregatesQualityCardProps) {
  const hasScore = payload && qualityMetrics.score != null;

  return (
    <Paper className={classes.aggregatesSubCard} radius="sm" p={8} withBorder>
      <Group gap={6} align="center" mb={6} wrap="nowrap">
        <Text
          className={classes.aggregatesCardTitle}
          size="xs"
          fw={700}
          c="#0ba09a"
          tt="uppercase"
          style={{ letterSpacing: "0.05em", marginBottom: 0 }}
        >
          Map quality
        </Text>
        <Tooltip
          label="How readable this heatmap is from event data (volume in bins vs hotspot concentration). Not a product score."
          multiline
          w={260}
          withArrow
        >
          <span className={classes.summaryQualityInfo} aria-label="About map quality">
            <IconInfoCircle size={14} stroke={1.5} />
          </span>
        </Tooltip>
      </Group>

      {hasScore ? (
        <Tooltip
          label="~35% how much event weight is in the map vs total events, ~65% how peaked the hottest area is."
          multiline
          w={260}
          withArrow
        >
          <Text
            component="div"
            className={classes.aggregatesQualityScore}
            style={{ color: tealValue, cursor: "help", marginBottom: 6 }}
          >
            {`${qualityMetrics.score} · ${qualityMetrics.label}`}
          </Text>
        </Tooltip>
      ) : (
        <Text component="div" c="dimmed" className={classes.aggregatesQualityScore} mb={6}>
          —
        </Text>
      )}

      <div className={classes.aggregatesQualityChips}>
        <Tooltip
          label={`Score ${HEATMAP_QUALITY_GOOD_MIN}+: map is easy to read and act on.`}
          withArrow
        >
          <span
            className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendGood}${qualityMetrics.band === "good" ? ` ${classes.legendChipActive}` : ""}`}
          >
            Good
          </span>
        </Tooltip>
        <Tooltip
          label={`Score ${HEATMAP_QUALITY_AVERAGE_MIN}–${HEATMAP_QUALITY_GOOD_MIN - 1}: usable but noisier or flatter hotspots.`}
          withArrow
        >
          <span
            className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendAvg}${qualityMetrics.band === "average" ? ` ${classes.legendChipActive}` : ""}`}
          >
            Average
          </span>
        </Tooltip>
        <Tooltip
          label={`Score below ${HEATMAP_QUALITY_AVERAGE_MIN}: harder to trust patterns from this aggregation.`}
          withArrow
        >
          <span
            className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendPoor}${qualityMetrics.band === "poor" ? ` ${classes.legendChipActive}` : ""}`}
          >
            Poor
          </span>
        </Tooltip>
      </div>

      <Text size="10px" c="dimmed" lh={1.4} mt={6}>
        Good {HEATMAP_QUALITY_GOOD_MIN}–100 · Average {HEATMAP_QUALITY_AVERAGE_MIN}–
        {HEATMAP_QUALITY_GOOD_MIN - 1} · Poor 0–{HEATMAP_QUALITY_AVERAGE_MIN - 1}
      </Text>
      <Text size="10px" c="dimmed" lh={1.4} mt={4}>
        Good = clearer hotspots and coverage; Poor = sparse or flat signal for this screen
        and filters.
      </Text>
    </Paper>
  );
}
