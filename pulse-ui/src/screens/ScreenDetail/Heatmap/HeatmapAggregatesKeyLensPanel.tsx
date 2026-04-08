import { Paper, Stack, Table, Text } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useMemo } from "react";
import type { HeatmapDataResponse } from "./heatmap.types";
import { formatPulseScore, screenPulseInteractionAverage01 } from "./heatmapKeyLensAggregates";
import { HeatmapPulseInteractionsAggregatesSection } from "./HeatmapPulseInteractionsAggregatesSection";
import classes from "./HeatmapPanel.module.css";
import { bandFromNumericScore, heatmapScoreColor } from "./heatmapQuality";

export interface HeatmapAggregatesKeyLensPanelProps {
  payload: HeatmapDataResponse;
}

/** Right rail when Focus = Key actions (Pulse interaction_map). */
export function HeatmapAggregatesKeyLensPanel({
  payload,
}: HeatmapAggregatesKeyLensPanelProps) {
  const screenAvg = useMemo(
    () => screenPulseInteractionAverage01(payload.layers.interaction_map?.regions ?? []),
    [payload],
  );
  const band =
    screenAvg != null ? bandFromNumericScore(Math.round(screenAvg * 100)) : "nodata";

  return (
    <Stack gap="sm" className={classes.aggregatesStack}>
      <Paper className={classes.aggregatesSubCard} radius="sm" p="sm" withBorder>
        <Stack gap={8}>
          <Text
            className={classes.aggregatesCardTitle}
            size="xs"
            fw={700}
            c="#0ba09a"
            tt="uppercase"
            style={{ letterSpacing: "0.05em" }}
          >
            Pulse · screen average
          </Text>
          <Text size="sm" c="dimmed" lh={1.5}>
            Mean of per-element scores from <code className={classes.interactionEmptyCode}>interaction_map</code>.
          </Text>
          <Table
            className={classes.aggregatesLayerTable}
            verticalSpacing={0}
            horizontalSpacing="xs"
            layout="fixed"
          >
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: "55%" }}>Metric</Table.Th>
                <Table.Th style={{ width: "45%", textAlign: "right" }}>Value</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              <Table.Tr>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    Screen average
                  </Text>
                </Table.Td>
                <Table.Td style={{ textAlign: "right" }}>
                  {screenAvg != null ? (
                    <Text
                      size="sm"
                      fw={700}
                      style={{ color: heatmapScoreColor(band) }}
                    >
                      {formatPulseScore(screenAvg)}
                    </Text>
                  ) : (
                    <Text size="sm" c="dimmed">
                      —
                    </Text>
                  )}
                </Table.Td>
              </Table.Tr>
            </Table.Tbody>
          </Table>
        </Stack>
      </Paper>

      <HeatmapPulseInteractionsAggregatesSection payload={payload} showElementsColumn />

      <Text size="sm" c="dimmed" lh={1.5} px={2} style={{ display: "flex", alignItems: "flex-start", gap: 6 }}>
        <IconInfoCircle size={14} stroke={1.5} style={{ flexShrink: 0, marginTop: 2 }} />
        Hover regions on the map for per-element breakdown.
      </Text>
    </Stack>
  );
}
