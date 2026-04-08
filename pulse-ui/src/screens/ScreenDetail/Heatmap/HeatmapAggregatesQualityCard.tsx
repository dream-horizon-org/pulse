import { Divider, Group, Paper, Stack, Text, Tooltip } from "@mantine/core";
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
          Frustration activity about{" "}
          {qualityMetrics.frustrationWeightSum.toLocaleString()} units; about{" "}
          {Math.round(qualityMetrics.frustrationPressure01 * 100)}% of combined tap and frustration
          events in this view.
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
        Good · Average · Poor
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        Labels map to the same numeric bands as the headline (shown as 0–1 here; 0.72 = 72/100):
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        <strong>Good</strong> {GOOD01.toFixed(2)}–1.00 — strong coverage and/or clear hotspots, low
        frustration share in the blend.
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        <strong>Average</strong> {AVG01.toFixed(2)}–{GOOD_UPPER01.toFixed(2)} — middling coverage or
        shape, or noticeable frustration.
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        <strong>Poor</strong> 0.00–{POOR_UPPER01.toFixed(2)} — sparse/flat signal or heavy
        rage/dead weight for this view.
      </Text>
      <Text size="sm" fw={600} c="inherit" mt={2}>
        How to read it
      </Text>
      <Text size="sm" c="inherit" opacity={0.95}>
        Higher scores: clearer hotspots and coverage, less frustration share. Lower: sparse or flat
        signal, or heavier rage/dead for this scope.
      </Text>
      {frustrationBlock}
    </Stack>
  );
}

const FRUSTRATION_TOOLTIP_INFO =
  "Approximate share of heatmap weight from rage taps and unresponsive (dead) zones, compared with taps plus all frustration weight on this view. The remainder is mostly taps and movement. The heatmap score uses this mix—more frustration lowers the score.";

export interface HeatmapAggregatesQualityCardProps {
  payload: HeatmapDataResponse | null | undefined;
  qualityMetrics: HeatmapQualityMetrics;
  /** Summary total events (same scope as the score). */
  totalEventsFormatted: string;
}

/** Heatmap score plus scope metrics (no table). */
export function HeatmapAggregatesQualityCard({
  payload,
  qualityMetrics,
  totalEventsFormatted,
}: HeatmapAggregatesQualityCardProps) {
  const hasScore = Boolean(payload && qualityMetrics.score01 != null);
  const fPct =
    qualityMetrics.frustrationPressure01 != null
      ? Math.round(qualityMetrics.frustrationPressure01 * 100)
      : null;

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
            marginBottom: 4,
          }}
        >
          {`${formatInteractionScore01(qualityMetrics.score01)} · ${qualityMetrics.label}`}
        </Text>
      ) : (
        <Text component="div" c="dimmed" className={classes.aggregatesQualityScore} mb={4}>
          —
        </Text>
      )}

      <Divider my="sm" color="var(--mantine-color-gray-3)" style={{ opacity: 0.85 }} />

      <Stack gap={10}>
        <Group justify="space-between" gap="md" wrap="nowrap" align="flex-start">
          <Text size="sm" c="dimmed" lh={1.4} style={{ flex: "0 1 auto" }}>
            Total events
          </Text>
          <Text size="sm" fw={600} ta="right" lh={1.4} style={{ flex: "0 1 auto" }}>
            {totalEventsFormatted}
          </Text>
        </Group>
        {fPct != null ? (
          <Group justify="space-between" gap="md" wrap="nowrap" align="center">
            <Group gap={6} wrap="nowrap">
              <Text size="sm" c="dimmed" lh={1.4}>
                Frustration vs. taps
              </Text>
              <Tooltip
                withArrow
                multiline
                maw={320}
                label={FRUSTRATION_TOOLTIP_INFO}
                styles={{
                  tooltip: {
                    padding: "var(--mantine-spacing-xs) var(--mantine-spacing-sm)",
                  },
                }}
              >
                <span
                  className={classes.summaryQualityInfo}
                  aria-label="About frustration vs. taps"
                >
                  <IconInfoCircle size={14} stroke={1.5} />
                </span>
              </Tooltip>
            </Group>
            <Text size="sm" fw={600} ta="right" lh={1.4}>
              ~{fPct}%
            </Text>
          </Group>
        ) : null}
      </Stack>
    </Paper>
  );
}
