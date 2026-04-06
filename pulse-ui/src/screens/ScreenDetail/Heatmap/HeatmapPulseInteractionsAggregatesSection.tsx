import { Paper, Stack, Table, Text, Tooltip } from "@mantine/core";
import { useMemo } from "react";
import type { HeatmapDataResponse } from "./heatmap.types";
import {
  formatPulseScore,
  keyLensHasInteractionOverlay,
  pulseInteractionRowsForKeyLens,
} from "./heatmapKeyLensAggregates";
import classes from "./HeatmapPanel.module.css";
import { bandFromNumericScore, heatmapScoreColor } from "./heatmapQuality";

export interface HeatmapPulseInteractionsAggregatesSectionProps {
  payload: HeatmapDataResponse;
  /** When false, table is Interaction + Score only (heatmap lens). */
  showElementsColumn?: boolean;
}

export function HeatmapPulseInteractionsAggregatesSection({
  payload,
  showElementsColumn = true,
}: HeatmapPulseInteractionsAggregatesSectionProps) {
  const hasOverlay = useMemo(() => keyLensHasInteractionOverlay(payload), [payload]);
  const rows = useMemo(() => pulseInteractionRowsForKeyLens(payload), [payload]);
  const showElements = showElementsColumn && hasOverlay;
  const sorted = useMemo(
    () => [...rows].sort((a, b) => b.score01 - a.score01),
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
                  <Table.Th style={{ width: showElements ? "52%" : "65%" }}>
                    Interaction
                  </Table.Th>
                  <Table.Th
                    style={{
                      width: showElements ? "26%" : "35%",
                      textAlign: "right",
                    }}
                  >
                    Score
                  </Table.Th>
                  {showElements ? (
                    <Table.Th style={{ width: "22%", textAlign: "right" }}>
                      Elements
                    </Table.Th>
                  ) : null}
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
                    {showElements ? (
                      <Table.Td style={{ textAlign: "right" }}>
                        <Text size="sm" c="dimmed">
                          {row.elementTouches}
                        </Text>
                      </Table.Td>
                    ) : null}
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
