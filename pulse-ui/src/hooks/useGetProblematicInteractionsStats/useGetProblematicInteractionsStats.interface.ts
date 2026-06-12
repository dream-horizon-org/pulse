import { CriticalInteractionDetailsFilterValues } from "../../screens/CriticalInteractionDetails";

export interface ProblematicInteractionsStatsData {
  total: number;
  completed: number;
  errored: string;
  crashed: string;
  latency: number;
}

export interface UseGetProblematicInteractionsStatsParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetProblematicInteractionsStatsReturn {
  stats: ProblematicInteractionsStatsData;
  isLoading: boolean;
  isError: boolean;
  error?: any;
}

