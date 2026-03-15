import { useMemo, useState } from "react";
import {
  Box,
  Loader,
  SegmentedControl,
  Select,
  Slider,
  Text,
  Group,
} from "@mantine/core";
import ReactECharts from "echarts-for-react";
import { useGetJourneyData, JourneyResponse } from "../../../hooks/useGetFunnelData";
import { getDateRangeFromPreset } from "../mockData";
import classes from "../FunnelAnalysis.module.css";

interface JourneyExplorerProps {
  dateRange: string;
  availableEvents: string[];
}

function buildSankeyOption(data: JourneyResponse) {
  const maxValue = Math.max(...data.links.map((l) => l.value));

  return {
    tooltip: {
      trigger: "item" as const,
      triggerOn: "mousemove" as const,
      formatter: (params: any) => {
        if (params.dataType === "edge") {
          return `<strong>${params.data.source}</strong> → <strong>${params.data.target}</strong><br/>Users: <strong>${params.data.value.toLocaleString()}</strong>`;
        }
        return `<strong>${params.name}</strong><br/>Users: <strong>${params.value?.toLocaleString() ?? "—"}</strong>`;
      },
    },
    series: [
      {
        type: "sankey" as const,
        emphasis: { focus: "adjacency" as const },
        nodeAlign: "justify" as const,
        layoutIterations: 32,
        draggable: true,
        left: 20,
        right: 160,
        top: 20,
        bottom: 20,
        nodeWidth: 20,
        nodeGap: 14,
        lineStyle: {
          color: "gradient" as const,
          curveness: 0.5,
          opacity: 0.3,
        },
        itemStyle: { borderWidth: 1, borderColor: "#fff" },
        label: {
          position: "right" as const,
          fontSize: 12,
          fontWeight: 500,
          color: "#334155",
          formatter: (params: any) => {
            const pct = maxValue > 0 ? ((params.value / maxValue) * 100).toFixed(1) : "0";
            return `${params.name}\n${pct}% · ${params.value?.toLocaleString() ?? ""}`;
          },
        },
        data: data.nodes.map((node) => ({
          name: node.name,
          itemStyle: {
            color: node.name === "Exit" ? "#ef4444" : "#0ba09a",
            borderColor: node.name === "Exit" ? "#dc2626" : "#077672",
          },
        })),
        links: data.links,
      },
    ],
  };
}

export function JourneyExplorer({ dateRange, availableEvents }: JourneyExplorerProps) {
  const [direction, setDirection] = useState<"forward" | "reverse">("forward");
  const [anchorEvent, setAnchorEvent] = useState<string | null>(null);
  const [depth, setDepth] = useState(5);

  const eventOptions = useMemo(
    () => availableEvents.map((e) => ({ value: e, label: e })),
    [availableEvents],
  );

  const timeRange = useMemo(() => getDateRangeFromPreset(dateRange), [dateRange]);

  const requestBody = useMemo(
    () => ({
      direction,
      anchorEvent: anchorEvent || "",
      depth,
      timeRange,
    }),
    [direction, anchorEvent, depth, timeRange],
  );

  const { data, isLoading } = useGetJourneyData({
    requestBody,
    enabled: !!anchorEvent,
  });

  const journeyData = data?.data;

  return (
    <Box className={classes.journeyLayout}>
      <Box className={classes.journeyControlPanel}>
        <Box>
          <Text size="xs" fw={600} c="dimmed" mb={4}>Direction</Text>
          <SegmentedControl
            value={direction}
            onChange={(val) => setDirection(val as "forward" | "reverse")}
            data={[
              { label: "Start Point →", value: "forward" },
              { label: "← End Point", value: "reverse" },
            ]}
            size="xs"
            color="teal"
          />
        </Box>

        <Box style={{ minWidth: 280 }}>
          <Select
            label="Anchor Event"
            data={eventOptions}
            value={anchorEvent}
            onChange={setAnchorEvent}
            placeholder={availableEvents.length === 0 ? "No events available" : "Select root event..."}
            size="xs"
            searchable
            disabled={availableEvents.length === 0}
          />
        </Box>

        <Box style={{ minWidth: 200 }}>
          <Text size="xs" fw={600} c="dimmed" mb={4}>Depth: {depth} steps</Text>
          <Slider
            value={depth}
            onChange={setDepth}
            min={1}
            max={10}
            step={1}
            marks={[
              { value: 1, label: "1" },
              { value: 5, label: "5" },
              { value: 10, label: "10" },
            ]}
            size="sm"
            color="teal"
            style={{ marginTop: 4 }}
          />
        </Box>

        <Box style={{ flex: 1 }} />

        {journeyData && (
          <Group gap="xs">
            <Text size="xs" c="dimmed">
              {journeyData.nodes.length} nodes · {journeyData.links.length} paths
            </Text>
          </Group>
        )}
      </Box>

      <Box className={classes.journeyCanvas}>
        <Box className={classes.sankeyContainer}>
          {anchorEvent ? (
            <Text size="sm" fw={600} c="dark.7" mb="md">
              {direction === "forward" ? "Forward" : "Reverse"} Journey from{" "}
              <Text span c="teal" fw={700}>{anchorEvent}</Text>
            </Text>
          ) : null}

          {isLoading ? (
            <Box style={{ display: "flex", justifyContent: "center", padding: 80 }}>
              <Loader color="teal" size="lg" />
            </Box>
          ) : journeyData ? (
            <ReactECharts
              option={buildSankeyOption(journeyData)}
              style={{ height: "520px", width: "100%" }}
              notMerge
            />
          ) : (
            <Box className={classes.emptyState}>
              <Text size="sm" c="dimmed">
                {availableEvents.length === 0
                  ? "No events available. Connect a data source to explore journeys."
                  : "Select an anchor event to explore journeys"}
              </Text>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
}
