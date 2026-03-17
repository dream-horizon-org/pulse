/**
 * Loads snapshot images for the session replay player using the snapshot APIs:
 * GET /v1/sessions/{sessionId}/snapshots-source and snapshots-data.
 * Caches blob ranges in IndexedDB; loads on initial mount and when user seeks.
 *
 * sessionStartMs is derived from the manifest's first blob startTimestamp
 * so we never depend on session-detail API timing.
 */

import { useState, useEffect, useRef, useCallback } from "react";
import type { SessionReplayImage } from "../../../services/sessionReplay/sessionReplayImages";
import {
  loadInitialSnapshots,
  loadSnapshotsForTime,
  fetchSnapshotManifest,
} from "../../../services/sessionReplay/sessionReplaySnapshotLoader";

const SEEK_DEBOUNCE_MS = 300;
const SEEK_THRESHOLD_MS = 2000;

export interface UseSessionReplaySnapshotsParams {
  sessionId: string | undefined;
  /** Current playback time in ms from session start */
  currentTime: number;
  enabled?: boolean;
}

export interface UseSessionReplaySnapshotsResult {
  images: SessionReplayImage[];
  loading: boolean;
  error: Error | null;
  snapshotDurationMs: number;
}

export function useSessionReplaySnapshots({
  sessionId,
  currentTime,
  enabled = true,
}: UseSessionReplaySnapshotsParams): UseSessionReplaySnapshotsResult {
  const [images, setImages] = useState<SessionReplayImage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [snapshotDurationMs, setSnapshotDurationMs] = useState(0);

  const loadedRangesRef = useRef<Set<string>>(new Set());
  const sessionStartMsRef = useRef(0);
  const seekTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastLoadTimeRef = useRef(0);

  const effectiveEnabled = Boolean(enabled && sessionId);

  const loadForTime = useCallback(
    async (timeMs: number) => {
      if (!sessionId || !effectiveEnabled) return;
      try {
        const result = await loadSnapshotsForTime({
          sessionId,
          sessionStartMs: sessionStartMsRef.current,
          currentTimeMs: timeMs,
          loadedRanges: loadedRangesRef.current,
        });
        loadedRangesRef.current = result.loadedRanges;
        setImages(result.images);
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
      }
    },
    [sessionId, effectiveEnabled],
  );

  // Fetch manifest once, then load the first window of blobs.
  // sessionStartMs is derived from the manifest (first blob startTimestamp)
  // so we are independent of the session-detail API load timing.
  useEffect(() => {
    if (!sessionId || !effectiveEnabled) {
      setImages([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);

    (async () => {
      const manifest = await fetchSnapshotManifest(sessionId);
      if (cancelled) return;

      setSnapshotDurationMs(manifest.durationMs);
      sessionStartMsRef.current = manifest.sessionStartMs;

      const result = await loadInitialSnapshots(
        sessionId,
        manifest.sessionStartMs,
      );
      if (cancelled) return;

      loadedRangesRef.current = result.loadedRanges;
      setImages(result.images);
      lastLoadTimeRef.current = 0;
    })()
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error(String(err)));
          setImages([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [sessionId, effectiveEnabled]);

  // On seek (large currentTime jump): load blob range for new time after debounce
  useEffect(() => {
    if (!sessionId || !effectiveEnabled) return;
    const isSeek =
      Math.abs(currentTime - lastLoadTimeRef.current) > SEEK_THRESHOLD_MS;
    if (!isSeek) return;

    if (seekTimerRef.current) {
      clearTimeout(seekTimerRef.current);
    }
    seekTimerRef.current = setTimeout(() => {
      seekTimerRef.current = null;
      lastLoadTimeRef.current = currentTime;
      loadForTime(currentTime);
    }, SEEK_DEBOUNCE_MS);

    return () => {
      if (seekTimerRef.current) {
        clearTimeout(seekTimerRef.current);
      }
    };
  }, [sessionId, currentTime, effectiveEnabled, loadForTime]);

  return { images, loading, error, snapshotDurationMs };
}
