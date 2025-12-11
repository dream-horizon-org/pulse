import { AlertState } from "../../AlertListingPage.interface";

export type SeverityDisplayConfig = {
  label: string;
  color: string;
  description?: string;
};

export type AlertCondition = {
  alias: string;
  metric: string;
  metric_operator: string;
  threshold: Record<string, number>;
};

export type AlertCardProps = {
  alert_id: number;
  name: string;
  description?: string;
  current_state?: AlertState;
  scope: string;
  alerts?: AlertCondition[];
  severity_id: number;
  is_snoozed?: boolean;
  evaluation_period?: number;
  evaluation_interval?: number;
  /** Scope ID to label mapping from API */
  scopeLabels?: Record<string, string>;
  /** Severity ID to display config mapping from API */
  severityConfig?: Record<number, SeverityDisplayConfig>;
  onClick?: () => void;
};
