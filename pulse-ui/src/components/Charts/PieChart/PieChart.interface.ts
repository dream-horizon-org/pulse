import { EChartsReactProps } from "echarts-for-react";

export interface PieChartProps extends EChartsReactProps {
  height?: number;
  withLegend?: boolean;
  tooltipValueFormatter?: (value: any) => string;
}
