import { Paper, Stack, Table, Text, Tooltip } from "@mantine/core";
import { useMemo } from "react";
import type { HeatmapDataResponse } from "./heatmap.types";
import { formatPulseScore, pulseInteractionRowsForKeyLens } from "./heatmapKeyLensAggregates";
import classes from "./HeatmapPanel.module.css";
import { bandFromNumericScore, heatmapScoreColor } from "./heatmapQuality";

export interface HeatmapPulseInteractionsAggregatesSectionProps {
  payload: HeatmapDataResponse;
}

/**
 * Pulse interaction scores from `interactions_metadata` only (not `layers.interaction_map`).
 */
export function HeatmapPulseInteractionsAggregatesSection({
  payload,
}: HeatmapPulseInteractionsAggregatesSectionProps) {
  const rows = useMemo(() => pulseInteractionRowsForKeyLens(payload), [payload]);
  const sorted = useMemo(
    () =>
      [...rows].sort((a, b) => {
        const av = a.score01 ?? -Infinity;
        const bv = b.score01 ?? -Infinity;
        return bv - av;
      }),
    [rows],
  );

  return (
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
        {sorted.length === 0 ? (
          <Text size="md" c="dimmed">
            No Pulse interaction scores for this screen in the current response.
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
                  <Table.Th style={{ width: "65%" }}>Interaction</Table.Th>
                  <Table.Th style={{ width: "35%", textAlign: "right" }}>
                    Score
                  </Table.Th>
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
                          color:
                            row.score01 == null
                              ? "var(--mantine-color-dimmed)"
                              : heatmapScoreColor(
                                  bandFromNumericScore(
                                    Math.round(row.score01 * 100),
                                  ),
                                ),
                        }}
                      >
                        {formatPulseScore(row.score01)}
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
  );
}
