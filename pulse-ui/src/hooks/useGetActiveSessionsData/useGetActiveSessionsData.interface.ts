export interface UseGetActiveSessionsDataProps {
  screenName?: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  startTime: string;
  endTime: string;
  bucketSize: string;
  spanType?: string;
}

export interface ActiveSessionsData {
  currentSessions: number | null;
  peakSessions: number | null;
  averageSessions: number | null;
  trendData: Array<{
    timestamp: number;
    sessions: number;
  }>;
  hasData: boolean;
}

