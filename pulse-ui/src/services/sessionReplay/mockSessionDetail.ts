/**
 * Mock data for Session Replay Detail Page
 * Provides sample data for testing the session detail view
 * 
 * PRODUCT PHILOSOPHY:
 * Each field serves a specific persona's need:
 * - Support: "What broke for the user?" → Plain language, actionable
 * - Product: "What's the business impact?" → Metrics, patterns, conversion
 * - Tech: "What's the root cause?" → Code refs, error chains, reproduce
 */

import type { AttributeValue } from "../../types/attributes";

// ============================================================================
// CORE TYPES
// ============================================================================

export type PersonaType = 'all' | 'support' | 'product' | 'tech';

/**
 * Session Type Classification
 * 
 * Support sees: "Error session" or "Abandoned session"
 * Product sees: "Conversion" or "Drop-off"
 * Tech sees: "Error" or "Performance issue"
 */
export type SessionType = 
  | 'conversion_success'      // ✅ User completed their goal
  | 'conversion_abandoned'    // 📉 Started but didn't finish
  | 'error_encountered'       // ❌ Hit errors during session
  | 'performance_issue'       // ⚠️ Slow interactions/APIs
  | 'exploration'             // 👀 Browsing, no clear goal
  | 'support_driven';         // 🎧 User came from support ticket

/**
 * Detected Issue
 * 
 * What went wrong? Auto-detected from session data.
 * - Support: Sees "userFacingImpact" (plain language)
 * - Product: Sees "affectedFeature" + similar count
 * - Tech: Sees "technicalCause" + code reference
 */
export interface DetectedIssue {
  id: string;
  type: 'error' | 'timeout' | 'slowness' | 'abandonment' | 'rage_click' | 'dead_click';
  severity: 'critical' | 'high' | 'medium' | 'low';
  timestamp: number; // ms from session start
  title: string; // Short summary
  description: string; // Detailed explanation
  
  // Context
  affectedFeature?: string; // "Checkout", "Login", "Search"
  errorGroup?: string; // Group ID for similar errors
  relatedSpanId?: string; // Link to flame chart span
  
  // Persona-specific descriptions
  userFacingImpact: string; // SUPPORT: "User couldn't complete payment"
  technicalCause: string;   // TECH: "POST /api/payment returned 504 Gateway Timeout"
  suggestedAction?: string; // "Check payment gateway status"
}

/**
 * Session Intent
 * 
 * PRODUCT: What was the user trying to do?
 * Answers: "Did they succeed?" "Where did they get stuck?"
 */
export interface SessionIntent {
  primary: string;          // "Complete checkout", "Sign up", "Browse products"
  completed: boolean;       // Did they finish?
  abandonedAt?: string;     // Which step: "Payment", "Signup form", etc.
  expectedDuration?: number; // Benchmark: 120000 (2 minutes)
  actualDuration: number;   // How long they actually took
}

export interface CriticalInteraction {
  interactionId: number;
  interactionName: string;
  displayName: string;
  status: "success" | "failed" | "not_attempted";
  timestamp?: number; // ms from session start
  latency?: number; // ms
  apdexScore?: number;
  // NEW: Business context
  businessValue?: string; // PRODUCT: "Checkout", "Signup", "Add to Cart"
  revenueImpact?: number; // PRODUCT: Dollar value if this was conversion step
}

export interface SessionEvent {
  timestamp: number; // ms from session start
  type: "click" | "navigation" | "api_call" | "error";
  description: string;
  details?: Record<string, AttributeValue>;
}

export interface ConsoleLog {
  timestamp: number; // ms from session start
  level: "log" | "warn" | "error";
  message: string;
  stackTrace?: string;
}

export interface NetworkRequest {
  timestamp: number; // ms from session start
  method: string;
  url: string;
  status: number;
  duration: number; // ms
  requestBody?: string;
  responseBody?: string;
}

/**
 * Business Context
 * 
 * PRODUCT MANAGER: Understand business impact and patterns
 * - Is this a conversion session?
 * - What's the revenue impact?
 * - Are there similar sessions?
 * - Which A/B test variant?
 */
export interface BusinessContext {
  // Conversion tracking
  isConversionSession: boolean;
  conversionGoal?: string; // "Complete Purchase", "Sign Up", "Activate Feature"
  conversionValue?: number; // Revenue/ARR value
  conversionStage?: 'awareness' | 'consideration' | 'decision' | 'completed';
  funnelStep?: string; // "Step 3 of 4: Payment"
  
  // User segmentation
  userSegment?: string;     // "Premium", "Free Trial", "Enterprise"
  cohort?: string;          // "Q4 2025 Signups"
  isFirstSession: boolean;  // First-time user behavior different
  lifetimeValue?: number;   // $1,234 LTV
  
  // A/B Testing
  experiments?: Array<{
    id: string;
    name: string;
    variant: string;        // "Control", "Variant A", "Variant B"
  }>;
  
  // Feature usage
  featuresUsed: string[];   // ["Search", "Filter", "Checkout", "Payment"]
  featureEngagement?: Record<string, number>; // { "Search": 30000ms, "Checkout": 45000ms }
  
  // Benchmarks & comparison
  similarSessionsCount?: number; // 156 similar sessions today
  similarErrorsToday?: number;   // 23 others hit same error
}

/**
 * Support Context
 * 
 * CUSTOMER SUPPORT: Help user fast with context
 * - What broke?
 * - How many others affected?
 * - Is there a workaround?
 * - Link to existing ticket/issue?
 */
export interface SupportContext {
  // Ticketing
  relatedTicketId?: string; // Link to Zendesk/Intercom ticket
  supportCategory?: string; // "Payment Issues", "Login Problems"
  
  // User history
  previousIssues?: Array<{
    sessionId: string;
    issueType: string;
    timestamp: string;
    resolved: boolean;
  }>;
  
  // Known issues database
  matchesKnownIssue?: {
    issueId: string;
    title: string;          // "iOS Payment Timeout"
    affectedUsers: number;  // 23 users today
    workaround?: string;    // "Use PayPal instead of credit card"
    status: 'investigating' | 'fix_in_progress' | 'resolved';
  };
  
  // Quick actions for support team
  suggestedActions: Array<{
    id: string;
    label: string;
    type: 'create_ticket' | 'escalate' | 'send_workaround' | 'tag_session' | 'add_to_known_issues';
    priority: 'high' | 'medium' | 'low';
  }>;
}

/**
 * Technical Context
 * 
 * ENGINEERING: Find root cause and ship fix
 * - What's the error chain?
 * - Where in code?
 * - Which PR might have caused this?
 * - How to reproduce?
 */
export interface TechnicalContext {
  // Root cause analysis
  rootCause?: {
    type: 'api_failure' | 'client_error' | 'timeout' | 'network' | 'memory' | 'rendering' | 'unknown';
    component: string;        // "PaymentService", "CheckoutScreen"
    errorChain: Array<{       // Error propagation
      timestamp: number;
      component: string;
      error: string;
      causedBy?: string;
    }>;
  };
  
  // Code references
  codeReferences?: Array<{
    file: string;             // "src/services/PaymentService.ts"
    line: number;             // 45
    function: string;         // "processPayment()"
    stackFrame?: string;      // Full stack frame
    githubUrl?: string;       // Direct link to GitHub
  }>;
  
  // Related issues/PRs
  relatedPRs?: Array<{
    id: string;               // "#1234"
    title: string;
    url: string;
    status: 'open' | 'merged' | 'closed';
    mergedAt?: string;        // Might be the culprit if recent
  }>;
  
  relatedJiraIssues?: Array<{
    key: string;              // "PULSE-456"
    title: string;
    status: string;           // "In Progress", "Done"
    url?: string;
  }>;
  
  // Error grouping (Sentry-style)
  errorGroupInfo?: {
    groupId: string;          // "error_group_234"
    firstSeen: string;        // When did this start?
    lastSeen: string;
    occurrenceCount: number;  // 23 times today
    affectedUsers: number;    // 18 unique users
    trend: 'increasing' | 'stable' | 'decreasing';
  };
  
  // Environment & config
  environmentInfo: {
    appVersion: string;       // "2.3.1"
    buildNumber?: string;     // "build-456"
    deployedAt?: string;      // When was this version deployed?
    featureFlags: Record<string, boolean>; // { "new_payment_flow": true }
  };
  
  // Reproducibility
  reproducibilityScore: number; // 0-100 (95 = very reproducible)
  reproductionSteps?: string[]; // Step-by-step instructions
}

/**
 * UX Metrics
 * 
 * PRODUCT + DESIGN: Understand user frustration and behavior
 * - Rage clicks = user frustrated
 * - Dead clicks = UI unresponsive
 * - Form abandonment = friction point
 */
export interface UXMetrics {
  // Frustration signals
  rageClicks: Array<{
    timestamp: number;
    elementSelector: string;  // "#pay-button"
    clickCount: number;       // Clicked 5 times in 1 second
  }>;
  
  deadClicks: Array<{
    timestamp: number;
    elementSelector: string;
    expectedAction: string;   // "Navigate to payment" (but didn't)
  }>;
  
  errorRecoveryAttempts: number; // User tried to fix error X times
  backButtonUsage: number;       // Lots of back = confusion
  
  // Engagement metrics
  scrollDepth: number;           // 0-100% (did they see the CTA?)
  viewportTime: Record<string, number>; // { "HomeScreen": 30000ms, "PaymentScreen": 45000ms }
  
  // Form interactions
  formInteractions?: Array<{
    formId: string;
    fieldsFilled: number;
    totalFields: number;
    abandoned: boolean;
    abandonedAt?: string;     // Which field they gave up on
  }>;
}

/**
 * Session Detail Data - Complete session information
 * 
 * STRUCTURE:
 * 1. Core session data (existing)
 * 2. Session classification (NEW)
 * 3. Persona-specific context (NEW)
 * 4. Technical data (traces, logs, etc.)
 */
export interface SessionDetailData {
  // ========================================
  // CORE SESSION DATA
  // ========================================
  sessionId: string;
  userId: string;
  isAnonymous: boolean;
  startTime: string; // ISO8601
  duration: number; // ms
  platform: "iOS" | "Android" | "Web";
  device: string;
  browser?: string;
  os: string;
  appVersion?: string;
  geography?: { country: string; city: string };
  interactionQuality: number; // 0-10
  
  // ========================================
  // SESSION CLASSIFICATION (NEW)
  // Auto-detected for smart persona views
  // ========================================
  sessionType: SessionType;           // What kind of session is this?
  detectedIssues: DetectedIssue[];    // What went wrong?
  sessionIntent?: SessionIntent;       // What was user trying to do?
  
  // ========================================
  // PERSONA-SPECIFIC CONTEXT (NEW)
  // Each persona sees different things
  // ========================================
  businessContext?: BusinessContext;   // PRODUCT: Conversion, A/B tests, revenue
  supportContext?: SupportContext;     // SUPPORT: Tickets, workarounds, actions
  technicalContext?: TechnicalContext; // TECH: Root cause, code refs, errors
  uxMetrics?: UXMetrics;              // DESIGN: Rage clicks, frustration signals
  
  // ========================================
  // TECHNICAL DATA (Existing)
  // ========================================
  
  // Critical interactions
  criticalInteractions: CriticalInteraction[];
  
  // Journey
  journey: string[];
  
  // For flame chart (mock traces, logs, exceptions)
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
  
  // Events timeline
  events: SessionEvent[];
  
  // Console logs
  consoleLogs: ConsoleLog[];
  
  // Network requests
  networkRequests: NetworkRequest[];
  
  // Performance metrics
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

/**
 * Generate mock session detail data
 * 
 * SCENARIO: Payment Timeout (Error + Abandonment)
 * - SUPPORT sees: "User couldn't complete $99 payment due to timeout"
 * - PRODUCT sees: "Conversion abandoned, 45 similar drop-offs today"
 * - TECH sees: "TimeoutException in PaymentService:45, Error Group #234"
 */
export function getMockSessionDetail(sessionId: string): SessionDetailData {
  const now = new Date();
  const sessionStart = new Date(now.getTime() - 154000); // 2m 34s ago
  
  return {
    // ========================================
    // CORE SESSION DATA
    // ========================================
    sessionId,
    userId: "user_3456",
    isAnonymous: false,
    startTime: sessionStart.toISOString(),
    duration: 154000, // 2m 34s
    platform: "iOS",
    device: "iPhone 15 Pro",
    os: "iOS 17.2",
    appVersion: "2.3.1",
    geography: {
      country: "United States",
      city: "San Francisco"
    },
    interactionQuality: 6.5,
    
    // ========================================
    // SESSION CLASSIFICATION (NEW)
    // ========================================
    sessionType: 'error_encountered', // Payment timeout error
    
    detectedIssues: [
      {
        id: 'issue_timeout_1',
        type: 'timeout',
        severity: 'high',
        timestamp: 77200, // When error happened
        title: 'Payment Timeout',
        description: 'Payment API failed to respond within timeout threshold',
        affectedFeature: 'Checkout',
        errorGroup: 'error_group_234',
        relatedSpanId: 'span_006',
        
        // SUPPORT: Plain language
        userFacingImpact: 'User could not complete $99.99 payment. Transaction was not processed.',
        
        // TECH: Technical details
        technicalCause: 'POST /api/payment returned 504 Gateway Timeout after 1.2 seconds',
        
        suggestedAction: 'Check payment gateway status and increase timeout threshold to 3s'
      },
      {
        id: 'issue_slowness_1',
        type: 'slowness',
        severity: 'medium',
        timestamp: 77000,
        title: 'Slow Payment Interaction',
        description: 'Payment button tap took 1.2 seconds to respond',
        affectedFeature: 'Payment',
        
        userFacingImpact: 'User experienced noticeable delay when tapping Pay button',
        technicalCause: 'Payment validation took 1200ms (2.4x above 500ms threshold)',
        suggestedAction: 'Optimize payment validation logic or add loading state'
      }
    ],
    
    sessionIntent: {
      primary: 'Complete checkout',      // What they wanted to do
      completed: false,                  // Did they succeed? No.
      abandonedAt: 'Payment',            // Where did they give up?
      expectedDuration: 120000,          // Benchmark: 2 minutes
      actualDuration: 154000             // They took 2m 34s (28% longer)
    },
    
    // ========================================
    // BUSINESS CONTEXT (PRODUCT)
    // ========================================
    businessContext: {
      isConversionSession: true,
      conversionGoal: 'Complete Purchase',
      conversionValue: 99.99,            // Lost revenue
      conversionStage: 'decision',       // Got to payment but didn't complete
      funnelStep: 'Step 3 of 4: Payment',
      
      userSegment: 'Free Trial',         // Not yet paying customer
      cohort: 'Q1 2026 Signups',
      isFirstSession: false,             // Has used app before
      lifetimeValue: 0,                  // No purchases yet
      
      experiments: [
        {
          id: 'exp_checkout_v2',
          name: 'New Checkout Flow',
          variant: 'Variant B'           // Testing new flow
        }
      ],
      
      featuresUsed: ['Search', 'Product View', 'Add to Cart', 'Checkout', 'Payment'],
      featureEngagement: {
        'Search': 20000,                 // 20 seconds
        'Product View': 45000,           // 45 seconds
        'Checkout': 42000,               // 42 seconds
        'Payment': 30000                 // 30 seconds (then gave up)
      },
      
      similarSessionsCount: 156,         // 156 sessions today with checkout
      similarErrorsToday: 45             // 45 hit same payment timeout
    },
    
    // ========================================
    // SUPPORT CONTEXT
    // ========================================
    supportContext: {
      relatedTicketId: undefined,        // No ticket yet (will be created)
      supportCategory: 'Payment Issues',
      
      previousIssues: [
        {
          sessionId: 'session_prev_1',
          issueType: 'Login timeout',
          timestamp: new Date(now.getTime() - 86400000 * 3).toISOString(), // 3 days ago
          resolved: true
        }
      ],
      
      matchesKnownIssue: {
        issueId: 'known_issue_456',
        title: 'iOS Payment Gateway Timeout',
        affectedUsers: 23,               // 23 users hit this today
        workaround: 'Ask user to try PayPal payment method instead of credit card',
        status: 'investigating'
      },
      
      suggestedActions: [
        {
          id: 'action_create_ticket',
          label: 'Create Support Ticket',
          type: 'create_ticket',
          priority: 'high'
        },
        {
          id: 'action_send_workaround',
          label: 'Send Workaround to User',
          type: 'send_workaround',
          priority: 'high'
        },
        {
          id: 'action_escalate',
          label: 'Escalate to Engineering',
          type: 'escalate',
          priority: 'medium'
        }
      ]
    },
    
    // ========================================
    // TECHNICAL CONTEXT
    // ========================================
    technicalContext: {
      rootCause: {
        type: 'timeout',
        component: 'PaymentService',
        errorChain: [
          {
            timestamp: 77200,
            component: 'PaymentService',
            error: 'Payment API timeout',
            causedBy: undefined
          },
          {
            timestamp: 77500,
            component: 'CheckoutState',
            error: 'State update failed',
            causedBy: 'Payment API timeout'
          },
          {
            timestamp: 77800,
            component: 'PaymentScreen',
            error: 'UI rendering error',
            causedBy: 'State update failed'
          }
        ]
      },
      
      codeReferences: [
        {
          file: 'src/services/PaymentService.ts',
          line: 45,
          function: 'processPayment()',
          stackFrame: 'at PaymentService.process (payment.ts:45)',
          githubUrl: 'https://github.com/yourorg/pulse/blob/main/src/services/PaymentService.ts#L45'
        },
        {
          file: 'src/screens/PaymentScreen.tsx',
          line: 123,
          function: 'handlePayment()',
          stackFrame: 'at handlePayment (handlers.ts:23)',
          githubUrl: 'https://github.com/yourorg/pulse/blob/main/src/screens/PaymentScreen.tsx#L123'
        }
      ],
      
      relatedPRs: [
        {
          id: '#1234',
          title: 'Reduce payment timeout threshold',
          url: 'https://github.com/yourorg/pulse/pull/1234',
          status: 'merged',
          mergedAt: new Date(now.getTime() - 86400000 * 2).toISOString() // 2 days ago (suspect!)
        }
      ],
      
      relatedJiraIssues: [
        {
          key: 'PULSE-456',
          title: 'Payment timeout on iOS devices',
          status: 'In Progress',
          url: 'https://yourorg.atlassian.net/browse/PULSE-456'
        }
      ],
      
      errorGroupInfo: {
        groupId: 'error_group_234',
        firstSeen: new Date(now.getTime() - 86400000 * 2).toISOString(), // Started 2 days ago
        lastSeen: now.toISOString(),
        occurrenceCount: 23,             // 23 times today
        affectedUsers: 18,               // 18 unique users
        trend: 'increasing'              // Getting worse!
      },
      
      environmentInfo: {
        appVersion: '2.3.1',
        buildNumber: 'build-456',
        deployedAt: new Date(now.getTime() - 86400000 * 2).toISOString(), // Deployed 2 days ago
        featureFlags: {
          'new_payment_flow': true,      // New feature enabled
          'paypal_integration': true,
          'stripe_v3': false
        }
      },
      
      reproducibilityScore: 95,          // Very reproducible
      reproductionSteps: [
        'Navigate to /checkout on iOS device',
        'Select credit card payment method',
        'Tap "Pay Now" button',
        'API times out after 1.2 seconds',
        'Error message appears'
      ]
    },
    
    // ========================================
    // UX METRICS
    // ========================================
    uxMetrics: {
      rageClicks: [
        {
          timestamp: 77000,
          elementSelector: '#pay-button',
          clickCount: 3                  // User clicked 3 times in frustration
        }
      ],
      
      deadClicks: [
        {
          timestamp: 77000,
          elementSelector: '#pay-button',
          expectedAction: 'Process payment' // But nothing happened
        }
      ],
      
      errorRecoveryAttempts: 2,          // User tried to retry twice
      backButtonUsage: 1,                // Went back once (confusion)
      
      scrollDepth: 85,                   // Saw 85% of payment screen
      viewportTime: {
        'HomeScreen': 20000,
        'SearchScreen': 25000,
        'ProductScreen': 45000,
        'CheckoutScreen': 42000,
        'PaymentScreen': 30000           // Spent 30s trying to pay
      },
      
      formInteractions: [
        {
          formId: 'payment-form',
          fieldsFilled: 4,
          totalFields: 4,
          abandoned: false               // Filled all fields before error
        }
      ]
    },
    
    // ========================================
    // TECHNICAL DATA (Existing)
    // ========================================
    
    // Critical Interactions
    criticalInteractions: [
      {
        interactionId: 1,
        interactionName: "tap_pay_button",
        displayName: "Payment Button Tap",
        status: "failed",
        timestamp: 77000, // 1m 17s from start
        latency: 1200,
        apdexScore: 0.4,
        businessValue: "Checkout",       // NEW
        revenueImpact: 99.99             // NEW: Lost revenue
      },
      {
        interactionId: 2,
        interactionName: "signup_form_submit",
        displayName: "Signup Form Submit",
        status: "success",
        timestamp: 15000,
        latency: 450,
        apdexScore: 0.85,
        businessValue: "Signup"          // NEW
      },
      {
        interactionId: 3,
        interactionName: "contest_join",
        displayName: "Contest Join",
        status: "not_attempted",
        businessValue: "Engagement"       // NEW
      }
    ],
    
    // Journey
    journey: ["/home", "/search", "/contest", "/pay", "/error"],
    
    // Traces (mock data)
    traces: {
      fields: ["traceId", "spanId", "parentSpanId", "spanName", "timestamp", "duration", "statusCode", "spanType", "pulseType", "serviceName"],
      rows: [
        ["trace_001", "span_001", "", "App Start", sessionStart.toISOString(), 50000000, "OK", "app_start", "app.lifecycle.start", "mobile-app"],
        ["trace_001", "span_002", "span_001", "Database Query", new Date(sessionStart.getTime() + 5000).toISOString(), 23000000, "OK", "database", "db.query", "mobile-app"],
        ["trace_001", "span_003", "span_001", "PreHomeScreen Load", new Date(sessionStart.getTime() + 10000).toISOString(), 245000000, "OK", "screen", "screen.load", "mobile-app"],
        ["trace_001", "span_004", "span_003", "API /user/profile", new Date(sessionStart.getTime() + 15000).toISOString(), 120000000, "OK", "http", "api.call", "mobile-app"],
        ["trace_002", "span_005", "", "Payment Button Tap", new Date(sessionStart.getTime() + 77000).toISOString(), 1200000000, "ERROR", "interaction", "interaction.tap", "mobile-app"],
        ["trace_002", "span_006", "span_005", "POST /api/payment", new Date(sessionStart.getTime() + 77200).toISOString(), 1100000000, "ERROR", "http", "api.call", "mobile-app"],
      ]
    },
    
    // Logs
    logs: {
      fields: ["traceId", "spanId", "timestamp", "severityText", "severityNumber", "body", "eventName", "pulseType", "serviceName", "scopeName", "logAttributesJson", "resourceAttributesJson"],
      rows: [
        ["trace_001", "span_001", new Date(sessionStart.getTime() + 1000).toISOString(), "INFO", 9, "App initialized", "app.init", "app.lifecycle.init", "mobile-app", "AppScope", "{}", "{}"],
        ["trace_001", "span_003", new Date(sessionStart.getTime() + 12000).toISOString(), "WARN", 13, "Slow network detected", "network.slow", "network.performance.slow", "mobile-app", "NetworkScope", "{}", "{}"],
        ["trace_002", "span_005", new Date(sessionStart.getTime() + 77500).toISOString(), "ERROR", 17, "Payment timeout", "payment.error", "api.error.timeout", "mobile-app", "PaymentScope", "{}", "{}"],
      ]
    },
    
    // Exceptions
    exceptions: {
      fields: ["timestamp", "eventName", "title", "exceptionMessage", "exceptionType", "screenName", "traceId", "spanId", "groupId", "pulseType"],
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
          "exception.timeout"
        ]
      ]
    },
    
    // Events
    events: [
      {
        timestamp: 5000,
        type: "click",
        description: "Tapped Submit button"
      },
      {
        timestamp: 12000,
        type: "api_call",
        description: "POST /user/signup"
      },
      {
        timestamp: 15000,
        type: "navigation",
        description: "/home → /dashboard"
      },
      {
        timestamp: 77000,
        type: "click",
        description: "Tapped Payment button"
      },
      {
        timestamp: 77200,
        type: "api_call",
        description: "POST /api/payment"
      },
      {
        timestamp: 78400,
        type: "error",
        description: "Payment timeout error"
      }
    ],
    
    // Console logs
    consoleLogs: [
      {
        timestamp: 5000,
        level: "log",
        message: "User clicked submit"
      },
      {
        timestamp: 12000,
        level: "warn",
        message: "Slow network detected"
      },
      {
        timestamp: 78400,
        level: "error",
        message: "TypeError: Cannot process payment",
        stackTrace: "at PaymentService.process (payment.ts:45)\nat handlePayment (handlers.ts:23)"
      }
    ],
    
    // Network requests
    networkRequests: [
      {
        timestamp: 12000,
        method: "POST",
        url: "/api/user/signup",
        status: 200,
        duration: 450,
        requestBody: '{"email":"user@example.com","name":"John"}',
        responseBody: '{"success":true,"userId":"user_3456"}'
      },
      {
        timestamp: 77200,
        method: "POST",
        url: "/api/payment",
        status: 504,
        duration: 1200,
        requestBody: '{"amount":99.99,"currency":"USD"}',
        responseBody: '{"error":"Gateway Timeout"}'
      },
      {
        timestamp: 80000,
        method: "GET",
        url: "/api/user/profile",
        status: 200,
        duration: 120
      }
    ],
    
    // Performance
    performance: {
      coreWebVitals: undefined, // Not applicable for mobile
      interactionMetrics: [
        {
          interactionId: 1,
          interactionName: "tap_pay_button",
          duration: 1200,
          apdexScore: 0.4
        },
        {
          interactionId: 2,
          interactionName: "signup_form_submit",
          duration: 450,
          apdexScore: 0.85
        }
      ]
    }
  };
}
