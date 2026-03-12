import { Paper, Group, Text, Badge } from "@mantine/core";
import { IconBug } from "@tabler/icons-react";
import { FlameChart } from "../../SessionTimeline/components/FlameChart";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";

interface SessionTimelineSectionProps {
  flameChartData: FlameChartNode[];
  sessionDuration: number;
  sessionStartTime: number;
  totalDepth: number;
  onItemClick: (item: FlameChartNode) => void;
}

export function SessionTimelineSection({
  flameChartData,
  sessionDuration,
  sessionStartTime,
  totalDepth,
  onItemClick,
}: SessionTimelineSectionProps) {
  return (
    <Paper>
      <Group justify="space-between" mb="md">
        <Text size="md" fw={600}>
          Session Timeline
        </Text>
        <Badge
          size="sm"
          variant="light"
          color="teal"
          leftSection={<IconBug size={12} />}
        >
          {totalDepth} levels deep
        </Badge>
      </Group>

      <FlameChart
        data={flameChartData}
        sessionDuration={sessionDuration}
        sessionStartTime={sessionStartTime}
        totalDepth={totalDepth}
        onItemClick={onItemClick}
        isLoading={false}
      />
    </Paper>
  );
}
