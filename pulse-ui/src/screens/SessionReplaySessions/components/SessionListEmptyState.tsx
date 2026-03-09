import { Button, Text } from "@mantine/core";
import { IconVideo } from "@tabler/icons-react";
import { SESSION_LIST_LABELS } from "../constants/sessionList.constants";
import { SessionListHeader } from "./SessionListHeader";
import classes from "../SessionReplaySessions.module.css";

export interface SessionListEmptyStateProps {
  hasActiveFilters: boolean;
  onClearFilters: () => void;
}

export function SessionListEmptyState({
  hasActiveFilters,
  onClearFilters,
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
        <Text className={classes.emptyStateDescription}>{description}</Text>
        {hasActiveFilters && (
          <Button variant="light" color="teal" onClick={onClearFilters}>
            {SESSION_LIST_LABELS.clearAllFilters}
          </Button>
        )}
      </div>
    </div>
  );
}
