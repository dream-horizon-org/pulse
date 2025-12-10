export type AlertSeverityItem = {
  severity_id: number;
  name: string;
  description: string;
};

export type GetAlertSeveritiesResponse = {
  severity: AlertSeverityItem[];
};

