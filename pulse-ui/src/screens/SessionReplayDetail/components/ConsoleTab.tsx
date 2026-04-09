import { Box, Text, Stack, Title } from "@mantine/core";
import { HEADERS, MESSAGES } from "../constants/strings";
import { TabPanelScrollArea } from "./TabPanelScrollArea";
import classes from "../SessionReplayDetail.module.css";

export function ConsoleTab() {
  return (
    <TabPanelScrollArea>
      <Box py="md" px="md" mih={200}>
        <Stack align="flex-start" gap="md">
          <Stack gap={0}>
            <Title
              order={4}
              size="h5"
              className={classes.sessionReplaySectionTitle}
            >
              {HEADERS.SESSION_REPLAY_CONSOLE_TITLE}
            </Title>
            <Text size="sm" c="dimmed" mt={4}>
              {MESSAGES.SESSION_REPLAY_CONSOLE_DESCRIPTION}
            </Text>
          </Stack>
        </Stack>
      </Box>
    </TabPanelScrollArea>
  );
}
