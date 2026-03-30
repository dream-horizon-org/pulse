import { Group, Paper, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import {
  HEATMAP_QUALITY_AVERAGE_MIN,
  HEATMAP_QUALITY_GOOD_MIN,
  heatmapScoreColor,
  type HeatmapQualityMetrics,
} from "./heatmapQuality";
import { formatInteractionScore01 } from "./heatmapInteractionScores";
import type { HeatmapDataResponse } from "./heatmap.types";
import classes from "./HeatmapPanel.module.css";

const GOOD01 = HEATMAP_QUALITY_GOOD_MIN / 100;
const AVG01 = HEATMAP_QUALITY_AVERAGE_MIN / 100;
const GOOD_UPPER01 = (HEATMAP_QUALITY_GOOD_MIN - 1) / 100;

export interface HeatmapAggregatesQualityCardProps {
  payload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
}

/** Heatmap score (tap / glow layer) — compact card matching other aggregate tiles. */
export function HeatmapAggregatesQualityCard({
  payload,
  qualityMetrics,
}: HeatmapAggregatesQualityCardProps) {
  const hasScore = Boolean(payload && qualityMetrics.score01 != null);

  return (
    <Paper
      className={`${classes.aggregatesSubCard} ${classes.aggregatesTopScoreCard}`}
      radius="sm"
      p={8}
      withBorder
    >
      <Group gap={6} align="center" mb={6} wrap="nowrap">
        <Text
          className={classes.aggregatesCardTitle}
          size="xs"
          fw={700}
          c="#0ba09a"
          tt="uppercase"
          style={{ letterSpacing: "0.05em", marginBottom: 0 }}
        >
          Heatmap score
        </Text>
        <Tooltip
          label="Indicative 0–1 score for how strong and readable the tap heatmap is for this screen and filters—derived from event weights in the map vs total events and how peaked the hottest area is. Not a business KPI; use it with the RCA and interaction scores."
          multiline
          w={280}
          withArrow
        >
          <span className={classes.summaryQualityInfo} aria-label="About heatmap score">
            <IconInfoCircle size={14} stroke={1.5} />
          </span>
        </Tooltip>
      </Group>

      {hasScore && qualityMetrics.score01 != null ? (
        <Tooltip
          label="Blend: ~35% of weight represented in the map vs total events, ~65% how dominant the single hottest bin is. Same math as per-layer interaction scores."
          multiline
          w={260}
          withArrow
        >
          <Text
            component="div"
            className={classes.aggregatesQualityScore}
            style={{
              color: heatmapScoreColor(qualityMetrics.band),
              cursor: "help",
              marginBottom: 6,
            }}
          >
            {`${formatInteractionScore01(qualityMetrics.score01)} · ${qualityMetrics.label}`}
          </Text>
        </Tooltip>
      ) : (
        <Text component="div" c="dimmed" className={classes.aggregatesQualityScore} mb={6}>
          —
        </Text>
      )}

      <div className={classes.aggregatesQualityChips}>
        <Tooltip
          label={`${GOOD01.toFixed(2)}+ (0–1): clearer hotspots and coverage—easier to trust the map.`}
          multiline
          w={240}
          withArrow
        >
          <span
            className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendGood}${qualityMetrics.band === "good" ? ` ${classes.legendChipActive}` : ""}`}
          >
            Good
          </span>
        </Tooltip>
        <Tooltip
          label={`${AVG01.toFixed(2)}–${GOOD_UPPER01.toFixed(2)}: usable but noisier or flatter hotspots.`}
          multiline
          w={240}
          withArrow
        >
          <span
            className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendAvg}${qualityMetrics.band === "average" ? ` ${classes.legendChipActive}` : ""}`}
          >
            Average
          </span>
        </Tooltip>
        <Tooltip
          label={`Below ${AVG01.toFixed(2)}: sparse or flat signal—harder to trust patterns from this aggregation (e.g. when frustration or errors fragment behavior, as in some RCAs).`}
          multiline
          w={260}
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
        Good {GOOD01.toFixed(2)}–1.00 · Average {AVG01.toFixed(2)}–{GOOD_UPPER01.toFixed(2)} · Poor
        0.00–{(AVG01 - 0.01).toFixed(2)}
      </Text>
      <Text size="10px" c="dimmed" lh={1.4} mt={4}>
        Higher = clearer hotspots and coverage; lower = sparse or flat signal for this screen and
        filters (often when frustration or errors fragment taps—check the RCA and interaction
        scores).
      </Text>
    </Paper>
  );
}
