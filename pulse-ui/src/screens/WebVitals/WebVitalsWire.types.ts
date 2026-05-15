/**
 * Wire JSON shapes from pulse-server Web Vitals endpoints.
 * Jackson may emit snake_case (@JsonProperty); some fields may also appear camelCase.
 */

export interface VitalSummaryWire {
  name?: string;
  p75?: number;
  good_pct?: number;
  goodPct?: number;
  needs_improvement_pct?: number;
  needsImprovementPct?: number;
  poor_pct?: number;
  poorPct?: number;
  total_count?: number;
  totalCount?: number;
}

export interface WebVitalsSummaryWire {
  vitals?: VitalSummaryWire[];
}

export interface TrendPointWire {
  bucket?: string;
  p75?: number;
}

export interface WebVitalsTrendWire {
  points?: TrendPointWire[];
}

export interface ScreenVitalWire {
  screen_name?: string;
  screenName?: string;
  p75?: number;
  total_count?: number;
  totalCount?: number;
  good_pct?: number;
  goodPct?: number;
}

export interface WebVitalsByScreenWire {
  screens?: ScreenVitalWire[];
}
