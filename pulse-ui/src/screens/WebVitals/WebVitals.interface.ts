export interface VitalSummary {
  name: string;
  p75: number;
  goodPct: number;
  needsImprovementPct: number;
  poorPct: number;
  totalCount: number;
}

export interface WebVitalsSummaryResponse {
  vitals: VitalSummary[];
}

export interface TrendPoint {
  bucket: string;
  p75: number;
}

export interface WebVitalsTrendResponse {
  points: TrendPoint[];
}

export interface ScreenVital {
  screenName: string;
  p75: number;
  totalCount: number;
  goodPct: number;
}

export interface WebVitalsByScreenResponse {
  screens: ScreenVital[];
}
