import { Stack, Title, Text } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import { HEADERS, MESSAGES } from "../constants/strings";
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
    <Stack gap="md" className={classes.allTabWrapper}>
      <div className={classes.sessionTimelineIntro}>
        <Title
          order={4}
          size="h5"
          className={classes.sessionReplaySectionTitle}
        >
          {HEADERS.SESSION_TIMELINE}
        </Title>
        <Text size="sm" c="dimmed" mt={4}>
          {MESSAGES.SESSION_TIMELINE_DESCRIPTION}
        </Text>
      </div>
      <div data-raw-events-section className={classes.rawEventsContainer}>
        <RawSessionEvents
          sessionData={sessionData}
          currentTime={currentTime}
          scrollToTimestamp={scrollToTimestamp ?? undefined}
          onEventClick={onEventClick}
        />
      </div>
    </Stack>
  );
}
