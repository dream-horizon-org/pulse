export interface SessionReplayProps {}

export interface SessionData {
  id: string;
  sessionId: string;
  startTime: Date;
  userId: string;
  isAnonymous: boolean;
  duration: number;
  pages: number;
  events: number;
  errors: number;
  device: string;
  browser: string;
  os: string;
  journey: string[];
  tags: SessionTag[];
  environment: 'production' | 'staging' | 'development';
  project: 'ios' | 'android' | 'web';
}

export interface SessionTag {
  type: 'rage' | 'slow' | 'new' | 'js_error' | 'network_fail' | 'dead_click';
  count?: number;
}

export interface FilterState {
  dateRange: string;
  environment: string;
  project: string;
  searchQuery: string;
  quickFilters: {
    hasErrors: boolean;
    rageClicks: boolean;
    slowSessions: boolean;
    mobile: boolean;
    newUsers: boolean;
  };
  advancedFilters: Record<string, any>;
}

export interface KPIMetrics {
  totalSessions: number;
  errorRate: number;
  avgDuration: number;
  p95LoadTime: number;
}
