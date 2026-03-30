import {
  Box,
  Group,
  Text,
  Slider,
  ActionIcon,
  Button,
  Tooltip,
  Stack,
} from "@mantine/core";
import {
  IconPlayerPlay,
  IconPlayerPause,
  IconPlayerSkipBack,
  IconPlayerSkipForward,
  IconArrowsMaximize,
} from "@tabler/icons-react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { formatPlayerTime } from "../utils/sessionUtils";
import { LABELS } from "../constants/strings";
import classes from "./PlayerControls.module.css";

const SKIP_MS = 10_000;

interface PlayerControlsProps {
  /** Tighter spacing when the player sits in the narrow split column */
  compact?: boolean;
  currentTime: number;
  duration: number;
  isPlaying: boolean;
  playbackSpeed: number;
  criticalInteractions: SessionDetailData["criticalInteractions"];
  onTimelineChange: (value: number) => void;
  onPlayPause: () => void;
  onSpeedChange: (speed: number) => void;
}

export function PlayerControls({
  compact,
  currentTime,
  duration,
  isPlaying,
  playbackSpeed,
  criticalInteractions,
  onTimelineChange,
  onPlayPause,
  onSpeedChange,
}: PlayerControlsProps) {
  const handleSkipBack = () => {
    onTimelineChange(Math.max(0, currentTime - SKIP_MS));
  };

  const handleSkipForward = () => {
    onTimelineChange(Math.min(duration, currentTime + SKIP_MS));
  };
  return (
    <Box
      className={`${classes.playerControls}${compact ? ` ${classes.playerControlsCompact}` : ""}`}
    >
      <Box mb={compact ? 4 : "xs"}>
        <Slider
          value={currentTime}
          onChange={onTimelineChange}
          min={0}
          max={duration}
          size="sm"
          color="teal"
          label={(value) => formatPlayerTime(value)}
          marks={criticalInteractions
            .filter((i) => i.timestamp)
            .map((i) => ({
              value: i.timestamp!,
              label: "",
            }))}
          styles={{
            mark: {
              backgroundColor: "var(--mantine-color-red-5)",
              borderColor: "var(--mantine-color-red-5)",
              width: 6,
              height: 6,
            },
          }}
        />
        <Group justify="space-between" mt={4}>
          <Text size="xs" c="dimmed">
            {formatPlayerTime(currentTime)}
          </Text>
          <Text size="xs" c="dimmed">
            {formatPlayerTime(duration)}
          </Text>
        </Group>
      </Box>

      {/* Row 1: transport · Row 2: speed (no horizontal scroll on narrow player) */}
      <Stack gap={compact ? 6 : "sm"} className={classes.controlsStack}>
        <Group gap="xs" wrap="wrap" className={classes.transportRow}>
          <ActionIcon
            size="lg"
            variant="filled"
            color="teal"
            onClick={onPlayPause}
          >
            {isPlaying ? (
              <IconPlayerPause size={18} />
            ) : (
              <IconPlayerPlay size={18} />
            )}
          </ActionIcon>
          <Tooltip label="Back 10s">
            <ActionIcon
              size="md"
              variant="subtle"
              color="gray"
              onClick={handleSkipBack}
              disabled={currentTime === 0}
            >
              <IconPlayerSkipBack size={16} />
            </ActionIcon>
          </Tooltip>
          <Tooltip label="Forward 10s">
            <ActionIcon
              size="md"
              variant="subtle"
              color="gray"
              onClick={handleSkipForward}
              disabled={currentTime >= duration}
            >
              <IconPlayerSkipForward size={16} />
            </ActionIcon>
          </Tooltip>
          <Tooltip label="Expand player">
            <ActionIcon size="md" variant="subtle" color="gray">
              <IconArrowsMaximize size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>

        <Group gap="xs" align="center" wrap="wrap" className={classes.speedRow}>
          <Text size="xs" c="dimmed" style={{ flexShrink: 0 }}>
            {LABELS.SPEED}:
          </Text>
          <Group gap={4} wrap="wrap">
            {[0.5, 1, 1.5, 2].map((speed) => (
              <Button
                key={speed}
                size="xs"
                variant={playbackSpeed === speed ? "filled" : "subtle"}
                color="gray"
                onClick={() => onSpeedChange(speed)}
              >
                {speed}x
              </Button>
            ))}
          </Group>
        </Group>
      </Stack>
    </Box>
  );
}
