import { EChartsReactProps } from "echarts-for-react";

export interface BarChartProps extends EChartsReactProps {
  height?: number;
  withLegend?: boolean;
  tooltipValueFormatter?: (value: any) => string;
}
