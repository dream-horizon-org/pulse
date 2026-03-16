import { useEffect, useRef } from "react";

interface UsePlaybackAnimationProps {
  isPlaying: boolean;
  currentTime: number;
  playbackSpeed: number;
  imagesLength: number;
  onTimeUpdate?: (time: number) => void;
}

/**
 * Drives the playback clock using requestAnimationFrame.
 *
 * The animation anchors to `performance.now()` at the moment the effect
 * (re-)starts and advances `currentTime` by `elapsed * playbackSpeed`.
 *
 * `onTimeUpdate` is kept in a ref so changes to the callback identity
 * do not restart the loop.
 */
export function usePlaybackAnimation({
  isPlaying,
  currentTime,
  playbackSpeed,
  imagesLength,
  onTimeUpdate,
}: UsePlaybackAnimationProps) {
  const animationFrameRef = useRef<number | null>(null);
  const onTimeUpdateRef = useRef(onTimeUpdate);
  onTimeUpdateRef.current = onTimeUpdate;

  useEffect(() => {
    if (!isPlaying || imagesLength === 0) {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      return;
    }

    const startWallTime = performance.now();
    const startPlaybackTime = currentTime;

    const animate = () => {
      const elapsed = performance.now() - startWallTime;
      const nextTime = startPlaybackTime + elapsed * playbackSpeed;
      onTimeUpdateRef.current?.(nextTime);
      animationFrameRef.current = requestAnimationFrame(animate);
    };

    animationFrameRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
    };
  }, [isPlaying, currentTime, playbackSpeed, imagesLength]);
}
