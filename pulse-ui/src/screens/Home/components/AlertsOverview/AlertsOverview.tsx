import { Text } from "@mantine/core";
import {
  IconBell,
  IconBellRinging,
  IconAlertTriangle,
  IconAlertCircle,
} from "@tabler/icons-react";
import { AlertsOverviewProps } from "./AlertsOverview.interface";
import classes from "./AlertsOverview.module.css";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";

dayjs.extend(relativeTime);

export function AlertsOverview({
  alertSummary,
  recentActiveAlerts,
}: AlertsOverviewProps) {
  const getSeverityColor = (severity: "critical" | "warning") => {
    return severity === "critical"
      ? "var(--mantine-color-red-6)"
      : "var(--mantine-color-orange-6)";
  };

  const getSeverityIcon = (severity: "critical" | "warning") => {
    return severity === "critical" ? IconAlertCircle : IconAlertTriangle;
  };

  return (
    <div className={classes.alertsContainer}>
      {/* Summary Cards */}
      <div className={classes.summaryCards}>
        <div className={classes.summaryCard}>
          <div className={classes.cardHeader}>
            <div className={classes.iconWrapper}>
              <IconBell size={16} color="#dc2626" className={classes.icon} />
            </div>
            <Text className={classes.cardLabel} size="sm">
              Total alerts
            </Text>
          </div>
          <Text className={classes.cardValue} style={{ color: "#dc2626" }}>
            {alertSummary.totalAlerts}
          </Text>
        </div>

        <div className={classes.summaryCard}>
          <div className={classes.cardHeader}>
            <div className={classes.iconWrapper}>
              <IconBellRinging
                size={16}
                color="#ef4444"
                className={classes.icon}
              />
            </div>
            <Text className={classes.cardLabel} size="sm">
              Active alerts
            </Text>
          </div>
          <Text className={classes.cardValue} style={{ color: "#ef4444" }}>
            {alertSummary.activeAlerts}
          </Text>
          <div className={classes.severityBreakdown}>
            <div className={classes.severityItem}>
              <div
                className={classes.severityDot}
                style={{ backgroundColor: "var(--mantine-color-red-6)" }}
              />
              <Text className={classes.severityText} size="xs">
                {alertSummary.criticalAlerts} Critical
              </Text>
            </div>
            <div className={classes.severityItem}>
              <div
                className={classes.severityDot}
                style={{ backgroundColor: "var(--mantine-color-orange-6)" }}
              />
              <Text className={classes.severityText} size="xs">
                {alertSummary.warningAlerts} Warning
              </Text>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Active Alerts */}
      {recentActiveAlerts.length > 0 && (
        <div className={classes.recentAlertsSection}>
          <Text className={classes.sectionTitle} size="sm" fw={700}>
            Recent Active Alerts
          </Text>
          <div className={classes.alertsList}>
            {recentActiveAlerts.map((alert) => {
              const Icon = getSeverityIcon(alert.severity);
              const color = getSeverityColor(alert.severity);

              return (
                <div key={alert.id} className={classes.alertItem}>
                  <div
                    className={classes.alertIconWrapper}
                    style={{ backgroundColor: `${color}15` }}
                  >
                    <Icon size={14} color={color} />
                  </div>
                  <div className={classes.alertContent}>
                    <Text className={classes.alertName} size="xs" fw={600}>
                      {alert.name}
                    </Text>
                    <Text className={classes.alertMeta} size="xs">
                      {alert.metric} • {dayjs(alert.triggeredAt).fromNow()}
                    </Text>
                  </div>
                  <Text
                    component="div"
                    className={classes.severityBadge}
                    size="xs"
                    fw={600}
                    style={{
                      backgroundColor: `${color}15`,
                      color: color,
                    }}
                  >
                    {alert.severity}
                  </Text>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
