import { Group, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import {
  HEATMAP_QUALITY_AVERAGE_MIN,
  HEATMAP_QUALITY_GOOD_MIN,
  type HeatmapQualityMetrics,
} from "./heatmapQuality";
import type { HeatmapDataResponse } from "./heatmap.types";
import graphClasses from "../components/EngagementGraph.module.css";
import classes from "./HeatmapPanel.module.css";

const tealValue = "#0ec9c2";

export interface HeatmapQualityInlineProps {
  singlePayload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
}

/** Map quality as a metric tile + band chips + score range mapping (Summary card). */
export function HeatmapQualityInline({
  singlePayload,
  qualityMetrics,
}: HeatmapQualityInlineProps) {
  const hasScore = singlePayload && qualityMetrics.score != null;

  return (
    <div className={classes.heatmapQualityRow}>
      <div className={graphClasses.metricCard} style={{ flex: "1 1 160px", textAlign: "left" }}>
        <Group gap={6} align="center" mb={6} wrap="nowrap">
          <Text className={graphClasses.metricLabel} style={{ marginBottom: 0 }}>
            Map quality
          </Text>
          <Tooltip
            label="How readable this heatmap is from event data (volume in bins vs hotspot concentration). Not a product score."
            multiline
            w={260}
            withArrow
          >
            <span className={classes.summaryQualityInfo} aria-label="About map quality">
              <IconInfoCircle size={15} stroke={1.5} />
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
              className={graphClasses.metricValue}
              style={{ color: tealValue, cursor: "help" }}
            >
              {`${qualityMetrics.score} · ${qualityMetrics.label}`}
            </Text>
          </Tooltip>
        ) : (
          <Text className={graphClasses.metricValue} c="dimmed" component="div">
            —
          </Text>
        )}
      </div>

      <div className={classes.qualityChipsColumn}>
        <Text size="10px" fw={700} c="dimmed" tt="uppercase" mb={6} style={{ letterSpacing: "0.06em" }}>
          Band
        </Text>
        <Text size="xs" c="dimmed" mb="xs" lh={1.45}>
          Score bands: Good {HEATMAP_QUALITY_GOOD_MIN}–100 · Average{" "}
          {HEATMAP_QUALITY_AVERAGE_MIN}–{HEATMAP_QUALITY_GOOD_MIN - 1} · Poor 0–
          {HEATMAP_QUALITY_AVERAGE_MIN - 1}
        </Text>
        <Group gap="xs" wrap="wrap" mb="xs">
          <Tooltip
            label={`Score ${HEATMAP_QUALITY_GOOD_MIN}+: map is easy to read and act on.`}
            withArrow
          >
            <span
              className={`${classes.legendChip} ${classes.legendGood}${qualityMetrics.band === "good" ? ` ${classes.legendChipActive}` : ""}`}
            >
              Good
            </span>
          </Tooltip>
          <Tooltip
            label={`Score ${HEATMAP_QUALITY_AVERAGE_MIN}–${HEATMAP_QUALITY_GOOD_MIN - 1}: usable but noisier or flatter hotspots.`}
            withArrow
          >
            <span
              className={`${classes.legendChip} ${classes.legendAvg}${qualityMetrics.band === "average" ? ` ${classes.legendChipActive}` : ""}`}
            >
              Average
            </span>
          </Tooltip>
          <Tooltip
            label={`Score below ${HEATMAP_QUALITY_AVERAGE_MIN}: harder to trust patterns from this aggregation.`}
            withArrow
          >
            <span
              className={`${classes.legendChip} ${classes.legendPoor}${qualityMetrics.band === "poor" ? ` ${classes.legendChipActive}` : ""}`}
            >
              Poor
            </span>
          </Tooltip>
        </Group>
        <Text size="xs" c="dimmed" lh={1.45}>
          Good = clearer hotspots and better coverage in the map; Poor = sparse or
          flat signal for this screen and filters.
        </Text>
      </div>
    </div>
  );
}
