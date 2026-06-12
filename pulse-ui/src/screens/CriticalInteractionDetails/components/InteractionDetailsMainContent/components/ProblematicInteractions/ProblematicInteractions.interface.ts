import { CriticalInteractionDetailsFilterValues } from "../../../../CriticalInteractionDetails.interface";

export interface ProblematicInteractionsProps {
  interactionName: string;
  startTime: string;
  endTime: string;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface ProblematicInteractionData {
  id: string;
  user_id: string;
  phone_number: string;
  device: string;
  os_version: string;
  start_time: Date;
  duration_seconds: number;
  event_count: number;
  screen_count: number;
  status: "completed" | "crashed";
  interaction_name: string;
  screens_visited: string;
}

export interface FiltersState {
  eventTypes: ("crash" | "anr" | "frozenFrame" | "nonFatal" | "error" | "completed")[];
  device: "all" | "ios" | "android";
  timeRange: "all" | "24h" | "7d";
}
