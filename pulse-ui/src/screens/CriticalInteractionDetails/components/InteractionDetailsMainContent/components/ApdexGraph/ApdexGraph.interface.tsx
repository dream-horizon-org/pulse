import {
  InteractionDetailsGraphsData,
  InteractionDetailsMetricsData,
} from "../InteractionDetailsGraphs/interactionDetailsGraphs.interface";
import { Metric } from "./ApdexGraph";
import type { StartEndDateTimeType } from "../../../DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export type ApdexGraphDataItem = {
  date: string;
  Apdex: number;
  ErrorRate: number;
};

export type ApdexGraphData = ApdexGraphDataItem[];

export type GraphDataProps = {
  className?: string;
  metricToShow?: Metric[];
  thresholds?: {
    metric: Metric;
    threshold: number;
  }[];
  height?: number;

  // TODO: remove this later
  graphData?: InteractionDetailsGraphsData[];
  metrics?: InteractionDetailsMetricsData;
  useCaseName?: string;
  startTime?: string;
  endTime?: string;
  filters?: any;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
};

export type DateTimeMap = {
  [key: string]: {
    date: string;
    Apdex: number;
    ErrorRate: number;
  };
};
