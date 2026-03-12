import { Badge, ActionIcon, Group, Stack, Text } from "@mantine/core";
import { IconX } from "@tabler/icons-react";
import {
  getFieldDefinition,
  OPERATOR_LABELS,
} from "../../../services/sessionReplay/filterConfig";
import type { FilterConfigResponse } from "../../../services/sessionReplay/types";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";

export interface FilterCondition {
  id: string;
  field: string;
  operator: string;
  value?: unknown;
}

export interface ActiveFilterChipsProps {
  operator: string;
  conditions: FilterCondition[];
  filtersConfig: FilterConfigResponse | null;
  onRemove: (conditionId: string) => void;
}

export function ActiveFilterChips({
  operator,
  conditions,
  filtersConfig,
  onRemove,
}: ActiveFilterChipsProps) {
  if (!conditions.length) return null;

  return (
    <Stack gap="xs">
      <Text size="xs" fw={500} c="dimmed">
        {SESSION_LIST_LABELS.advancedFiltersSection} ({operator}):
      </Text>
      <Group gap="xs" style={{ flexWrap: "wrap" }}>
        {conditions.map((condition) => {
          const fieldLabel =
            filtersConfig?.advanced
              ?.flatMap((c) => c.fields)
              .find((f) => f.key === condition.field)?.displayName ??
            getFieldDefinition(condition.field)?.label ??
            condition.field;
          const operatorLabel =
            filtersConfig?.advanced
              ?.flatMap((c) => c.fields)
              .find((f) => f.key === condition.field)
              ?.allowedOperators.find((o) => o.key === condition.operator)
              ?.label ??
            OPERATOR_LABELS[
              condition.operator as keyof typeof OPERATOR_LABELS
            ] ??
            condition.operator;
          const valueDisplay =
            condition.value !== undefined &&
            condition.value !== null &&
            condition.value !== ""
              ? typeof condition.value === "boolean"
                ? condition.value
                  ? "Yes"
                  : "No"
                : String(condition.value)
              : "";

          return (
            <Badge
              key={condition.id}
              variant="light"
              color="indigo"
              size="md"
              rightSection={
                <ActionIcon
                  size="xs"
                  color="indigo"
                  radius="xl"
                  variant="transparent"
                  onClick={() => onRemove(condition.id)}
                >
                  <IconX size={10} />
                </ActionIcon>
              }
            >
              {fieldLabel} {operatorLabel} {valueDisplay}
            </Badge>
          );
        })}
      </Group>
    </Stack>
  );
}
