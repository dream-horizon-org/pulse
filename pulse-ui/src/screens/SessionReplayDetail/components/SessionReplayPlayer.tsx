import { Box, Stack } from "@mantine/core";
import { useEffect, useRef, useState, useMemo, useCallback } from "react";
import type { SessionReplayImage } from "../../../services/sessionReplay/sessionReplayImages";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { DeviceOverview } from "./DeviceOverview";
import classes from "./SessionReplayPlayer.module.css";

interface SessionReplayPlayerProps {
  images: SessionReplayImage[];
  currentTime: number; // Current playback time in ms
  isPlaying: boolean;
  playbackSpeed: number;
  sessionData: SessionDetailData;
  onTimeUpdate?: (time: number) => void;
}

export function SessionReplayPlayer({
  images,
  currentTime,
  isPlaying,
  playbackSpeed,
  sessionData,
  onTimeUpdate,
}: SessionReplayPlayerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const animationFrameRef = useRef<number | null>(null);
  const playbackStartTimeRef = useRef<number>(0);
  const playbackStartPositionRef = useRef<number>(0);
  const [viewportStart, setViewportStart] = useState(0);
  const [viewportEnd, setViewportEnd] = useState(10);
  const [loadedImages, setLoadedImages] = useState<Set<number>>(new Set());
  const [previousImage, setPreviousImage] = useState<SessionReplayImage | null>(
    null,
  );

  // Calculate viewport range based on current time
  const viewportSize = 3; // Number of images to render before/after current (reduced for per-second images)
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

  // Update viewport to show images around current position
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

  // Find the current image to display
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

  // Preload images in viewport
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
  }, [viewportImages, loadedImages]);

  // Playback animation loop
  useEffect(() => {
    if (!isPlaying || images.length === 0) {
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
  }, [isPlaying, currentTime, playbackSpeed, images.length, onTimeUpdate]);

  // Determine which image to show (current or previous if current not loaded)
  // Also calculate transition opacity for smooth crossfade
  const { imageToShow, transitionOpacity } = useMemo(() => {
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
  }, [currentImage, previousImage, loadedImages, currentTime]);

  if (!imageToShow || imageToShow === null) {
    return (
      <Box className={classes.playerContainer}>
        <Stack align="center" justify="center" h="100%">
          <Box className={classes.placeholder}>
            {images.length === 0
              ? "Loading session replay images..."
              : `No image found for time ${currentTime}ms (${images.length} images available)`}
          </Box>
        </Stack>
      </Box>
    );
  }

  return (
    <Box className={classes.playerContainer} ref={containerRef}>
      <DeviceOverview sessionData={sessionData}>
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
              setLoadedImages((prev) =>
                new Set(prev).add(imageToShow.timestamp),
              );
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
      </DeviceOverview>
    </Box>
  );
}
