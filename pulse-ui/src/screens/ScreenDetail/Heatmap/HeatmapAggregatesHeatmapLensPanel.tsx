import { Group, Paper, Stack, Table, Text } from "@mantine/core";
import { Fragment, useMemo, type ReactNode } from "react";
import { buildHeatmapAggregateSnapshot } from "./heatmapAggregates";
import type { HeatmapDataResponse } from "./heatmap.types";
import { HEATMAP_SIGNALS, formatInt, type HeatmapSignal } from "./heatmapPanelUtils";
import type { HeatmapQualityMetrics } from "./heatmapQuality";
import { HeatmapAggregatesQualityCard } from "./HeatmapAggregatesQualityCard";
import { HeatmapPulseInteractionsAggregatesSection } from "./HeatmapPulseInteractionsAggregatesSection";
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
      p="sm"
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
          <Text size="md" c="dimmed" className={classes.aggregatesKvLabel}>
            {row.label}
          </Text>
          <Text size="md" fw={600} className={classes.aggregatesKvValue}>
            {row.value}
          </Text>
        </Fragment>
      ))}
    </div>
  );
}

type LayerTableRow = {
  key: string;
  layer: string;
  active: boolean;
  cells: number;
  weightSum: number;
};

function LayerBreakdownTable({ rows }: { rows: LayerTableRow[] }) {
  return (
    <Table
      className={classes.aggregatesLayerTable}
      verticalSpacing={0}
      horizontalSpacing="xs"
      layout="fixed"
    >
      <Table.Thead>
        <Table.Tr>
          <Table.Th style={{ width: "50%" }}>View</Table.Th>
          <Table.Th style={{ width: "25%", textAlign: "right" }}>Total spots</Table.Th>
          <Table.Th style={{ width: "25%", textAlign: "right" }}>Total events</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {rows.map((r) => (
          <Table.Tr
            key={r.key}
            className={r.active ? classes.aggregatesLayerTableActive : undefined}
          >
            <Table.Td>{r.layer}</Table.Td>
            <Table.Td style={{ textAlign: "right" }}>{formatInt(r.cells)}</Table.Td>
            <Table.Td style={{ textAlign: "right" }}>{formatWeight(r.weightSum)}</Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}

export interface HeatmapAggregatesHeatmapLensPanelProps {
  payload: HeatmapDataResponse;
  signal: HeatmapSignal;
  qualityMetrics: HeatmapQualityMetrics;
}

/** Right rail when Focus = All interaction data (density heatmap). */
export function HeatmapAggregatesHeatmapLensPanel({
  payload,
  signal,
  qualityMetrics,
}: HeatmapAggregatesHeatmapLensPanelProps) {
  const snap = useMemo(
    () => buildHeatmapAggregateSnapshot(payload, signal),
    [payload, signal],
  );

  const signalLabel =
    HEATMAP_SIGNALS.find((s) => s.id === signal)?.label ?? signal;

  const layerTableRows: LayerTableRow[] = [
    {
      key: "tap",
      layer: "Taps & movement",
      active: signal === "tap",
      cells: snap.glowMapBins,
      weightSum: snap.glowMapWeightSum,
    },
    {
      key: "rage",
      layer: "Rage",
      active: signal === "rage",
      cells: snap.rageBins,
      weightSum: snap.rageWeightSum,
    },
    {
      key: "dead",
      layer: "Unresponsive areas",
      active: signal === "dead",
      cells: snap.deadBins,
      weightSum: snap.deadWeightSum,
    },
  ];

  const showTapVersusNote =
    signal !== "tap" &&
    signal !== "rage" &&
    signal !== "dead" &&
    (snap.selectedLayerBins !== snap.glowMapBins ||
      Math.abs(snap.selectedLayerWeightSum - snap.glowMapWeightSum) > 1);

  const tapVersusRows: { label: string; value: string }[] = showTapVersusNote
    ? [
        {
          label: "Taps & movement (reference)",
          value: `${formatInt(snap.glowMapBins)} · ${formatWeight(snap.glowMapWeightSum)}`,
        },
        {
          label: `${signalLabel} (on map)`,
          value: `${formatInt(snap.selectedLayerBins)} · ${formatWeight(snap.selectedLayerWeightSum)}`,
        },
      ]
    : [];

  return (
    <Stack gap="sm" className={classes.aggregatesStack}>
      <div className={classes.aggregatesGrid}>
        <div
          className={`${classes.aggregatesScoreRow} ${classes.aggregatesScoreRowSingle}`}
        >
          <div className={classes.aggregatesScoreRowCell}>
            <HeatmapAggregatesQualityCard
              payload={payload}
              qualityMetrics={qualityMetrics}
              totalEventsFormatted={formatInt(snap.totalEventsReported ?? 0)}
            />
          </div>
        </div>

        <Paper className={classes.aggregatesSubCard} radius="sm" p="sm" withBorder>
          <Group justify="space-between" align="center" wrap="wrap" gap="sm" mb={10}>
            <Text size="sm" fw={600} c="dimmed">
              Breakdown
            </Text>
            <div
              className={classes.aggregatesScoreSignalBadge}
              title="Current map layer"
            >
              {signalLabel}
            </div>
          </Group>
          <LayerBreakdownTable rows={layerTableRows} />
          <Text size="xs" c="dimmed" lh={1.45} mt={8}>
            <strong>Total spots</strong> is how many map cells we summarize (not every individual
            tap). Per-layer <strong>Total events</strong> can differ from <strong>Total events</strong>{" "}
            in the score block above.
          </Text>
        </Paper>

        <HeatmapPulseInteractionsAggregatesSection
          payload={payload}
          showElementsColumn={false}
        />

        {tapVersusRows.length > 0 && (
          <AggregatesCard title="Compared to taps & movement">
            <Text size="sm" c="dimmed" lh={1.5} mb={8}>
              This signal uses a different view than taps & movement.
            </Text>
            <AggregateRows rows={tapVersusRows} />
          </AggregatesCard>
        )}
      </div>

      <Text size="sm" c="dimmed" lh={1.5} px={2}>
        Hover the map for cell detail. Use <strong>Tap · Rage · Dead</strong> to change what you see.
      </Text>
    </Stack>
  );
}
