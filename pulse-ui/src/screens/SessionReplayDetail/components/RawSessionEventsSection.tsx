import { Paper } from "@mantine/core";
import { RawSessionEvents } from "./RawSessionEvents";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import classes from "../SessionReplayDetail.module.css";

interface RawSessionEventsSectionProps {
  sessionData: SessionDetailData;
  scrollToTimestamp: { t0: number; t1: number } | null;
  onEventClick: (item: FlameChartNode) => void;
}

export function RawSessionEventsSection({
  sessionData,
  scrollToTimestamp,
  onEventClick,
}: RawSessionEventsSectionProps) {
  return (
    <Paper
      className={classes.rawEventsContainer}
      mt="md"
      data-raw-events-section
    >
      <RawSessionEvents
        sessionData={sessionData}
        scrollToTimestamp={scrollToTimestamp}
        onEventClick={onEventClick}
      />
    </Paper>
  );
}
