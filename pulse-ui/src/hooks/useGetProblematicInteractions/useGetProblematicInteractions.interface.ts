import { CriticalInteractionDetailsFilterValues } from "../../screens/CriticalInteractionDetails";

export type EventTypeFilter = "crash" | "anr" | "frozenFrame" | "nonFatal" | "error" | "completed" 

export type InteractionEventType = "crash" | "anr" | "frozenFrame" | "nonFatal" | "error" | "completed";

export interface ProblematicInteractionData {
  trace_id: string;
  sessionId: string;
  user_id: string;
  phone_number: string;
  device: string;
  os_version: string;
  start_time: string;
  duration_ms: number;
  event_count: number;
  screen_count: number;
  event_type: InteractionEventType;
  event_names?: string;
  interaction_name: string;
  screens_visited: string;
}

export interface UseGetProblematicInteractionsParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  eventTypeFilters?: EventTypeFilter[];
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetProblematicInteractionsReturn {
  interactions: ProblematicInteractionData[];
  isLoading: boolean;
  isError: boolean;
  error?: any;
}

