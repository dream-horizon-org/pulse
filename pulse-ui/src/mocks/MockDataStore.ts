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
        teamName: "Frontend",
        userId: 1,
        emailId: "user1@dream11.com",
        commEmailId: "user1@dream11.com",
        phoneNo: "9876543210",
        experiments: ["experiment1", "experiment2"],
        lastActiveToday: true,
      },
      {
        teamName: "Backend",
        userId: 2,
        emailId: "user2@dream11.com",
        commEmailId: "user2@dream11.com",
        phoneNo: "9876543211",
        experiments: ["experiment3"],
        lastActiveToday: false,
      },
      {
        teamName: "DevOps",
        userId: 3,
        emailId: "user3@dream11.com",
        commEmailId: "user3@dream11.com",
        phoneNo: "9876543212",
        experiments: [],
        lastActiveToday: true,
      },
    ];
  }

  private initializeJobs(): void {
    this.data.jobs = [
      {
        id: 1,
        interactionName: "ContestJoinSuccess",
        description: "User successfully joins a fantasy contest",
        status: "RUNNING",
        createdBy: "user1@dream11.com",
        updatedBy: "user1@dream11.com",
        createdAt: 1705312800000,
        updatedAt: 1705312800000,
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 1000,
        uptimeMidLimit: 500,
        interactionThreshold: 60000,
        eventSequence: [
          {
            eventName: "contest_join_start",
            props: [
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "contest_join_success",
            props: [
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "success", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 2,
        interactionName: "CreateTeamSuccess",
        description: "User successfully creates a fantasy team",
        status: "STOPPED",
        createdBy: "user2@dream11.com",
        updatedBy: "user2@dream11.com",
        createdAt: 1705221000000,
        updatedAt: 1705246200000,
        uptimeLowerLimit: 200,
        uptimeUpperLimit: 2000,
        uptimeMidLimit: 1000,
        interactionThreshold: 120000,
        eventSequence: [
          {
            eventName: "team_create_start",
            props: [
              { propName: "team_id", propValue: "string", operator: "EQUALS" },
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "team_save_start",
            props: [
              { propName: "team_id", propValue: "string", operator: "EQUALS" },
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "team_save_success",
            props: [
              { propName: "team_id", propValue: "string", operator: "EQUALS" },
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "team_create_success",
            props: [
              { propName: "team_id", propValue: "string", operator: "EQUALS" },
              { propName: "created", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "error_event",
            props: [
              {
                propName: "error_type",
                propValue: "critical",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
          {
            eventName: "error_event2",
            props: [
              {
                propName: "error_type",
                propValue: "more critical",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 3,
        interactionName: "CreateTeamPageLoaded",
        description: "Team creation page loads successfully",
        status: "RUNNING",
        createdBy: "user1@dream11.com",
        updatedBy: "user1@dream11.com",
        createdAt: 1705154400000,
        updatedAt: 1705154400000,
        uptimeLowerLimit: 50,
        uptimeUpperLimit: 500,
        uptimeMidLimit: 250,
        interactionThreshold: 15000,
        eventSequence: [
          {
            eventName: "page_load_start",
            props: [
              {
                propName: "page_name",
                propValue: "team_create",
                operator: "EQUALS",
              },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "page_load_complete",
            props: [
              {
                propName: "page_name",
                propValue: "team_create",
                operator: "EQUALS",
              },
              {
                propName: "load_time",
                propValue: "number",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 4,
        interactionName: "ContestHomeLanded",
        description: "User lands on contest home page",
        status: "RUNNING",
        createdBy: "user3@dream11.com",
        updatedBy: "user3@dream11.com",
        createdAt: 1705064400000,
        updatedAt: 1705064400000,
        uptimeLowerLimit: 80,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 20000,
        eventSequence: [
          {
            eventName: "page_view_start",
            props: [
              {
                propName: "page_name",
                propValue: "contest_home",
                operator: "EQUALS",
              },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "page_view_complete",
            props: [
              {
                propName: "view_time",
                propValue: "number",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 5,
        interactionName: "AddCashLoaded",
        description: "Add cash page loads successfully",
        status: "RUNNING",
        createdBy: "user2@dream11.com",
        updatedBy: "user2@dream11.com",
        createdAt: 1704981600000,
        updatedAt: 1704981600000,
        uptimeLowerLimit: 60,
        uptimeUpperLimit: 600,
        uptimeMidLimit: 300,
        interactionThreshold: 18000,
        eventSequence: [
          {
            eventName: "add_cash_page_load",
            props: [
              {
                propName: "page_type",
                propValue: "add_cash",
                operator: "EQUALS",
              },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "add_cash_page_ready",
            props: [
              {
                propName: "load_success",
                propValue: "true",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "payment_error",
            props: [
              {
                propName: "error_type",
                propValue: "gateway_failure",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 6,
        interactionName: "PaymentSuccess",
        description: "User completes payment successfully",
        status: "RUNNING",
        createdBy: "user1@dream11.com",
        updatedBy: "user1@dream11.com",
        createdAt: 1704891600000,
        updatedAt: 1704891600000,
        uptimeLowerLimit: 300,
        uptimeUpperLimit: 3000,
        uptimeMidLimit: 1500,
        interactionThreshold: 45000,
        eventSequence: [
          {
            eventName: "payment_start",
            props: [
              {
                propName: "payment_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "amount", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "payment_success",
            props: [
              {
                propName: "payment_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "success", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "payment_failed",
            props: [
              {
                propName: "error_code",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 7,
        interactionName: "ProfileUpdateSuccess",
        description: "User updates profile information successfully",
        status: "STOPPED",
        createdBy: "user2@dream11.com",
        updatedBy: "user2@dream11.com",
        createdAt: 1704806400000,
        updatedAt: 1704806400000,
        uptimeLowerLimit: 150,
        uptimeUpperLimit: 1500,
        uptimeMidLimit: 750,
        interactionThreshold: 30000,
        eventSequence: [
          {
            eventName: "profile_edit_start",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              { propName: "section", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "profile_update_success",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              { propName: "updated", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 8,
        interactionName: "MatchListLoaded",
        description: "Match list page loads with all matches",
        status: "RUNNING",
        createdBy: "user3@dream11.com",
        updatedBy: "user3@dream11.com",
        createdAt: 1704720000000,
        updatedAt: 1704720000000,
        uptimeLowerLimit: 200,
        uptimeUpperLimit: 2000,
        uptimeMidLimit: 1000,
        interactionThreshold: 25000,
        eventSequence: [
          {
            eventName: "match_list_request",
            props: [
              { propName: "sport", propValue: "string", operator: "EQUALS" },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "match_list_loaded",
            props: [
              {
                propName: "match_count",
                propValue: "number",
                operator: "EQUALS",
              },
              { propName: "loaded", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "api_timeout",
            props: [
              {
                propName: "timeout_duration",
                propValue: "number",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 9,
        interactionName: "WalletRechargeSuccess",
        description: "User successfully recharges wallet",
        status: "RUNNING",
        createdBy: "user1@dream11.com",
        updatedBy: "user1@dream11.com",
        createdAt: 1704638400000,
        updatedAt: 1704638400000,
        uptimeLowerLimit: 400,
        uptimeUpperLimit: 4000,
        uptimeMidLimit: 2000,
        interactionThreshold: 60000,
        eventSequence: [
          {
            eventName: "wallet_recharge_start",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              { propName: "amount", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "wallet_recharge_success",
            props: [
              {
                propName: "transaction_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "success", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "payment_gateway_down",
            props: [
              { propName: "gateway", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 10,
        interactionName: "LeaderboardLoaded",
        description: "Leaderboard page loads with rankings",
        status: "RUNNING",
        createdBy: "user2@dream11.com",
        updatedBy: "user2@dream11.com",
        createdAt: 1704552000000,
        updatedAt: 1704552000000,
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 1000,
        uptimeMidLimit: 500,
        interactionThreshold: 20000,
        eventSequence: [
          {
            eventName: "leaderboard_request",
            props: [
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "leaderboard_loaded",
            props: [
              {
                propName: "user_count",
                propValue: "number",
                operator: "EQUALS",
              },
              { propName: "loaded", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 11,
        interactionName: "WithdrawRequestSuccess",
        description: "User successfully requests withdrawal",
        status: "STOPPED",
        createdBy: "user3@dream11.com",
        updatedBy: "user3@dream11.com",
        createdAt: 1704465600000,
        updatedAt: 1704465600000,
        uptimeLowerLimit: 500,
        uptimeUpperLimit: 5000,
        uptimeMidLimit: 2500,
        interactionThreshold: 90000,
        eventSequence: [
          {
            eventName: "withdraw_request_start",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              { propName: "amount", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "withdraw_request_success",
            props: [
              {
                propName: "request_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "submitted", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "insufficient_balance",
            props: [
              { propName: "balance", propValue: "number", operator: "EQUALS" },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 12,
        interactionName: "LiveScoreLoaded",
        description: "Live score updates load successfully",
        status: "RUNNING",
        createdBy: "user1@dream11.com",
        updatedBy: "user1@dream11.com",
        createdAt: 1704384000000,
        updatedAt: 1704384000000,
        uptimeLowerLimit: 50,
        uptimeUpperLimit: 500,
        uptimeMidLimit: 250,
        interactionThreshold: 10000,
        eventSequence: [
          {
            eventName: "live_score_request",
            props: [
              { propName: "match_id", propValue: "string", operator: "EQUALS" },
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "live_score_loaded",
            props: [
              {
                propName: "score_data",
                propValue: "object",
                operator: "EQUALS",
              },
              { propName: "updated", propValue: "true", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "score_service_down",
            props: [
              {
                propName: "service",
                propValue: "live_score",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
    ];
  }

  private initializeAlerts(): void {
    this.data.alerts = [
      {
        alert_id: 1,
        job_id: "1",
        name: "High Error Rate Alert",
        description: "Alert when error rate exceeds 10% threshold",
        appdex_threshold: 0.1,
        severity_id: "3",
        service_name: "ContestJoinSuccess",
        roster_name: "Frontend Team",
        current_state: "FIRING",
        last_evaluated_at: new Date(1705305600000),
        conditions: "",
        updated_at: new Date(1705305600000),
        created_by: "user1@dream11.com",
        job_name: "ContestJoinSuccess",
        metric: "ERROR_RATE",
        metric_operator: "GREATER_THAN",
        threshold: 0.1,
        min_total_interactions: 100,
        min_success_interactions: 90,
        min_error_interactions: 10,
        evaluation_interval: 300,
        evaluation_period: 600,
        is_snoozed: false,
        snoozed_from: 0,
        snoozed_until: 0,
        handleDuplicateAlert: async () => {},
      },
      {
        alert_id: 2,
        job_id: "2",
        name: "Low Apdex Score Alert",
        description: "Alert when Apdex score is below 70% threshold",
        appdex_threshold: 0.7,
        severity_id: "4",
        service_name: "CreateTeamSuccess",
        roster_name: "Backend Team",
        current_state: "SILENCED",
        last_evaluated_at: new Date(1705222800000),
        conditions: "",
        updated_at: new Date(1705222800000),
        created_by: "user2@dream11.com",
        job_name: "CreateTeamSuccess",
        metric: "APDEX",
        metric_operator: "LESS_THAN",
        threshold: 0.7,
        min_total_interactions: 200,
        min_success_interactions: 180,
        min_error_interactions: 20,
        evaluation_interval: 600,
        evaluation_period: 1200,
        is_snoozed: true,
        snoozed_from: 1705222800000,
        snoozed_until: 1705309200000,
        handleDuplicateAlert: async () => {},
      },
      {
        alert_id: 3,
        job_id: "3",
        name: "High P95 Response Time Alert",
        description: "Alert when P95 response time exceeds threshold",
        appdex_threshold: 2000,
        severity_id: "2",
        service_name: "CreateTeamPageLoaded",
        roster_name: "Frontend Team",
        current_state: "FIRING",
        last_evaluated_at: new Date(1705305600000),
        conditions: "",
        updated_at: new Date(1705305600000),
        created_by: "user1@dream11.com",
        job_name: "CreateTeamPageLoaded",
        metric: "INTERACTION_TIME_P95",
        metric_operator: "GREATER_THAN",
        threshold: 2000,
        min_total_interactions: 150,
        min_success_interactions: 140,
        min_error_interactions: 10,
        evaluation_interval: 300,
        evaluation_period: 900,
        is_snoozed: false,
        snoozed_from: 0,
        snoozed_until: 0,
        handleDuplicateAlert: async () => {},
      },
      {
        alert_id: 4,
        job_id: "4",
        name: "Low Excellent Interactions Alert",
        description:
          "Alert when excellent interactions percentage is below threshold",
        appdex_threshold: 0.95,
        severity_id: "5",
        service_name: "ContestHomeLanded",
        roster_name: "Backend Team",
        current_state: "NORMAL",
        last_evaluated_at: new Date(1705222800000),
        conditions: "",
        updated_at: new Date(1705222800000),
        created_by: "user3@dream11.com",
        job_name: "ContestHomeLanded",
        metric: "INTERACTION_CATEGORY_EXCELLENT",
        metric_operator: "LESS_THAN",
        threshold: 0.95,
        min_total_interactions: 300,
        min_success_interactions: 285,
        min_error_interactions: 15,
        evaluation_interval: 600,
        evaluation_period: 1800,
        is_snoozed: false,
        snoozed_from: 0,
        snoozed_until: 0,
        handleDuplicateAlert: async () => {},
      },
      {
        alert_id: 5,
        job_id: "5",
        name: "High P99 Response Time Alert",
        description: "Alert when P99 response time exceeds threshold",
        appdex_threshold: 0.8,
        severity_id: "3",
        service_name: "AddCashLoaded",
        roster_name: "DevOps Team",
        current_state: "FIRING",
        last_evaluated_at: new Date(1705305600000),
        conditions: "",
        updated_at: new Date(1705305600000),
        created_by: "user2@dream11.com",
        job_name: "AddCashLoaded",
        metric: "INTERACTION_TIME_P99",
        metric_operator: "GREATER_THAN",
        threshold: 5000,
        min_total_interactions: 80,
        min_success_interactions: 75,
        min_error_interactions: 5,
        evaluation_interval: 300,
        evaluation_period: 600,
        is_snoozed: false,
        snoozed_from: 0,
        snoozed_until: 0,
        handleDuplicateAlert: async () => {},
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
