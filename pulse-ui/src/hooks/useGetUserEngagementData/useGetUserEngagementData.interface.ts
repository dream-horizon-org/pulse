export interface UseGetUserEngagementDataProps {
  screenName?: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  dailyStartDate: string;
  dailyEndDate: string;
  weekStartDate: string;
  weekEndDate: string;
  monthStartDate: string;
  monthEndDate: string;
  spanType?: string;
}

export interface UserEngagementData {
  dailyUsers: number;
  weeklyUsers: number;
  monthlyUsers: number;
  trendData: Array<{
    timestamp: number;
    dau: number;
  }>;
}

