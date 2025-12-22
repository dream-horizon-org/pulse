/**
 * Mock Data Store
 *
 * Centralized data store for mock responses
 * Maintains state across API calls for realistic behavior
 */

import { MockDataStore as IMockDataStore } from "./types";

export class MockDataStore {
  private static instance: MockDataStore;
  private data: IMockDataStore;

  private constructor() {
    this.data = {
      users: [],
      jobs: [],
      alerts: [],
      analytics: [],
      queries: [],
      events: [],
    };
    this.initializeData();
  }

  static getInstance(): MockDataStore {
    if (!MockDataStore.instance) {
      MockDataStore.instance = new MockDataStore();
    }
    return MockDataStore.instance;
  }

  private initializeData(): void {
    this.initializeUsers();
    this.initializeJobs();
    this.initializeAlerts();
    this.initializeAnalytics();
    this.initializeEvents();
  }

  private initializeUsers(): void {
    this.data.users = [
      {
        teamName: "Product Engineering",
        userId: 1,
        emailId: "rahul.sharma@example.com",
        commEmailId: "rahul.sharma@example.com",
        phoneNo: "+91 98765 43210",
        experiments: ["new_team_creation_flow", "contest_join_optimization"],
        lastActiveToday: true,
      },
      {
        teamName: "Platform Engineering",
        userId: 2,
        emailId: "priya.patel@example.com",
        commEmailId: "priya.patel@example.com",
        phoneNo: "+91 98765 43211",
        experiments: ["payment_gateway_v2"],
        lastActiveToday: true,
      },
      {
        teamName: "Infrastructure",
        userId: 3,
        emailId: "amit.kumar@example.com",
        commEmailId: "amit.kumar@example.com",
        phoneNo: "+91 98765 43212",
        experiments: [],
        lastActiveToday: true,
      },
      {
        teamName: "Mobile Engineering",
        userId: 4,
        emailId: "neha.singh@example.com",
        commEmailId: "neha.singh@example.com",
        phoneNo: "+91 98765 43213",
        experiments: ["android_performance_improvements"],
        lastActiveToday: true,
      },
    ];
  }

  private initializeJobs(): void {
    const now = Date.now();
    const oneDay = 24 * 60 * 60 * 1000;
    
    // Interactions are atomic user actions - single operations with start/end events
    this.data.jobs = [
      {
        id: 1,
        interactionName: "JoinContestButtonClick",
        description: "Tracks the time from when user taps the 'Join Contest' button until the contest join API responds successfully. Measures backend latency for contest participation.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - (5 * oneDay),
        updatedAt: now - (2 * oneDay),
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 30000,
        eventSequence: [
          {
            eventName: "join_contest_click",
            props: [
              { propName: "contest_id", propValue: "string", operator: "EQUALS" },
              { propName: "entry_fee", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "join_contest_response",
            props: [
              { propName: "contest_id", propValue: "string", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 2,
        interactionName: "SaveTeamButtonClick",
        description: "Measures the time from 'Save Team' button tap to successful team save API response. Critical for team creation experience.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - (4 * oneDay),
        updatedAt: now - (1 * oneDay),
        uptimeLowerLimit: 150,
        uptimeUpperLimit: 1200,
        uptimeMidLimit: 600,
        interactionThreshold: 45000,
        eventSequence: [
          {
            eventName: "save_team_click",
            props: [
              { propName: "team_id", propValue: "string", operator: "EQUALS" },
              { propName: "player_count", propValue: "11", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "save_team_response",
            props: [
              { propName: "team_id", propValue: "string", operator: "EQUALS" },
              { propName: "saved", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "save_team_error",
            props: [
              { propName: "error_type", propValue: "validation_error", operator: "EQUALS" },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 3,
        interactionName: "PlayerSelectTap",
        description: "Time from player card tap to player added/removed confirmation. Ensures smooth player selection experience in team creation.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - (6 * oneDay),
        updatedAt: now - (3 * oneDay),
        uptimeLowerLimit: 30,
        uptimeUpperLimit: 200,
        uptimeMidLimit: 100,
        interactionThreshold: 10000,
        eventSequence: [
          {
            eventName: "player_tap",
            props: [
              { propName: "player_id", propValue: "string", operator: "EQUALS" },
              { propName: "action", propValue: "select", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "player_selection_complete",
            props: [
              { propName: "player_id", propValue: "string", operator: "EQUALS" },
              { propName: "selected", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 4,
        interactionName: "ContestListAPIFetch",
        description: "API call duration for fetching available contests list. Measures backend performance for contest discovery.",
        status: "RUNNING",
        createdBy: "amit.kumar@example.com",
        updatedBy: "amit.kumar@example.com",
        createdAt: now - (7 * oneDay),
        updatedAt: now - (2 * oneDay),
        uptimeLowerLimit: 80,
        uptimeUpperLimit: 600,
        uptimeMidLimit: 300,
        interactionThreshold: 20000,
        eventSequence: [
          {
            eventName: "contest_list_request",
            props: [
              { propName: "match_id", propValue: "string", operator: "EQUALS" },
              { propName: "filter_type", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "contest_list_response",
            props: [
              { propName: "contest_count", propValue: "number", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 5,
        interactionName: "PaymentSubmitClick",
        description: "Time from payment submit button tap to payment gateway response. Critical for revenue and user trust.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - (8 * oneDay),
        updatedAt: now - (4 * oneDay),
        uptimeLowerLimit: 200,
        uptimeUpperLimit: 2000,
        uptimeMidLimit: 1000,
        interactionThreshold: 60000,
        eventSequence: [
          {
            eventName: "payment_submit_click",
            props: [
              { propName: "amount", propValue: "number", operator: "EQUALS" },
              { propName: "payment_method", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "payment_gateway_response",
            props: [
              { propName: "transaction_id", propValue: "string", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "payment_failed",
            props: [
              { propName: "error_code", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 6,
        interactionName: "WalletBalanceFetch",
        description: "API call to fetch user's current wallet balance. Frequently called action that impacts overall app responsiveness.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - (9 * oneDay),
        updatedAt: now - (5 * oneDay),
        uptimeLowerLimit: 50,
        uptimeUpperLimit: 400,
        uptimeMidLimit: 200,
        interactionThreshold: 15000,
        eventSequence: [
          {
            eventName: "wallet_balance_request",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "wallet_balance_response",
            props: [
              { propName: "balance", propValue: "number", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 7,
        interactionName: "MatchScheduleAPICall",
        description: "Fetches upcoming match schedule from backend. Core API for match discovery and contest browsing.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - (10 * oneDay),
        updatedAt: now - (6 * oneDay),
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 25000,
        eventSequence: [
          {
            eventName: "match_schedule_request",
            props: [
              { propName: "sport_type", propValue: "string", operator: "EQUALS" },
              { propName: "date_range", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "match_schedule_response",
            props: [
              { propName: "match_count", propValue: "number", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "api_timeout",
            props: [
              { propName: "timeout_ms", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 8,
        interactionName: "LeaderboardRefreshTap",
        description: "Time to refresh and display updated leaderboard data when user pulls to refresh or taps refresh.",
        status: "RUNNING",
        createdBy: "amit.kumar@example.com",
        updatedBy: "amit.kumar@example.com",
        createdAt: now - (11 * oneDay),
        updatedAt: now - (7 * oneDay),
        uptimeLowerLimit: 80,
        uptimeUpperLimit: 600,
        uptimeMidLimit: 300,
        interactionThreshold: 20000,
        eventSequence: [
          {
            eventName: "leaderboard_refresh_tap",
            props: [
              { propName: "contest_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "leaderboard_data_loaded",
            props: [
              { propName: "rank_count", propValue: "number", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 9,
        interactionName: "ProfileSaveClick",
        description: "Time from profile save button click to successful profile update confirmation.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - (12 * oneDay),
        updatedAt: now - (8 * oneDay),
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 25000,
        eventSequence: [
          {
            eventName: "profile_save_click",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              { propName: "fields_updated", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "profile_save_response",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 10,
        interactionName: "NotificationTap",
        description: "Time from notification tap to destination screen fully loaded. Measures deep link navigation performance.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - (13 * oneDay),
        updatedAt: now - (9 * oneDay),
        uptimeLowerLimit: 150,
        uptimeUpperLimit: 1200,
        uptimeMidLimit: 600,
        interactionThreshold: 35000,
        eventSequence: [
          {
            eventName: "notification_tap",
            props: [
              { propName: "notification_id", propValue: "string", operator: "EQUALS" },
              { propName: "notification_type", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "destination_screen_loaded",
            props: [
              { propName: "screen_name", propValue: "string", operator: "EQUALS" },
              { propName: "load_complete", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 11,
        interactionName: "FilterApplyTap",
        description: "Time from filter apply button tap to filtered results displayed. Measures filter query performance.",
        status: "RUNNING",
        createdBy: "amit.kumar@example.com",
        updatedBy: "amit.kumar@example.com",
        createdAt: now - (14 * oneDay),
        updatedAt: now - (10 * oneDay),
        uptimeLowerLimit: 60,
        uptimeUpperLimit: 500,
        uptimeMidLimit: 250,
        interactionThreshold: 18000,
        eventSequence: [
          {
            eventName: "filter_apply_tap",
            props: [
              { propName: "filter_type", propValue: "string", operator: "EQUALS" },
              { propName: "filter_values", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "filtered_results_displayed",
            props: [
              { propName: "result_count", propValue: "number", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 12,
        interactionName: "LiveScoreRefresh",
        description: "API call to fetch real-time match scores during live matches. High-frequency call during peak hours.",
        status: "RUNNING",
        createdBy: "neha.singh@example.com",
        updatedBy: "neha.singh@example.com",
        createdAt: now - (15 * oneDay),
        updatedAt: now - (11 * oneDay),
        uptimeLowerLimit: 40,
        uptimeUpperLimit: 300,
        uptimeMidLimit: 150,
        interactionThreshold: 12000,
        eventSequence: [
          {
            eventName: "live_score_request",
            props: [
              { propName: "match_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "live_score_response",
            props: [
              { propName: "score_data", propValue: "object", operator: "EQUALS" },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "score_service_unavailable",
            props: [
              { propName: "error_code", propValue: "503", operator: "EQUALS" },
            ],
            isBlacklisted: true,
          },
        ],
      },
    ];
  }

  private initializeAlerts(): void {
    const now = Date.now();
    
    // Alert data matching backend AlertDetailsResponseDto structure
    this.data.alerts = [
      {
        alert_id: 1,
        name: "Payment Flow - High P99 Latency",
        description: "Latency exceeds 4s for payment interactions",
        scope: "interaction",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "DURATION_P99", metric_operator: "GREATER_THAN", threshold: { "PaymentSubmit": 4000, "PaymentConfirm": 3500, "PaymentOTP": 3000 } },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 1,
        notification_channel_id: 1,
        notification_webhook_url: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 7 * 86400000).toISOString(),
        updated_at: new Date(now - 3600000).toISOString(),
        is_active: true,
        current_state: "FIRING",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 2,
        name: "Checkout - Multi-Condition Alert",
        description: "Error rate AND latency thresholds for checkout",
        scope: "network_api",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "ERROR_RATE", metric_operator: "GREATER_THAN", threshold: { "/checkout/initiate": 0.05, "/checkout/confirm": 0.03 } },
          { alias: "B", metric: "DURATION_P99", metric_operator: "GREATER_THAN", threshold: { "/checkout/initiate": 3000, "/checkout/confirm": 2500 } },
          { alias: "C", metric: "NET_5XX_RATE", metric_operator: "GREATER_THAN", threshold: { "/checkout/initiate": 0.01 } },
        ],
        condition_expression: "A && B || C",
        evaluation_period: 900,
        evaluation_interval: 120,
        severity_id: 1,
        notification_channel_id: 1,
        notification_webhook_url: "https://hooks.slack.com/xxx",
        created_by: "john@example.com",
        updated_by: "john@example.com",
        created_at: new Date(now - 14 * 86400000).toISOString(),
        updated_at: new Date(now - 7200000).toISOString(),
        is_active: true,
        current_state: "NORMAL",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 3,
        name: "App Crash Rate - Critical",
        description: "Crash rate exceeds acceptable threshold",
        scope: "app_vitals",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "CRASH_RATE", metric_operator: "GREATER_THAN", threshold: { "Android": 0.02, "iOS": 0.015 } },
        ],
        condition_expression: "A",
        evaluation_period: 1800,
        evaluation_interval: 300,
        severity_id: 1,
        notification_channel_id: 3,
        notification_webhook_url: "https://events.pagerduty.com/v2/enqueue",
        created_by: "admin@example.com",
        updated_by: "admin@example.com",
        created_at: new Date(now - 30 * 86400000).toISOString(),
        updated_at: new Date(now - 86400000).toISOString(),
        is_active: true,
        current_state: "FIRING",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 4,
        name: "Home & Product Screens - Load Time",
        description: "Screen load time above 3 seconds",
        scope: "screen",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "SCREEN_LOAD_TIME_P95", metric_operator: "GREATER_THAN", threshold: { "HomeScreen": 3000, "ProductListScreen": 2500, "CategoryScreen": 2000 } },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 1,
        notification_webhook_url: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 21 * 86400000).toISOString(),
        updated_at: new Date(now - 43200000).toISOString(),
        is_active: true,
        current_state: "NORMAL",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 5,
        name: "Login Flow - Error Spike",
        description: "High error count for login interactions",
        scope: "interaction",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "INTERACTION_ERROR_COUNT", metric_operator: "GREATER_THAN", threshold: { "LoginSubmit": 100, "OTPVerify": 50, "BiometricAuth": 30 } },
        ],
        condition_expression: "A",
        evaluation_period: 300,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 2,
        notification_webhook_url: "mailto:engineering@example.com",
        created_by: "john@example.com",
        updated_by: "john@example.com",
        created_at: new Date(now - 10 * 86400000).toISOString(),
        updated_at: new Date(now - 21600000).toISOString(),
        is_active: true,
        current_state: "NORMAL",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 6,
        name: "Search & Suggest APIs - Latency",
        description: "Search APIs latency exceeding threshold",
        scope: "network_api",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "DURATION_P99", metric_operator: "GREATER_THAN", threshold: { "/search/products": 2000, "/search/suggest": 500 } },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 1,
        notification_webhook_url: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 5 * 86400000).toISOString(),
        updated_at: new Date(now - 18000000).toISOString(),
        is_active: true,
        current_state: "FIRING",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 7,
        name: "ANR Rate - Warning",
        description: "ANR rate tracking across platforms",
        scope: "app_vitals",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "ANR_RATE", metric_operator: "GREATER_THAN", threshold: { "Android": 0.01, "iOS": 0.005 } },
        ],
        condition_expression: "A",
        evaluation_period: 1200,
        evaluation_interval: 180,
        severity_id: 2,
        notification_channel_id: 2,
        notification_webhook_url: "mailto:engineering@example.com",
        created_by: "admin@example.com",
        updated_by: "admin@example.com",
        created_at: new Date(now - 25 * 86400000).toISOString(),
        updated_at: new Date(now - 172800000).toISOString(),
        is_active: true,
        current_state: "NORMAL",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 8,
        name: "Cart Interactions - APDEX",
        description: "APDEX score below acceptable threshold",
        scope: "interaction",
        dimension_filter: null,
        alerts: [
          { alias: "A", metric: "APDEX", metric_operator: "LESS_THAN", threshold: { "AddToCart": 0.85, "UpdateCart": 0.90, "RemoveFromCart": 0.88 } },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 3,
        notification_channel_id: 4,
        notification_webhook_url: "https://api.example.com/webhooks/alerts",
        created_by: "john@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 3 * 86400000).toISOString(),
        updated_at: new Date(now - 86400000).toISOString(),
        is_active: true,
        current_state: "NORMAL",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
    ];
  }

  private initializeAnalytics(): void {
    // Generate sample analytics data
    const now = new Date();
    const dataPoints = [];

    for (let i = 0; i < 30; i++) {
      const timestamp = new Date(now.getTime() - i * 60000); // 1 minute intervals
      dataPoints.push({
        timestamp: timestamp.toISOString(),
        apdexScore: 0.8 + Math.random() * 0.2,
        errorRate: Math.random() * 0.1,
        interactionTime: 100 + Math.random() * 900,
        userCategorization: {
          isAppLaunchInteractionAction: Math.random() > 0.5,
          isInPlaceUpdateAction: Math.random() > 0.3,
          isTriggerNetworkRequestAction: Math.random() > 0.4,
          isTriggerAnimationAction: Math.random() > 0.6,
        },
      });
    }

    this.data.analytics = dataPoints;
  }

  private initializeEvents(): void {
    this.data.events = [
      {
        eventName: "login_start",
        screenName: "LoginScreen",
        properties: ["user_id", "timestamp", "device_type"],
      },
      {
        eventName: "login_complete",
        screenName: "LoginScreen",
        properties: ["user_id", "timestamp", "success"],
      },
      {
        eventName: "checkout_start",
        screenName: "CheckoutScreen",
        properties: ["user_id", "timestamp", "amount"],
      },
      {
        eventName: "checkout_complete",
        screenName: "CheckoutScreen",
        properties: ["user_id", "timestamp", "amount", "payment_method"],
      },
    ];
  }

  getData(): IMockDataStore {
    return this.data;
  }

  getUsers() {
    return this.data.users;
  }

  getJobs() {
    return this.data.jobs;
  }

  getAlerts() {
    return this.data.alerts;
  }

  getAnalytics() {
    return this.data.analytics;
  }

  getEvents() {
    return this.data.events;
  }

  addUser(user: any): void {
    this.data.users.push(user);
  }

  updateJob(jobId: number, updates: any): void {
    const jobIndex = this.data.jobs.findIndex((job) => job.id === jobId);
    if (jobIndex !== -1) {
      this.data.jobs[jobIndex] = { ...this.data.jobs[jobIndex], ...updates };
    }
  }

  updateJobByName(interactionName: string, updates: any): void {
    const jobIndex = this.data.jobs.findIndex(
      (job) => job.interactionName === interactionName,
    );
    if (jobIndex !== -1) {
      this.data.jobs[jobIndex] = { ...this.data.jobs[jobIndex], ...updates };
    }
  }

  addJob(job: any): void {
    this.data.jobs.push(job);
  }

  deleteJob(jobId: number): void {
    this.data.jobs = this.data.jobs.filter((job) => job.id !== jobId);
  }

  deleteJobByName(interactionName: string): void {
    this.data.jobs = this.data.jobs.filter(
      (job) => job.interactionName !== interactionName,
    );
  }

  findJobByName(interactionName: string): any | undefined {
    return this.data.jobs.find((job) => job.interactionName === interactionName);
  }

  addAlert(alert: any): void {
    this.data.alerts.push(alert);
  }

  updateAlert(alertId: number, updates: any): void {
    const alertIndex = this.data.alerts.findIndex(
      (alert) => alert.alert_id === alertId,
    );
    if (alertIndex !== -1) {
      this.data.alerts[alertIndex] = {
        ...this.data.alerts[alertIndex],
        ...updates,
      };
    }
  }

  deleteAlert(alertId: number): void {
    this.data.alerts = this.data.alerts.filter(
      (alert) => alert.alert_id !== alertId,
    );
  }
}
