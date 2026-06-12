import {
  EventSequenceItem,
  GlobalBlacklistedEvent,
} from "../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";

export type CriticalInteractionFormRequestBodyParams = {
  id?: number;
  name: string;
  description: string;
  uptimeLowerLimitInMs: number;
  uptimeMidLimitInMs: number;
  uptimeUpperLimitInMs: number;
  thresholdInMs: number;
  events: EventSequenceItem[];
  globalBlacklistedEvents: GlobalBlacklistedEvent[];
  status?: "RUNNING" | "STOPPED";
};

export type CreateJobResponse = {
  name: string;
  description: string;
  id: number;
  status: "RUNNING" | "STOPPED";
  createdAt: number;
  createdBy: string;
};
