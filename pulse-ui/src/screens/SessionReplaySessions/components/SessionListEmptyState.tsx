import { Button, Group, Text } from "@mantine/core";
import { IconVideo } from "@tabler/icons-react";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import { SessionListHeader } from "./SessionListHeader";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListEmptyStateProps {
  hasActiveFilters: boolean;
  onClearFilters: () => void;
  onRemoveLastFilter: () => void;
}

export function SessionListEmptyState({
  hasActiveFilters,
  onClearFilters,
  onRemoveLastFilter,
}: SessionListEmptyStateProps) {
  const description = hasActiveFilters
    ? SESSION_LIST_LABELS.emptyStateDescriptionWithFilters
    : SESSION_LIST_LABELS.emptyStateDescriptionDefault;

  return (
    <div className={classes.container}>
      <SessionListHeader
        subtitle={SESSION_LIST_LABELS.emptyStateSubtitleFiltered}
      />
      <div className={classes.emptyState}>
        <IconVideo size={64} className={classes.emptyStateIcon} />
        <Text className={classes.emptyStateTitle}>
          {SESSION_LIST_LABELS.emptyStateTitle}
        </Text>
        <Text className={classes.emptyStateDescription} mb="md">
          {description}
        </Text>
        {hasActiveFilters && (
          <Group gap="sm">
            <Button variant="filled" color="teal" onClick={onRemoveLastFilter}>
              {SESSION_LIST_LABELS.removeLastFilter}
            </Button>
            <Button variant="subtle" color="gray" onClick={onClearFilters}>
              {SESSION_LIST_LABELS.clearAllFilters}
            </Button>
          </Group>
        )}
      </div>
    </div>
  );
}
