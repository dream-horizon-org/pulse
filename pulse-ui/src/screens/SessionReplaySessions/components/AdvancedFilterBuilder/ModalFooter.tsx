import { Box, Divider, Group, Button } from "@mantine/core";
import { ADVANCED_FILTER_LABELS } from "./constants";

export interface AdvancedFilterModalFooterProps {
  conditionCount: number;
  onClear: () => void;
  onCancel: () => void;
  onApply: () => void;
}

export function AdvancedFilterModalFooter({
  conditionCount,
  onClear,
  onCancel,
  onApply,
}: AdvancedFilterModalFooterProps) {
  return (
    <Box>
      <Divider mb="md" />
      <Group justify="space-between" wrap="wrap" gap="sm">
        <Button
          variant="subtle"
          color="gray"
          onClick={onClear}
          disabled={conditionCount === 0}
        >
          {ADVANCED_FILTER_LABELS.clearAll}
        </Button>
        <Group gap="sm">
          <Button variant="default" onClick={onCancel}>
            {ADVANCED_FILTER_LABELS.cancel}
          </Button>
          <Button
            color="teal"
            onClick={onApply}
            disabled={conditionCount === 0}
          >
            {ADVANCED_FILTER_LABELS.apply}{" "}
            {conditionCount > 0 ? `(${conditionCount})` : ""}
          </Button>
        </Group>
      </Group>
    </Box>
  );
}
