export type SessionRcaMode = "flat" | "hierarchical";

export interface SessionRcaSegment {
  label: string;
  dimensions: Record<string, string>;
  metrics: Record<string, unknown>;
  deltas?: Record<string, number> | null;
}

export interface SessionRcaData {
  baseline: Record<string, unknown> | null;
  segments: SessionRcaSegment[] | null;
  mode?: SessionRcaMode | null;
  cachedAt?: string | null;
  everythingGood?: boolean | null;
  noDataAvailable?: boolean | null;
  message?: string | null;
}

export interface UseGetSessionRcaParams {
  date: string | null | undefined;
  asOfIso: string | null | undefined;
  projectId: string | null | undefined;
  enabled?: boolean;
}
