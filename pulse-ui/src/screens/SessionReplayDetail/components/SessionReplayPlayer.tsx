import { Box, Stack } from "@mantine/core";
import { useEffect, useRef, useState, useMemo } from "react";
import type { SessionReplayImage } from "../../../services/sessionReplay/sessionReplayImages";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { DeviceOverview } from "./DeviceOverview";
import { useImageSelection } from "./player/hooks/useImageSelection";
import { useImagePreloading } from "./player/hooks/useImagePreloading";
import { usePlaybackAnimation } from "./player/hooks/usePlaybackAnimation";
import { calculateImageTransition } from "./player/utils/imageTransition";
import { ReplayImageView } from "./player/ReplayImageView";
import { MESSAGES } from "../constants/strings";
import classes from "./SessionReplayPlayer.module.css";

interface SessionReplayPlayerProps {
  images: SessionReplayImage[];
  currentTime: number; // Current playback time in ms
  isPlaying: boolean;
  playbackSpeed: number;
  sessionData: SessionDetailData;
  compact?: boolean;
  onTimeUpdate?: (time: number) => void;
}

export function SessionReplayPlayer({
  images,
  currentTime,
  isPlaying,
  playbackSpeed,
  sessionData,
  compact,
  onTimeUpdate,
}: SessionReplayPlayerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [loadedImages, setLoadedImages] = useState<Set<number>>(new Set());

  // Calculate viewport range based on current time
  const viewportSize = 3; // Number of images to render before/after current

  // Image selection logic
  const { currentImage, previousImage, currentImageIndex } = useImageSelection(
    images,
    currentTime,
  );

  // Update viewport to show images around current position
  const [viewportStart, setViewportStart] = useState(0);
  const [viewportEnd, setViewportEnd] = useState(10);

  useEffect(() => {
    if (currentImageIndex < 0) return;

    const start = Math.max(0, currentImageIndex - viewportSize);
    const end = Math.min(images.length, currentImageIndex + viewportSize + 1);

    setViewportStart(start);
    setViewportEnd(end);
  }, [currentImageIndex, images.length, viewportSize]);

  // Get images in viewport
  const viewportImages = useMemo(() => {
    return images.slice(viewportStart, viewportEnd);
  }, [images, viewportStart, viewportEnd]);

  // Preload images in viewport
  const preloadedImages = useImagePreloading(viewportImages);

  // Merge preloaded images with existing loaded images
  useEffect(() => {
    setLoadedImages((prev) => {
      const merged = new Set(prev);
      preloadedImages.forEach((timestamp) => merged.add(timestamp));
      return merged;
    });
  }, [preloadedImages]);

  // Playback animation loop
  usePlaybackAnimation({
    isPlaying,
    currentTime,
    playbackSpeed,
    imagesLength: images.length,
    onTimeUpdate,
  });

  // Calculate image transition
  const { imageToShow, transitionOpacity } = useMemo(
    () =>
      calculateImageTransition(
        currentImage,
        previousImage,
        loadedImages,
        currentTime,
      ),
    [currentImage, previousImage, loadedImages, currentTime],
  );

  const handleImageLoad = (timestamp: number) => {
    setLoadedImages((prev) => new Set(prev).add(timestamp));
  };

  if (!imageToShow || imageToShow === null) {
    return (
      <Box className={classes.playerContainer}>
        <Stack align="center" justify="center" h="100%">
          <Box className={classes.placeholder}>
            {images.length === 0
              ? MESSAGES.LOADING_SESSION_REPLAY_IMAGES
              : MESSAGES.NO_IMAGE_FOUND.replace(
                  "{time}",
                  currentTime.toString(),
                ).replace("{count}", images.length.toString())}
          </Box>
        </Stack>
      </Box>
    );
  }

  const containerClass = compact
    ? `${classes.playerContainer} ${classes.playerContainerCompact}`
    : classes.playerContainer;

  return (
    <Box className={containerClass} ref={containerRef}>
      <DeviceOverview sessionData={sessionData} compact={compact}>
        {imageToShow && (
          <ReplayImageView
            imageToShow={imageToShow}
            previousImage={previousImage}
            transitionOpacity={transitionOpacity}
            loadedImages={loadedImages}
            onImageLoad={handleImageLoad}
          />
        )}
      </DeviceOverview>
    </Box>
  );
}
