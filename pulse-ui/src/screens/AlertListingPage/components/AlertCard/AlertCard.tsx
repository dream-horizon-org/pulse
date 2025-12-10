import { Text } from "@mantine/core";
import {
  IconBell,
  IconBellRinging,
  IconBellOff,
  TablerIcon,
} from "@tabler/icons-react";
import { AlertCardProps } from "./AlertCard.interface";
import classes from "./AlertCard.module.css";

// Map scope IDs to display labels
const SCOPE_LABELS: Record<string, string> = {
  interaction: "Interactions",
  network_api: "Network APIs",
  app_vitals: "App Vitals",
  screen: "Screen",
};

const getScopeLabel = (scopeId: string): string => {
  return SCOPE_LABELS[scopeId] || scopeId;
};

export function AlertCard({
  name,
  current_state,
  scope,
  alerts,
  severity_id,
  is_snoozed,
  onClick,
}: AlertCardProps) {
  // Determine alert status and colors
  const isFiring = current_state === "FIRING";

  const getHealthColor = () => {
    if (is_snoozed) return "#94a3b8";
    if (isFiring) return "#ef4444";
    return "#10b981";
  };

  const getMockupGradient = () => {
    if (is_snoozed)
      return "linear-gradient(135deg, rgba(148, 163, 184, 0.08) 0%, rgba(148, 163, 184, 0.15) 100%)";
    if (isFiring)
      return "linear-gradient(135deg, rgba(239, 68, 68, 0.08) 0%, rgba(239, 68, 68, 0.15) 100%)";
    return "linear-gradient(135deg, rgba(14, 201, 194, 0.03) 0%, rgba(14, 201, 194, 0.08) 100%)";
  };

  const getHoverMockupGradient = () => {
    if (is_snoozed)
      return "linear-gradient(135deg, rgba(148, 163, 184, 0.12) 0%, rgba(148, 163, 184, 0.2) 100%)";
    if (isFiring)
      return "linear-gradient(135deg, rgba(239, 68, 68, 0.12) 0%, rgba(239, 68, 68, 0.2) 100%)";
    return "linear-gradient(135deg, rgba(14, 201, 194, 0.05) 0%, rgba(14, 201, 194, 0.12) 100%)";
  };

  const getAlertIcon = (): TablerIcon => {
    if (is_snoozed) return IconBellOff;
    if (isFiring) return IconBellRinging;
    return IconBell;
  };

  const getStateLabel = () => {
    if (is_snoozed) return "Snoozed";
    if (isFiring) return "Firing";
    return "Normal";
  };

  const getIconBackground = () => {
    if (is_snoozed)
      return "linear-gradient(135deg, rgba(148, 163, 184, 0.12), rgba(148, 163, 184, 0.2))";
    if (isFiring)
      return "linear-gradient(135deg, rgba(239, 68, 68, 0.12), rgba(239, 68, 68, 0.2))";
    return "linear-gradient(135deg, rgba(14, 201, 194, 0.12), rgba(14, 201, 194, 0.2))";
  };

  const getIconBorder = () => {
    if (is_snoozed) return "2px solid rgba(148, 163, 184, 0.25)";
    if (isFiring) return "2px solid rgba(239, 68, 68, 0.25)";
    return "2px solid rgba(14, 201, 194, 0.25)";
  };

  const getIconShadow = () => {
    if (is_snoozed) return "0 4px 12px rgba(148, 163, 184, 0.15)";
    if (isFiring) return "0 4px 12px rgba(239, 68, 68, 0.15)";
    return "0 4px 12px rgba(14, 201, 194, 0.15)";
  };

  const healthColor = getHealthColor();
  const mockupGradient = getMockupGradient();
  const hoverMockupGradient = getHoverMockupGradient();
  const iconBackground = getIconBackground();
  const iconBorder = getIconBorder();
  const iconShadow = getIconShadow();
  const AlertIcon = getAlertIcon();

  // Get first alert condition for display
  const firstAlert = alerts?.[0];
  const threshold = firstAlert?.threshold
    ? Object.values(firstAlert.threshold)[0]
    : "N/A";
  const metric = firstAlert?.metric || "N/A";

  return (
    <div
      className={classes.alertCard}
      onClick={onClick}
      style={{
        // @ts-ignore - CSS custom properties
        "--mockup-gradient": mockupGradient,
        "--hover-mockup-gradient": hoverMockupGradient,
        "--health-color": healthColor,
      }}
    >
      {/* Status Badge */}
      <div
        className={classes.statusBadge}
        style={{
          backgroundColor: healthColor,
          color: "#ffffff",
        }}
      >
        {getStateLabel()}
      </div>

      {/* Alert Mockup */}
      <div
        className={classes.alertMockup}
        style={{ background: mockupGradient }}
      >
        <div className={classes.alertHeader}></div>
        <div className={classes.alertContent}>
          <div
            className={classes.alertIcon}
            style={{
              color: healthColor,
              background: iconBackground,
              border: iconBorder,
              boxShadow: iconShadow,
            }}
          >
            <AlertIcon size={32} stroke={1.8} />
          </div>
          <Text className={classes.alertName}>{name}</Text>
        </div>
      </div>
      <div className={classes.healthIndicator} style={{ color: healthColor }} />

      {/* Alert Information Grid */}
      <div className={classes.infoContainer}>
        {/* Row 1: Metrics Grid */}
        <div className={classes.metricsGrid}>
          <div className={classes.metricItem}>
            <Text className={classes.metricLabel}>Metric</Text>
            <Text className={classes.metricValue}>{metric}</Text>
          </div>
          <div className={classes.metricItem}>
            <Text className={classes.metricLabel}>Threshold</Text>
            <Text className={classes.metricValue}>{threshold}</Text>
          </div>
          <div className={classes.metricItem}>
            <Text className={classes.metricLabel}>Severity</Text>
            <Text className={classes.metricValue}>P{severity_id}</Text>
          </div>
          <div className={classes.metricItem}>
            <Text className={classes.metricLabel}>Conditions</Text>
            <Text className={classes.metricValue}>{alerts?.length || 0}</Text>
          </div>
        </div>

        {/* Row 2: Scope */}
        <div className={classes.infoRowHorizontal}>
          <div className={classes.infoItem}>
            <Text className={classes.infoLabel}>Scope</Text>
            <Text className={classes.infoValue} lineClamp={1}>
              {getScopeLabel(scope)}
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
}

