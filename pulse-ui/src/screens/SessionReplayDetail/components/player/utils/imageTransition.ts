import type { SessionReplayImage } from "../../../../../services/sessionReplay/sessionReplayImages";

export interface ImageTransitionResult {
  imageToShow: SessionReplayImage | null;
  transitionOpacity: number;
}

export function calculateImageTransition(
  currentImage: SessionReplayImage | null,
  previousImage: SessionReplayImage | null,
  loadedImages: Set<number>,
  currentTime: number,
): ImageTransitionResult {
  if (!currentImage) return { imageToShow: null, transitionOpacity: 1 };

  const currentLoaded = loadedImages.has(currentImage.timestamp);
  const previousLoaded =
    previousImage && loadedImages.has(previousImage.timestamp);

  // Calculate smooth transition based on time between images
  let opacity = 1;
  if (previousImage && previousImage.timestamp !== currentImage.timestamp) {
    const timeDiff = currentTime - previousImage.timestamp;
    const imageInterval = currentImage.timestamp - previousImage.timestamp;

    // Smooth crossfade: fade in current image as we progress through the second
    if (timeDiff > 0 && imageInterval > 0) {
      opacity = Math.min(1, Math.max(0, timeDiff / imageInterval));
    }
  }

  // If current image is loaded, use it
  if (currentLoaded) {
    return { imageToShow: currentImage, transitionOpacity: opacity };
  }

  // Otherwise, use previous image if available and loaded
  if (previousLoaded) {
    return { imageToShow: previousImage, transitionOpacity: 1 };
  }

  // Fallback to current image (will show loading state)
  return { imageToShow: currentImage, transitionOpacity: opacity };
}
