import {
  EventSequenceItem,
  GlobalBlacklistedEvent,
} from "../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";

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

