import { useMemo, useRef } from "react";
import type { SessionReplayImage } from "../../../../../services/sessionReplay/sessionReplayImages";

/**
 * Picks the image whose timestamp is closest-but-not-after `currentTime`.
 * Also tracks the previous image for fallback display.
 */
export function useImageSelection(
  images: SessionReplayImage[],
  currentTime: number,
) {
  const previousImageRef = useRef<SessionReplayImage | null>(null);

  const currentImageIndex = useMemo(() => {
    if (images.length === 0) return -1;

    const roundedTime = Math.floor(currentTime / 1000) * 1000;
    let bestIndex = -1;

    for (let i = 0; i < images.length; i++) {
      const diff = images[i].timestamp - roundedTime;

      if (Math.abs(diff) <= 500) return i;
      if (diff <= 0) bestIndex = i;
      if (diff > 0 && bestIndex >= 0) return bestIndex;
    }

    return bestIndex >= 0 ? bestIndex : 0;
  }, [images, currentTime]);

  const currentImage =
    images.length === 0
      ? null
      : currentImageIndex >= 0
        ? images[currentImageIndex]
        : images[0];

  const previousImage = previousImageRef.current;
  if (
    currentImage &&
    (!previousImage || currentImage.timestamp !== previousImage.timestamp)
  ) {
    previousImageRef.current = currentImage;
  }

  return { currentImage, previousImage, currentImageIndex };
}
