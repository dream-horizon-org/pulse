import type { AttributeValue } from "../../types/attributes";

export type PersonaType = "all" | "support" | "product" | "tech";

export type SessionType =
  | "conversion_success"
  | "conversion_abandoned"
  | "error_encountered"
  | "performance_issue"
  | "exploration"
  | "support_driven";

export interface DetectedIssue {
  id: string;
  type:
    | "error"
    | "timeout"
    | "slowness"
    | "abandonment"
    | "rage_click"
    | "dead_click";
  severity: "critical" | "high" | "medium" | "low";
  timestamp: number;
  title: string;
  description: string;

  affectedFeature?: string;
  errorGroup?: string;
  relatedSpanId?: string;

  userFacingImpact: string;
  technicalCause: string;
  suggestedAction?: string;
}

export interface SessionIntent {
  primary: string;
  completed: boolean;
  abandonedAt?: string;
  expectedDuration?: number;
  actualDuration: number;
}

export interface CriticalInteraction {
  interactionId: number;
  interactionName: string;
  displayName: string;
  status: "success" | "failed" | "not_attempted";
  timestamp?: number;
  latency?: number;
  apdexScore?: number;
  businessValue?: string;
  revenueImpact?: number;
}

export interface SessionEvent {
  timestamp: number;
  type: "click" | "navigation" | "api_call" | "error";
  description: string;
  details?: Record<string, AttributeValue>;
}

export interface ConsoleLog {
  timestamp: number;
  level: "log" | "warn" | "error";
  message: string;
  stackTrace?: string;
}

export interface NetworkRequest {
  timestamp: number;
  method: string;
  url: string;
  status: number;
  duration: number; // ms
  requestBody?: string;
  responseBody?: string;
  offset?: string;
  target?: string;
  errorCode?: string;
}

export interface BusinessContext {
  isConversionSession: boolean;
  conversionGoal?: string;
  conversionValue?: number;
  conversionStage?: "awareness" | "consideration" | "decision" | "completed";
  funnelStep?: string;
  userSegment?: string;
  cohort?: string;
  isFirstSession: boolean;
  lifetimeValue?: number;
  experiments?: Array<{
    id: string;
    name: string;
    variant: string;
  }>;

  featuresUsed: string[];
  featureEngagement?: Record<string, number>;

  similarSessionsCount?: number;
  similarErrorsToday?: number;
}

export interface SupportContext {
  relatedTicketId?: string;
  supportCategory?: string;
  previousIssues?: Array<{
    sessionId: string;
    issueType: string;
    timestamp: string;
    resolved: boolean;
  }>;

  matchesKnownIssue?: {
    issueId: string;
    title: string;
    affectedUsers: number;
    workaround?: string;
    status: "investigating" | "fix_in_progress" | "resolved";
  };

  suggestedActions: Array<{
    id: string;
    label: string;
    type:
      | "create_ticket"
      | "escalate"
      | "send_workaround"
      | "tag_session"
      | "add_to_known_issues";
    priority: "high" | "medium" | "low";
  }>;
}

export interface TechnicalContext {
  rootCause?: {
    type:
      | "api_failure"
      | "client_error"
      | "timeout"
      | "network"
      | "memory"
      | "rendering"
      | "unknown";
    component: string;
    errorChain: Array<{
      timestamp: number;
      component: string;
      error: string;
      causedBy?: string;
    }>;
  };

  codeReferences?: Array<{
    file: string;
    line: number;
    function: string;
    stackFrame?: string;
    githubUrl?: string;
  }>;

  relatedPRs?: Array<{
    id: string;
    title: string;
    url: string;
    status: "open" | "merged" | "closed";
    mergedAt?: string;
  }>;

  relatedJiraIssues?: Array<{
    key: string;
    title: string;
    status: string;
    url?: string;
  }>;

  errorGroupInfo?: {
    groupId: string;
    firstSeen: string;
    lastSeen: string;
    occurrenceCount: number;
    affectedUsers: number;
    trend: "increasing" | "stable" | "decreasing";
  };

  environmentInfo: {
    appVersion: string;
    buildNumber?: string;
    deployedAt?: string;
    featureFlags: Record<string, boolean>;
  };

  reproducibilityScore: number;
  reproductionSteps?: string[];
}

export interface UXMetrics {
  rageClicks: Array<{
    timestamp: number;
    elementSelector: string;
    clickCount: number;
  }>;

  deadClicks: Array<{
    timestamp: number;
    elementSelector: string;
    expectedAction: string;
  }>;

  errorRecoveryAttempts: number;
  backButtonUsage: number;

  scrollDepth: number;
  viewportTime: Record<string, number>;

  formInteractions?: Array<{
    formId: string;
    fieldsFilled: number;
    totalFields: number;
    abandoned: boolean;
    abandonedAt?: string;
  }>;
}

export interface SessionDetailData {
  sessionId: string;
  userId: string;
  isAnonymous: boolean;
  startTime: string;
  duration: number;
  platform: "iOS" | "Android" | "Web";
  device: string;
  browser?: string;
  os: string;
  appVersion?: string;
  geography?: { country: string; city: string };
  interactionQuality: number;
  sessionType: SessionType;
  detectedIssues: DetectedIssue[];
  sessionIntent?: SessionIntent;
  businessContext?: BusinessContext;
  supportContext?: SupportContext;
  technicalContext?: TechnicalContext;
  uxMetrics?: UXMetrics;
  criticalInteractions: CriticalInteraction[];
  journey: string[];
  traces: {
    fields: string[];
    rows: (string | number | null)[][];
  };
  logs: {
    fields: string[];
    rows: (string | number | null)[][];
  };
  exceptions: {
    fields: string[];
    rows: (string | number | null)[][];
  };

  events: SessionEvent[];
  consoleLogs: ConsoleLog[];
  networkRequests: NetworkRequest[];
  /** Session replay metadata from API */
  internalCallback?: boolean;
  timestampProcessCount?: number;
  intervalGapCount?: number;
  totalEvents?: number;
  sessionReplayTime?: number;
  offset?: string;
  target?: string;
  errorCode?: string;
  associatedApplicationIdentifier?: string;
  performance: {
    coreWebVitals?: {
      lcp: number;
      fid: number;
      cls: number;
    };
    interactionMetrics: Array<{
      interactionId: number;
      interactionName: string;
      duration: number;
      apdexScore: number;
    }>;
  };
}

export function getMockSessionDetail(sessionId: string): SessionDetailData {
  const now = new Date();
  const sessionStart = new Date(now.getTime() - 154000);

  return {
    sessionId,
    userId: "user_3456",
    isAnonymous: false,
    startTime: sessionStart.toISOString(),
    duration: 154000,
    platform: "iOS",
    device: "iPhone 15 Pro",
    os: "iOS 17.2",
    appVersion: "2.3.1",
    geography: {
      country: "United States",
      city: "San Francisco",
    },
    interactionQuality: 6.5,
    sessionType: "error_encountered",
    detectedIssues: [
      {
        id: "issue_timeout_1",
        type: "timeout",
        severity: "high",
        timestamp: 77200,
        title: "Payment Timeout",
        description: "Payment API failed to respond within timeout threshold",
        affectedFeature: "Checkout",
        errorGroup: "error_group_234",
        relatedSpanId: "span_006",
        userFacingImpact:
          "User could not complete $99.99 payment. Transaction was not processed.",

        technicalCause:
          "POST /api/payment returned 504 Gateway Timeout after 1.2 seconds",

        suggestedAction:
          "Check payment gateway status and increase timeout threshold to 3s",
      },
      {
        id: "issue_slowness_1",
        type: "slowness",
        severity: "medium",
        timestamp: 77000,
        title: "Slow Payment Interaction",
        description: "Payment button tap took 1.2 seconds to respond",
        affectedFeature: "Payment",

        userFacingImpact:
          "User experienced noticeable delay when tapping Pay button",
        technicalCause:
          "Payment validation took 1200ms (2.4x above 500ms threshold)",
        suggestedAction:
          "Optimize payment validation logic or add loading state",
      },
    ],
    sessionIntent: {
      primary: "Complete checkout",
      completed: false,
      abandonedAt: "Payment",
      expectedDuration: 120000,
      actualDuration: 154000,
    },
    businessContext: {
      isConversionSession: true,
      conversionGoal: "Complete Purchase",
      conversionValue: 99.99,
      conversionStage: "decision",
      funnelStep: "Step 3 of 4: Payment",
      userSegment: "Free Trial",
      cohort: "Q1 2026 Signups",
      isFirstSession: false,
      lifetimeValue: 0,
      experiments: [
        {
          id: "exp_checkout_v2",
          name: "New Checkout Flow",
          variant: "Variant B",
        },
      ],
      featuresUsed: [
        "Search",
        "Product View",
        "Add to Cart",
        "Checkout",
        "Payment",
      ],
      featureEngagement: {
        Search: 20000,
        "Product View": 45000,
        Checkout: 42000,
        Payment: 30000,
      },
      similarSessionsCount: 156,
      similarErrorsToday: 45,
    },
    supportContext: {
      relatedTicketId: undefined,
      supportCategory: "Payment Issues",

      previousIssues: [
        {
          sessionId: "session_prev_1",
          issueType: "Login timeout",
          timestamp: new Date(now.getTime() - 86400000 * 3).toISOString(),
          resolved: true,
        },
      ],

      matchesKnownIssue: {
        issueId: "known_issue_456",
        title: "iOS Payment Gateway Timeout",
        affectedUsers: 23,
        workaround:
          "Ask user to try PayPal payment method instead of credit card",
        status: "investigating",
      },

      suggestedActions: [
        {
          id: "action_create_ticket",
          label: "Create Support Ticket",
          type: "create_ticket",
          priority: "high",
        },
        {
          id: "action_send_workaround",
          label: "Send Workaround to User",
          type: "send_workaround",
          priority: "high",
        },
        {
          id: "action_escalate",
          label: "Escalate to Engineering",
          type: "escalate",
          priority: "medium",
        },
      ],
    },

    technicalContext: {
      rootCause: {
        type: "timeout",
        component: "PaymentService",
        errorChain: [
          {
            timestamp: 77200,
            component: "PaymentService",
            error: "Payment API timeout",
            causedBy: undefined,
          },
          {
            timestamp: 77500,
            component: "CheckoutState",
            error: "State update failed",
            causedBy: "Payment API timeout",
          },
          {
            timestamp: 77800,
            component: "PaymentScreen",
            error: "UI rendering error",
            causedBy: "State update failed",
          },
        ],
      },

      codeReferences: [
        {
          file: "src/services/PaymentService.ts",
          line: 45,
          function: "processPayment()",
          stackFrame: "at PaymentService.process (payment.ts:45)",
          githubUrl:
            "https://github.com/yourorg/pulse/blob/main/src/services/PaymentService.ts#L45",
        },
        {
          file: "src/screens/PaymentScreen.tsx",
          line: 123,
          function: "handlePayment()",
          stackFrame: "at handlePayment (handlers.ts:23)",
          githubUrl:
            "https://github.com/yourorg/pulse/blob/main/src/screens/PaymentScreen.tsx#L123",
        },
      ],

      relatedPRs: [
        {
          id: "#1234",
          title: "Reduce payment timeout threshold",
          url: "https://github.com/yourorg/pulse/pull/1234",
          status: "merged",
          mergedAt: new Date(now.getTime() - 86400000 * 2).toISOString(), // 2 days ago (suspect!)
        },
      ],

      relatedJiraIssues: [
        {
          key: "PULSE-456",
          title: "Payment timeout on iOS devices",
          status: "In Progress",
          url: "https://yourorg.atlassian.net/browse/PULSE-456",
        },
      ],

      errorGroupInfo: {
        groupId: "error_group_234",
        firstSeen: new Date(now.getTime() - 86400000 * 2).toISOString(), // Started 2 days ago
        lastSeen: now.toISOString(),
        occurrenceCount: 23,
        affectedUsers: 18,
        trend: "increasing",
      },

      environmentInfo: {
        appVersion: "2.3.1",
        buildNumber: "build-456",
        deployedAt: new Date(now.getTime() - 86400000 * 2).toISOString(),
        featureFlags: {
          new_payment_flow: true,
          paypal_integration: true,
          stripe_v3: false,
        },
      },

      reproducibilityScore: 95,
      reproductionSteps: [
        "Navigate to /checkout on iOS device",
        "Select credit card payment method",
        'Tap "Pay Now" button',
        "API times out after 1.2 seconds",
        "Error message appears",
      ],
    },

    uxMetrics: {
      rageClicks: [
        {
          timestamp: 77000,
          elementSelector: "#pay-button",
          clickCount: 3,
        },
      ],

      deadClicks: [
        {
          timestamp: 77000,
          elementSelector: "#pay-button",
          expectedAction: "Process payment",
        },
      ],

      errorRecoveryAttempts: 2,
      backButtonUsage: 1,

      scrollDepth: 85,
      viewportTime: {
        HomeScreen: 20000,
        SearchScreen: 25000,
        ProductScreen: 45000,
        CheckoutScreen: 42000,
        PaymentScreen: 30000,
      },

      formInteractions: [
        {
          formId: "payment-form",
          fieldsFilled: 4,
          totalFields: 4,
          abandoned: false,
        },
      ],
    },

    criticalInteractions: [
      {
        interactionId: 1,
        interactionName: "tap_pay_button",
        displayName: "Payment Button Tap",
        status: "failed",
        timestamp: 77000,
        latency: 1200,
        apdexScore: 0.4,
        businessValue: "Checkout",
        revenueImpact: 99.99,
      },
      {
        interactionId: 2,
        interactionName: "signup_form_submit",
        displayName: "Signup Form Submit",
        status: "success",
        timestamp: 15000,
        latency: 450,
        apdexScore: 0.85,
        businessValue: "Signup",
      },
      {
        interactionId: 3,
        interactionName: "contest_join",
        displayName: "Contest Join",
        status: "not_attempted",
        businessValue: "Engagement",
      },
    ],

    journey: ["/home", "/search", "/contest", "/pay", "/error"],

    traces: {
      fields: [
        "traceId",
        "spanId",
        "parentSpanId",
        "spanName",
        "timestamp",
        "duration",
        "statusCode",
        "spanType",
        "pulseType",
        "serviceName",
      ],
      rows: [
        [
          "trace_001",
          "span_001",
          "",
          "App Start",
          sessionStart.toISOString(),
          50000000,
          "OK",
          "app_start",
          "app.lifecycle.start",
          "mobile-app",
        ],
        [
          "trace_001",
          "span_002",
          "span_001",
          "Database Query",
          new Date(sessionStart.getTime() + 5000).toISOString(),
          23000000,
          "OK",
          "database",
          "db.query",
          "mobile-app",
        ],
        [
          "trace_001",
          "span_003",
          "span_001",
          "PreHomeScreen Load",
          new Date(sessionStart.getTime() + 10000).toISOString(),
          245000000,
          "OK",
          "screen",
          "screen.load",
          "mobile-app",
        ],
        [
          "trace_001",
          "span_004",
          "span_003",
          "API /user/profile",
          new Date(sessionStart.getTime() + 15000).toISOString(),
          120000000,
          "OK",
          "http",
          "api.call",
          "mobile-app",
        ],
        [
          "trace_002",
          "span_005",
          "",
          "Payment Button Tap",
          new Date(sessionStart.getTime() + 77000).toISOString(),
          1200000000,
          "ERROR",
          "interaction",
          "interaction.tap",
          "mobile-app",
        ],
        [
          "trace_002",
          "span_006",
          "span_005",
          "POST /api/payment",
          new Date(sessionStart.getTime() + 77200).toISOString(),
          1100000000,
          "ERROR",
          "http",
          "api.call",
          "mobile-app",
        ],
      ],
    },

    logs: {
      fields: [
        "traceId",
        "spanId",
        "timestamp",
        "severityText",
        "severityNumber",
        "body",
        "eventName",
        "pulseType",
        "serviceName",
        "scopeName",
        "logAttributesJson",
        "resourceAttributesJson",
      ],
      rows: [
        [
          "trace_001",
          "span_001",
          new Date(sessionStart.getTime() + 1000).toISOString(),
          "INFO",
          9,
          "App initialized",
          "app.init",
          "app.lifecycle.init",
          "mobile-app",
          "AppScope",
          "{}",
          "{}",
        ],
        [
          "trace_001",
          "span_003",
          new Date(sessionStart.getTime() + 12000).toISOString(),
          "WARN",
          13,
          "Slow network detected",
          "network.slow",
          "network.performance.slow",
          "mobile-app",
          "NetworkScope",
          "{}",
          "{}",
        ],
        [
          "trace_002",
          "span_005",
          new Date(sessionStart.getTime() + 77500).toISOString(),
          "ERROR",
          17,
          "Payment timeout",
          "payment.error",
          "api.error.timeout",
          "mobile-app",
          "PaymentScope",
          "{}",
          "{}",
        ],
      ],
    },

    exceptions: {
      fields: [
        "timestamp",
        "eventName",
        "title",
        "exceptionMessage",
        "exceptionType",
        "screenName",
        "traceId",
        "spanId",
        "groupId",
        "pulseType",
      ],
      rows: [
        [
          new Date(sessionStart.getTime() + 77800).toISOString(),
          "non_fatal_exception",
          "Payment Timeout",
          "Request timeout after 1.2s",
          "TimeoutException",
          "PaymentScreen",
          "trace_002",
          "span_006",
          "group_001",
          "exception.timeout",
        ],
      ],
    },

    events: [
      {
        timestamp: 5000,
        type: "click",
        description: "Tapped Submit button",
      },
      {
        timestamp: 12000,
        type: "api_call",
        description: "POST /user/signup",
      },
      {
        timestamp: 15000,
        type: "navigation",
        description: "/home → /dashboard",
      },
      {
        timestamp: 77000,
        type: "click",
        description: "Tapped Payment button",
      },
      {
        timestamp: 77200,
        type: "api_call",
        description: "POST /api/payment",
      },
      {
        timestamp: 78400,
        type: "error",
        description: "Payment timeout error",
      },
    ],

    consoleLogs: [
      {
        timestamp: 5000,
        level: "log",
        message: "User clicked submit",
      },
      {
        timestamp: 12000,
        level: "warn",
        message: "Slow network detected",
      },
      {
        timestamp: 78400,
        level: "error",
        message: "TypeError: Cannot process payment",
        stackTrace:
          "at PaymentService.process (payment.ts:45)\nat handlePayment (handlers.ts:23)",
      },
    ],

    networkRequests: [
      {
        timestamp: 12000,
        method: "POST",
        url: "/api/user/signup",
        status: 200,
        duration: 450,
        requestBody: '{"email":"user@example.com","name":"John"}',
        responseBody: '{"success":true,"userId":"user_3456"}',
      },
      {
        timestamp: 77200,
        method: "POST",
        url: "/api/payment",
        status: 504,
        duration: 1200,
        requestBody: '{"amount":99.99,"currency":"USD"}',
        responseBody: '{"error":"Gateway Timeout"}',
      },
      {
        timestamp: 80000,
        method: "GET",
        url: "/api/user/profile",
        status: 200,
        duration: 120,
      },
    ],
    performance: {
      coreWebVitals: undefined,
      interactionMetrics: [
        {
          interactionId: 1,
          interactionName: "tap_pay_button",
          duration: 1200,
          apdexScore: 0.4,
        },
        {
          interactionId: 2,
          interactionName: "signup_form_submit",
          duration: 450,
          apdexScore: 0.85,
        },
      ],
    },
  };
}
