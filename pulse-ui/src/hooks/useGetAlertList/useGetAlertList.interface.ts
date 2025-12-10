import { AlertState } from "../../screens/AlertListingPage/AlertListingPage.interface";

export type GetAlertListQueryParams = {
  queryParams: {
    offset: number | null;
    limit: number | null;
    created_by: string | null;
    updated_by: string | null;
    scope: string | null;
    name: string | null;
  } | null;
};

export type AlertCondition = {
  alias: string;
  metric: string;
  metric_operator: string;
  threshold: Record<string, number>;
};

export type AlertListItem = {
  alert_id: number;
  name: string;
  description: string;
  scope: string;
  dimension_filter: string | null;
  alerts: AlertCondition[];
  condition_expression: string;
  evaluation_period: number;
  evaluation_interval: number;
  severity_id: number;
  notification_channel_id: number;
  notification_webhook_url: string;
  created_by: string;
  updated_by: string | null;
  created_at: string;
  updated_at: string;
  is_active: boolean;
  last_snoozed_at: string | null;
  snoozed_from: number | null;
  snoozed_until: number | null;
  is_snoozed: boolean;
  current_state?: AlertState;
};

export type GetAlertListResponse = {
  total_alerts: number;
  alerts: AlertListItem[];
  limit: number;
  offset: number;
};


