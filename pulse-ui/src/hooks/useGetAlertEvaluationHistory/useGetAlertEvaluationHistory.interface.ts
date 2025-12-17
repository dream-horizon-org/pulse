export type GetAlertEvaluationHistoryParams = {
  alertId: string;
};

/**
 * Individual evaluation entry within a scope's history
 */
export type EvaluationHistoryEntry = {
  /** Unique identifier for this evaluation */
  evaluation_id: number;
  /** JSON string containing metric readings (e.g., {"CRASH_FREE_SESSIONS_PERCENTAGE": 0.6666667}) */
  evaluation_result: string;
  /** Current state: FIRING, NORMAL, etc. */
  state: string;
  /** Timestamp when the evaluation occurred (milliseconds) */
  evaluated_at: number;
};

/**
 * Scope-level evaluation history container
 */
export type ScopeEvaluationHistory = {
  /** Unique identifier for this scope */
  scope_id: number;
  /** Name of the scope (e.g., "APP_VITALS") */
  scope_name: string;
  /** Array of evaluation history entries for this scope */
  evaluation_history: EvaluationHistoryEntry[];
};

/**
 * Response type for the alert evaluation history API
 */
export type GetAlertEvaluationHistoryResponse = ScopeEvaluationHistory[];
