export interface SnapshotsSourceBlob {
  source?: string;
  blobKey: string;
  startTimestamp: string;
  endTimestamp: string;
}

export interface SnapshotsSourceResponse {
  data: {
    sessionId?: string;
    snapshotSource: string;
    sources: SnapshotsSourceBlob[];
  };
  error: string | null;
}

export interface SnapshotEventData {
  href?: string;
  width?: number;
  height?: number;
  wireframes?: unknown[];
  initialOffset?: Record<string, unknown>;
  source?: number;
  positions?: unknown[];
  [key: string]: unknown;
}

export interface SnapshotEvent {
  timestamp: number;
  type: number;
  data: SnapshotEventData;
}

export interface SnapshotsDataResponse {
  data: {
    snapshots: SnapshotEvent[];
  };
  error: string | null;
}
