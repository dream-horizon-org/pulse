/**
 * Analytics & Metrics Mock Responses
 */

export const mockAnalyticsResponses = {
  getApdexScore: {
    data: {
      filteredResults: [],
      apdexResults: [],
      interactionTimeResults: [],
      userCategorizationResults: [],
      errorInteractionResults: [],
      jobComplete: true,
      jobReference: {
        jobId: "mock_job_123",
      },
    },
    status: 200,
  },
  getErrorRate: {
    data: {
      readings: [],
      jobComplete: true,
      jobReference: {
        jobId: "mock_job_123",
      },
    },
    status: 200,
  },
  getInteractionTime: {
    data: {
      readings: [],
      jobComplete: true,
      jobReference: {
        jobId: "mock_job_123",
      },
    },
    status: 200,
  },
  getUserCategorization: {
    data: {
      readings: [],
      jobComplete: true,
      jobReference: {
        jobId: "mock_job_123",
      },
    },
    status: 200,
  },
  getInteractionInsights: {
    data: {
      data: {
        readings: [
          {
            performanceMetric: 12450,
            groupBy: ["v1.8.0"],
          },
          {
            performanceMetric: 8320,
            groupBy: ["v1.1.1"],
          },
        ],
      },
    },
    status: 200,
  },
};
