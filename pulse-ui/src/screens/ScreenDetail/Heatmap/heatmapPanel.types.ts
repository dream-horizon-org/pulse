export interface HeatmapPanelProps {
  screenName: string;
  startTime: string;
  endTime: string;
  engagement?: {
    avgTimeSpent: number | null;
    totalSessions: number;
    totalUsers: number;
  } | null;
}
