export interface AlertSummary {
  totalAlerts: number;
  activeAlerts: number;
  criticalAlerts: number;
  warningAlerts: number;
}

export interface ActiveAlert {
  id: string;
  name: string;
  severity: "critical" | "warning";
  triggeredAt: string;
  metric: string;
}

export interface AlertsOverviewProps {
  alertSummary: AlertSummary;
  recentActiveAlerts: ActiveAlert[];
}
