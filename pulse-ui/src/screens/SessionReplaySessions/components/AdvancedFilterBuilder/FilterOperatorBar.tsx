import { Group, Text, Select, Button } from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { ADVANCED_FILTER_LABELS, OPERATOR_OPTIONS } from "./constants";

export interface FilterOperatorBarProps {
  operator: "AND" | "OR";
  onOperatorChange: (value: "AND" | "OR") => void;
  onAddCondition: () => void;
}

export function FilterOperatorBar({
  operator,
  onOperatorChange,
  onAddCondition,
}: FilterOperatorBarProps) {
  return (
    <Group justify="space-between" wrap="wrap" gap="sm">
      <Group gap="xs" wrap="wrap">
        <Text size="sm" fw={500}>
          {ADVANCED_FILTER_LABELS.matchLabel}
        </Text>
        <Select
          value={operator}
          onChange={(value) => onOperatorChange(value as "AND" | "OR")}
          data={OPERATOR_OPTIONS.map((o) => ({
            value: o.value,
            label: o.label,
          }))}
          styles={{ root: { width: 100 } }}
          size="xs"
        />
        <Text size="sm" fw={500}>
          {ADVANCED_FILTER_LABELS.ofTheFollowing}
        </Text>
      </Group>
      <Button
        size="xs"
        variant="light"
        color="teal"
        leftSection={<IconPlus size={14} />}
        onClick={onAddCondition}
      >
        {ADVANCED_FILTER_LABELS.addCondition}
      </Button>
    </Group>
  );
}
