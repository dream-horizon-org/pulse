import type { CriticalInteractionDetailsFilterValues } from "../../CriticalInteractionDetails.interface";

export interface RootCauseProps {
  interactionName: string | null;
  date?: string | null;
  projectId?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  dashboardFilters?: CriticalInteractionDetailsFilterValues | null;
}
