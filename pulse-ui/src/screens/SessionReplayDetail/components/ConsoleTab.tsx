import { Center, Text, Badge, Stack } from "@mantine/core";
import { IconTerminal } from "@tabler/icons-react";
import { TabPanelScrollArea } from "./TabPanelScrollArea";

export function ConsoleTab() {
  return (
    <TabPanelScrollArea>
      <Center py="xl" mih={200}>
        <Stack align="center" gap="xs">
          <IconTerminal size={32} color="var(--mantine-color-gray-4)" />
          <Badge size="sm" variant="light" color="gray">
            Coming Soon
          </Badge>
          <Text size="sm" c="dimmed" ta="center">
            Console logs will be available here in a future update.
          </Text>
        </Stack>
      </Center>
    </TabPanelScrollArea>
  );
}
