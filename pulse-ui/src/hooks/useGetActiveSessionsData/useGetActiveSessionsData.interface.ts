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
  currentSessions: number;
  peakSessions: number;
  averageSessions: number;
  trendData: Array<{
    timestamp: number;
    sessions: number;
  }>;
}

