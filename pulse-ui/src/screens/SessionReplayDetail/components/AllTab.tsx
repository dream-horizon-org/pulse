import { Stack, Text, Title } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import type { FlameChartNode } from "../../SessionTimeline/utils/flameChartTransform";
import { RawSessionEvents } from "./RawSessionEvents";
import classes from "../SessionReplayDetail.module.css";
import { TAB_PANEL_DESCRIPTION, TAB_PANEL_TITLE } from "../constants/strings";

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
    <Stack gap="sm" style={{ flex: 1, minHeight: 0, width: "100%" }}>
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
          scrollToTimestamp={scrollToTimestamp ?? undefined}
          onEventClick={onEventClick}
        />
      </div>
    </Stack>
  );
}
