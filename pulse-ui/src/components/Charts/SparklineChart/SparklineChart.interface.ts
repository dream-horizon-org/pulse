import { EChartsReactProps } from "echarts-for-react";

export interface SparklineChartProps extends EChartsReactProps {
  height?: number;
  zoom?: boolean;
  fillOpacity?: number;
  withGradient?: boolean;
  strokeWidth?: number;
}
