import { EChartsReactProps } from "echarts-for-react";
import type { StartEndDateTimeType } from "../../../screens/CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export interface LineChartProps extends EChartsReactProps {
  height?: number;
  zoom?: boolean;
  withLegend?: boolean;
  tooltipValueFormatter?: (value: any) => string;
  syncTooltips?: boolean;
  group?: string;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
  mapBrushToTimeFilter?: (
    startLabel: string,
    endLabel: string,
  ) => StartEndDateTimeType | null | undefined;
}
