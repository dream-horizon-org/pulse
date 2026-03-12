import { useState } from "react";
import {
  Box,
  SegmentedControl,
  Select,
  Slider,
  Text,
  Group,
} from "@mantine/core";
import ReactECharts from "echarts-for-react";
import {
  AVAILABLE_EVENTS,
  MOCK_JOURNEY_FORWARD,
  MOCK_JOURNEY_REVERSE,
  MockJourneyData,
} from "../mockData";
import classes from "../FunnelAnalysis.module.css";

const eventOptions = AVAILABLE_EVENTS.map((e) => ({ value: e, label: e }));

function buildSankeyOption(data: MockJourneyData) {
  const maxValue = Math.max(...data.links.map((l) => l.value));

  return {
    tooltip: {
      trigger: "item",
      triggerOn: "mousemove",
      formatter: (params: any) => {
        if (params.dataType === "edge") {
          return `<strong>${params.data.source}</strong> → <strong>${params.data.target}</strong><br/>Users: <strong>${params.data.value.toLocaleString()}</strong>`;
        }
        return `<strong>${params.name}</strong><br/>Users: <strong>${params.value?.toLocaleString() ?? "—"}</strong>`;
      },
    },
    series: [
      {
        type: "sankey",
        emphasis: { focus: "adjacency" },
        nodeAlign: "justify",
        layoutIterations: 32,
        draggable: true,
        left: 20,
        right: 160,
        top: 20,
        bottom: 20,
        nodeWidth: 20,
        nodeGap: 14,
        lineStyle: {
          color: "gradient",
          curveness: 0.5,
          opacity: 0.3,
        },
        itemStyle: {
          borderWidth: 1,
          borderColor: "#fff",
        },
        label: {
          position: "right",
          fontSize: 12,
          fontWeight: 500,
          color: "#334155",
          formatter: (params: any) => {
            const pct =
              maxValue > 0
                ? ((params.value / maxValue) * 100).toFixed(1)
                : "0";
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

export function JourneyExplorer() {
  const [direction, setDirection] = useState<"forward" | "reverse">("forward");
  const [anchorEvent, setAnchorEvent] = useState<string | null>("App_Launch");
  const [depth, setDepth] = useState(5);

  const journeyData =
    direction === "forward" ? MOCK_JOURNEY_FORWARD : MOCK_JOURNEY_REVERSE;

  const option = buildSankeyOption(journeyData);

  return (
    <Box className={classes.journeyLayout}>
      {/* Control Panel */}
      <Box className={classes.journeyControlPanel}>
        <Box>
          <Text size="xs" fw={600} c="dimmed" mb={4}>
            Direction
          </Text>
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
            placeholder="Select root event..."
            size="xs"
            searchable
          />
        </Box>

        <Box style={{ minWidth: 200 }}>
          <Text size="xs" fw={600} c="dimmed" mb={4}>
            Depth: {depth} steps
          </Text>
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

        <Group gap="xs">
          <Text size="xs" c="dimmed">
            Showing mock data ·
          </Text>
          <Text size="xs" fw={600} c="teal">
            {journeyData.nodes.length} nodes · {journeyData.links.length} paths
          </Text>
        </Group>
      </Box>

      {/* Sankey Diagram */}
      <Box className={classes.journeyCanvas}>
        <Box className={classes.sankeyContainer}>
          <Text size="sm" fw={600} c="dark.7" mb="md">
            {direction === "forward" ? "Forward" : "Reverse"} Journey from{" "}
            <Text span c="teal" fw={700}>
              {anchorEvent || "—"}
            </Text>
          </Text>
          <ReactECharts
            option={option}
            style={{ height: "520px", width: "100%" }}
            notMerge
          />
        </Box>
      </Box>
    </Box>
  );
}
