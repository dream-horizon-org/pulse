import {
  Stack,
  Title,
  Text,
  Loader,
  Center,
  Alert,
  Button,
} from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { HEADERS, MESSAGES } from "../constants/strings";
import { TabPanelScrollArea } from "./TabPanelScrollArea";
import { UserJourney } from "./all/UserJourney";
import classes from "../SessionReplayDetail.module.css";

interface UserJourneyTabProps {
  journey: string[];
  isLoading: boolean;
  isError: boolean;
  onRetry?: () => void;
}

export function UserJourneyTab({
  journey,
  isLoading,
  isError,
  onRetry,
}: UserJourneyTabProps) {
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
        {isLoading ? (
          <Center py="xl">
            <Loader color="teal" size="md" />
          </Center>
        ) : isError ? (
          <Alert
            icon={<IconAlertCircle size={16} />}
            title="Could not load user journey"
            color="red"
            variant="light"
          >
            <Stack gap="sm">
              <Text size="sm">Try again or check your connection.</Text>
              {onRetry ? (
                <Button size="xs" variant="light" color="red" onClick={onRetry}>
                  Retry
                </Button>
              ) : null}
            </Stack>
          </Alert>
        ) : (
          <UserJourney journey={journey} showSectionTitle={false} />
        )}
      </Stack>
    </TabPanelScrollArea>
  );
}
