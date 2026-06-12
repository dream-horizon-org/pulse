export interface Screen {
  id: string;
  screenName: string;
  description: string;
  status: string;
  apdexScore?: number;
  errorRate?: number;
  p50Latency?: number;
  volume?: number;
}

export interface GetScreensResponse {
  screens: Screen[];
  totalScreens: number;
}

export interface FiltersType {
  users: string;
  statuses: string;
}

export interface PaginationType {
  page: number;
  size: number;
}

export const defaultPageSize = 20;

export enum TEAM_NAMES {
  PAYMENTS = "PAYMENTS",
  MEDIA = "MEDIA",
  CHAT = "CHAT",
  SEARCH = "SEARCH",
  MONETIZATION = "MONETIZATION",
}

export const TEAM_NAMES_VS_LABELS: Record<TEAM_NAMES, string> = {
  [TEAM_NAMES.PAYMENTS]: "Payments",
  [TEAM_NAMES.MEDIA]: "Media",
  [TEAM_NAMES.CHAT]: "Chat",
  [TEAM_NAMES.SEARCH]: "Search",
  [TEAM_NAMES.MONETIZATION]: "Monetization",
};
