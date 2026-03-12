import { Paper, Stack, Text } from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import { ADVANCED_FILTER_LABELS, MODAL_STYLES } from "./constants";

export function FilterEmptyState() {
  return (
    <Paper
      p="xl"
      withBorder
      style={{ backgroundColor: MODAL_STYLES.emptyStateBg }}
    >
      <Stack gap="sm" align="center">
        <IconFilter size={40} color="var(--mantine-color-gray-4)" />
        <Text size="sm" c="dimmed" ta="center">
          {ADVANCED_FILTER_LABELS.emptyTitle}
        </Text>
        <Text size="xs" c="dimmed" ta="center">
          {ADVANCED_FILTER_LABELS.emptyDescription}
        </Text>
      </Stack>
    </Paper>
  );
}
