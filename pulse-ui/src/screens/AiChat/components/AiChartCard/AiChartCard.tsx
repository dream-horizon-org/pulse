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
import {
  AI_CHAT_LIMITS,
  CHART_GRID_DEFAULTS,
  CHART_Y_AXIS_DEFAULTS,
} from "../../AiChat.constants";
import classes from "./AiChartCard.module.css";

type ChartComponentType = React.FC<{
  option: Record<string, unknown>;
  height?: number;
}>;

const CHART_COMPONENTS: Record<AiChartType, ChartComponentType> = {
  line: LineChart as ChartComponentType,
  bar: BarChart as ChartComponentType,
  pie: PieChart as ChartComponentType,
  area: AreaChart as ChartComponentType,
};

export const AiChartCard = ({ chart }: AiChartCardProps) => {
  const ChartComponent = CHART_COMPONENTS[chart.type];

  const option = useMemo(() => {
    const base = (chart.data ?? {}) as Record<string, Record<string, unknown>>;
    const isPie = chart.type === "pie";

    if (isPie) return base;

    return {
      ...base,
      grid: {
        ...CHART_GRID_DEFAULTS,
        ...base.grid,
      },
      yAxis: {
        type: "value",
        ...base.yAxis,
        ...CHART_Y_AXIS_DEFAULTS,
        nameTextStyle: {
          ...CHART_Y_AXIS_DEFAULTS.nameTextStyle,
          color: "var(--mantine-color-gray-5)",
          ...(((base.yAxis as Record<string, unknown>)?.nameTextStyle as Record<
            string,
            unknown
          >) ?? {}),
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
          <ChartComponent
            option={option}
            height={AI_CHAT_LIMITS.CHART_HEIGHT}
          />
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
