import { Stack, Title, Text } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, MESSAGES } from "../constants/strings";
import { TabPanelScrollArea } from "./TabPanelScrollArea";
import { UserJourney } from "./all/UserJourney";
import classes from "../SessionReplayDetail.module.css";

interface UserJourneyTabProps {
  sessionData: SessionDetailData;
}

export function UserJourneyTab({ sessionData }: UserJourneyTabProps) {
  return (
    <TabPanelScrollArea>
      <Stack gap="md">
        <Stack gap={0}>
          <Title
            order={4}
            size="h5"
            className={classes.sessionReplaySectionTitle}
          >
            {HEADERS.SESSION_REPLAY_USER_JOURNEY_TITLE}
          </Title>
          <Text size="sm" c="dimmed" mt={4}>
            {MESSAGES.SESSION_REPLAY_USER_JOURNEY_DESCRIPTION}
          </Text>
        </Stack>
        <UserJourney journey={sessionData.journey} showSectionTitle={false} />
      </Stack>
    </TabPanelScrollArea>
  );
}
