import { Group, Paper, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useMemo } from "react";
import type { HeatmapDataResponse } from "./heatmap.types";
import {
  formatInteractionScore01,
  getInteractionLayerScores,
} from "./heatmapInteractionScores";
import classes from "./HeatmapPanel.module.css";
import { bandFromNumericScore, heatmapScoreColor } from "./heatmapQuality";

export interface HeatmapAggregatesInteractionAvgCardProps {
  payload: HeatmapDataResponse;
}

/**
 * Screen-level average (Tap / Rage / Dead layers with data)—sits beside heatmap score.
 */
export function HeatmapAggregatesInteractionAvgCard({
  payload,
}: HeatmapAggregatesInteractionAvgCardProps) {
  const scores = useMemo(() => getInteractionLayerScores(payload), [payload]);
  const average = scores.average;
  const hasAvg = average != null;
  const band = hasAvg
    ? bandFromNumericScore(Math.round(average * 100))
    : "nodata";

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
          Avg interaction score
        </Text>
        <Tooltip
          label="Screen-wide average on a 0–1 scale (same blend as heatmap score). Use it alongside the heatmap for any signal—not only rage or dead."
          multiline
          w={300}
          withArrow
        >
          <span
            className={classes.summaryQualityInfo}
            aria-label="About average interaction score"
          >
            <IconInfoCircle size={14} stroke={1.5} />
          </span>
        </Tooltip>
      </Group>

      {hasAvg ? (
        <Tooltip
          label="Mean of per-layer 0–1 scores for layers with telemetry (coverage vs total events and hotspot concentration per layer)."
          multiline
          w={280}
          withArrow
        >
          <Text
            component="div"
            className={classes.aggregatesInteractionAvgHero}
            style={{
              color: heatmapScoreColor(band),
              cursor: "help",
            }}
          >
            {formatInteractionScore01(average)}
          </Text>
        </Tooltip>
      ) : (
        <Text
          component="div"
          className={classes.aggregatesInteractionAvgHero}
          c="dimmed"
        >
          —
        </Text>
      )}

      <Text size="10px" c="dimmed" lh={1.4} mt={6}>
        Based on layers with telemetry in this screen and filter scope.
      </Text>
    </Paper>
  );
}
