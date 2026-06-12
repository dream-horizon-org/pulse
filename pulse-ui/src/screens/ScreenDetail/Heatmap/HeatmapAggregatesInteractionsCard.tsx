import { Fragment, useMemo } from "react";
import { Group, Paper, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import {
  formatInteractionScore01,
  getInteractionLayerScores,
} from "./heatmapInteractionScores";
import {
  HEATMAP_QUALITY_AVERAGE_MIN,
  HEATMAP_QUALITY_GOOD_MIN,
  bandFromNumericScore,
  heatmapScoreColor,
  type HeatmapQualityMetrics,
} from "./heatmapQuality";
import type { HeatmapDataResponse } from "./heatmap.types";
import classes from "./HeatmapPanel.module.css";

function accountedLayersLine(scores: ReturnType<typeof getInteractionLayerScores>): string {
  const parts: string[] = [];
  if (scores.tap != null) parts.push("Tap");
  if (scores.rage != null) parts.push("Rage");
  if (scores.dead != null) parts.push("Dead zone");
  return parts.length > 0 ? parts.join(" · ") : "None in this scope";
}

/** Scores are 0–1; bands match heatmap score when scaled ×100. */
function bandForScore01(score: number | null): HeatmapQualityMetrics["band"] {
  if (score == null) return "nodata";
  return bandFromNumericScore(Math.round(score * 100));
}

export interface HeatmapAggregatesInteractionsCardProps {
  payload: HeatmapDataResponse;
}

/**
 * Screen-level breakdown (not tied to rage/dead): layers counted + per-type scores.
 * Avg sits beside heatmap score; this card is the detail row below.
 */
export function HeatmapAggregatesInteractionsCard({
  payload,
}: HeatmapAggregatesInteractionsCardProps) {
  const scores = useMemo(() => getInteractionLayerScores(payload), [payload]);

  const perType: { label: string; score: number | null }[] = [
    { label: "Tap", score: scores.tap },
    { label: "Rage", score: scores.rage },
    { label: "Dead zone", score: scores.dead },
  ];

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
          Interaction breakdown
        </Text>
        <Tooltip
          label="Screen-level scores on a 0–1 scale for this heatmap scope. Read next to the map for Tap, Rage, or Dead—same blend as heatmap score per layer."
          multiline
          w={300}
          withArrow
        >
          <span className={classes.summaryQualityInfo} aria-label="About interaction breakdown">
            <IconInfoCircle size={14} stroke={1.5} />
          </span>
        </Tooltip>
      </Group>

      <Text size="10px" c="dimmed" lh={1.45} mb={8}>
        Same screen and filters as the heatmap. Use the average above with the map; this section
        shows which interaction layers have bins and each layer’s score.
      </Text>

      <div className={classes.aggregatesKvGrid}>
        <Text size="sm" c="dimmed" className={classes.aggregatesKvLabel}>
          Layers with bins
        </Text>
        <Text size="sm" fw={600} className={classes.aggregatesKvValue} c="dimmed">
          {accountedLayersLine(scores)}
        </Text>
      </div>

      <Text
        className={classes.aggregatesCardTitle}
        size="xs"
        fw={700}
        c="dimmed"
        tt="uppercase"
        mt={10}
        mb={4}
        style={{ letterSpacing: "0.05em" }}
      >
        Individual scores
      </Text>

      <div className={classes.aggregatesKvGrid}>
        {perType.map((row) => {
          const b = bandForScore01(row.score);
          return (
            <Fragment key={row.label}>
              <Text size="sm" c="dimmed" className={classes.aggregatesKvLabel}>
                {row.label}
              </Text>
              <Text
                size="sm"
                fw={600}
                className={classes.aggregatesKvValue}
                style={{ color: heatmapScoreColor(b) }}
              >
                {formatInteractionScore01(row.score)}
              </Text>
            </Fragment>
          );
        })}
      </div>

      <Text size="10px" c="dimmed" lh={1.4} mt={8}>
        Bands (0–1 scale, same cutoffs as heatmap score): Good ≥
        {(HEATMAP_QUALITY_GOOD_MIN / 100).toFixed(2)} · Average{" "}
        {(HEATMAP_QUALITY_AVERAGE_MIN / 100).toFixed(2)}–
        {((HEATMAP_QUALITY_GOOD_MIN - 1) / 100).toFixed(2)} · Poor below{" "}
        {(HEATMAP_QUALITY_AVERAGE_MIN / 100).toFixed(2)}.
      </Text>
    </Paper>
  );
}
