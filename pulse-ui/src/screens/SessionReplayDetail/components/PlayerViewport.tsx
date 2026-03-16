import { Box, Stack, Text, Badge } from "@mantine/core";
import { IconReload, IconDeviceMobile } from "@tabler/icons-react";
import { SessionReplayPlayer } from "./SessionReplayPlayer";
import type { SessionReplayImage } from "../../../services/sessionReplay/sessionReplayImages";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import { formatPlayerTime } from "../utils/sessionUtils";
import { MESSAGES } from "../constants/strings";
import classes from "./PlayerViewport.module.css";

interface PlayerViewportProps {
  images: SessionReplayImage[];
  imagesLoading: boolean;
  currentTime: number;
  isPlaying: boolean;
  playbackSpeed: number;
  sessionData: SessionDetailData;
  selectedSpan: FlameChartNode | null;
  compact?: boolean;
  onTimeUpdate?: (time: number) => void;
}

export function PlayerViewport({
  images,
  imagesLoading,
  currentTime,
  isPlaying,
  playbackSpeed,
  sessionData,
  selectedSpan,
  compact,
  onTimeUpdate,
}: PlayerViewportProps) {
  const viewportClass = compact
    ? `${classes.playerViewport} ${classes.playerViewportCompact}`
    : classes.playerViewport;
  const placeholderClass = compact
    ? `${classes.playerPlaceholder} ${classes.playerPlaceholderCompact}`
    : classes.playerPlaceholder;

  if (images.length > 0) {
    return (
      <Box className={viewportClass}>
        <SessionReplayPlayer
          images={images}
          currentTime={currentTime}
          isPlaying={isPlaying}
          playbackSpeed={playbackSpeed}
          sessionData={sessionData}
          compact={compact}
          onTimeUpdate={onTimeUpdate}
        />
        {/* Sync Marker Overlay */}
        {selectedSpan && (
          <Box className={classes.syncMarker}>
            <Badge size="sm" color="teal" variant="filled">
              {MESSAGES.SYNCED_TO} {formatPlayerTime(selectedSpan.start)}
            </Badge>
          </Box>
        )}
      </Box>
    );
  }

  if (imagesLoading) {
    return (
      <Box className={viewportClass}>
        <Stack align="center" gap="md">
          <IconReload size={32} className={classes.loadingSpinner} />
          <Text size="sm" c="dimmed">
            {MESSAGES.LOADING_SESSION_REPLAY_IMAGES}
          </Text>
        </Stack>
      </Box>
    );
  }

  return (
    <Box className={placeholderClass}>
      <Stack align="center" gap="md">
        <IconDeviceMobile
          size={64}
          stroke={1.5}
          color="var(--mantine-color-gray-5)"
        />
        <Text size="lg" fw={600} c="dimmed">
          {MESSAGES.SESSION_REPLAY_PLAYER}
        </Text>
        <Text size="sm" c="dimmed" ta="center" maw={400}>
          {MESSAGES.SESSION_RECORDING_DESCRIPTION}
          <br />
          {MESSAGES.SESSION_RECORDING_DETAILS}
        </Text>
        <Badge
          size="lg"
          variant="light"
          color="blue"
          leftSection={<IconReload size={14} />}
        >
          {MESSAGES.INTEGRATION_READY}
        </Badge>
      </Stack>
    </Box>
  );
}
