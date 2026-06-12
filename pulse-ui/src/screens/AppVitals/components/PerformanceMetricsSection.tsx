import { Box, Text, Group, SimpleGrid } from "@mantine/core";
import { IconChartLine } from "@tabler/icons-react";
import { PerformanceChart } from "./PerformanceChart";
import { SessionReplayCard } from "./SessionReplayCard";

interface PerformanceMetricsSectionProps {
  performanceData: any[];
  title?: string;
  sessionId: string;
  onSessionReplayClick: () => void;
}

const CHART_CONFIGS = [
  {
    title: "CPU Usage",
    dataKey: "cpu",
    color: "#ef4444",
    domain: [0, 100] as [number, number],
    unit: "%",
  },
  {
    title: "Memory Usage",
    dataKey: "memory",
    color: "#f59e0b",
    domain: [0, 100] as [number, number],
    unit: "%",
  },
  {
    title: "Frame Rate",
    dataKey: "fps",
    color: "#10b981",
    domain: [0, 60] as [number, number],
    unit: "FPS",
  },
];

export const PerformanceMetricsSection: React.FC<
  PerformanceMetricsSectionProps
> = ({
  performanceData,
  title = "Performance Metrics & Session Replay (Last 1 Minute Before Crash)",
  sessionId,
  onSessionReplayClick,
}) => {
  return (
    <Box mb="md">
      {/* Section Header */}
      <Group gap="sm" mb="md">
        <IconChartLine size={20} color="#0ba09a" />
        <Text
          style={{
            fontSize: "16px",
            fontWeight: 700,
            color: "#0ba09a",
            letterSpacing: "-0.2px",
          }}
        >
          {title}
        </Text>
      </Group>

      {/* Charts Grid */}
      <SimpleGrid cols={4} spacing="md">
        {CHART_CONFIGS.map((config) => (
          <PerformanceChart
            key={config.dataKey}
            title={config.title}
            data={performanceData}
            dataKey={config.dataKey}
            color={config.color}
            domain={config.domain}
            unit={config.unit}
          />
        ))}
        <SessionReplayCard
          sessionId={sessionId}
          onClick={onSessionReplayClick}
        />
      </SimpleGrid>
    </Box>
  );
};
