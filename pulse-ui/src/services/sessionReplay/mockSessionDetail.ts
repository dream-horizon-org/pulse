import { patchSessionDetailDataForEcommerceTheme } from "../../mocks/ecommerceSessionDetailPatch";
import type { AttributeValue } from "../../types/attributes";

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
  eventType?: string;
  description: string;
  durationNs?: number;
  traceId?: string;
  spanId?: string;
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
  interactionQuality: number | null;
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

const MOCK_PAYMENT_GATEWAY_STACK = [
  "com.dream11.payment.PaymentGatewayTimeout: 504 Gateway Timeout",
  "Caused by: retrofit2.HttpException: HTTP 504 ",
  "\tat retrofit2.KotlinExtensions$await$2$2.onResponse(KotlinExtensions.kt:53)",
  "\tat retrofit2.OkHttpCall$1.onResponse(OkHttpCall.java:161)",
  "\tat okhttp3.internal.connection.RealCall$AsyncCall.run(RealCall.kt:519)",
  "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)",
  "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)",
  "\tat java.lang.Thread.run(Thread.java:1119)",
  "Caused by: okhttp3.internal.http2.StreamResetException: stream was reset: NO_ERROR",
  "\tat okhttp3.internal.http2.Http2Stream.takeHeaders(Http2Stream.kt:148)",
  "\tat okhttp3.internal.http2.Http2ExchangeCodec.readResponseHeaders(Http2ExchangeCodec.kt:97)",
  "\tat okhttp3.internal.connection.Exchange.readResponseHeaders(Exchange.kt:110)",
  "\tat okhttp3.internal.http.CallServerInterceptor.intercept(CallServerInterceptor.kt:93)",
  "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)",
  "\tat com.dream11.network.LoggingInterceptor.intercept(LoggingInterceptor.kt:42)",
  "\tat com.dream11.network.AuthHeaderInterceptor.intercept(AuthHeaderInterceptor.kt:28)",
  "--- Request ---",
  "POST https://api.example.com/api/v1/payments/contest-entry HTTP/2",
  "X-Request-Id: req_pay_8f2a9c1d",
  "",
  "--- Response ---",
  "HTTP/2 504  upstream payment service did not respond within 30s.",
].join("\n");

const MOCK_CONTEST_ENTRY_VALIDATION_STACK = [
  "java.lang.IllegalStateException: Contest entry fee not locked for selected XI",
  "\tat com.dream11.contest.ContestEntryRepository.validateEntry(ContestEntryRepository.kt:198)",
  "\tat com.dream11.contest.ContestEntryRepository.reserveEntry$lambda$3(ContestEntryRepository.kt:91)",
  "\tat kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)",
  "\tat android.os.Handler.handleCallback(Handler.java:959)",
  "\tat android.os.Looper.loopOnce(Looper.java:232)",
  "\tat android.app.ActivityThread.main(ActivityThread.java:8705)",
].join("\n");

const MOCK_NETWORK_SLOW_STACK = [
  "com.dream11.network.SlowResponseWarning: GET /api/v1/contests/recommended exceeded p95 (245ms > 200ms)",
  "\tat com.dream11.network.TelemetryInterceptor.intercept(TelemetryInterceptor.kt:71)",
  "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)",
  "Thread: OkHttp https://api.example.com/...",
  "DNS: 12ms, TCP connect: 28ms, TLS: 41ms, TTFB: 164ms",
].join("\n");

export function getMockSessionDetail(sessionId: string): SessionDetailData {
  const now = new Date();
  const sessionStart = new Date(now.getTime() - 154000);

  const data: SessionDetailData = {
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
      country: "India",
      city: "Mumbai",
    },
    interactionQuality: 0.65,
    sessionType: "error_encountered",
    detectedIssues: [
      {
        id: "issue_timeout_1",
        type: "timeout",
        severity: "high",
        timestamp: 77200,
        title: "Payment Timeout",
        description: "Payment API failed to respond within timeout threshold",
        affectedFeature: "Contest entry payment",
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
      conversionGoal: "Contest entry paid",
      conversionValue: 99.99,
      conversionStage: "decision",
      funnelStep: "Step 3 of 4: Pay entry fee",
      userSegment: "Free Trial",
      cohort: "Q1 2026 Signups",
      isFirstSession: false,
      lifetimeValue: 0,
      experiments: [
        {
          id: "exp_pay_entry_v2",
          name: "New contest pay entry flow",
          variant: "Variant B",
        },
      ],
      featuresUsed: [
        "Search",
        "Contest detail",
        "Team selection",
        "Pay entry fee",
        "Wallet",
      ],
      featureEngagement: {
        Search: 20000,
        "Contest detail": 45000,
        "Pay entry fee": 42000,
        Wallet: 30000,
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
            component: "ContestEntryState",
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
        "Open Dream11 → contest detail on iOS",
        "Select contest entry fee payment",
        'Tap "Pay entry fee"',
        "Payment API times out after 30s",
        "Error banner appears",
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
        ContestDetailScreen: 45000,
        PayEntryScreen: 42000,
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
        interactionName: "JoinContestButtonClick",
        displayName: "Join Contest",
        status: "success",
        timestamp: 15000,
        latency: 420,
        apdexScore: 0.85,
        businessValue: "Contest entry",
        revenueImpact: 49,
      },
      {
        interactionId: 2,
        interactionName: "SaveTeamButtonClick",
        displayName: "Save Team",
        status: "success",
        timestamp: 45000,
        latency: 890,
        apdexScore: 0.82,
        businessValue: "Team creation",
      },
      {
        interactionId: 3,
        interactionName: "PaymentSubmitClick",
        displayName: "Payment Submit",
        status: "failed",
        timestamp: 77000,
        latency: 30100,
        apdexScore: 0,
        businessValue: "Contest entry",
        revenueImpact: 99.99,
      },
    ],

    journey: [
      "HomeScreen",
      "ProductDetailScreen",
      "CartScreen",
      "PaymentScreen",
    ],

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
          new Date(sessionStart.getTime() + 45200).toISOString(),
          "error",
          "PaymentGatewayTimeout",
          MOCK_PAYMENT_GATEWAY_STACK,
          "PaymentGatewayTimeout",
          "PayEntryScreen",
          `trace_${sessionId}_10`,
          `span_${sessionId}_10`,
          "group_payment_504",
          "error",
        ],
        [
          new Date(sessionStart.getTime() + 22100).toISOString(),
          "non_fatal_exception",
          "ContestEntryValidationError",
          MOCK_CONTEST_ENTRY_VALIDATION_STACK,
          "IllegalStateException",
          "ContestListScreen",
          `trace_${sessionId}_7`,
          `span_${sessionId}_7`,
          "group_contest_entry_validation",
          "non_fatal",
        ],
        [
          new Date(sessionStart.getTime() + 15300).toISOString(),
          "non_fatal_exception",
          "NetworkSlowWarning",
          MOCK_NETWORK_SLOW_STACK,
          "SlowResponseWarning",
          "HomeScreen",
          `trace_${sessionId}_4`,
          `span_${sessionId}_4`,
          "group_network_slow",
          "non_fatal",
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
        description: "POST /api/v1/payments/contest-entry",
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

  return patchSessionDetailDataForEcommerceTheme(data);
}
