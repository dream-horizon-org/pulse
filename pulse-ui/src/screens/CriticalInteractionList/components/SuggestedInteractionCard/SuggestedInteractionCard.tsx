import { Button, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { SuggestedInteraction } from "../../../../hooks/useGetSuggestedInteractions/useGetSuggestedInteractions.interface";
import classes from "./SuggestedInteractionCard.module.css";

interface SuggestedInteractionCardProps {
  suggestion: SuggestedInteraction;
  onDismiss: (id: number) => void;
  onActivate: (suggestion: SuggestedInteraction) => void;
  isDismissing?: boolean;
  isActivating?: boolean;
}

const formatDuration = (seconds: number): string => {
  if (seconds >= 1) return `${seconds.toFixed(2)}s`;
  return `${(seconds * 1000).toFixed(0)}ms`;
};

const formatCount = (n: number): string => {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toString();
};

const generateDescription = (s: SuggestedInteraction): string => {
  const pattern = s.events.map((e) => e.name).join(" -> ");
  return `Auto-created from suggested interaction. Pattern: ${pattern}. Based on ${s.uniqueSessions} sessions (${s.sessionPct.toFixed(1)}% of traffic).`;
};

const toPascalWord = (name: string): string =>
  name
    .split(/[^a-zA-Z0-9]+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join("");

const toPascalCaseName = (eventNames: string[]): string => {
  if (eventNames.length <= 2) {
    return eventNames.map(toPascalWord).join("To");
  }
  return `${toPascalWord(eventNames[0])}To${toPascalWord(eventNames[eventNames.length - 1])}`;
};

export function SuggestedInteractionCard({
  suggestion,
  onDismiss,
  onActivate,
  isDismissing = false,
  isActivating = false,
}: SuggestedInteractionCardProps) {
  const eventNames = suggestion.events.map((e) => e.name);
  const pascalName = toPascalCaseName(eventNames);

  return (
    <div className={classes.suggestedCard}>
      <div className={classes.cardHeader}>
        <div className={classes.cardInfo}>
          <Text className={classes.patternName} title={pascalName}>
            {pascalName}
          </Text>
          <Text className={classes.descriptionText}>
            {generateDescription(suggestion)}
          </Text>
          <div className={classes.patternFlow}>
            {eventNames.map((event, idx) => (
              <span key={idx}>
                <span className={classes.eventPill}>{event}</span>
                {idx < eventNames.length - 1 && (
                  <span className={classes.arrow}> {"\u2192"} </span>
                )}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Volume</Text>
          <Text className={classes.metricValue}>
            {formatCount(suggestion.totalOccurrences)}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Sessions</Text>
          <Text className={classes.metricValue}>
            {suggestion.sessionPct.toFixed(1)}%
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>P50</Text>
          <Text className={classes.metricValue}>
            {formatDuration(suggestion.medianSpanS)}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>P95</Text>
          <Text className={classes.metricValue}>
            {formatDuration(suggestion.p95SpanS)}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <div className={classes.metricLabelWithInfo}>
            <Text className={classes.metricLabel}>Consistency</Text>
            <Tooltip
              label="How predictable the timing is across sessions. 100% = everyone takes the same time; lower = high variance between users."
              withArrow
              multiline
              styles={{ tooltip: { maxWidth: 240 } }}
            >
              <span className={classes.infoIcon}>
                <IconInfoCircle size={12} stroke={1.5} />
              </span>
            </Tooltip>
          </div>
          <Text className={classes.metricValue}>
            {Math.max(0, (1 - suggestion.cv) * 100).toFixed(0)}%
          </Text>
        </div>
      </div>

      <div className={classes.cardActions}>
        <Button
          className={classes.dismissBtn}
          onClick={() => onDismiss(suggestion.id)}
          disabled={isDismissing}
          loading={isDismissing}
          variant="outline"
          size="sm"
        >
          Dismiss
        </Button>
        <Button
          className={classes.activateBtn}
          onClick={() => onActivate(suggestion)}
          disabled={isActivating}
          loading={isActivating}
          variant="filled"
          size="sm"
        >
          Track this
        </Button>
      </div>
    </div>
  );
}
