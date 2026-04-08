import { Box, Group, SegmentedControl, Text } from "@mantine/core";
import { BarChart } from "../../../components/Charts";
import { useMemo, useState } from "react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";

interface PerformanceVisualizationProps {
  performance: SessionDetailData["performance"];
}

type ViewMode = "duration" | "apdex";

export function PerformanceVisualization({
  performance,
}: PerformanceVisualizationProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("duration");

  // Prepare data for Duration Bar Chart
  const durationChartData = useMemo(() => {
    const interactions = performance.interactionMetrics.map(
      (m) => m.interactionName,
    );
    const durations = performance.interactionMetrics.map((m) => m.duration);

    return {
      labels: interactions,
      datasets: [
        {
          name: "Duration (ms)",
          values: durations,
        },
      ],
    };
  }, [performance.interactionMetrics]);

  // Prepare data for Apdex Bar Chart
  const apdexChartData = useMemo(() => {
    const interactions = performance.interactionMetrics.map(
      (m) => m.interactionName,
    );
    const apdexScores = performance.interactionMetrics.map((m) => m.apdexScore);

    return {
      labels: interactions,
      datasets: [
        {
          name: "Apdex Score",
          values: apdexScores,
        },
      ],
    };
  }, [performance.interactionMetrics]);

  // Duration chart option
  const durationOption = useMemo(() => {
    return {
      tooltip: {
        trigger: "axis",
        formatter: (params: any) => {
          const param = params[0];
          return `${param.name}<br/>${param.seriesName}: ${param.value}ms`;
        },
      },
      xAxis: {
        type: "category",
        data: durationChartData.labels,
        axisLabel: {
          rotate: durationChartData.labels.length > 4 ? 25 : 0,
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
          name: "Duration (ms)",
          type: "bar",
          data: durationChartData.datasets[0].values,
          itemStyle: {
            color: (params: any) => {
              const duration = params.value;
              if (duration > 1000) return "#ef4444"; // Red for slow
              if (duration > 500) return "#f59e0b"; // Yellow for medium
              return "#0ec9c2"; // Teal for fast
            },
          },
          barMaxWidth: 60,
        },
      ],
    };
  }, [durationChartData]);

  // Apdex chart option
  const apdexOption = useMemo(() => {
    return {
      tooltip: {
        trigger: "axis",
        formatter: (params: any) => {
          const param = params[0];
          const score = param.value;
          let status = "Poor";
          if (score >= 0.8) status = "Excellent";
          else if (score >= 0.5) status = "Fair";
          return `${param.name}<br/>Apdex: ${score.toFixed(2)} (${status})`;
        },
      },
      xAxis: {
        type: "category",
        data: apdexChartData.labels,
        axisLabel: {
          rotate: apdexChartData.labels.length > 4 ? 25 : 0,
          fontSize: 11,
        },
      },
      yAxis: {
        type: "value",
        name: "Apdex Score",
        min: 0,
        max: 1,
        nameTextStyle: {
          padding: [0, 0, 0, 20],
        },
      },
      series: [
        {
          name: "Apdex Score",
          type: "bar",
          data: apdexChartData.datasets[0].values,
          itemStyle: {
            color: (params: any) => {
              const score = params.value;
              if (score >= 0.8) return "#0ec9c2"; // Teal for excellent
              if (score >= 0.5) return "#f59e0b"; // Yellow for fair
              return "#ef4444"; // Red for poor
            },
          },
          barMaxWidth: 60,
        },
      ],
    };
  }, [apdexChartData]);

  return (
    <Box>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          Interaction Metrics Visualization
        </Text>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          data={[
            { label: "Duration", value: "duration" },
            { label: "Apdex", value: "apdex" },
          ]}
        />
      </Group>

      {viewMode === "duration" && (
        <BarChart option={durationOption} height={300} withLegend={false} />
      )}

      {viewMode === "apdex" && (
        <BarChart option={apdexOption} height={300} withLegend={false} />
      )}
    </Box>
  );
}
