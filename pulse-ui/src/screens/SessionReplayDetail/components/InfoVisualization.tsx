import { Box, Group, SegmentedControl, Text } from '@mantine/core';
import { BarChart, PieChart } from '../../../components/Charts';
import { useMemo, useState } from 'react';
import type { CriticalInteraction } from '../../../services/sessionReplay/mockSessionDetail';

interface InfoVisualizationProps {
  criticalInteractions: CriticalInteraction[];
  journey: string[];
}

type ViewMode = 'interactions' | 'success-rate';

export function InfoVisualization({ criticalInteractions, journey }: InfoVisualizationProps) {
  const [viewMode, setViewMode] = useState<ViewMode>('interactions');

  // Critical Interactions success/failure bar chart
  const interactionsOption = useMemo(() => {
    const interactions = criticalInteractions.map(i => ({
      name: i.displayName,
      latency: i.latency || 0,
      status: i.status,
    }));

    return {
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const param = params[0];
          const interaction = interactions[param.dataIndex];
          return `${param.name}<br/>Latency: ${interaction.latency}ms<br/>Status: ${interaction.status}`;
        },
      },
      xAxis: {
        type: 'category',
        data: interactions.map(i => i.name),
        axisLabel: {
          rotate: interactions.length > 4 ? 25 : 0,
          fontSize: 11,
        },
      },
      yAxis: {
        type: 'value',
        name: 'Latency (ms)',
        nameTextStyle: {
          padding: [0, 0, 0, 20],
        },
      },
      series: [
        {
          name: 'Latency',
          type: 'bar',
          data: interactions.map(i => i.latency),
          itemStyle: {
            color: (params: any) => {
              const interaction = interactions[params.dataIndex];
              if (interaction.status === 'failed') return '#ef4444';
              if (interaction.latency > 1000) return '#f59e0b';
              return '#0ec9c2';
            },
          },
          barMaxWidth: 60,
        },
      ],
    };
  }, [criticalInteractions]);

  // Success rate pie chart
  const successRateOption = useMemo(() => {
    const successCount = criticalInteractions.filter(i => i.status === 'success').length;
    const failedCount = criticalInteractions.filter(i => i.status === 'failed').length;
    const notAttemptedCount = criticalInteractions.filter(i => i.status === 'not_attempted').length;

    return {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)',
      },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          data: [
            { value: successCount, name: 'Success' },
            { value: failedCount, name: 'Failed' },
            { value: notAttemptedCount, name: 'Not Attempted' },
          ],
          itemStyle: {
            color: (params: any) => {
              const name = params.name.toLowerCase();
              if (name === 'success') return '#0ec9c2';
              if (name === 'failed') return '#ef4444';
              return '#6b7280';
            },
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)',
            },
          },
        },
      ],
    };
  }, [criticalInteractions]);

  return (
    <Box>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          Critical Interactions Visualization
        </Text>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          data={[
            { label: 'Latency', value: 'interactions' },
            { label: 'Success Rate', value: 'success-rate' },
          ]}
        />
      </Group>

      {viewMode === 'interactions' && (
        <BarChart
          option={interactionsOption}
          height={300}
          withLegend={false}
        />
      )}

      {viewMode === 'success-rate' && (
        <PieChart
          option={successRateOption}
          height={300}
          withLegend={true}
        />
      )}
    </Box>
  );
}
