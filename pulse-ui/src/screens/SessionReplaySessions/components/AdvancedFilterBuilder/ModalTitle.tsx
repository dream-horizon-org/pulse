import { Group, Text, ActionIcon, Tooltip } from "@mantine/core";
import { IconFilter, IconInfoCircle } from "@tabler/icons-react";
import { ADVANCED_FILTER_LABELS } from "./constants";

export function AdvancedFilterModalTitle() {
  return (
    <Group gap="xs">
      <IconFilter size={20} />
      <Text fw={600}>{ADVANCED_FILTER_LABELS.modalTitle}</Text>
      <Tooltip label={ADVANCED_FILTER_LABELS.modalTooltip} position="right">
        <ActionIcon variant="transparent" size="sm" c="dimmed">
          <IconInfoCircle size={16} />
        </ActionIcon>
      </Tooltip>
    </Group>
  );
}
