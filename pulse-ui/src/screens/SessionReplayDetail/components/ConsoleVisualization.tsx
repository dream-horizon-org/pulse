import { Box, Group, SegmentedControl, Text } from '@mantine/core';
import { PieChart, LineChart } from '../../../components/Charts';
import { useMemo, useState } from 'react';
import type { ConsoleLog } from '../../../services/sessionReplay/mockSessionDetail';

interface ConsoleVisualizationProps {
  consoleLogs: ConsoleLog[];
  sessionStartTime: Date;
}

type ViewMode = 'distribution' | 'timeline';

export function ConsoleVisualization({ consoleLogs, sessionStartTime }: ConsoleVisualizationProps) {
  const [viewMode, setViewMode] = useState<ViewMode>('distribution');

  // Log level distribution pie chart
  const distributionOption = useMemo(() => {
    const counts: Record<string, number> = {};
    consoleLogs.forEach(log => {
      counts[log.level] = (counts[log.level] || 0) + 1;
    });

    return {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)',
      },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          data: Object.entries(counts).map(([level, count]) => ({
            value: count,
            name: level.toUpperCase(),
          })),
          itemStyle: {
            color: (params: any) => {
              const level = params.name.toLowerCase();
              if (level === 'error') return '#ef4444';
              if (level === 'warn') return '#f59e0b';
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
  }, [consoleLogs]);

  // Timeline of log levels over time
  const timelineOption = useMemo(() => {
    const levels = ['log', 'warn', 'error'];
    const series = levels.map(level => ({
      name: level.toUpperCase(),
      type: 'line',
      data: consoleLogs
        .filter(l => l.level === level)
        .map(l => [l.timestamp, 1]),
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: {
        width: 2,
      },
    }));

    const timestamps = consoleLogs.map(l => l.timestamp);
    const minTime = timestamps.length > 0 ? Math.min(...timestamps) : 0;
    const maxTime = timestamps.length > 0 ? Math.max(...timestamps) : 1000;

    return {
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const param = params[0];
          const log = consoleLogs.find(l => l.timestamp === param.value[0]);
          if (!log) return '';
          return `${log.level.toUpperCase()}<br/>${log.message.substring(0, 50)}...<br/>Time: ${param.value[0]}ms`;
        },
      },
      legend: {
        data: levels.map(l => l.toUpperCase()),
        bottom: 0,
      },
      xAxis: {
        type: 'value',
        name: 'Time (ms)',
        min: minTime,
        max: maxTime,
        nameTextStyle: {
          padding: [15, 0, 0, 0],
        },
      },
      yAxis: {
        type: 'value',
        name: 'Log Count',
        max: 1.5,
        nameTextStyle: {
          padding: [0, 0, 0, 20],
        },
      },
      series: series.filter(s => s.data.length > 0),
    };
  }, [consoleLogs]);

  return (
    <Box>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          Console Logs Visualization
        </Text>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          data={[
            { label: 'Distribution', value: 'distribution' },
            { label: 'Timeline', value: 'timeline' },
          ]}
        />
      </Group>

      {viewMode === 'distribution' && (
        <PieChart
          option={distributionOption}
          height={300}
          withLegend={true}
        />
      )}

      {viewMode === 'timeline' && (
        <LineChart
          option={timelineOption}
          height={300}
          withLegend={true}
        />
      )}
    </Box>
  );
}
