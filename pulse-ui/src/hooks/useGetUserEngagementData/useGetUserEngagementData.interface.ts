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
}

export interface UserEngagementData {
  dailyUsers: number | null;
  weeklyUsers: number | null;
  monthlyUsers: number | null;
  trendData: Array<{
    timestamp: number;
    dau: number;
  }>;
  hasData: boolean;
}

