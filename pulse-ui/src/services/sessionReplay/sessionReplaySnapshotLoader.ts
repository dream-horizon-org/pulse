/**
 * Snapshot loader for session replay video player.
 * Uses GET /v1/sessions/{sessionId}/snapshots-source and
 * GET /v1/sessions/{sessionId}/snapshots-data?start_blob_key=&end_blob_key=
 * with IndexedDB cache (max 20 blobs per request).
 */

import { sessionReplayService } from "./SessionReplayService";
import type { SnapshotsSourceBlob } from "./sessionReplaySnapshotTypes";
import type { SnapshotEvent } from "./sessionReplaySnapshotTypes";
import {
  getCachedBlobRange,
  setCachedBlobRange,
  touchSession,
  cleanupStaleSessions,
} from "./sessionReplaySnapshotCache";
import type { SessionReplayImage } from "./sessionReplayImages";

const MAX_BLOBS_PER_REQUEST = 20;

function parseTimestamp(ts: string): number {
  const normalized = ts.includes("T") ? ts : ts.replace(" ", "T") + "Z";
  return new Date(normalized).getTime();
}

/** Type 4 = image frame with href in data */
const IMAGE_FRAME_TYPE = 4;
/** Type 3 = incremental snapshot with updates[].wireframe (may have base64) */
const INCREMENTAL_SNAPSHOT_TYPE = 3;

/**
 * Decode raw base64 string to a blob: URL.
 * Detects MIME from the binary header (RIFF → webp, PNG → png, JFIF/Exif → jpeg, fallback octet-stream).
 */
function base64ToBlobUrl(raw: string): string | null {
  try {
    const cleaned = raw.replace(/\s/g, "");
    const binary = atob(cleaned);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }

    let mime = "application/octet-stream";
    const header = binary.slice(0, 16);
    if (header.startsWith("RIFF") && header.includes("WEBP")) {
      mime = "image/webp";
    } else if (binary.charCodeAt(0) === 0x89 && header.includes("PNG")) {
      mime = "image/png";
    } else if (binary.charCodeAt(0) === 0xff && binary.charCodeAt(1) === 0xd8) {
      mime = "image/jpeg";
    }

    const blob = new Blob([bytes], { type: mime });
    return URL.createObjectURL(blob);
  } catch {
    return null;
  }
}

/**
 * Convert API snapshot events to player images.
 * Uses type 4 events (image frame) with data.href, or type 3 with data.updates[].wireframe.base64.
 * Base64 payloads are decoded to blob: URLs for reliable <img> rendering.
 */
export function snapshotEventsToImages(
  events: SnapshotEvent[],
  sessionStartMs: number,
  blobKey: string,
): SessionReplayImage[] {
  const images: SessionReplayImage[] = [];
  for (const event of events) {
    const timestampMs = Math.max(0, event.timestamp - sessionStartMs);
    if (event.type === IMAGE_FRAME_TYPE && event.data?.href) {
      images.push({
        timestamp: timestampMs,
        imageUrl: event.data.href,
        blobKey,
      });
      continue;
    }
    const updates = event.data?.updates;
    if (
      event.type === INCREMENTAL_SNAPSHOT_TYPE &&
      Array.isArray(updates) &&
      updates.length > 0
    ) {
      for (const u of updates as Array<{ wireframe?: { base64?: string } }>) {
        const w = u.wireframe;
        if (w?.base64) {
          const blobUrl = base64ToBlobUrl(w.base64);
          if (blobUrl) {
            images.push({
              timestamp: timestampMs,
              imageUrl: blobUrl,
              blobKey,
            });
          }
          break;
        }
      }
    }
  }
  return images;
}

/**
 * Fetch a blob range from API or cache.
 */
async function fetchBlobRange(
  sessionId: string,
  startBlobKey: string,
  endBlobKey: string,
): Promise<SnapshotEvent[]> {
  const cached = await getCachedBlobRange(sessionId, startBlobKey, endBlobKey);
  if (cached != null) return cached;

  const data = await sessionReplayService.getSnapshotsData(
    sessionId,
    startBlobKey,
    endBlobKey,
  );
  const snapshots = data.snapshots ?? [];
  await setCachedBlobRange(sessionId, startBlobKey, endBlobKey, snapshots);
  return snapshots;
}

/**
 * Find the blob index in manifest that contains the given time (ms from session start).
 * sessionStartMs = session start as Unix ms.
 */
function findBlobIndexForTime(
  sources: SnapshotsSourceBlob[],
  sessionStartMs: number,
  currentTimeMs: number,
): number {
  const absoluteMs = sessionStartMs + currentTimeMs;
  for (let i = 0; i < sources.length; i++) {
    const start = parseTimestamp(sources[i].startTimestamp);
    const end = parseTimestamp(sources[i].endTimestamp);
    if (absoluteMs >= start && absoluteMs <= end) return i;
  }
  if (sources.length === 0) return -1;
  if (absoluteMs < parseTimestamp(sources[0].startTimestamp)) return 0;
  return sources.length - 1;
}

/**
 * Compute blob range to load: up to MAX_BLOBS_PER_REQUEST blobs centered around the given index,
 * with buffer before/after for seeking.
 */
function getBlobRangeToLoad(
  sources: SnapshotsSourceBlob[],
  centerIndex: number,
): { startIndex: number; endIndex: number } {
  if (sources.length === 0) return { startIndex: 0, endIndex: 0 };
  const half = Math.floor(MAX_BLOBS_PER_REQUEST / 2);
  const startIndex = Math.max(0, centerIndex - half);
  const endIndex = Math.min(
    sources.length - 1,
    startIndex + MAX_BLOBS_PER_REQUEST - 1,
  );
  return { startIndex, endIndex };
}

/**
 * Compute total snapshot duration (ms) from the manifest:
 * last blob endTimestamp − first blob startTimestamp.
 */
export function computeSnapshotDurationMs(
  sources: SnapshotsSourceBlob[],
): number {
  if (sources.length === 0) return 0;
  const first = parseTimestamp(sources[0].startTimestamp);
  const last = parseTimestamp(sources[sources.length - 1].endTimestamp);
  return Math.max(0, last - first);
}

/**
 * Fetch the snapshot source manifest for a session.
 * Returns the sources array and computed total duration in ms.
 */
export async function fetchSnapshotManifest(
  sessionId: string,
): Promise<{ sources: SnapshotsSourceBlob[]; durationMs: number }> {
  const manifest = await sessionReplayService.getSnapshotsSource(sessionId);
  const sources = manifest.sources ?? [];
  return {
    sources,
    durationMs: computeSnapshotDurationMs(sources),
  };
}

export interface LoadSnapshotsOptions {
  sessionId: string;
  sessionStartMs: number;
  currentTimeMs: number;
  /** Blob key ranges already loaded in this session (e.g. "0-19") */
  loadedRanges: Set<string>;
}

export interface LoadSnapshotsResult {
  images: SessionReplayImage[];
  loadedRanges: Set<string>;
}

/**
 * Load snapshot images for the current time window (and buffer).
 * Uses manifest to determine blob range, then cache/API. Merges with already-loaded ranges.
 */
export async function loadSnapshotsForTime(
  options: LoadSnapshotsOptions,
): Promise<LoadSnapshotsResult> {
  const { sessionId, sessionStartMs, currentTimeMs, loadedRanges } = options;

  await touchSession(sessionId);

  const manifest = await sessionReplayService.getSnapshotsSource(sessionId);
  const sources = manifest.sources ?? [];
  if (sources.length === 0) {
    return { images: [], loadedRanges: new Set(loadedRanges) };
  }

  const centerIndex = findBlobIndexForTime(
    sources,
    sessionStartMs,
    currentTimeMs,
  );
  if (centerIndex < 0) {
    return { images: [], loadedRanges: new Set(loadedRanges) };
  }

  const { startIndex, endIndex } = getBlobRangeToLoad(sources, centerIndex);
  const startBlobKey = sources[startIndex].blobKey;
  const endBlobKey = sources[endIndex].blobKey;
  const rangeKey = `${startBlobKey}-${endBlobKey}`;

  if (loadedRanges.has(rangeKey)) {
    // Already have this range; build images from all loaded ranges
    return buildImagesFromLoadedRanges(
      sessionId,
      sessionStartMs,
      sources,
      loadedRanges,
    );
  }

  const events = await fetchBlobRange(sessionId, startBlobKey, endBlobKey);
  const newLoaded = new Set(loadedRanges);
  newLoaded.add(rangeKey);

  return buildImagesFromLoadedRanges(
    sessionId,
    sessionStartMs,
    sources,
    newLoaded,
  );
}

/**
 * Build full images array from all blob ranges we've loaded for this session.
 * Re-fetches from cache (no API) for each range in loadedRanges.
 */
async function buildImagesFromLoadedRanges(
  sessionId: string,
  sessionStartMs: number,
  sources: SnapshotsSourceBlob[],
  loadedRanges: Set<string>,
): Promise<LoadSnapshotsResult> {
  const allImages: SessionReplayImage[] = [];

  for (const rangeKey of Array.from(loadedRanges)) {
    const [startBlobKey, endBlobKey] = rangeKey.split("-");
    if (!startBlobKey || !endBlobKey) continue;
    const events = await getCachedBlobRange(
      sessionId,
      startBlobKey,
      endBlobKey,
    );
    if (events && events.length > 0) {
      const imgs = snapshotEventsToImages(events, sessionStartMs, startBlobKey);
      allImages.push(...imgs);
    }
  }

  allImages.sort((a, b) => a.timestamp - b.timestamp);

  return {
    images: allImages,
    loadedRanges: new Set(loadedRanges),
  };
}

/**
 * Initial load: cleanup stale cache, then load first window of snapshots (start of session).
 */
export async function loadInitialSnapshots(
  sessionId: string,
  sessionStartMs: number,
): Promise<LoadSnapshotsResult> {
  await cleanupStaleSessions();
  await touchSession(sessionId);

  const manifest = await sessionReplayService.getSnapshotsSource(sessionId);
  const sources = manifest.sources ?? [];
  if (sources.length === 0) {
    return { images: [], loadedRanges: new Set() };
  }

  const { startIndex, endIndex } = getBlobRangeToLoad(sources, 0);
  const startBlobKey = sources[startIndex].blobKey;
  const endBlobKey = sources[endIndex].blobKey;

  const events = await fetchBlobRange(sessionId, startBlobKey, endBlobKey);
  const loadedRanges = new Set<string>();
  loadedRanges.add(`${startBlobKey}-${endBlobKey}`);

  const images = snapshotEventsToImages(events, sessionStartMs, startBlobKey);
  images.sort((a, b) => a.timestamp - b.timestamp);

  return { images, loadedRanges };
}
