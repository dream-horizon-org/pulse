import { Box, Group, SegmentedControl, Text } from "@mantine/core";
import { LineChart, PieChart } from "../../../components/Charts";
import { useMemo, useState } from "react";
import type { SessionEvent } from "../../../services/sessionReplay/mockSessionDetail";

interface EventsVisualizationProps {
  events: SessionEvent[];
  sessionStartTime: Date;
}

type ViewMode = "timeline" | "distribution";

export function EventsVisualization({
  events,
  sessionStartTime,
}: EventsVisualizationProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("timeline");

  // Timeline visualization - events over time
  const timelineOption = useMemo(() => {
    // Group events by type
    const eventTypes = ["click", "navigation", "api_call", "error"];
    const series = eventTypes.map((type) => ({
      name: type.charAt(0).toUpperCase() + type.slice(1).replace("_", " "),
      type: "line",
      data: events.filter((e) => e.type === type).map((e) => [e.timestamp, 1]),
      symbol: "circle",
      symbolSize: 8,
      lineStyle: {
        width: 2,
      },
    }));

    // Get time range
    const timestamps = events.map((e) => e.timestamp);
    const minTime = Math.min(...timestamps);
    const maxTime = Math.max(...timestamps);

    return {
      tooltip: {
        trigger: "axis",
        formatter: (params: any) => {
          const param = params[0];
          const event = events.find((e) => e.timestamp === param.value[0]);
          if (!event) return "";
          return `${event.type.toUpperCase()}<br/>${event.description}<br/>Time: ${param.value[0]}ms`;
        },
      },
      legend: {
        data: eventTypes.map(
          (t) => t.charAt(0).toUpperCase() + t.slice(1).replace("_", " "),
        ),
        bottom: 0,
      },
      xAxis: {
        type: "value",
        name: "Time (ms)",
        min: minTime,
        max: maxTime,
        nameTextStyle: {
          padding: [15, 0, 0, 0],
        },
      },
      yAxis: {
        type: "value",
        name: "Event Count",
        max: 1.5,
        nameTextStyle: {
          padding: [0, 0, 0, 20],
        },
      },
      series: series.filter((s) => s.data.length > 0),
    };
  }, [events]);

  // Event type distribution
  const distributionOption = useMemo(() => {
    const counts: Record<string, number> = {};
    events.forEach((e) => {
      counts[e.type] = (counts[e.type] || 0) + 1;
    });

    return {
      tooltip: {
        trigger: "item",
        formatter: "{b}: {c} ({d}%)",
      },
      series: [
        {
          type: "pie",
          radius: "60%",
          data: Object.entries(counts).map(([type, count]) => ({
            value: count,
            name:
              type.charAt(0).toUpperCase() + type.slice(1).replace("_", " "),
          })),
          itemStyle: {
            color: (params: any) => {
              const type = params.name.toLowerCase();
              if (type.includes("error")) return "#ef4444";
              if (type.includes("api")) return "#0ec9c2";
              if (type.includes("navigation")) return "#6366f1";
              return "#f59e0b";
            },
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: "rgba(0, 0, 0, 0.5)",
            },
          },
        },
      ],
    };
  }, [events]);

  return (
    <Box>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          Events Visualization
        </Text>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          data={[
            { label: "Timeline", value: "timeline" },
            { label: "Distribution", value: "distribution" },
          ]}
        />
      </Group>

      {viewMode === "timeline" && (
        <LineChart option={timelineOption} height={300} withLegend={true} />
      )}

      {viewMode === "distribution" && (
        <PieChart option={distributionOption} height={300} withLegend={true} />
      )}
    </Box>
  );
}
