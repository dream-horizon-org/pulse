import type { StartEndDateTimeType } from "../../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export interface NonFatalTrendGraphProps {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  platform?: string;
  networkProvider?: string;
  state?: string;
  screenName?: string;
  title: string;
  lineColor: string;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
}
