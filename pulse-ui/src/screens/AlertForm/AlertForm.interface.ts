export type AlertCondition = {
  alias: string;
  metric: string;
  metric_operator: MetricOperator;
  threshold: Record<string, number>;
};

export type AlertFormData = {
  alert_id?: number;
  name: string;
  description: string;
  scope: string;
  dimension_filters?: string;
  condition_expression: string;
  alerts: AlertCondition[];
  evaluation_period: number;
  evaluation_interval: number;
  severity_id: number;
  notification_channel_id: number;
  created_by?: string;
  updated_by?: string;
};

// Alert scope IDs as returned by the API
export enum AlertScope {
  Interaction = "interaction",
  NetworkAPI = "network_api",
  AppVitals = "app_vitals",
  Screen = "screen",
}

export enum MetricOperator {
  GREATER_THAN = "GREATER_THAN",
  LESS_THAN = "LESS_THAN",
  GREATER_THAN_EQUAL = "GREATER_THAN_EQUAL",
  LESS_THAN_EQUAL = "LESS_THAN_EQUAL",
}

export enum OperatorType {
  GREATER_THAN = "GREATER_THAN",
  LESS_THAN = "LESS_THAN",
  GREATER_THAN_OR_EQUAL_TO = "GREATER_THAN_OR_EQUAL_TO",
  LESS_THAN_OR_EQUAL_TO = "LESS_THAN_OR_EQUAL_TO",
  EQUAL_TO = "EQUAL_TO",
  NOT_EQUAL_TO = "NOT_EQUAL_TO",
}

export const alertDefaultValue: AlertFormData = {
  alert_id: undefined,
  name: "",
  description: "",
  scope: AlertScope.Interaction,
  dimension_filters: undefined,
  condition_expression: "A",
  alerts: [
    {
      alias: "A",
      metric: "",
      metric_operator: MetricOperator.LESS_THAN,
      threshold: {},
    },
  ],
  evaluation_period: 300,
  evaluation_interval: 60,
  severity_id: 1,
  notification_channel_id: 1,
  created_by: "",
  updated_by: "",
};

export const metricOperatorOptions = [
  { value: MetricOperator.GREATER_THAN, label: "Greater Than" },
  { value: MetricOperator.LESS_THAN, label: "Less Than" },
  {
    value: MetricOperator.GREATER_THAN_EQUAL,
    label: "Greater Than or Equal To",
  },
  { value: MetricOperator.LESS_THAN_EQUAL, label: "Less Than or Equal To" },
];

export type AlertFormProps = {
  isInteractionDetailsFlow?: boolean;
  interactionAlertId?: number;
  onBackButtonClick?: () => void;
};

