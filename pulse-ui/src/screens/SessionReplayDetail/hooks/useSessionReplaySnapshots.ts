/**
 * Loads snapshot images for the session replay player using the snapshot APIs:
 * GET /v1/sessions/{sessionId}/snapshots-source and snapshots-data.
 * Caches blob ranges in IndexedDB; loads on initial mount and when user seeks.
 */

import { useState, useEffect, useRef, useCallback } from "react";
import type { SessionReplayImage } from "../../../services/sessionReplay/sessionReplayImages";
import {
  loadInitialSnapshots,
  loadSnapshotsForTime,
  fetchSnapshotManifest,
} from "../../../services/sessionReplay/sessionReplaySnapshotLoader";

const SEEK_DEBOUNCE_MS = 300;

export interface UseSessionReplaySnapshotsParams {
  sessionId: string | undefined;
  /** Session start time (Date or ISO string) */
  sessionStartTime: Date | string;
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
  sessionStartTime,
  currentTime,
  enabled = true,
}: UseSessionReplaySnapshotsParams): UseSessionReplaySnapshotsResult {
  const [images, setImages] = useState<SessionReplayImage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [snapshotDurationMs, setSnapshotDurationMs] = useState(0);
  const loadedRangesRef = useRef<Set<string>>(new Set());
  const sessionStartMs = useRef(0);
  const seekTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastLoadTimeRef = useRef(0);
  const SEEK_THRESHOLD_MS = 2000;

  const effectiveEnabled = Boolean(enabled && sessionId);

  useEffect(() => {
    if (!sessionStartTime) return;
    sessionStartMs.current =
      typeof sessionStartTime === "string"
        ? new Date(sessionStartTime).getTime()
        : sessionStartTime.getTime();
  }, [sessionStartTime]);

  const loadForTime = useCallback(
    async (timeMs: number) => {
      if (!sessionId || !effectiveEnabled) return;
      setLoading(true);
      setError(null);
      try {
        const result = await loadSnapshotsForTime({
          sessionId,
          sessionStartMs: sessionStartMs.current,
          currentTimeMs: timeMs,
          loadedRanges: loadedRangesRef.current,
        });
        loadedRangesRef.current = result.loadedRanges;
        setImages(result.images);
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
        setImages([]);
      } finally {
        setLoading(false);
      }
    },
    [sessionId, effectiveEnabled],
  );

  useEffect(() => {
    if (!sessionId || !effectiveEnabled) return;
    let cancelled = false;
    fetchSnapshotManifest(sessionId).then((result) => {
      if (!cancelled) setSnapshotDurationMs(result.durationMs);
    });
    return () => {
      cancelled = true;
    };
  }, [sessionId, effectiveEnabled]);

  // Initial load
  useEffect(() => {
    if (!sessionId || !effectiveEnabled) {
      setImages([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadInitialSnapshots(sessionId, sessionStartMs.current)
      .then((result) => {
        if (cancelled) return;
        loadedRangesRef.current = result.loadedRanges;
        setImages(result.images);
        lastLoadTimeRef.current = 0;
      })
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
