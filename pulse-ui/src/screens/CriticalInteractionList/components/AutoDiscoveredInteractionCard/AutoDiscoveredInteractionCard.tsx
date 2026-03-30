import { Badge, Button, Group, Text } from "@mantine/core";
import { IconCheck, IconX } from "@tabler/icons-react";
import { CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS } from "../../../../constants";
import type { InteractionDiscoverySuggestion } from "../../../../hooks/useGetInteractionDiscoveries";
import cardClasses from "../InteractionCard/InteractionCard.module.css";
import classes from "./AutoDiscoveredInteractionCard.module.css";

function formatDuration(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  return `${Math.round(ms)}ms`;
}

export interface AutoDiscoveredInteractionCardProps {
  suggestion: InteractionDiscoverySuggestion;
  onDismiss: () => void;
  onActivate: () => void;
  isActivateLoading?: boolean;
}

export function AutoDiscoveredInteractionCard({
  suggestion,
  onDismiss,
  onActivate,
  isActivateLoading = false,
}: AutoDiscoveredInteractionCardProps) {
  const c = CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS;

  return (
    <div className={classes.card}>
      <div className={cardClasses.interactionHeader}>
        <div className={cardClasses.interactionInfo}>
          <Text className={cardClasses.interactionName}>
            {suggestion.displayTitle}
          </Text>
          <Text className={classes.eventPair} ff="monospace" size="xs">
            {suggestion.startEvent} → {suggestion.endEvent}
          </Text>
          <Text className={cardClasses.interactionDescription} mt={4}>
            {suggestion.description}
          </Text>
        </div>
        <Badge variant="light" color="teal" size="sm" tt="uppercase">
          {suggestion.categoryLabel}
        </Badge>
      </div>

      <div className={cardClasses.metricsGrid}>
        <div className={cardClasses.metricCard}>
          <Text className={cardClasses.metricLabel}>
            {c.DISCOVERY_VOLUME_LABEL}
          </Text>
          <Text className={cardClasses.metricValue}>
            {suggestion.volumePerWeek}/wk
          </Text>
        </div>
        <div className={cardClasses.metricCard}>
          <Text className={cardClasses.metricLabel}>
            {c.DISCOVERY_P50_LABEL}
          </Text>
          <Text className={cardClasses.metricValue}>
            {formatDuration(suggestion.p50Ms)}
          </Text>
        </div>
        <div className={cardClasses.metricCard}>
          <Text className={cardClasses.metricLabel}>
            {c.DISCOVERY_P95_LABEL}
          </Text>
          <Text className={cardClasses.metricValue}>
            {formatDuration(suggestion.p95Ms)}
          </Text>
        </div>
        <div className={cardClasses.metricCard}>
          <Text className={cardClasses.metricLabel}>
            {c.DISCOVERY_COMPLETION_LABEL}
          </Text>
          <Text className={cardClasses.metricValue}>
            {suggestion.completionRatePercent}%
          </Text>
        </div>
        <div className={cardClasses.metricCard}>
          <Text className={cardClasses.metricLabel}>
            {c.DISCOVERY_USERS_LABEL}
          </Text>
          <Text className={cardClasses.metricValue}>
            {suggestion.uniqueUsers}
          </Text>
        </div>
        <div className={cardClasses.metricCard}>
          <Text className={cardClasses.metricLabel}>
            {c.DISCOVERY_RELEVANCE_LABEL}
          </Text>
          <Text className={cardClasses.metricValue}>
            {suggestion.relevancePercent}%
          </Text>
        </div>
      </div>

      <Text className={classes.insight} size="sm">
        {suggestion.insight}
      </Text>

      <Group justify="flex-end" gap="sm" mt="md" wrap="nowrap">
        <Button
          variant="light"
          size="sm"
          className={classes.actionButton}
          leftSection={<IconX size={16} stroke={1.5} />}
          onClick={(e) => {
            e.stopPropagation();
            onDismiss();
          }}
        >
          {c.DISMISS_DISCOVERY_LABEL}
        </Button>
        <Button
          variant="filled"
          color="teal"
          size="sm"
          className={classes.actionButton}
          leftSection={<IconCheck size={16} stroke={1.5} />}
          loading={isActivateLoading}
          disabled={isActivateLoading}
          onClick={(e) => {
            e.stopPropagation();
            onActivate();
          }}
        >
          {c.ACTIVATE_DISCOVERY_LABEL}
        </Button>
      </Group>
    </div>
  );
}
