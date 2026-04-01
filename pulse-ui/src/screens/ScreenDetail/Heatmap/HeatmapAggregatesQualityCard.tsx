import { Group, Paper, Stack, Text, Tooltip } from "@mantine/core";
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
const POOR_UPPER01 = AVG01 - 0.01;

function heatmapScoreInfoTooltipContent(qualityMetrics: HeatmapQualityMetrics) {
  const frustrationBlock =
    qualityMetrics.frustrationPressure01 != null &&
    qualityMetrics.frustrationWeightSum != null ? (
      <>
        <Text size="sm" fw={600} c="inherit" mt={6}>
          Frustration
        </Text>
        <Text size="sm" c="inherit" opacity={0.95}>
          Aggregated weight {qualityMetrics.frustrationWeightSum.toLocaleString()}; pressure{" "}
          {Math.round(qualityMetrics.frustrationPressure01 * 100)}% of events + frustration (rage +
          dead).
        </Text>
      </>
    ) : null;

  return (
    <Stack gap={6} maw="100%" c="var(--mantine-color-white)" style={{ lineHeight: 1.5 }}>
      <Text size="sm" c="inherit">
        0–1 blend of tap coverage, peak-bin concentration, and frustration mass. Indicative
        diagnostic, not a business KPI.
      </Text>
      <Text size="sm" fw={600} c="inherit">
        Bands (0–1)
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        Good {GOOD01.toFixed(2)}–1.00 · Average {AVG01.toFixed(2)}–{GOOD_UPPER01.toFixed(2)} · Poor
        0.00–{POOR_UPPER01.toFixed(2)}
      </Text>
      <Text size="sm" fw={600} c="inherit" mt={2}>
        How to read it
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        Higher: clearer hotspots and coverage, less frustration share. Lower: sparse or flat signal,
        or heavier rage/dead for this scope.
      </Text>
      {frustrationBlock}
    </Stack>
  );
}

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
      p="sm"
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
          label={heatmapScoreInfoTooltipContent(qualityMetrics)}
          withArrow
          multiline
          styles={{
            tooltip: {
              maxWidth: "min(520px, calc(100vw - 24px))",
              padding: "var(--mantine-spacing-sm) var(--mantine-spacing-md)",
              backgroundColor: "var(--mantine-color-gray-9)",
              color: "var(--mantine-color-white)",
            },
          }}
        >
          <span className={classes.summaryQualityInfo} aria-label="About heatmap score">
            <IconInfoCircle size={14} stroke={1.5} />
          </span>
        </Tooltip>
      </Group>

      {hasScore && qualityMetrics.score01 != null ? (
        <Text
          component="div"
          className={classes.aggregatesQualityScore}
          style={{
            color: heatmapScoreColor(qualityMetrics.band),
            marginBottom: 6,
          }}
        >
          {`${formatInteractionScore01(qualityMetrics.score01)} · ${qualityMetrics.label}`}
        </Text>
      ) : (
        <Text component="div" c="dimmed" className={classes.aggregatesQualityScore} mb={6}>
          —
        </Text>
      )}

      <div className={classes.aggregatesQualityChips}>
        <span
          className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendGood}${qualityMetrics.band === "good" ? ` ${classes.legendChipActive}` : ""}`}
        >
          Good
        </span>
        <span
          className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendAvg}${qualityMetrics.band === "average" ? ` ${classes.legendChipActive}` : ""}`}
        >
          Average
        </span>
        <span
          className={`${classes.legendChip} ${classes.aggregatesQualityChip} ${classes.legendPoor}${qualityMetrics.band === "poor" ? ` ${classes.legendChipActive}` : ""}`}
        >
          Poor
        </span>
      </div>
    </Paper>
  );
}
