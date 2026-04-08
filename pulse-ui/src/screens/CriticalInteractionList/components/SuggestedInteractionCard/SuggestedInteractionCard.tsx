import { Text } from "@mantine/core";
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

export function SuggestedInteractionCard({
  suggestion,
  onDismiss,
  onActivate,
  isDismissing = false,
  isActivating = false,
}: SuggestedInteractionCardProps) {
  const patternLabel = suggestion.pattern.join(" → ");

  return (
    <div className={classes.suggestedCard}>
      <div className={classes.cardHeader}>
        <div className={classes.cardInfo}>
          <Text className={classes.patternName} title={patternLabel}>
            {patternLabel}
          </Text>
          <div className={classes.patternFlow}>
            {suggestion.pattern.map((event, idx) => (
              <span key={idx}>
                <span className={classes.eventPill}>{event}</span>
                {idx < suggestion.pattern.length - 1 && (
                  <span className={classes.arrow}> → </span>
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
          <Text className={classes.metricLabel}>Consistency</Text>
          <Text className={classes.metricValue}>
            {((1 - suggestion.cv) * 100).toFixed(0)}%
          </Text>
        </div>
      </div>

      <div className={classes.cardActions}>
        <button
          className={classes.dismissBtn}
          onClick={() => onDismiss(suggestion.id)}
          disabled={isDismissing}
        >
          {isDismissing ? "Dismissing..." : "Dismiss"}
        </button>
        <button
          className={classes.activateBtn}
          onClick={() => onActivate(suggestion)}
          disabled={isActivating}
        >
          {isActivating ? "Creating..." : "Track this"}
        </button>
      </div>
    </div>
  );
}
