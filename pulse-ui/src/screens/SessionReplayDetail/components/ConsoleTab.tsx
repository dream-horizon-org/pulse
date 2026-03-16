import { Center, Text, Badge, Stack } from "@mantine/core";
import { IconTerminal } from "@tabler/icons-react";

export function ConsoleTab() {
  return (
    <Center py="xl">
      <Stack align="center" gap="xs">
        <IconTerminal size={32} color="var(--mantine-color-gray-4)" />
        <Badge size="sm" variant="light" color="gray">
          Coming Soon
        </Badge>
        <Text size="sm" c="dimmed">
          Console logs will be available here in a future update.
        </Text>
      </Stack>
    </Center>
  );
}
