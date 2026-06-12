/**
 * Universal Query System Mock Responses
 */

export const mockQueryResponses = {
  getTables: {
    data: [
      "user_events",
      "interaction_metrics",
      "error_logs",
      "performance_data",
    ],
    status: 200,
  },
  getTableColumns: (tableName: string) => ({
    data: ["id", "name", "timestamp", "value"],
    status: 200,
  }),
  executeQuery: {
    data: {
      queryId: "mock_query_123",
      status: "COMPLETED",
      results: [
        { f: [{ v: "sample_data_1" }] },
        { f: [{ v: "sample_data_2" }] },
      ],
    },
    status: 200,
  },
  validateQuery: {
    data: { valid: true },
    status: 200,
  },
  getQueryHistory: {
    data: [],
    status: 200,
  },
  getSuggestedQueries: {
    data: [
      "SELECT * FROM user_events LIMIT 10",
      "SELECT COUNT(*) FROM interaction_metrics",
    ],
    status: 200,
  },
};
