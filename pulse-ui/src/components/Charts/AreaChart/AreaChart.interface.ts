import { EChartsReactProps } from "echarts-for-react";
import type { StartEndDateTimeType } from "../../../screens/CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export interface AreaChartProps extends EChartsReactProps {
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
  /**
   * Sync chart dataZoom on a **time** x-axis (ms) to the global time filter. Category-axis charts
   * must leave this false.
   */
  syncDataZoomToTimeFilter?: boolean;
}
