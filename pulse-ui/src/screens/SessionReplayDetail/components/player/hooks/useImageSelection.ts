import { useMemo, useState } from "react";
import type { SessionReplayImage } from "../../../../../services/sessionReplay/sessionReplayImages";

export function useImageSelection(
  images: SessionReplayImage[],
  currentTime: number,
) {
  const [previousImage, setPreviousImage] = useState<SessionReplayImage | null>(
    null,
  );

  const currentImageIndex = useMemo(() => {
    if (images.length === 0) return -1;

    // Find the closest previous image (or exact match) to currentTime
    // Since images are per second, we round currentTime to nearest second
    const roundedTime = Math.floor(currentTime / 1000) * 1000;
    let bestIndex = -1;

    for (let i = 0; i < images.length; i++) {
      const diff = images[i].timestamp - roundedTime;

      // Exact match or very close (within 500ms) - use it immediately
      if (Math.abs(diff) <= 500) {
        return i;
      }

      // Previous image (timestamp <= roundedTime) - keep track of it
      if (diff <= 0) {
        bestIndex = i;
      }

      // If we've passed roundedTime, use the previous one we found
      if (diff > 0 && bestIndex >= 0) {
        return bestIndex;
      }
    }

    // If all images are before currentTime, use the last one
    // If no previous image found, use the first one
    return bestIndex >= 0 ? bestIndex : 0;
  }, [images, currentTime]);

  const currentImage = useMemo(() => {
    if (images.length === 0) return null;

    // If no match found or index is invalid, use first image as fallback
    const image =
      currentImageIndex >= 0 ? images[currentImageIndex] : images[0];

    // Update previous image when current image changes
    if (
      image &&
      (!previousImage || image.timestamp !== previousImage.timestamp)
    ) {
      setPreviousImage(image);
    }

    // Debug logging (throttled to avoid console spam)
    if (process.env.NODE_ENV === "development" && Math.random() < 0.01) {
      console.log("Current image:", {
        index: currentImageIndex,
        timestamp: image.timestamp,
        currentTime,
        url: image.imageUrl,
        totalImages: images.length,
      });
    }
    return image;
  }, [images, currentImageIndex, currentTime, previousImage]);

  return { currentImage, previousImage, currentImageIndex };
}
