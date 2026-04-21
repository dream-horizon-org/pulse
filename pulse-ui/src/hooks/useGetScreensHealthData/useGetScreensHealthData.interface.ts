export interface UseGetScreensHealthDataProps {
  startTime: string;
  endTime: string;
  limit?: number;
}

export interface ScreenHealthData {
  screenName: string;
  avgTimeSpent: number | undefined;
  crashRate: number;
  loadTime: number;
  users: number;
  screenType: string;
  errorRate: number;
}

