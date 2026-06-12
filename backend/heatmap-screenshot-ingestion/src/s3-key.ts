import { DateTime } from "luxon";

import type { HeatmapScreenshotPayload } from "./heatmap-extract";

const MAX_SEGMENT_LEN = 200;

/** Safe single path segment for S3 keys (project id, screen name, etc.). */
export function sanitizePathSegment(raw: string, fallback: string): string {
  const trimmed = raw.trim().slice(0, MAX_SEGMENT_LEN);
  const cleaned = trimmed
    .replace(/[/\\]+/g, "_")
    .replace(/[\x00-\x1f\x7f]/g, "")
    .replace(/^\.+/, "");
  return cleaned.length > 0 ? cleaned : fallback;
}

export function buildHeatmapS3ObjectKey(params: {
  s3Prefix: string;
  projectId: string;
  metaTimestampMs: number;
  platform: string;
  appVersionLabel: string;
  screenHref: string;
  breakpoint: string;
  objectFileName: string;
}): string {
  const dateUtc = DateTime.fromMillis(params.metaTimestampMs, {
    zone: "utc",
  }).toFormat("yyyyMMdd");

  const prefix = params.s3Prefix.replace(/\/+$/, "");
  /** Order: project → date → screen (browse by screen per day) → platform → app version → breakpoint → file */
  const parts = [
    prefix,
    sanitizePathSegment(params.projectId, "unknown"),
    dateUtc,
    sanitizePathSegment(params.screenHref, "screen"),
    sanitizePathSegment(params.platform, "unknown"),
    sanitizePathSegment(params.appVersionLabel, "unknown"),
    sanitizePathSegment(params.breakpoint, "unknown"),
    params.objectFileName.replace(/[/\\]/g, "_"),
  ];

  return parts.join("/");
}

/**
 * UTC calendar date for S3 object tag `date` (yyyy-MM-dd). Same semantics as session-replay key folder date.
 */
export function utcDateTagYyyyMmDdFromMillis(metaTimestampMs: number): string {
  return DateTime.fromMillis(metaTimestampMs, { zone: "utc" }).toFormat(
    "yyyy-MM-dd",
  );
}

/**
 * S3 `Tagging` header: `project_id` + `date` (must match session-replay-ingestion exactly).
 */
export function buildIngestionS3ObjectTagging(
  projectId: string,
  dateUtcYyyyMmDd: string,
): string {
  return `project_id=${encodeURIComponent(projectId)}&date=${encodeURIComponent(dateUtcYyyyMmDd)}`;
}

export function appVersionForPath(
  appVersion: string | null | undefined,
): string {
  if (typeof appVersion === "string" && appVersion.trim().length > 0) {
    return sanitizePathSegment(appVersion.trim(), "unknown");
  }
  return "unknown";
}

export function heatmapJsonBody(params: {
  schemaVersion: number;
  projectId: string;
  sessionId: string;
  snapshotSource: string;
  appVersion: string | null;
  screenHref: string;
  breakpoint: string;
  meta: HeatmapScreenshotPayload["meta"];
  base64: string;
}): string {
  return JSON.stringify(
    {
      schemaVersion: params.schemaVersion,
      projectId: params.projectId,
      sessionId: params.sessionId,
      snapshotSource: params.snapshotSource,
      appVersion: params.appVersion,
      screenHref: params.screenHref,
      breakpoint: params.breakpoint,
      metaTimestamp: params.meta.timestamp,
      viewport: {
        width: params.meta.width,
        height: params.meta.height,
      },
      image: {
        encoding: "base64",
        mimeType: "image/png",
        data: params.base64,
      },
    },
    null,
    0,
  );
}
