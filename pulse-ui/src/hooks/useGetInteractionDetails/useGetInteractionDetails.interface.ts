export type GetInteractionDetailsParams = {
  queryParams: {
    name: string | null;
  } | null;
};

export type EventProp = {
  name: string;
  value: string;
  operator:
    | "EQUALS"
    | "CONTAINS"
    | "NOTEQUALS"
    | "NOTCONTAINS"
    | "STARTSWITH"
    | "ENDSWITH";
};

export type EventSequenceItem = {
  name: string;
  props: EventProp[];
  isBlacklisted: boolean | null;
};

export type GlobalBlacklistedEvent = {
  name: string;
  props: EventProp[];
  isBlacklisted: boolean;
};

export type InteractionDetailsResponse = {
  name: string;
  description: string;
  id: number;
  uptimeLowerLimitInMs: number;
  uptimeMidLimitInMs: number;
  uptimeUpperLimitInMs: number;
  thresholdInMs: number;
  status: "RUNNING" | "STOPPED";
  events: EventSequenceItem[];
  globalBlacklistedEvents: GlobalBlacklistedEvent[];
  createdAt: number;
  createdBy: string;
  updatedAt: number;
  updatedBy: string;
};
