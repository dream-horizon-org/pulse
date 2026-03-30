import { Box, Button, Group, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import { CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS } from "../../../../constants";
import type { InteractionDiscoverySuggestion } from "../../../../hooks/useGetInteractionDiscoveries";
import { AutoDiscoveredInteractionCard } from "../AutoDiscoveredInteractionCard";
import listClasses from "../../CriticalInteractionList.module.css";
import classes from "./AutoDiscoveredSection.module.css";

export interface AutoDiscoveredSectionProps {
  suggestions: InteractionDiscoverySuggestion[];
  isRefreshing: boolean;
  activatingSuggestionId: string | null;
  onDiscover: () => void;
  onDismiss: (id: string) => void;
  onActivate: (suggestion: InteractionDiscoverySuggestion) => void;
}

export function AutoDiscoveredSection({
  suggestions,
  isRefreshing,
  activatingSuggestionId,
  onDiscover,
  onDismiss,
  onActivate,
}: AutoDiscoveredSectionProps) {
  const c = CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS;

  return (
    <Box className={classes.section}>
      <Group justify="space-between" align="center" wrap="wrap" mb="sm">
        <Group gap="sm" align="center">
          <Text className={listClasses.pageTitle} component="h2" fz="lg">
            {c.AUTO_DISCOVERED_TITLE}
          </Text>
          <span className={listClasses.interactionCount}>
            {suggestions.length}
          </span>
        </Group>
        <Button
          variant="light"
          size="sm"
          className={listClasses.createButton}
          leftSection={<IconRefresh size={16} stroke={1.5} />}
          loading={isRefreshing}
          onClick={onDiscover}
        >
          {c.DISCOVER_BUTTON_LABEL}
        </Button>
      </Group>
      <div className={classes.grid}>
        {suggestions.map((s) => (
          <AutoDiscoveredInteractionCard
            key={s.id}
            suggestion={s}
            onDismiss={() => onDismiss(s.id)}
            onActivate={() => onActivate(s)}
            isActivateLoading={activatingSuggestionId === s.id}
          />
        ))}
      </div>
    </Box>
  );
}
