/**
 * Mock API service for Session Replay Images
 *
 * PostHog-style API Contract:
 * 1. GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?blob_v2=true - Get manifest
 * 2. GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?source=blob_v2&blob_key=0 - Get JSONL snapshot data
 */

export interface SessionReplayImage {
  timestamp: number; // ms from session start
  imageUrl: string; // Image URL
  blobKey: string; // Identifier for the image chunk
}

/**
 * API A: Snapshots manifest response
 * GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?blob_v2=true
 */
export interface SessionReplayManifest {
  sources: Array<{
    source: string; // "blob_v2" - storage source to pass back in API B
    start_timestamp: string; // ISO 8601 timestamp
    end_timestamp: string; // ISO 8601 timestamp
    blob_key: string; // Chunk identifier to fetch next
  }>;
}

/**
 * Snapshot event structure (PostHog JSONL format)
 * Each line in JSONL is: [windowId, event]
 */
export interface SnapshotEvent {
  type: number; // Event type (2 = full snapshot, 4 = incremental snapshot, etc.)
  timestamp: number; // Unix timestamp in ms
  data: {
    isCheckout?: boolean;
    source?: number;
    adds?: Array<{
      parentId: number;
      nextId: number | null;
      node: {
        id: number;
        type: number;
        tagName?: string;
        attributes?: Record<string, string>;
        childNodes?: any[];
      };
    }>;
    texts?: Array<{
      id: number;
      value: string;
    }>;
    attributes?: Array<{
      id: number;
      attributes: Record<string, string>;
    }>;
    [key: string]: any;
  };
}

/**
 * API B: Snapshots data response (parsed from JSONL)
 * GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?source=blob_v2&blob_key=0
 */
export interface SnapshotDataResponse {
  source: string;
  blob_key: string;
  events: SnapshotEvent[]; // Parsed JSONL events
  start_timestamp: number; // Parsed from ISO string
  end_timestamp: number; // Parsed from ISO string
}

/**
 * API A: Fetch snapshots manifest
 * GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?blob_v2=true
 */
export async function fetchSessionReplayManifest(
  sessionId: string,
): Promise<SessionReplayManifest> {
  // Simulate API delay
  await new Promise((resolve) => setTimeout(resolve, 300));

  const now = new Date();
  const startTime = new Date(now.getTime() - 12000); // 12 seconds ago
  const midTime = new Date(now.getTime() - 6000); // 6 seconds ago

  return {
    sources: [
      {
        source: "blob_v2",
        start_timestamp: startTime.toISOString(),
        end_timestamp: midTime.toISOString(),
        blob_key: "0",
      },
      {
        source: "blob_v2",
        start_timestamp: midTime.toISOString(),
        end_timestamp: now.toISOString(),
        blob_key: "1",
      },
    ],
  };
}

/**
 * API B: Fetch snapshots data (JSONL format)
 * GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?source=blob_v2&blob_key=0&decompress=true
 *
 * Returns JSONL (newline-delimited JSON) where each line is: [windowId, event]
 */
export async function fetchSnapshotData(
  sessionId: string,
  source: string,
  blobKey: string,
  decompress: boolean = true,
): Promise<SnapshotDataResponse> {
  // Simulate API delay
  await new Promise((resolve) => setTimeout(resolve, 500));

  // Parse timestamps from manifest (we'll get these from the source)
  const now = Date.now();
  const startTimestamp = now - 12000;
  const endTimestamp = blobKey === "0" ? now - 6000 : now;

  // Generate mock JSONL events
  const events: SnapshotEvent[] = [];
  const fps = 8; // 8 events per second
  const frameInterval = 1000 / fps; // 125ms between events

  for (
    let timestamp = startTimestamp;
    timestamp <= endTimestamp;
    timestamp += frameInterval
  ) {
    // Generate different event types to simulate a session
    const eventIndex = Math.floor((timestamp - startTimestamp) / frameInterval);
    const isFullSnapshot = eventIndex === 0;

    if (isFullSnapshot) {
      // Full snapshot event (type 2)
      events.push({
        type: 2,
        timestamp,
        data: {
          isCheckout: false,
        },
      });
    } else {
      // Incremental snapshot events (type 4)
      const eventType = (eventIndex % 3) + 1; // Cycle through different incremental types

      switch (eventType) {
        case 1:
          // Add node event
          events.push({
            type: 4,
            timestamp,
            data: {
              source: 0,
              adds: [
                {
                  parentId: 1,
                  nextId: null,
                  node: {
                    id: 2 + eventIndex,
                    type: 2,
                    tagName: "div",
                    attributes: { id: `element-${eventIndex}` },
                    childNodes: [],
                  },
                },
              ],
            },
          });
          break;
        case 2:
          // Text change event
          events.push({
            type: 4,
            timestamp,
            data: {
              source: 1,
              texts: [
                {
                  id: 2,
                  value: `Frame ${eventIndex}`,
                },
              ],
            },
          });
          break;
        case 3:
          // Attribute change event
          events.push({
            type: 4,
            timestamp,
            data: {
              source: 2,
              attributes: [
                {
                  id: 2,
                  attributes: {
                    class: `screen-${eventIndex % 4}`,
                  },
                },
              ],
            },
          });
          break;
      }
    }
  }

  return {
    source,
    blob_key: blobKey,
    events,
    start_timestamp: startTimestamp,
    end_timestamp: endTimestamp,
  };
}

/**
 * Convert snapshot events to images
 * In production, this would use rrweb to replay events and render to canvas
 * For now, we generate mock image URLs based on timestamps
 */
function convertSnapshotEventsToImages(
  snapshotData: SnapshotDataResponse,
  sessionStartTime: Date,
  firstTimestamp: number,
): SessionReplayImage[] {
  const images: SessionReplayImage[] = [];

  // Calculate relative timestamps (relative to first timestamp, starting from 0)
  const snapshotStartMs = snapshotData.start_timestamp;
  const snapshotEndMs = snapshotData.end_timestamp;

  // Make timestamps relative to the first chunk's start time
  const relativeStartMs = snapshotStartMs - firstTimestamp;
  const relativeEndMs = snapshotEndMs - firstTimestamp;

  // Generate images per second (1 image per 1000ms)
  // Round to exact second boundaries (0ms, 1000ms, 2000ms, etc.)
  const frameInterval = 1000; // 1 second per image
  const roundedStart = Math.floor(relativeStartMs / frameInterval) * frameInterval;
  const roundedEnd = Math.ceil(relativeEndMs / frameInterval) * frameInterval;

  for (
    let relativeTimestamp = roundedStart;
    relativeTimestamp <= roundedEnd;
    relativeTimestamp += frameInterval
  ) {
    // Find the closest event to this timestamp
    // Convert relative timestamp back to absolute for event matching
    const absoluteTimestamp =
      snapshotStartMs + (relativeTimestamp - relativeStartMs);
    let closestEvent = snapshotData.events[0];
    let minDiff = Math.abs(
      snapshotData.events[0].timestamp - absoluteTimestamp,
    );

    for (const event of snapshotData.events) {
      const diff = Math.abs(event.timestamp - absoluteTimestamp);
      if (diff < minDiff) {
        minDiff = diff;
        closestEvent = event;
      }
    }

    // Generate a unique image URL based on the event
    // In production, this would be a screenshot from rrweb replay
    const seed = `${snapshotData.blob_key}_${Math.round(relativeTimestamp)}_${closestEvent.type}`;
    const imageUrl = `https://picsum.photos/seed/${seed}/960/540`;

    images.push({
      timestamp: Math.max(0, relativeTimestamp), // Exact second boundaries (0, 1000, 2000, etc.)
      imageUrl,
      blobKey: snapshotData.blob_key,
    });
  }

  return images;
}

/**
 * Main function to fetch and prepare session replay images
 *
 * Follows PostHog API contract:
 * 1. GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?blob_v2=true → Get manifest
 * 2. GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?source=blob_v2&blob_key=0&decompress=true → Get JSONL snapshot data
 * 3. Convert snapshot events to images (using rrweb in production, mock URLs for now)
 */
export async function getSessionReplayImages(
  sessionId: string,
  sessionStartTime: Date,
  frameRate?: number, // Not used, fps determined by snapshot events
): Promise<SessionReplayImage[]> {
  // Step 1: Fetch manifest (API A)
  // GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?blob_v2=true
  const manifest = await fetchSessionReplayManifest(sessionId);

  // Step 2: Fetch snapshot data for each blob (API B)
  // GET /api/environments/{environment_id}/session_recordings/{session_id}/snapshots?source=blob_v2&blob_key=0&decompress=true
  const allImages: SessionReplayImage[] = [];

  // Track the first timestamp to make all timestamps relative to 0
  let firstTimestamp: number | null = null;

  for (const source of manifest.sources) {
    // Fetch snapshot data (JSONL format)
    const snapshotData = await fetchSnapshotData(
      sessionId,
      source.source, // "blob_v2"
      source.blob_key, // "0", "1", etc.
      true, // decompress=true
    );

    // Track the first timestamp across all chunks
    if (firstTimestamp === null) {
      firstTimestamp = snapshotData.start_timestamp;
    }

    // Step 3: Convert snapshot events to images
    // In production: Use rrweb to replay events → render to canvas → screenshot
    // For now: Generate mock image URLs based on events
    const images = convertSnapshotEventsToImages(
      snapshotData,
      sessionStartTime,
      firstTimestamp,
    );

    allImages.push(...images);
  }

  // Sort by timestamp and ensure they start from 0
  const sortedImages = allImages.sort((a, b) => a.timestamp - b.timestamp);

  // Normalize timestamps to start from 0
  if (sortedImages.length > 0) {
    const offset = sortedImages[0].timestamp;
    const normalized = sortedImages.map((img) => ({
      ...img,
      timestamp: Math.max(0, img.timestamp - offset),
    }));

    // Debug logging
    if (process.env.NODE_ENV === "development") {
      console.log("Normalized images:", {
        total: normalized.length,
        first: normalized[0],
        last: normalized[normalized.length - 1],
        sample: normalized.slice(0, 5),
      });
    }

    return normalized;
  }

  return sortedImages;
}
