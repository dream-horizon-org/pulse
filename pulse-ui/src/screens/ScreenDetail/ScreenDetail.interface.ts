export interface ScreenDetailProps {
  screenName?: string;
}

export interface ScreenMetrics {
  screenLoadTime: number; // in ms
  avgTimeSpent: number; // in seconds
  dau: number;
  wau: number;
  mau: number;
  sessions: number;
  anrs: number;
  crashes: number;
  frozenFrames: number;
  networkRequests: number;
  networkLoadTime: number; // in ms
}

export interface ScreenTrendData {
  timestamp: number;
  loadTime: number;
  sessions: number;
  errors: number;
}
