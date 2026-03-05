import { useEffect, useRef } from "react";

interface UsePlaybackAnimationProps {
  isPlaying: boolean;
  currentTime: number;
  playbackSpeed: number;
  imagesLength: number;
  onTimeUpdate?: (time: number) => void;
}

export function usePlaybackAnimation({
  isPlaying,
  currentTime,
  playbackSpeed,
  imagesLength,
  onTimeUpdate,
}: UsePlaybackAnimationProps) {
  const animationFrameRef = useRef<number | null>(null);

  useEffect(() => {
    if (!isPlaying || imagesLength === 0) {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      return;
    }

    const startTime = Date.now();
    const startPlaybackTime = currentTime;

    const animate = () => {
      const now = Date.now();
      const elapsed = now - startTime;
      const nextTime = startPlaybackTime + elapsed * playbackSpeed;

      if (onTimeUpdate) {
        onTimeUpdate(nextTime);
      }

      animationFrameRef.current = requestAnimationFrame(animate);
    };

    animationFrameRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
    };
  }, [isPlaying, currentTime, playbackSpeed, imagesLength, onTimeUpdate]);
}
