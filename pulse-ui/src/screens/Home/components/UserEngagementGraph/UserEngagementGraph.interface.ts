export interface UserEngagementMetrics {
  dau: number;
  wau: number;
  mau: number;
}

export interface UserEngagementTrendData {
  timestamp: number;
  dau: number;
  wau: number;
  mau: number;
}

export interface UserEngagementGraphProps {
  screenName?: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  startTime?: string;
  endTime?: string;
}
