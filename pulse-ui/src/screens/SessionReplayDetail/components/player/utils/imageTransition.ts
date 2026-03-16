import type { SessionReplayImage } from "../../../../../services/sessionReplay/sessionReplayImages";

export interface ImageTransitionResult {
  imageToShow: SessionReplayImage | null;
  transitionOpacity: number;
}

/**
 * Decide which image to display and at what opacity.
 *
 * We intentionally skip crossfade: session-replay screenshots are discrete
 * frames, so an instant swap looks natural and avoids the "flicker" that
 * a CSS transition causes when the user seeks to a distant timestamp.
 *
 * Falls back to the previous image while the current one is loading.
 */
export function calculateImageTransition(
  currentImage: SessionReplayImage | null,
  previousImage: SessionReplayImage | null,
  loadedImages: Set<number>,
  _currentTime: number,
): ImageTransitionResult {
  if (!currentImage) return { imageToShow: null, transitionOpacity: 1 };

  if (loadedImages.has(currentImage.timestamp)) {
    return { imageToShow: currentImage, transitionOpacity: 1 };
  }

  if (previousImage && loadedImages.has(previousImage.timestamp)) {
    return { imageToShow: previousImage, transitionOpacity: 1 };
  }

  return { imageToShow: currentImage, transitionOpacity: 1 };
}
