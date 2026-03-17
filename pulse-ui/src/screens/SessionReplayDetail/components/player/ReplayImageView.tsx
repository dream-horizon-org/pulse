import { Box } from "@mantine/core";
import type { SessionReplayImage } from "../../../../services/sessionReplay/sessionReplayImages";
import classes from "../SessionReplayPlayer.module.css";

interface ReplayImageViewProps {
  imageToShow: SessionReplayImage;
  previousImage: SessionReplayImage | null;
  transitionOpacity: number;
  loadedImages: Set<number>;
  onImageLoad: (timestamp: number) => void;
}

/**
 * Renders the current replay frame.
 *
 * No CSS transition / crossfade — instant swap gives the cleanest result
 * for discrete session-replay screenshots and avoids flicker on seek.
 * The `<img>` intentionally omits a React `key` so the DOM element is
 * reused; the browser swaps src in-place which avoids an unmount flash.
 */
export function ReplayImageView({
  imageToShow,
  previousImage,
  transitionOpacity,
  loadedImages,
  onImageLoad,
}: ReplayImageViewProps) {
  return (
    <Box className={classes.imageViewport}>
      <img
        src={imageToShow.imageUrl}
        alt={`Frame at ${imageToShow.timestamp}ms`}
        className={classes.currentImage}
        style={{
          opacity: transitionOpacity,
          display: "block",
          visibility: "visible",
        }}
        onLoad={() => onImageLoad(imageToShow.timestamp)}
        onError={(e) => {
          const target = e.target as HTMLImageElement;
          if (target) {
            target.style.border = "2px solid red";
            target.style.backgroundColor = "#fee";
          }
        }}
      />
    </Box>
  );
}
