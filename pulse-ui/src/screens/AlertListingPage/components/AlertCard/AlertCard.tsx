import { Text, Tooltip, Badge, Group, Divider } from "@mantine/core";
import {
  IconBell,
  IconBellRinging,
  IconBellOff,
  IconClock,
  IconChevronRight,
} from "@tabler/icons-react";
import { AlertCardProps, SeverityDisplayConfig, AlertCondition } from "./AlertCard.interface";
import classes from "./AlertCard.module.css";

const DEFAULT_SEVERITY: SeverityDisplayConfig = { label: "Unknown", color: "#6b7280" };

const METRIC_LABELS: Record<string, string> = {
  APDEX: "APDEX", CRASH_RATE: "Crash Rate", ANR_RATE: "ANR Rate",
  DURATION_P99: "P99", DURATION_P95: "P95", DURATION_P50: "P50",
  ERROR_RATE: "Error Rate", INTERACTION_ERROR_COUNT: "Errors",
  SCREEN_LOAD_TIME_P99: "Load P99", SCREEN_LOAD_TIME_P95: "Load P95",
  SCREEN_LOAD_TIME_P50: "Load P50", NET_5XX_RATE: "5XX Rate", NET_4XX_RATE: "4XX Rate",
};

const OPERATOR_SYMBOLS: Record<string, string> = {
  GREATER_THAN: ">", LESS_THAN: "<", GREATER_THAN_OR_EQUAL: "≥",
  LESS_THAN_OR_EQUAL: "≤", EQUAL: "=",
};

const formatDuration = (seconds: number): string => {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  return `${Math.floor(seconds / 3600)}h`;
};

const formatThresholdValue = (value: number, metric: string): string => {
  if (metric.includes("RATE") || metric === "APDEX") {
    return value < 1 ? `${(value * 100).toFixed(0)}%` : value.toString();
  }
  if (metric.includes("DURATION") || metric.includes("TIME")) {
    return value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${value}ms`;
  }
  return value.toLocaleString();
};

const getAllScopeNames = (conditions: AlertCondition[]): string[] => {
  const allNames = new Set<string>();
  conditions.forEach(c => Object.keys(c.threshold || {}).forEach(n => allNames.add(n)));
  return Array.from(allNames);
};

export function AlertCard({
  name, description, current_state, scope, alerts = [], severity_id,
  is_snoozed, evaluation_period, scopeLabels = {}, severityConfig = {}, onClick,
}: AlertCardProps) {
  const isFiring = current_state === "FIRING";
  const scopeLabel = scopeLabels[scope] || scope;
  const severity = severityConfig[severity_id] || DEFAULT_SEVERITY;

  const getStatusConfig = () => {
    if (is_snoozed) return { label: "Snoozed", color: "#94a3b8", bgColor: "rgba(148, 163, 184, 0.1)", icon: IconBellOff };
    if (isFiring) return { label: "Firing", color: "#ef4444", bgColor: "rgba(239, 68, 68, 0.08)", icon: IconBellRinging };
    return { label: "Normal", color: "#10b981", bgColor: "rgba(16, 185, 129, 0.08)", icon: IconBell };
  };

  const statusConfig = getStatusConfig();
  const AlertIcon = statusConfig.icon;
  const allScopeNames = getAllScopeNames(alerts);
  const hasMultipleConditions = alerts.length > 1;

  return (
    <div
      className={`${classes.alertCard} ${isFiring && !is_snoozed ? classes.firing : ""} ${is_snoozed ? classes.snoozed : ""}`}
      onClick={onClick}
      style={{ "--status-color": statusConfig.color, "--status-bg": statusConfig.bgColor } as React.CSSProperties}
    >
      {/* Snooze Indicator Banner */}
      {is_snoozed && (
        <div className={classes.snoozeBanner}>
          <IconBellOff size={12} />
          <span>Snoozed</span>
        </div>
      )}

      {/* Header: Icon + Scope + Severity + Status */}
      <div className={classes.cardHeader}>
        <div className={classes.headerLeft}>
          <div className={classes.iconWrapper} style={{ color: statusConfig.color }}>
            <AlertIcon size={20} stroke={1.8} />
          </div>
          <Badge size="xs" variant="light" color="gray" className={classes.scopeBadge}>
            {scopeLabel}
          </Badge>
          <Badge size="xs" variant="light" className={classes.severityBadge} style={{ backgroundColor: `${severity.color}18`, color: severity.color }}>
            {severity.label}
          </Badge>
        </div>
        <Badge size="xs" variant="filled" className={classes.statusBadge} style={{ backgroundColor: statusConfig.color }}>
          {statusConfig.label}
        </Badge>
      </div>

      {/* Alert Name */}
      <Text className={classes.alertName} lineClamp={2}>{name}</Text>
      
      {/* Description */}
      {description && (
        <span className={classes.alertDescription}>{description}</span>
      )}

      {/* Scope Names as Chips */}
      {allScopeNames.length > 0 && (
        <Group gap={4} className={classes.scopeNamesRow}>
          {allScopeNames.slice(0, 3).map((scopeName) => (
            <Badge key={scopeName} size="xs" variant="outline" color="teal" className={classes.scopeChip}>
              {scopeName}
            </Badge>
          ))}
          {allScopeNames.length > 3 && (
            <Badge size="xs" variant="light" color="gray" className={classes.scopeChip}>
              +{allScopeNames.length - 3}
            </Badge>
          )}
        </Group>
      )}

      {/* Conditions Display - using spans for full CSS control */}
      {alerts.length > 0 && (
        <div className={classes.conditionBox}>
          <div className={classes.conditionsStack}>
            {alerts.slice(0, hasMultipleConditions ? 2 : 1).map((condition) => (
              <div key={condition.alias} className={classes.conditionRow}>
                {hasMultipleConditions && (
                  <span className={classes.conditionAlias}>{condition.alias}</span>
                )}
                <span className={classes.conditionText}>
                  {METRIC_LABELS[condition.metric] || condition.metric.replace(/_/g, " ")}
                  <span className={classes.operator}> {OPERATOR_SYMBOLS[condition.metric_operator] || ">"} </span>
                  <span className={classes.threshold}>
                    {formatThresholdValue(Object.values(condition.threshold || {})[0] as number, condition.metric)}
                  </span>
                </span>
              </div>
            ))}
            {alerts.length > 2 && (
              <span className={classes.moreConditions}>+{alerts.length - 2} more</span>
            )}
          </div>
        </div>
      )}

      <Divider className={classes.divider} />

      {/* Footer */}
      <div className={classes.cardFooter}>
        <div className={classes.footerLeft}>
          {evaluation_period && (
            <Tooltip label={`Evaluation: ${formatDuration(evaluation_period)}`}>
              <div className={classes.evalInfo}>
                <IconClock size={11} stroke={1.5} />
                <span>{formatDuration(evaluation_period)}</span>
              </div>
            </Tooltip>
          )}
          {hasMultipleConditions && (
            <span className={classes.condCount}>{alerts.length} cond.</span>
          )}
        </div>
        <div className={classes.viewDetails}>
          <span>View</span>
          <IconChevronRight size={11} stroke={1.5} />
        </div>
      </div>
    </div>
  );
}
