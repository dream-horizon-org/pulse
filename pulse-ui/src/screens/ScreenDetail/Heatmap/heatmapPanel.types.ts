export interface HeatmapPanelProps {
  screenName: string;
  startTime: string;
  endTime: string;
  /** Set from Screen URL when opening heatmap from RCA evidence (`rcaHeatmapSignal`). */
  rcaHeatmapSignal?: string | null;
  engagement?: {
    avgTimeSpent: number | null;
    totalSessions: number;
    totalUsers: number;
  } | null;
}
