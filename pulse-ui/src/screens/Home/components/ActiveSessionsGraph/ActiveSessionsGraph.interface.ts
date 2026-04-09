import type { StartEndDateTimeType } from "../../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export interface ActiveSessions {
  current: number;
  peak: number;
  average: number;
}

export interface ActiveSessionsTrendData {
  timestamp: number;
  sessions: number;
}

export interface ActiveSessionsGraphProps {
  screenName?: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  startTime?: string;
  endTime?: string;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
}
