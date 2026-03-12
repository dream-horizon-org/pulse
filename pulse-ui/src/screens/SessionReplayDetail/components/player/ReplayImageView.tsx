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

export function ReplayImageView({
  imageToShow,
  previousImage,
  transitionOpacity,
  loadedImages,
  onImageLoad,
}: ReplayImageViewProps) {
  return (
    <Box className={classes.imageViewport}>
      {/* Render previous image for smooth crossfade transition */}
      {previousImage &&
        previousImage.timestamp !== imageToShow.timestamp &&
        loadedImages.has(previousImage.timestamp) && (
          <img
            key={`prev-${previousImage.timestamp}`}
            src={previousImage.imageUrl}
            alt={`Previous frame at ${previousImage.timestamp}ms`}
            className={classes.transitionImage}
            style={{
              opacity: 1 - transitionOpacity,
              transition: "opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1)",
            }}
          />
        )}

      {/* Render current image with smooth transition */}
      <img
        key={`img-${imageToShow.timestamp}`}
        src={imageToShow.imageUrl}
        alt={`Frame at ${imageToShow.timestamp}ms`}
        className={classes.currentImage}
        style={{
          opacity: loadedImages.has(imageToShow.timestamp)
            ? transitionOpacity
            : 0.3,
          transition: "opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1)",
          display: "block",
          visibility: "visible",
        }}
        onLoad={(e) => {
          onImageLoad(imageToShow.timestamp);
          const target = e.target as HTMLImageElement;
          if (target && process.env.NODE_ENV === "development") {
            console.log("✅ Image loaded:", imageToShow.imageUrl, {
              width: target.naturalWidth,
              height: target.naturalHeight,
            });
          }
        }}
        onError={(e) => {
          const target = e.target as HTMLImageElement;
          console.error("❌ Failed to load image:", imageToShow.imageUrl, {
            error: e,
            target,
          });
          // Show error indicator
          if (target) {
            target.style.border = "2px solid red";
            target.style.backgroundColor = "#fee";
          }
        }}
      />
    </Box>
  );
}
