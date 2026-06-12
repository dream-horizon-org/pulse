import { EChartsReactProps } from "echarts-for-react";
import type { StartEndDateTimeType } from "../../../screens/CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export interface LineChartProps extends EChartsReactProps {
  height?: number;
  zoom?: boolean;
  withLegend?: boolean;
  tooltipValueFormatter?: (value: any) => string;
  syncTooltips?: boolean;
  /** When false, disables brush selection (recommended for time-axis charts without category `xAxis.data`). */
  enableBrushSelection?: boolean;
  group?: string;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
  mapBrushToTimeFilter?: (
    startLabel: string,
    endLabel: string,
  ) => StartEndDateTimeType | null | undefined;
  /** Sync chart dataZoom (time x-axis, ms) to the global time filter. */
  syncDataZoomToTimeFilter?: boolean;
}
