export interface UseGetAppStatsProps {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
}

export interface AppStatsData {
  totalUsers: number;
  totalSessions: number;
  hasData: boolean;
}

