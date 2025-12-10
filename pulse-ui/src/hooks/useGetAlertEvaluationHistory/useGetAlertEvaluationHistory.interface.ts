export type GetAlertEvaluationHistoryParams = {
  alertId: string;
};

export type AlertEvaluationHistoryItem = {
  current_state: string;
  evaluated_at: string;
  reading: number;
  threshold: number;
  total_interaction_count?: number;
  metric?: string;
};

export type GetAlertEvaluationHistoryResponse = AlertEvaluationHistoryItem[];


