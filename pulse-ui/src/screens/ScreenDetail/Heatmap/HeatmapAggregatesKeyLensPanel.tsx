import { Paper, Stack, Table, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useMemo } from "react";
import type { HeatmapDataResponse } from "./heatmap.types";
import {
  aggregatePulseInteractionsForScreen,
  formatPulseScore,
  screenPulseInteractionAverage01,
} from "./heatmapKeyLensAggregates";
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
  const rows = useMemo(
    () => aggregatePulseInteractionsForScreen(payload.layers.interaction_map?.regions ?? []),
    [payload],
  );
  const sorted = useMemo(
    () => [...rows].sort((a, b) => b.score01 - a.score01),
    [rows],
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
            Pulse interactions
          </Text>
          <Text size="sm" c="dimmed" lh={1.5}>
            All interactions on this screen (merged by id).
          </Text>
          {sorted.length === 0 ? (
            <Text size="md" c="dimmed">
              No rows in <code className={classes.interactionEmptyCode}>interaction_map</code>.
            </Text>
          ) : (
            <div className={classes.keyLensInteractionList}>
              <Table
                className={classes.aggregatesLayerTable}
                verticalSpacing={0}
                horizontalSpacing="xs"
                layout="fixed"
              >
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th style={{ width: "52%" }}>Interaction</Table.Th>
                    <Table.Th style={{ width: "26%", textAlign: "right" }}>Score</Table.Th>
                    <Table.Th style={{ width: "22%", textAlign: "right" }}>Elements</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {sorted.map((row) => (
                    <Table.Tr key={row.key}>
                      <Table.Td>
                        <Tooltip label={row.displayName} withArrow position="left">
                          <Text
                            component="span"
                            display="block"
                            size="sm"
                            fw={600}
                            lineClamp={2}
                            className={classes.keyLensInteractionName}
                          >
                            {row.displayName}
                          </Text>
                        </Tooltip>
                      </Table.Td>
                      <Table.Td style={{ textAlign: "right" }}>
                        <Text
                          size="sm"
                          fw={700}
                          style={{
                            color: heatmapScoreColor(
                              bandFromNumericScore(Math.round(row.score01 * 100)),
                            ),
                          }}
                        >
                          {formatPulseScore(row.score01)}
                        </Text>
                      </Table.Td>
                      <Table.Td style={{ textAlign: "right" }}>
                        <Text size="sm" c="dimmed">
                          {row.elementTouches}
                        </Text>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </div>
          )}
        </Stack>
      </Paper>

      <Text size="sm" c="dimmed" lh={1.5} px={2} style={{ display: "flex", alignItems: "flex-start", gap: 6 }}>
        <IconInfoCircle size={14} stroke={1.5} style={{ flexShrink: 0, marginTop: 2 }} />
        Hover regions on the map for per-element breakdown.
      </Text>
    </Stack>
  );
}
