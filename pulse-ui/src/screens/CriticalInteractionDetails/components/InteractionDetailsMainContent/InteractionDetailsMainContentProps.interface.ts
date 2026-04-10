import { InteractionDetailsResponse } from "../../../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";
import { CriticalInteractionDetailsFilterValues } from "../../CriticalInteractionDetails.interface";
import type { StartEndDateTimeType } from "../DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

export type InteractionDetailsMainContentProps = {
  jobDetails?: InteractionDetailsResponse;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
  startTime?: string;
  endTime?: string;
  orientation?: "horizontal" | "vertical";
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
};
