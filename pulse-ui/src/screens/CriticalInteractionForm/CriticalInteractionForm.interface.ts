import { EventListData } from "../../hooks/useGetScreenNameToEventQueryMapping";

export type CriticalInteractionFormErrors = {
  isEmailError: boolean;
  isCriticalInteractionNameError: boolean;
  isLowerThresholdError: boolean;
  isUpperThresholdError: boolean;
};

export type EventSequenceData = EventFilters & {
  draggableId: string;
};

export type EventFilters = {
  name: string;
  props: Array<PropFilter>;
  isBlacklisted: boolean;
};

export type FilterOperator =
  | "EQUALS"
  | "NOTEQUALS"
  | "CONTAINS"
  | "NOTCONTAINS"
  | "STARTSWITH"
  | "ENDSWITH";
export type PropFilter = {
  name: string;
  value: string;
  operator: FilterOperator;
};

export type CriticalInteractionFormData = {
  name: string;
  description: string;
  uptimeLowerLimitInMs: number;
  uptimeMidLimitInMs: number;
  uptimeUpperLimitInMs: number;
  thresholdInMs: number;
  events: Array<EventSequenceData>;
  globalBlacklistedEvents: Array<EventFilters>;
};

export type CriticalInteractionsFetchingState = {
  isFetching: boolean;
  error: string;
};

export type CriticalInteractionFormSteps = Array<FormSteps>;

export type FormSteps = {
  label: string;
  description: string;
};

export type CriticalInteractionFormStepsRecords = Record<number, FormRecord>;

export type FormRecord = {
  errorMessage: string;
  isCompleted: boolean;
};

export type EventsMetadata = Record<string, Metadata>;

export type Metadata = {
  description: string;
  properties: EventListData["properties"];
};
