import { useEffect, useRef } from "react";

interface UsePlaybackAnimationProps {
  isPlaying: boolean;
  currentTime: number;
  playbackSpeed: number;
  imagesLength: number;
  onTimeUpdate?: (time: number) => void;
}

/**
 * Drives the playback clock using requestAnimationFrame with a
 * frame-delta approach: each frame computes the wall-clock delta
 * since the previous frame and advances currentTime accordingly.
 *
 * This avoids re-anchoring on every state update, which would lose
 * time during React re-renders and cause playback to drift slower
 * than real time.
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

  const currentTimeRef = useRef(currentTime);
  const playbackSpeedRef = useRef(playbackSpeed);
  const lastFrameRef = useRef<number | null>(null);

  currentTimeRef.current = currentTime;
  playbackSpeedRef.current = playbackSpeed;

  useEffect(() => {
    if (!isPlaying || imagesLength === 0) {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      lastFrameRef.current = null;
      return;
    }

    const animate = (now: number) => {
      if (lastFrameRef.current !== null) {
        const delta = now - lastFrameRef.current;
        const nextTime =
          currentTimeRef.current + delta * playbackSpeedRef.current;
        onTimeUpdateRef.current?.(nextTime);
      }
      lastFrameRef.current = now;
      animationFrameRef.current = requestAnimationFrame(animate);
    };

    animationFrameRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      lastFrameRef.current = null;
    };
  }, [isPlaying, imagesLength]);
}
