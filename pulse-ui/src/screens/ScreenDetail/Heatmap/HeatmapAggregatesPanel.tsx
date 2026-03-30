import { Paper, Stack, Text } from "@mantine/core";
import { Fragment, useMemo, type ReactNode } from "react";
import { buildHeatmapAggregateSnapshot } from "./heatmapAggregates";
import type { HeatmapDataResponse } from "./heatmap.types";
import {
  HEATMAP_SIGNALS,
  formatInt,
  type HeatmapSignal,
} from "./heatmapPanelUtils";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapAggregatesInteractionAvgCard } from "./HeatmapAggregatesInteractionAvgCard";
import { HeatmapAggregatesInteractionsCard } from "./HeatmapAggregatesInteractionsCard";
import { HeatmapAggregatesQualityCard } from "./HeatmapAggregatesQualityCard";
import classes from "./HeatmapPanel.module.css";

function formatWeight(n: number): string {
  if (!Number.isFinite(n)) return "—";
  return n >= 1000
    ? n.toLocaleString(undefined, { maximumFractionDigits: 0 })
    : n.toLocaleString();
}

function AggregatesCard({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <Paper
      className={classes.aggregatesSubCard}
      radius="sm"
      p={8}
      withBorder
    >
      <Text
        className={classes.aggregatesCardTitle}
        size="xs"
        fw={700}
        c="#0ba09a"
        tt="uppercase"
        mb={6}
        style={{ letterSpacing: "0.05em" }}
      >
        {title}
      </Text>
      {children}
    </Paper>
  );
}

function AggregateRows({ rows }: { rows: { label: string; value: string }[] }) {
  return (
    <div className={classes.aggregatesKvGrid}>
      {rows.map((row) => (
        <Fragment key={row.label}>
          <Text size="sm" c="dimmed" className={classes.aggregatesKvLabel}>
            {row.label}
          </Text>
          <Text size="sm" fw={600} className={classes.aggregatesKvValue}>
            {row.value}
          </Text>
        </Fragment>
      ))}
    </div>
  );
}

export interface HeatmapAggregatesPanelProps {
  payload: HeatmapDataResponse;
  signal: HeatmapSignal;
  qualityMetrics: HeatmapQualityMetrics;
}

/** Grouped aggregate cards for the current filters and signal. */
export function HeatmapAggregatesPanel({
  payload,
  signal,
  qualityMetrics,
}: HeatmapAggregatesPanelProps) {
  const snap = useMemo(
    () => buildHeatmapAggregateSnapshot(payload, signal),
    [payload, signal],
  );

  const signalLabel =
    HEATMAP_SIGNALS.find((s) => s.id === signal)?.label ?? signal;

  const scopeRows: { label: string; value: string }[] = [
    { label: "Signal", value: signalLabel },
  ];
  if (snap.totalEventsReported != null) {
    scopeRows.push({
      label: "Total events (scope)",
      value: formatInt(snap.totalEventsReported),
    });
  }

  const currentLayerRows: { label: string; value: string }[] = [
    { label: "Bins", value: formatInt(snap.selectedLayerBins) },
    { label: "Weight sum", value: formatWeight(snap.selectedLayerWeightSum) },
  ];

  const glowRows: { label: string; value: string }[] = [
    { label: "Bins", value: formatInt(snap.glowMapBins) },
    { label: "Weight sum", value: formatWeight(snap.glowMapWeightSum) },
  ];

  const frustrationRows: { label: string; value: string }[] = [
    {
      label: "Rage",
      value: `${formatInt(snap.rageBins)} bins · ${formatWeight(snap.rageWeightSum)}`,
    },
    {
      label: "Dead zone",
      value: `${formatInt(snap.deadBins)} bins · ${formatWeight(snap.deadWeightSum)}`,
    },
  ];

  const obsRows: { label: string; value: string }[] = [];
  if (snap.errorClickBins > 0) {
    obsRows.push({
      label: "Error-click bins",
      value: formatInt(snap.errorClickBins),
    });
  }
  if (snap.latencyHotspotBins > 0) {
    obsRows.push({
      label: "Latency hotspot bins",
      value: formatInt(snap.latencyHotspotBins),
    });
  }

  const showGlowCard =
    signal !== "tap" ||
    snap.selectedLayerBins !== snap.glowMapBins ||
    Math.abs(snap.selectedLayerWeightSum - snap.glowMapWeightSum) > 1;

  return (
    <Stack gap="sm" className={classes.aggregatesStack}>
      <div className={classes.aggregatesGrid}>
        <div className={classes.aggregatesScoreRow}>
          <div className={classes.aggregatesScoreRowCell}>
            <HeatmapAggregatesQualityCard
              payload={payload}
              qualityMetrics={qualityMetrics}
            />
          </div>
          <div className={classes.aggregatesScoreRowCell}>
            <HeatmapAggregatesInteractionAvgCard payload={payload} />
          </div>
        </div>

        <div className={classes.aggregatesQualitySpan}>
          <HeatmapAggregatesInteractionsCard payload={payload} />
        </div>

        <AggregatesCard title="Signal &amp; scope">
          <AggregateRows rows={scopeRows} />
        </AggregatesCard>

        <AggregatesCard title="Selected layer">
          <AggregateRows rows={currentLayerRows} />
        </AggregatesCard>

        {showGlowCard && (
          <AggregatesCard title="All interactions">
            <AggregateRows rows={glowRows} />
          </AggregatesCard>
        )}

        <AggregatesCard title="Frustration">
          <AggregateRows rows={frustrationRows} />
        </AggregatesCard>

        {obsRows.length > 0 && (
          <AggregatesCard title="Observability">
            <AggregateRows rows={obsRows} />
          </AggregatesCard>
        )}
      </div>

      <Text size="xs" c="dimmed" lh={1.45} px={2}>
        Weights are aggregated bin totals from telemetry for this screen and
        filter scope—not raw session counts.
      </Text>
    </Stack>
  );
}
