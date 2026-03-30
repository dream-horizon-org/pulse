import { Stack, Text, Title } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import classes from "../SessionReplayDetail.module.css";
import { TAB_PANEL_DESCRIPTION, TAB_PANEL_TITLE } from "../constants/strings";
import { RawSessionEvents } from "./RawSessionEvents";

interface AllTabProps {
  sessionData: SessionDetailData;
  currentTime?: number;
  isPlaying?: boolean;
  scrollToTimestamp?: { t0: number; t1: number } | null;
  onEventClick?: (item: FlameChartNode) => void;
}

export function AllTab({
  sessionData,
  currentTime = 0,
  isPlaying = false,
  scrollToTimestamp = null,
  onEventClick,
}: AllTabProps) {
  return (
    <Stack gap="sm" w="100%">
      <Stack gap={4} style={{ flexShrink: 0 }}>
        <Title order={5} fz="md" fw={600}>
          {TAB_PANEL_TITLE.ALL}
        </Title>
        <Text size="sm" c="dimmed" lh={1.55} maw={720}>
          {TAB_PANEL_DESCRIPTION.ALL}
        </Text>
      </Stack>
      <div data-raw-events-section className={classes.rawEventsContainer}>
        <RawSessionEvents
          sessionData={sessionData}
          currentTime={currentTime}
          isPlaying={isPlaying}
          scrollToTimestamp={scrollToTimestamp ?? undefined}
          onEventClick={onEventClick}
        />
      </div>
    </Stack>
  );
}
