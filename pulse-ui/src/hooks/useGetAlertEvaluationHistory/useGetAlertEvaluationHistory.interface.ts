export type GetAlertEvaluationHistoryParams = {
  alertId: string;
};

/**
 * Reading per scope name
 * Matches backend: AlertMetricReading.java
 */
export type ScopeReading = {
  /** The actual reading value */
  reading: number;
  /** Scope name (e.g., "PaymentSubmit", "HomeScreen") */
  useCaseId: string;
  /** Success interactions for this scope */
  successInteractionCount: number;
  /** Error interactions for this scope */
  errorInteractionCount: number;
  /** Total interactions for this scope */
  totalInteractionCount: number;
  /** Timestamp of reading */
  timestamp: string;
};

/**
 * Matches backend: AlertEvaluationHistoryResponseDto.java
 * Note: 'reading' is a JSON string containing array of ScopeReading
 */
export type AlertEvaluationHistoryItem = {
  /** JSON string containing array of ScopeReading objects */
  reading: string;
  /** Aggregated success interactions */
  success_interaction_count: number;
  /** Aggregated error interactions */
  error_interaction_count: number;
  /** Aggregated total interactions */
  total_interaction_count: number;
  /** Time taken to evaluate (seconds) */
  evaluation_time: number;
  /** When the evaluation occurred */
  evaluated_at: string;
  /** Current state: FIRING, NORMAL, etc. */
  current_state: string;
  /** Minimum success interactions required */
  min_success_interactions: number;
  /** Minimum error interactions required */
  min_error_interactions: number;
  /** Minimum total interactions required */
  min_total_interactions: number;
  /** The threshold value for comparison */
  threshold: number;
};

export type GetAlertEvaluationHistoryResponse = AlertEvaluationHistoryItem[];
