import { Paper, Box } from "@mantine/core";
import { PlayerHeader } from "./PlayerHeader";
import { PlayerViewport } from "./PlayerViewport";
import { PlayerControls } from "./PlayerControls";
import type { SessionReplayImage } from "../../../services/sessionReplay/sessionReplayImages";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import classes from "../SessionReplayDetail.module.css";

interface SessionPlayerSectionProps {
  sessionData: SessionDetailData;
  playerPlatform?: SessionDetailData["platform"];
  images: SessionReplayImage[];
  imagesLoading: boolean;
  currentTime: number;
  isPlaying: boolean;
  playbackSpeed: number;
  selectedSpan: FlameChartNode | null;
  compact?: boolean;
  /** Override for player duration (ms). Falls back to sessionData.duration. */
  duration?: number;
  onTimeUpdate?: (time: number) => void;
  onTimelineChange: (value: number) => void;
  onPlayPause: () => void;
  onSpeedChange: (speed: number) => void;
}

export function SessionPlayerSection({
  sessionData,
  playerPlatform,
  images,
  imagesLoading,
  currentTime,
  isPlaying,
  playbackSpeed,
  selectedSpan,
  compact,
  duration,
  onTimeUpdate,
  onTimelineChange,
  onPlayPause,
  onSpeedChange,
}: SessionPlayerSectionProps) {
  return (
    <Paper
      className={`${classes.playerContainer} ${classes.playerSectionStretch}`}
    >
      <Box className={classes.playerHeader}>
        <PlayerHeader sessionData={sessionData} />
      </Box>
      <Box className={classes.playerViewportGrow}>
        <PlayerViewport
          images={images}
          imagesLoading={imagesLoading}
          currentTime={currentTime}
          isPlaying={isPlaying}
          playbackSpeed={playbackSpeed}
          sessionData={sessionData}
          playerPlatform={playerPlatform}
          selectedSpan={selectedSpan}
          compact={compact}
          onTimeUpdate={onTimeUpdate}
        />
      </Box>
      <Box className={classes.playerControlsSlot}>
        <PlayerControls
          currentTime={currentTime}
          duration={duration ?? sessionData.duration}
          isPlaying={isPlaying}
          playbackSpeed={playbackSpeed}
          criticalInteractions={sessionData.criticalInteractions}
          onTimelineChange={onTimelineChange}
          onPlayPause={onPlayPause}
          onSpeedChange={onSpeedChange}
        />
      </Box>
    </Paper>
  );
}
