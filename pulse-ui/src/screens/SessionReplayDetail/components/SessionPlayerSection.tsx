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
  images: SessionReplayImage[];
  imagesLoading: boolean;
  currentTime: number;
  isPlaying: boolean;
  playbackSpeed: number;
  selectedSpan: FlameChartNode | null;
  compact?: boolean;
  onTimeUpdate?: (time: number) => void;
  onTimelineChange: (value: number) => void;
  onPlayPause: () => void;
  onSpeedChange: (speed: number) => void;
}

export function SessionPlayerSection({
  sessionData,
  images,
  imagesLoading,
  currentTime,
  isPlaying,
  playbackSpeed,
  selectedSpan,
  compact,
  onTimeUpdate,
  onTimelineChange,
  onPlayPause,
  onSpeedChange,
}: SessionPlayerSectionProps) {
  return (
    <Paper className={classes.playerContainer}>
      <Box className={classes.playerHeader}>
        <PlayerHeader sessionData={sessionData} />
      </Box>
      <PlayerViewport
        images={images}
        imagesLoading={imagesLoading}
        currentTime={currentTime}
        isPlaying={isPlaying}
        playbackSpeed={playbackSpeed}
        sessionData={sessionData}
        selectedSpan={selectedSpan}
        compact={compact}
        onTimeUpdate={onTimeUpdate}
      />
      <PlayerControls
        currentTime={currentTime}
        duration={sessionData.duration}
        isPlaying={isPlaying}
        playbackSpeed={playbackSpeed}
        criticalInteractions={sessionData.criticalInteractions}
        onTimelineChange={onTimelineChange}
        onPlayPause={onPlayPause}
        onSpeedChange={onSpeedChange}
      />
    </Paper>
  );
}
