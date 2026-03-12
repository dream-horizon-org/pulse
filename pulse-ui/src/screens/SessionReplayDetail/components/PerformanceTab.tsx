import { Stack, Group, SegmentedControl, Box, Text, Table, Badge } from "@mantine/core";
import { PerformanceVisualization } from "./PerformanceVisualization";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface PerformanceTabProps {
  sessionData: SessionDetailData;
  viewMode: "text" | "graph";
  onViewModeChange: (mode: "text" | "graph") => void;
}

export function PerformanceTab({
  sessionData,
  viewMode,
  onViewModeChange,
}: PerformanceTabProps) {
  return (
    <Stack gap="md">
      <Group justify="flex-end">
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => onViewModeChange(value as "text" | "graph")}
          data={[
            { label: "Text", value: "text" },
            { label: "Graph", value: "graph" },
          ]}
        />
      </Group>
      {viewMode === "graph" ? (
        <PerformanceVisualization
          performance={sessionData.performance}
        />
      ) : (
        <>
          <Box>
            <Text
              size="xs"
              tt="uppercase"
              fw={600}
              c="dimmed"
              mb="xs"
            >
              Interaction Metrics
            </Text>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Interaction</Table.Th>
                  <Table.Th>Duration</Table.Th>
                  <Table.Th>Apdex</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {sessionData.performance.interactionMetrics.map(
                  (metric) => (
                    <Table.Tr key={metric.interactionId}>
                      <Table.Td>
                        <Text size="sm">
                          {metric.interactionName}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{metric.duration}ms</Text>
                      </Table.Td>
                      <Table.Td>
                        <Badge
                          size="sm"
                          color={
                            metric.apdexScore >= 0.8
                              ? "teal"
                              : metric.apdexScore >= 0.5
                                ? "yellow"
                                : "red"
                          }
                        >
                          {metric.apdexScore.toFixed(2)}
                        </Badge>
                      </Table.Td>
                    </Table.Tr>
                  ),
                )}
              </Table.Tbody>
            </Table>
          </Box>
        </>
      )}
    </Stack>
  );
}
