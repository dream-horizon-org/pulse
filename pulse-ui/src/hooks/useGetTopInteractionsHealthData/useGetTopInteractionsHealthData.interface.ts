export interface UseGetTopInteractionsHealthDataProps {
  startTime: string;
  endTime: string;
  limit?: number;
}

export interface TopInteractionHealthData {
  id: number;
  interactionName: string;
  apdex: number;
  errorRate: number;
  p50: number;
  poorUserPercentage: number;
}

