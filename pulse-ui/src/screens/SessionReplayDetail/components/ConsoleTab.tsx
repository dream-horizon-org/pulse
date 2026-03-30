import { Box, Text, Stack } from "@mantine/core";
import { TabPanelScrollArea } from "./TabPanelScrollArea";

export function ConsoleTab() {
  return (
    <TabPanelScrollArea>
      <Box py="xl" px="md" mih={200}>
        <Stack align="flex-start" gap="xs">
          <Text size="sm" c="dimmed" ta="left" lh={1.5}>
            Console logs will be available here in a future update.
          </Text>
        </Stack>
      </Box>
    </TabPanelScrollArea>
  );
}
