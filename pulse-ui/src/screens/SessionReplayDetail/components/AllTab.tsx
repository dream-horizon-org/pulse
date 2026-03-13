import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import { RawSessionEvents } from "./RawSessionEvents";
import classes from "../SessionReplayDetail.module.css";

interface AllTabProps {
  sessionData: SessionDetailData;
  currentTime?: number;
  scrollToTimestamp?: { t0: number; t1: number } | null;
  onEventClick?: (item: FlameChartNode) => void;
}

export function AllTab({
  sessionData,
  currentTime = 0,
  scrollToTimestamp = null,
  onEventClick,
}: AllTabProps) {
  return (
    <div data-raw-events-section className={classes.rawEventsContainer}>
      <RawSessionEvents
        sessionData={sessionData}
        currentTime={currentTime}
        scrollToTimestamp={scrollToTimestamp ?? undefined}
        onEventClick={onEventClick}
      />
    </div>
  );
}
