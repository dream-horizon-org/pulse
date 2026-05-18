import { TrendPoint } from "../..";

export interface VitalTrendChartProps {
  vitalName: string;
  data: TrendPoint[] | undefined;
  isLoading: boolean;
  error: Error | null;
  height?: number;
}
