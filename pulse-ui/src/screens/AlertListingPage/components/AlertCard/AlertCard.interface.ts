import { AlertState } from "../../AlertListingPage.interface";

export type AlertCardProps = {
  alert_id: number;
  name: string;
  description?: string;
  current_state?: AlertState;
  scope: string;
  alerts: Array<{
    alias: string;
    metric: string;
    metric_operator: string;
    threshold: Record<string, number>;
  }>;
  severity_id: number;
  is_snoozed?: boolean;
  onClick?: () => void;
};


