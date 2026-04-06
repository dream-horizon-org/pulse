import { useEffect, useState } from "react";
import type { SessionReplayImage } from "../../../../../services/sessionReplay/sessionReplayImages";

export function useImagePreloading(viewportImages: SessionReplayImage[]) {
  const [loadedImages, setLoadedImages] = useState<Set<number>>(new Set());

  useEffect(() => {
    const preloadImages = async () => {
      const newLoaded = new Set(loadedImages);

      for (const img of viewportImages) {
        if (!newLoaded.has(img.timestamp)) {
          const imageElement = new window.Image();
          imageElement.src = img.imageUrl;
          await new Promise((resolve) => {
            imageElement.onload = () => {
              newLoaded.add(img.timestamp);
              resolve(null);
            };
            imageElement.onerror = () => resolve(null);
          });
        }
      }

      setLoadedImages(newLoaded);
    };

    preloadImages();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewportImages]);

  return loadedImages;
}
