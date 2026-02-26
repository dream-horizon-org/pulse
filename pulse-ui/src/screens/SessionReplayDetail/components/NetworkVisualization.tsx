import { Box, Group, SegmentedControl, Text } from "@mantine/core";
import { BarChart, PieChart } from "../../../components/Charts";
import { useMemo, useState } from "react";
import type { NetworkRequest } from "../../../services/sessionReplay/mockSessionDetail";

interface NetworkVisualizationProps {
  networkRequests: NetworkRequest[];
  sessionStartTime: Date;
}

type ViewMode = "waterfall" | "status" | "duration";

export function NetworkVisualization({
  networkRequests,
  sessionStartTime,
}: NetworkVisualizationProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("waterfall");

  // Waterfall chart - shows requests over time
  const waterfallOption = useMemo(() => {
    if (networkRequests.length === 0) {
      return {
        xAxis: { type: "value" },
        yAxis: { type: "category", data: [] },
        series: [],
      };
    }

    const requests = networkRequests.map((req, idx) => ({
      name: req.url.split("/").pop() || req.url.substring(0, 30),
      start: req.timestamp,
      duration: req.duration,
      status: req.status,
      method: req.method,
      index: idx,
    }));

    const maxTime = Math.max(...requests.map((r) => r.start + r.duration));
    const minTime = Math.min(...requests.map((r) => r.start));

    return {
      tooltip: {
        trigger: "axis",
        formatter: (params: any) => {
          const param = params[0];
          const req = requests[param.dataIndex];
          return `${req.method} ${req.name}<br/>Status: ${req.status}<br/>Duration: ${req.duration}ms`;
        },
      },
      xAxis: {
        type: "value",
        name: "Time (ms)",
        min: 0,
        max: maxTime - minTime,
      },
      yAxis: {
        type: "category",
        data: requests.map((r) => r.name),
        inverse: true,
      },
      series: [
        {
          name: "Request Duration",
          type: "bar",
          data: requests.map((r) => ({
            value: [r.start - minTime, r.start - minTime + r.duration],
            itemStyle: {
              color:
                r.status >= 200 && r.status < 300
                  ? "#0ec9c2"
                  : r.status >= 500
                    ? "#ef4444"
                    : "#f59e0b",
            },
          })),
          barWidth: 20,
        },
      ],
    };
  }, [networkRequests]);

  // Status distribution pie chart
  const statusDistributionOption = useMemo(() => {
    const statusCounts: Record<number, number> = {};
    networkRequests.forEach((req) => {
      const statusGroup =
        req.status >= 200 && req.status < 300
          ? 200
          : req.status >= 400 && req.status < 500
            ? 400
            : 500;
      statusCounts[statusGroup] = (statusCounts[statusGroup] || 0) + 1;
    });

    const data = Object.entries(statusCounts).map(([status, count]) => ({
      value: count,
      name:
        status === "200"
          ? "2xx Success"
          : status === "400"
            ? "4xx Client Error"
            : "5xx Server Error",
    }));

    return {
      tooltip: {
        trigger: "item",
        formatter: "{b}: {c} ({d}%)",
      },
      series: [
        {
          type: "pie",
          radius: ["40%", "70%"],
          data,
          itemStyle: {
            color: (params: any) => {
              if (params.name.includes("2xx")) return "#0ec9c2";
              if (params.name.includes("4xx")) return "#f59e0b";
              return "#ef4444";
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
  }, [networkRequests]);

  // Duration comparison bar chart
  const durationOption = useMemo(() => {
    const requests = networkRequests.map((req) => ({
      name: req.url.split("/").pop() || req.url.substring(0, 20),
      duration: req.duration,
      status: req.status,
    }));

    return {
      tooltip: {
        trigger: "axis",
        formatter: (params: any) => {
          const param = params[0];
          return `${param.name}<br/>Duration: ${param.value}ms`;
        },
      },
      xAxis: {
        type: "category",
        data: requests.map((r) => r.name),
        axisLabel: {
          rotate: requests.length > 4 ? 25 : 0,
          fontSize: 11,
        },
      },
      yAxis: {
        type: "value",
        name: "Duration (ms)",
        nameTextStyle: {
          padding: [0, 0, 0, 20],
        },
      },
      series: [
        {
          name: "Duration",
          type: "bar",
          data: requests.map((r) => r.duration),
          itemStyle: {
            color: (params: any) => {
              const req = requests[params.dataIndex];
              if (req.duration > 1000) return "#ef4444";
              if (req.duration > 500) return "#f59e0b";
              return "#0ec9c2";
            },
          },
          barMaxWidth: 60,
        },
      ],
    };
  }, [networkRequests]);

  return (
    <Box>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          Network Requests Visualization
        </Text>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          data={[
            { label: "Waterfall", value: "waterfall" },
            { label: "Status", value: "status" },
            { label: "Duration", value: "duration" },
          ]}
        />
      </Group>

      {viewMode === "waterfall" && (
        <BarChart
          option={waterfallOption}
          height={Math.max(300, networkRequests.length * 40)}
          withLegend={false}
        />
      )}

      {viewMode === "status" && (
        <PieChart
          option={statusDistributionOption}
          height={300}
          withLegend={true}
        />
      )}

      {viewMode === "duration" && (
        <BarChart
          option={durationOption}
          height={Math.max(300, networkRequests.length * 40)}
          withLegend={false}
        />
      )}
    </Box>
  );
}
