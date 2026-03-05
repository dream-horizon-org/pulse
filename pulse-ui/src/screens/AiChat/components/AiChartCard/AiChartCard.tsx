import { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartBar } from "@tabler/icons-react";
import { LineChart } from "../../../../components/Charts/LineChart/LineChart";
import { BarChart } from "../../../../components/Charts/BarChart/BarChart";
import { PieChart } from "../../../../components/Charts/PieChart/PieChart";
import { AreaChart } from "../../../../components/Charts/AreaChart/AreaChart";
import { AiChartCardProps } from "./AiChartCard.interface";
import { AiChartType } from "../../types/chat";
import { ChartErrorBoundary } from "./ChartErrorBoundary";
import classes from "./AiChartCard.module.css";

const CHART_HEIGHT = 300;

const CHART_COMPONENTS: Record<AiChartType, React.FC<{ option: any; height?: number }>> = {
  line: LineChart,
  bar: BarChart,
  pie: PieChart,
  area: AreaChart,
};

export const AiChartCard = ({ chart }: AiChartCardProps) => {
  const ChartComponent = CHART_COMPONENTS[chart.type];

  const option = useMemo(() => {
    const base = chart.data ?? {};
    const isPie = chart.type === "pie";

    if (isPie) return base;

    return {
      ...base,
      grid: {
        top: 30,
        left: 50,
        right: 30,
        bottom: 50,
        containLabel: true,
        ...(base as any).grid,
      },
      yAxis: {
        type: "value",
        ...(base as any).yAxis,
        nameLocation: "middle",
        nameGap: 45,
        nameTextStyle: {
          fontSize: 12,
          color: "#888",
          ...((base as any).yAxis?.nameTextStyle),
        },
      },
    };
  }, [chart.data, chart.type]);

  if (!ChartComponent) return null;

  return (
    <ChartErrorBoundary chartConfig={chart}>
      <Box className={classes.container}>
        <div className={classes.header}>
          <Text size="xs" fw={600} c="teal.7">
            <IconChartBar size={12} className={classes.headerIcon} />
            {chart.title}
          </Text>
        </div>
        <div className={classes.chartWrapper}>
          <ChartComponent option={option} height={CHART_HEIGHT} />
        </div>
        {chart.description && (
          <Text size="xs" c="dimmed" className={classes.description}>
            {chart.description}
          </Text>
        )}
      </Box>
    </ChartErrorBoundary>
  );
};
