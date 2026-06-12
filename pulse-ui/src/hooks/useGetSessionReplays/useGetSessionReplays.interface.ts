export interface SessionReplayData {
  trace_id: string;
  id: string;
  user_id: string;
  phone_number: string;
  device: string;
  os_version: string;
  start_time: string;
  duration_ms: number;
  event_count: number;
  screen_count: number;
  event_type: "crash" | "anr" | "networkError" | "frozenFrame" | "nonFatal" | "completed";
  event_names?: string;
  interaction_name: string;
  screens_visited: string;
}

export interface SessionReplayResponse {
  data: SessionReplayData[];
  pagination: {
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
    hasNextPage: boolean;
    hasPreviousPage: boolean;
  };
  stats: {
    total: number;
    completed: number;
    crashed: number;
    avgDuration: number;
  };
}

export interface GetSessionReplaysQueryParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  page?: number;
  pageSize?: number;
  eventTypes?: (
    | "crash"
    | "anr"
    | "networkError"
    | "frozenFrame"
    | "nonFatal"
    | "completed"
  )[];
  device?: "all" | "ios" | "android";
  enabled?: boolean;
}
