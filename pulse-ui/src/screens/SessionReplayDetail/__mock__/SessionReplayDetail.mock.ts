import type {
  SessionDetailData,
  DetectedIssue,
  TechnicalContext,
} from "../../../services/sessionReplay/mockSessionDetail";

/**
 * Minimal session data for unit tests - with technical context
 */
export const mockSessionDataWithTechnical: SessionDetailData = {
  sessionId: "test-session-123",
  userId: "user-456",
  isAnonymous: false,
  startTime: "2025-03-08T14:00:00.000Z",
  duration: 154000,
  platform: "Web",
  device: "Chrome",
  browser: "Chrome 120",
  os: "macOS",
  appVersion: "1.0.0",
  interactionQuality: 7.5,
  sessionType: "error_encountered",
  detectedIssues: [
    {
      id: "issue-1",
      type: "timeout",
      severity: "high",
      timestamp: 50000,
      title: "Payment timeout",
      description: "API timeout during checkout",
      userFacingImpact: "User couldn't complete payment",
      technicalCause: "POST /api/payment returned 504",
    },
  ],
  criticalInteractions: [],
  journey: ["Home", "Checkout"],
  traces: { fields: [], rows: [] },
  logs: { fields: [], rows: [] },
  exceptions: { fields: [], rows: [] },
  events: [],
  consoleLogs: [],
  networkRequests: [],
  performance: { interactionMetrics: [] },
  technicalContext: {
    rootCause: {
      type: "timeout",
      component: "PaymentService",
      errorChain: [
        {
          timestamp: 50000,
          component: "PaymentService",
          error: "504 Gateway Timeout",
        },
      ],
    },
    codeReferences: [
      {
        file: "src/services/PaymentService.ts",
        line: 45,
        function: "processPayment()",
        githubUrl: "https://github.com/example/repo/blob/main/src/services/PaymentService.ts#L45",
      },
    ],
    relatedPRs: [{ id: "#1234", title: "Fix payment timeout", url: "https://github.com/pr/1234", status: "open" }],
    relatedJiraIssues: [{ key: "PULSE-456", title: "Payment timeout", status: "In Progress" }],
    errorGroupInfo: {
      groupId: "error_group_234",
      firstSeen: "2025-03-08T10:00:00Z",
      lastSeen: "2025-03-08T14:00:00Z",
      occurrenceCount: 23,
      affectedUsers: 18,
      trend: "increasing",
    },
    environmentInfo: {
      appVersion: "2.3.1",
      buildNumber: "build-456",
      featureFlags: { new_payment_flow: true },
    },
    reproducibilityScore: 85,
    reproductionSteps: ["Open checkout", "Click Pay", "Wait 30s"],
  },
};

/**
 * Session data without technical context - for testing empty state
 */
export const mockSessionDataNoTechnical: SessionDetailData = {
  ...mockSessionDataWithTechnical,
  sessionId: "test-session-no-tech",
  technicalContext: undefined,
};

/**
 * Detected issues list for TechnicalTab
 */
export const mockDetectedIssues: DetectedIssue[] = [
  {
    id: "issue-1",
    type: "timeout",
    severity: "high",
    timestamp: 50000,
    title: "Payment timeout",
    description: "API timeout during checkout",
    userFacingImpact: "User couldn't complete payment",
    technicalCause: "POST /api/payment returned 504 Gateway Timeout",
  },
];

/**
 * Empty technical context shape for tests that need partial data
 */
export const mockTechnicalContextMinimal: TechnicalContext = {
  rootCause: {
    type: "timeout",
    component: "PaymentService",
    errorChain: [],
  },
  reproducibilityScore: 0,
  reproductionSteps: [],
  environmentInfo: {
    appVersion: "1.0.0",
    featureFlags: {},
  },
};
