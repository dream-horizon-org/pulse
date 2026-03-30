import { Group, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import type { HeatmapDataResponse } from "./heatmap.types";
import graphClasses from "../components/EngagementGraph.module.css";
import classes from "./HeatmapPanel.module.css";

const tealValue = "#0ec9c2";

export interface HeatmapQualityInlineProps {
  singlePayload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
}

/** Compact map-quality row inside the Summary card (no extra sub-section). */
export function HeatmapQualityInline({
  singlePayload,
  qualityMetrics,
}: HeatmapQualityInlineProps) {
  const hasScore = singlePayload && qualityMetrics.score != null;

  return (
    <div className={classes.summaryQualityRow}>
      <Group gap={6} wrap="nowrap" align="center">
        <Text size="xs" fw={600} c="dimmed" tt="uppercase" style={{ letterSpacing: "0.06em" }}>
          Map quality
        </Text>
        <Tooltip
          label="How readable this heatmap is from event data (blend of volume in the map vs hotspot concentration). Not a product score."
          multiline
          w={260}
          withArrow
        >
          <span className={classes.summaryQualityInfo} aria-label="About map quality">
            <IconInfoCircle size={15} stroke={1.5} />
          </span>
        </Tooltip>
      </Group>

      <Group gap="md" wrap="wrap" align="center" justify="flex-end" style={{ flex: 1 }}>
        <div>
          <Text className={graphClasses.metricLabel}>Score</Text>
          {hasScore ? (
            <Tooltip
              label="~35% how much event weight is in the map vs total events, ~65% how peaked the hottest area is."
              multiline
              w={260}
              withArrow
            >
              <Text
                component="span"
                className={classes.summaryQualityValue}
                style={{ color: tealValue, cursor: "help" }}
              >
                {`${qualityMetrics.score} · ${qualityMetrics.label}`}
              </Text>
            </Tooltip>
          ) : (
            <Text className={classes.summaryQualityValueMuted} component="span">
              —
            </Text>
          )}
        </div>
        <div className={classes.scoreLegendInline}>
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
      </Group>
    </div>
  );
}
