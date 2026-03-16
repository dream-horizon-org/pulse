/**
 * Mock Data Store
 *
 * Centralized data store for mock responses
 * Maintains state across API calls for realistic behavior
 */

import { MockDataStore as IMockDataStore } from "./types";
import {
  MockEventDefinition,
  mockEventDefinitions,
  getNextEventDefId,
} from "./responses/eventDefinitionResponses";

// SDK Config types matching the PulseConfig schema
type SdkEnum = "android_native" | "android_rn" | "ios_native" | "ios_rn";
type ScopeEnum = "logs" | "traces" | "metrics" | "baggage";
type FilterMode = "blacklist" | "whitelist";
type SamplingMatchType = "app_version_min" | "app_version_max";

interface EventPropMatch {
  name: string;
  value: string;
}

interface EventFilter {
  id?: string;
  name: string;
  props: EventPropMatch[];
  scope: ScopeEnum[];
  sdks: SdkEnum[];
}

interface FiltersConfig {
  mode: FilterMode;
  whitelist: EventFilter[];
  blacklist: EventFilter[];
}

interface SamplingMatchCondition {
  type: SamplingMatchType;
  sdks: SdkEnum[];
  app_version_min_inclusive?: string;
  app_version_max_inclusive?: string;
}

interface SamplingRule {
  id?: string;
  name: string;
  match: SamplingMatchCondition;
  session_sample_rate: number;
}

interface CriticalEventPolicy {
  id?: string;
  name: string;
  props: EventPropMatch[];
  scope: ScopeEnum[];
}

interface SamplingConfig {
  default: { session_sample_rate: number };
  rules: SamplingRule[];
  criticalEventPolicies: { alwaysSend: CriticalEventPolicy[] };
}

interface SignalsConfig {
  scheduleDurationMs: number;
  collectorUrl: string;
  attributesToDrop: string[];
}

interface InteractionConfig {
  collectorUrl: string;
  configUrl: string;
  beforeInitQueueSize: number;
}

interface FeatureConfig {
  id?: string;
  featureName: string;
  session_sample_rate: number;
  sdks: SdkEnum[];
}

interface PulseConfig {
  version: number;
  filtersConfig: FiltersConfig;
  samplingConfig: SamplingConfig;
  signals: SignalsConfig;
  interaction: InteractionConfig;
  featureConfigs: FeatureConfig[];
}

// Version metadata
interface ConfigVersionMeta {
  version: number;
  createdAt: string;
  createdBy: string;
  description?: string;
  isActive: boolean;
}

// Config with metadata
interface PulseConfigWithMeta extends PulseConfig {
  _meta: ConfigVersionMeta;
}

// Auth/tenant types for mock server
export interface MockProjectSummary {
  projectId: string;
  name: string;
  description: string;
  isActive: boolean;
  role: "admin" | "editor" | "viewer";
}

export interface MockTenantContext {
  tenantId: string;
  tenantName: string;
  projects: MockProjectSummary[];
}

// Member types for tenant/project member management
export interface MockMember {
  userId: string;
  email: string;
  name: string;
  role: string;
  status: "active" | "pending" | "inactive";
  lastLoginAt: string | null;
}

// Full project details (for GET /v1/projects/:projectId)
export interface MockProjectDetails {
  projectId: string;
  name: string;
  description: string;
  tenantId: string;
  apiKey?: string;
  isActive: boolean;
  createdAt: string;
  createdBy: string;
}

// Tenant details (for GET /v1/tenants/:tenantId)
export interface MockTenantDetails {
  tenantId: string;
  name: string;
  description: string;
  tier: string;
  isActive: boolean;
  createdAt: string;
}

// API key for project (matches ApiKeyRestResponse)
export interface MockApiKey {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  isActive: boolean;
  expiresAt: string | null;
  gracePeriodEndsAt: string | null;
  createdBy: string;
  createdAt: string;
  deactivatedAt: string | null;
  deactivatedBy: string | null;
  deactivationReason: string | null;
}

// Project settings (mock structure)
export interface MockProjectSettings {
  retentionDays?: number;
  samplingRate?: number;
  [key: string]: unknown;
}

export class MockDataStore {
  private static instance: MockDataStore;
  private data: IMockDataStore;
  private sdkConfig: PulseConfig;
  private configHistory: PulseConfigWithMeta[];
  private eventDefinitions: MockEventDefinition[];

  /** Current tenant context - set by login (dev-id-token) or onboarding complete */
  private currentTenant: MockTenantContext | null = null;

  /** Tenant members: tenantId -> members[] */
  private mockTenantMembers: Map<string, MockMember[]> = new Map();

  /** Project members: projectId -> members[] */
  private mockProjectMembers: Map<string, MockMember[]> = new Map();

  /** Full project details: projectId -> project (includes apiKey for newly created) */
  private mockProjects: Map<string, MockProjectDetails> = new Map();

  /** Tenant details: tenantId -> tenant */
  private mockTenants: Map<string, MockTenantDetails> = new Map();

  /** Project API keys: projectId -> MockApiKey[] */
  private mockProjectApiKeys: Map<string, MockApiKey[]> = new Map();

  /** Project settings: projectId -> settings */
  private mockProjectSettings: Map<string, MockProjectSettings> = new Map();

  /** Next API key ID for mock generation */
  private nextApiKeyId = 1000;

  private constructor() {
    this.data = {
      users: [],
      jobs: [],
      alerts: [],
      analytics: [],
      queries: [],
      events: [],
    };
    this.sdkConfig = this.getDefaultSdkConfig();
    this.configHistory = this.initializeConfigHistory();
    this.eventDefinitions = [...mockEventDefinitions];
    this.initializeData();
  }

  private getDefaultSdkConfig(): PulseConfig {
    const generateId = () => Math.random().toString(36).substring(2, 11);

    return {
      version: 1,
      filtersConfig: {
        mode: "blacklist",
        whitelist: [
          {
            id: generateId(),
            name: "test_event",
            props: [{ name: "user_id", value: ".*test.*" }],
            scope: ["logs", "traces"],
            sdks: ["android_native", "ios_native"],
          },
        ],
        blacklist: [
          {
            id: generateId(),
            name: "sensitive_event",
            props: [{ name: "contains_pii", value: "true" }],
            scope: ["logs", "traces", "metrics"],
            sdks: ["android_native", "android_rn", "ios_native", "ios_rn"],
          },
          {
            id: generateId(),
            name: "debug_log",
            props: [{ name: "level", value: "debug" }],
            scope: ["logs"],
            sdks: ["android_native", "ios_native"],
          },
        ],
      },
      samplingConfig: {
        default: { session_sample_rate: 0.5 },
        rules: [
          {
            id: generateId(),
            name: "high_value_users",
            match: {
              type: "app_version_min",
              sdks: ["android_native", "ios_native"],
              app_version_min_inclusive: "2.0.0",
            },
            session_sample_rate: 1.0,
          },
          {
            id: generateId(),
            name: "legacy_users",
            match: {
              type: "app_version_max",
              sdks: ["android_native", "android_rn"],
              app_version_max_inclusive: "1.5.0",
            },
            session_sample_rate: 0.1,
          },
        ],
        criticalEventPolicies: {
          alwaysSend: [
            {
              id: generateId(),
              name: "crash",
              props: [{ name: "severity", value: "critical" }],
              scope: ["traces", "logs"],
            },
            {
              id: generateId(),
              name: "payment_error",
              props: [{ name: "error_type", value: "payment.*" }],
              scope: ["traces"],
            },
            {
              id: generateId(),
              name: "auth_failure",
              props: [{ name: "error_code", value: "401|403" }],
              scope: ["traces", "logs"],
            },
          ],
        },
      },
      signals: {
        scheduleDurationMs: 5000,
        collectorUrl: "https://collector.pulse.io/v1/traces",
        attributesToDrop: ["password", "credit_card", "ssn", "auth_token"],
      },
      interaction: {
        collectorUrl: "https://collector.pulse.io/v1/interactions",
        configUrl: "https://config.pulse.io/v1/configs/latest",
        beforeInitQueueSize: 100,
      },
      featureConfigs: [
        {
          id: generateId(),
          featureName: "crash_reporting",
          session_sample_rate: 1.0,
          sdks: ["android_native", "android_rn", "ios_native", "ios_rn"],
        },
        {
          id: generateId(),
          featureName: "network_monitoring",
          session_sample_rate: 0.8,
          sdks: ["android_native", "android_rn", "ios_native", "ios_rn"],
        },
        {
          id: generateId(),
          featureName: "performance_monitoring",
          session_sample_rate: 0.6,
          sdks: ["android_native", "ios_native"],
        },
        {
          id: generateId(),
          featureName: "user_interaction_tracking",
          session_sample_rate: 0.0,
          sdks: ["android_native", "ios_native"],
        },
      ],
    };
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
    this.initializeMembersAndTenants();
  }

  private initializeMembersAndTenants(): void {
    const now = Date.now();
    const oneHour = 60 * 60 * 1000;
    const oneDay = 24 * 60 * 60 * 1000;

    // Default tenant details
    const defaultTenantId = "tenant-mock-1";
    this.mockTenants.set(defaultTenantId, {
      tenantId: defaultTenantId,
      name: "Acme Corp",
      description: "Mobile observability platform",
      tier: "enterprise",
      isActive: true,
      createdAt: new Date(now - 90 * oneDay).toISOString(),
    });

    // Tenant members (3-4 with admin/member roles)
    this.mockTenantMembers.set(defaultTenantId, [
      {
        userId: "user-rahul-1",
        email: "rahul.sharma@example.com",
        name: "Rahul Sharma",
        role: "admin",
        status: "active",
        lastLoginAt: new Date(now - 2 * oneHour).toISOString(),
      },
      {
        userId: "user-priya-2",
        email: "priya.patel@example.com",
        name: "Priya Patel",
        role: "admin",
        status: "active",
        lastLoginAt: new Date(now - 5 * oneHour).toISOString(),
      },
      {
        userId: "user-amit-3",
        email: "amit.kumar@example.com",
        name: "Amit Kumar",
        role: "member",
        status: "active",
        lastLoginAt: new Date(now - 1 * oneDay).toISOString(),
      },
      {
        userId: "user-neha-4",
        email: "neha.singh@example.com",
        name: "Neha Singh",
        role: "member",
        status: "pending",
        lastLoginAt: null,
      },
    ]);

    // Project members for default projects (2-3 per project with admin/editor/viewer)
    const proj1Members: MockMember[] = [
      {
        userId: "user-rahul-1",
        email: "rahul.sharma@example.com",
        name: "Rahul Sharma",
        role: "admin",
        status: "active",
        lastLoginAt: new Date(now - 2 * oneHour).toISOString(),
      },
      {
        userId: "user-priya-2",
        email: "priya.patel@example.com",
        name: "Priya Patel",
        role: "editor",
        status: "active",
        lastLoginAt: new Date(now - 5 * oneHour).toISOString(),
      },
      {
        userId: "user-amit-3",
        email: "amit.kumar@example.com",
        name: "Amit Kumar",
        role: "viewer",
        status: "active",
        lastLoginAt: new Date(now - 1 * oneDay).toISOString(),
      },
    ];
    this.mockProjectMembers.set("proj-mock-1", proj1Members);

    const proj2Members: MockMember[] = [
      {
        userId: "user-rahul-1",
        email: "rahul.sharma@example.com",
        name: "Rahul Sharma",
        role: "admin",
        status: "active",
        lastLoginAt: new Date(now - 2 * oneHour).toISOString(),
      },
      {
        userId: "user-neha-4",
        email: "neha.singh@example.com",
        name: "Neha Singh",
        role: "editor",
        status: "pending",
        lastLoginAt: null,
      },
    ];
    this.mockProjectMembers.set("proj-mock-2", proj2Members);

    const proj3Members: MockMember[] = [
      {
        userId: "user-priya-2",
        email: "priya.patel@example.com",
        name: "Priya Patel",
        role: "admin",
        status: "active",
        lastLoginAt: new Date(now - 5 * oneHour).toISOString(),
      },
      {
        userId: "user-amit-3",
        email: "amit.kumar@example.com",
        name: "Amit Kumar",
        role: "viewer",
        status: "active",
        lastLoginAt: new Date(now - 1 * oneDay).toISOString(),
      },
    ];
    this.mockProjectMembers.set("proj-mock-3", proj3Members);

    // Default project details
    this.mockProjects.set("proj-mock-1", {
      projectId: "proj-mock-1",
      name: "Mobile App",
      description: "Main mobile application",
      tenantId: defaultTenantId,
      isActive: true,
      createdAt: new Date(now - 60 * oneDay).toISOString(),
      createdBy: "rahul.sharma@example.com",
    });
    this.mockProjects.set("proj-mock-2", {
      projectId: "proj-mock-2",
      name: "Web Dashboard",
      description: "Web analytics dashboard",
      tenantId: defaultTenantId,
      isActive: true,
      createdAt: new Date(now - 45 * oneDay).toISOString(),
      createdBy: "priya.patel@example.com",
    });
    this.mockProjects.set("proj-mock-3", {
      projectId: "proj-mock-3",
      name: "API Services",
      description: "Backend API monitoring",
      tenantId: defaultTenantId,
      isActive: false,
      createdAt: new Date(now - 30 * oneDay).toISOString(),
      createdBy: "amit.kumar@example.com",
    });

    // Initialize API keys for default projects
    this.initializeProjectApiKeys(defaultTenantId, now);
  }

  private initializeProjectApiKeys(tenantId: string, now: number): void {
    const projects = ["proj-mock-1", "proj-mock-2", "proj-mock-3"];
    for (const projectId of projects) {
      const keys: MockApiKey[] = [
        {
          apiKeyId: this.nextApiKeyId++,
          projectId,
          displayName: "Production",
          apiKey: `pk_mock_${projectId}_${Math.random().toString(36).slice(2, 18)}`,
          isActive: true,
          expiresAt: null,
          gracePeriodEndsAt: null,
          createdBy: "rahul.sharma@example.com",
          createdAt: new Date(now - 60 * 24 * 60 * 60 * 1000).toISOString(),
          deactivatedAt: null,
          deactivatedBy: null,
          deactivationReason: null,
        },
      ];
      this.mockProjectApiKeys.set(projectId, keys);
    }
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
        description:
          "Tracks the time from when user taps the 'Join Contest' button until the contest join API responds successfully. Measures backend latency for contest participation.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - 5 * oneDay,
        updatedAt: now - 2 * oneDay,
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 30000,
        eventSequence: [
          {
            eventName: "join_contest_click",
            props: [
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
              {
                propName: "entry_fee",
                propValue: "number",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "join_contest_response",
            props: [
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
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
        description:
          "Measures the time from 'Save Team' button tap to successful team save API response. Critical for team creation experience.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - 4 * oneDay,
        updatedAt: now - 1 * oneDay,
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
              {
                propName: "error_type",
                propValue: "validation_error",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 3,
        interactionName: "PlayerSelectTap",
        description:
          "Time from player card tap to player added/removed confirmation. Ensures smooth player selection experience in team creation.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - 6 * oneDay,
        updatedAt: now - 3 * oneDay,
        uptimeLowerLimit: 30,
        uptimeUpperLimit: 200,
        uptimeMidLimit: 100,
        interactionThreshold: 10000,
        eventSequence: [
          {
            eventName: "player_tap",
            props: [
              {
                propName: "player_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "action", propValue: "select", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "player_selection_complete",
            props: [
              {
                propName: "player_id",
                propValue: "string",
                operator: "EQUALS",
              },
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
        description:
          "API call duration for fetching available contests list. Measures backend performance for contest discovery.",
        status: "RUNNING",
        createdBy: "amit.kumar@example.com",
        updatedBy: "amit.kumar@example.com",
        createdAt: now - 7 * oneDay,
        updatedAt: now - 2 * oneDay,
        uptimeLowerLimit: 80,
        uptimeUpperLimit: 600,
        uptimeMidLimit: 300,
        interactionThreshold: 20000,
        eventSequence: [
          {
            eventName: "contest_list_request",
            props: [
              { propName: "match_id", propValue: "string", operator: "EQUALS" },
              {
                propName: "filter_type",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "contest_list_response",
            props: [
              {
                propName: "contest_count",
                propValue: "number",
                operator: "EQUALS",
              },
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
        description:
          "Time from payment submit button tap to payment gateway response. Critical for revenue and user trust.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - 8 * oneDay,
        updatedAt: now - 4 * oneDay,
        uptimeLowerLimit: 200,
        uptimeUpperLimit: 2000,
        uptimeMidLimit: 1000,
        interactionThreshold: 60000,
        eventSequence: [
          {
            eventName: "payment_submit_click",
            props: [
              { propName: "amount", propValue: "number", operator: "EQUALS" },
              {
                propName: "payment_method",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "payment_gateway_response",
            props: [
              {
                propName: "transaction_id",
                propValue: "string",
                operator: "EQUALS",
              },
              { propName: "status", propValue: "success", operator: "EQUALS" },
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
        id: 6,
        interactionName: "WalletBalanceFetch",
        description:
          "API call to fetch user's current wallet balance. Frequently called action that impacts overall app responsiveness.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - 9 * oneDay,
        updatedAt: now - 5 * oneDay,
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
        description:
          "Fetches upcoming match schedule from backend. Core API for match discovery and contest browsing.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - 10 * oneDay,
        updatedAt: now - 6 * oneDay,
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 25000,
        eventSequence: [
          {
            eventName: "match_schedule_request",
            props: [
              {
                propName: "sport_type",
                propValue: "string",
                operator: "EQUALS",
              },
              {
                propName: "date_range",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "match_schedule_response",
            props: [
              {
                propName: "match_count",
                propValue: "number",
                operator: "EQUALS",
              },
              { propName: "status", propValue: "success", operator: "EQUALS" },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [
          {
            eventName: "api_timeout",
            props: [
              {
                propName: "timeout_ms",
                propValue: "number",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: true,
          },
        ],
      },
      {
        id: 8,
        interactionName: "LeaderboardRefreshTap",
        description:
          "Time to refresh and display updated leaderboard data when user pulls to refresh or taps refresh.",
        status: "RUNNING",
        createdBy: "amit.kumar@example.com",
        updatedBy: "amit.kumar@example.com",
        createdAt: now - 11 * oneDay,
        updatedAt: now - 7 * oneDay,
        uptimeLowerLimit: 80,
        uptimeUpperLimit: 600,
        uptimeMidLimit: 300,
        interactionThreshold: 20000,
        eventSequence: [
          {
            eventName: "leaderboard_refresh_tap",
            props: [
              {
                propName: "contest_id",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "leaderboard_data_loaded",
            props: [
              {
                propName: "rank_count",
                propValue: "number",
                operator: "EQUALS",
              },
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
        description:
          "Time from profile save button click to successful profile update confirmation.",
        status: "RUNNING",
        createdBy: "rahul.sharma@example.com",
        updatedBy: "rahul.sharma@example.com",
        createdAt: now - 12 * oneDay,
        updatedAt: now - 8 * oneDay,
        uptimeLowerLimit: 100,
        uptimeUpperLimit: 800,
        uptimeMidLimit: 400,
        interactionThreshold: 25000,
        eventSequence: [
          {
            eventName: "profile_save_click",
            props: [
              { propName: "user_id", propValue: "string", operator: "EQUALS" },
              {
                propName: "fields_updated",
                propValue: "number",
                operator: "EQUALS",
              },
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
        description:
          "Time from notification tap to destination screen fully loaded. Measures deep link navigation performance.",
        status: "RUNNING",
        createdBy: "priya.patel@example.com",
        updatedBy: "priya.patel@example.com",
        createdAt: now - 13 * oneDay,
        updatedAt: now - 9 * oneDay,
        uptimeLowerLimit: 150,
        uptimeUpperLimit: 1200,
        uptimeMidLimit: 600,
        interactionThreshold: 35000,
        eventSequence: [
          {
            eventName: "notification_tap",
            props: [
              {
                propName: "notification_id",
                propValue: "string",
                operator: "EQUALS",
              },
              {
                propName: "notification_type",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "destination_screen_loaded",
            props: [
              {
                propName: "screen_name",
                propValue: "string",
                operator: "EQUALS",
              },
              {
                propName: "load_complete",
                propValue: "true",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
        ],
        globalBlacklistedEvents: [],
      },
      {
        id: 11,
        interactionName: "FilterApplyTap",
        description:
          "Time from filter apply button tap to filtered results displayed. Measures filter query performance.",
        status: "RUNNING",
        createdBy: "amit.kumar@example.com",
        updatedBy: "amit.kumar@example.com",
        createdAt: now - 14 * oneDay,
        updatedAt: now - 10 * oneDay,
        uptimeLowerLimit: 60,
        uptimeUpperLimit: 500,
        uptimeMidLimit: 250,
        interactionThreshold: 18000,
        eventSequence: [
          {
            eventName: "filter_apply_tap",
            props: [
              {
                propName: "filter_type",
                propValue: "string",
                operator: "EQUALS",
              },
              {
                propName: "filter_values",
                propValue: "string",
                operator: "EQUALS",
              },
            ],
            isBlacklisted: false,
          },
          {
            eventName: "filtered_results_displayed",
            props: [
              {
                propName: "result_count",
                propValue: "number",
                operator: "EQUALS",
              },
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
        description:
          "API call to fetch real-time match scores during live matches. High-frequency call during peak hours.",
        status: "RUNNING",
        createdBy: "neha.singh@example.com",
        updatedBy: "neha.singh@example.com",
        createdAt: now - 15 * oneDay,
        updatedAt: now - 11 * oneDay,
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
              {
                propName: "score_data",
                propValue: "object",
                operator: "EQUALS",
              },
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
          {
            alias: "A",
            metric: "DURATION_P99",
            metric_operator: "GREATER_THAN",
            threshold: {
              PaymentSubmit: 4000,
              PaymentConfirm: 3500,
              PaymentOTP: 3000,
            },
          },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 1,
        notification_channel_id: 1,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 7 * 86400000).toISOString(),
        updated_at: new Date(now - 3600000).toISOString(),
        is_active: true,
        status: "FIRING",
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
          {
            alias: "A",
            metric: "ERROR_RATE",
            metric_operator: "GREATER_THAN",
            threshold: {
              "post_https://api.fancode.com/v1/checkout/initiate": 0.05,
              "post_https://api.fancode.com/v1/checkout/confirm": 0.03,
            },
          },
          {
            alias: "B",
            metric: "DURATION_P99",
            metric_operator: "GREATER_THAN",
            threshold: {
              "post_https://api.fancode.com/v1/checkout/initiate": 3000,
              "post_https://api.fancode.com/v1/checkout/confirm": 2500,
            },
          },
          {
            alias: "C",
            metric: "NET_5XX_RATE",
            metric_operator: "GREATER_THAN",
            threshold: {
              "post_https://api.fancode.com/v1/checkout/initiate": 0.01,
            },
          },
        ],
        condition_expression: "A && B || C",
        evaluation_period: 900,
        evaluation_interval: 120,
        severity_id: 1,
        notification_channel_id: 1,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/xxx",
        created_by: "john@example.com",
        updated_by: "john@example.com",
        created_at: new Date(now - 14 * 86400000).toISOString(),
        updated_at: new Date(now - 7200000).toISOString(),
        is_active: true,
        status: "NORMAL",
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
          {
            alias: "A",
            metric: "CRASH_RATE",
            metric_operator: "GREATER_THAN",
            threshold: { Android: 0.02, iOS: 0.015 },
          },
        ],
        condition_expression: "A",
        evaluation_period: 1800,
        evaluation_interval: 300,
        severity_id: 1,
        notification_channel_id: 3,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/yyy",
        created_by: "admin@example.com",
        updated_by: "admin@example.com",
        created_at: new Date(now - 30 * 86400000).toISOString(),
        updated_at: new Date(now - 86400000).toISOString(),
        is_active: true,
        status: "FIRING",
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
          {
            alias: "A",
            metric: "SCREEN_LOAD_TIME_P95",
            metric_operator: "GREATER_THAN",
            threshold: {
              HomeScreen: 3000,
              ProductListScreen: 2500,
              CategoryScreen: 2000,
            },
          },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 1,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 21 * 86400000).toISOString(),
        updated_at: new Date(now - 43200000).toISOString(),
        is_active: true,
        status: "NORMAL",
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
          {
            alias: "A",
            metric: "INTERACTION_ERROR_COUNT",
            metric_operator: "GREATER_THAN",
            threshold: { LoginSubmit: 100, OTPVerify: 50, BiometricAuth: 30 },
          },
        ],
        condition_expression: "A",
        evaluation_period: 300,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 2,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/services/yyy",
        created_by: "john@example.com",
        updated_by: "john@example.com",
        created_at: new Date(now - 10 * 86400000).toISOString(),
        updated_at: new Date(now - 21600000).toISOString(),
        is_active: true,
        status: "NORMAL",
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
          {
            alias: "A",
            metric: "DURATION_P99",
            metric_operator: "GREATER_THAN",
            threshold: {
              "get_https://api.fancode.com/v1/search/products": 2000,
              "get_https://api.fancode.com/v1/search/suggest": 500,
            },
          },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 1,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 5 * 86400000).toISOString(),
        updated_at: new Date(now - 18000000).toISOString(),
        is_active: true,
        status: "FIRING",
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
          {
            alias: "A",
            metric: "ANR_RATE",
            metric_operator: "GREATER_THAN",
            threshold: { Android: 0.01, iOS: 0.005 },
          },
        ],
        condition_expression: "A",
        evaluation_period: 1200,
        evaluation_interval: 180,
        severity_id: 2,
        notification_channel_id: 2,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/services/yyy",
        created_by: "admin@example.com",
        updated_by: "admin@example.com",
        created_at: new Date(now - 25 * 86400000).toISOString(),
        updated_at: new Date(now - 172800000).toISOString(),
        is_active: true,
        status: "NORMAL",
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
          {
            alias: "A",
            metric: "APDEX",
            metric_operator: "LESS_THAN",
            threshold: {
              AddToCart: 0.85,
              UpdateCart: 0.9,
              RemoveFromCart: 0.88,
            },
          },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 3,
        notification_channel_id: 4,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/services/aaa",
        created_by: "john@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 3 * 86400000).toISOString(),
        updated_at: new Date(now - 86400000).toISOString(),
        is_active: true,
        status: "NORMAL",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 9,
        name: "Payment Gateway - Snoozed for Maintenance",
        description: "Payment API latency - temporarily snoozed",
        scope: "network_api",
        dimension_filter: null,
        alerts: [
          {
            alias: "A",
            metric: "DURATION_P99",
            metric_operator: "GREATER_THAN",
            threshold: {
              "post_https://api.fancode.com/v1/payment/process": 5000,
              "post_https://api.fancode.com/v1/payment/verify": 3000,
            },
          },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 1,
        notification_channel_id: 1,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/xxx",
        created_by: "admin@example.com",
        updated_by: "admin@example.com",
        created_at: new Date(now - 15 * 86400000).toISOString(),
        updated_at: new Date(now - 3600000).toISOString(),
        is_active: true,
        status: "SNOOZED",
        is_snoozed: true,
        last_snoozed_at: new Date(now - 3600000).toISOString(),
        snoozed_from: new Date(now - 3600000).toISOString(),
        snoozed_until: new Date(now + 7200000).toISOString(),
      },
      {
        alert_id: 10,
        name: "New Feature - Beta Testing",
        description: "Monitoring new feature rollout - awaiting data",
        scope: "interaction",
        dimension_filter: null,
        alerts: [
          {
            alias: "A",
            metric: "INTERACTION_COUNT",
            metric_operator: "LESS_THAN",
            threshold: { NewFeatureButton: 10 },
          },
        ],
        condition_expression: "A",
        evaluation_period: 1800,
        evaluation_interval: 300,
        severity_id: 3,
        notification_channel_id: 2,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/services/yyy",
        created_by: "john@example.com",
        updated_by: "john@example.com",
        created_at: new Date(now - 86400000).toISOString(),
        updated_at: new Date(now - 86400000).toISOString(),
        is_active: true,
        status: "NO_DATA",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
      {
        alert_id: 11,
        name: "Profile Screen - Snoozed Alert",
        description: "Profile screen performance - under investigation",
        scope: "screen",
        dimension_filter: null,
        alerts: [
          {
            alias: "A",
            metric: "SCREEN_LOAD_TIME_P95",
            metric_operator: "GREATER_THAN",
            threshold: { ProfileScreen: 4000 },
          },
        ],
        condition_expression: "A",
        evaluation_period: 600,
        evaluation_interval: 60,
        severity_id: 2,
        notification_channel_id: 1,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/xxx",
        created_by: "chirag@example.com",
        updated_by: "chirag@example.com",
        created_at: new Date(now - 7 * 86400000).toISOString(),
        updated_at: new Date(now - 7200000).toISOString(),
        is_active: true,
        status: "SNOOZED",
        is_snoozed: true,
        last_snoozed_at: new Date(now - 7200000).toISOString(),
        snoozed_from: new Date(now - 7200000).toISOString(),
        snoozed_until: new Date(now + 10800000).toISOString(),
      },
      {
        alert_id: 12,
        name: "Experimental API - No Data Yet",
        description: "New API endpoint monitoring - pending data",
        scope: "network_api",
        dimension_filter: null,
        alerts: [
          {
            alias: "A",
            metric: "ERROR_RATE",
            metric_operator: "GREATER_THAN",
            threshold: { "get_https://api.fancode.com/v2/experimental": 0.01 },
          },
        ],
        condition_expression: "A",
        evaluation_period: 900,
        evaluation_interval: 120,
        severity_id: 3,
        notification_channel_id: 2,
        notification_type: "slack",
        notification_config: "https://hooks.slack.com/services/yyy",
        created_by: "admin@example.com",
        updated_by: "admin@example.com",
        created_at: new Date(now - 2 * 86400000).toISOString(),
        updated_at: new Date(now - 2 * 86400000).toISOString(),
        is_active: true,
        status: "NO_DATA",
        is_snoozed: false,
        last_snoozed_at: null,
        snoozed_from: null,
        snoozed_until: null,
      },
    ];
  }

  private initializeAnalytics(): void {
    const now = new Date();
    const dataPoints = [];

    for (let i = 0; i < 30; i++) {
      const timestamp = new Date(now.getTime() - i * 60000);
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

  // ============================================================================
  // Auth / Tenant / Onboarding (for mock server Phase 1)
  // ============================================================================

  getCurrentTenant(): MockTenantContext | null {
    return this.currentTenant;
  }

  setCurrentTenant(tenant: MockTenantContext | null): void {
    this.currentTenant = tenant;
  }

  // ============================================================================
  // Tenant / Project / Member operations (Phase 2 mock endpoints)
  // ============================================================================

  getTenant(tenantId: string): MockTenantDetails | null {
    const stored = this.mockTenants.get(tenantId);
    if (stored) return stored;
    // Derive from current tenant (e.g. onboarding-created)
    const tenant = this.currentTenant;
    if (tenant && tenant.tenantId === tenantId) {
      return {
        tenantId,
        name: tenant.tenantName,
        description: "",
        tier: "enterprise",
        isActive: true,
        createdAt: new Date().toISOString(),
      };
    }
    return null;
  }

  ensureTenantExists(tenantId: string, name: string): void {
    if (!this.mockTenants.has(tenantId)) {
      this.mockTenants.set(tenantId, {
        tenantId,
        name,
        description: "",
        tier: "free",
        isActive: true,
        createdAt: new Date().toISOString(),
      });
      this.mockTenantMembers.set(tenantId, []);
    }
  }

  getTenantMembers(tenantId: string): MockMember[] {
    return this.mockTenantMembers.get(tenantId) ?? [];
  }

  getProject(projectId: string): MockProjectDetails | null {
    const stored = this.mockProjects.get(projectId);
    if (stored) return stored;
    // Derive from current tenant (e.g. onboarding-created)
    const tenant = this.currentTenant;
    if (tenant) {
      const proj = tenant.projects.find((p) => p.projectId === projectId);
      if (proj) {
        return {
          projectId: proj.projectId,
          name: proj.name,
          description: proj.description,
          tenantId: tenant.tenantId,
          isActive: proj.isActive,
          createdAt: new Date().toISOString(),
          createdBy: "unknown",
        };
      }
    }
    return null;
  }

  getProjectMembers(projectId: string): MockMember[] {
    return this.mockProjectMembers.get(projectId) ?? [];
  }

  createProject(
    tenantId: string,
    name: string,
    description: string,
    createdBy: string,
  ): MockProjectDetails & { apiKey: string } {
    const projectId = "proj-" + Math.random().toString(36).slice(2, 11);
    const apiKey = "pk_mock_" + Math.random().toString(36).slice(2, 18);
    const now = new Date().toISOString();

    const project: MockProjectDetails & { apiKey: string } = {
      projectId,
      name,
      description: description ?? "",
      tenantId,
      apiKey,
      isActive: true,
      createdAt: now,
      createdBy,
    };

    this.mockProjects.set(projectId, { ...project });
    this.mockProjectApiKeys.set(projectId, [
      {
        apiKeyId: this.nextApiKeyId++,
        projectId,
        displayName: "Default",
        apiKey,
        isActive: true,
        expiresAt: null,
        gracePeriodEndsAt: null,
        createdBy,
        createdAt: now,
        deactivatedAt: null,
        deactivatedBy: null,
        deactivationReason: null,
      },
    ]);
    this.mockProjectMembers.set(projectId, [
      {
        userId: "user-" + createdBy.replace(/[^a-z0-9]/gi, "-"),
        email: createdBy,
        name: createdBy.split("@")[0].replace(/\./g, " "),
        role: "admin",
        status: "active",
        lastLoginAt: now,
      },
    ]);

    // Add to current tenant's projects if it matches
    const tenant = this.currentTenant;
    if (tenant && tenant.tenantId === tenantId) {
      tenant.projects.push({
        projectId,
        name,
        description: description ?? "",
        isActive: true,
        role: "admin",
      });
    }

    // Ensure tenant exists in mockTenants
    if (!this.mockTenants.has(tenantId)) {
      const tenantName =
        tenant?.tenantName ?? "Organization " + tenantId.slice(-6);
      this.mockTenants.set(tenantId, {
        tenantId,
        name: tenantName,
        description: "",
        tier: "enterprise",
        isActive: true,
        createdAt: now,
      });
      this.mockTenantMembers.set(tenantId, []);
    }

    return project;
  }

  addTenantMember(
    tenantId: string,
    email: string,
    role: "admin" | "member",
    name?: string,
  ): MockMember {
    const members = this.mockTenantMembers.get(tenantId) ?? [];
    const userId = "user-" + Math.random().toString(36).slice(2, 11);
    const member: MockMember = {
      userId,
      email,
      name: name ?? email.split("@")[0].replace(/\./g, " "),
      role,
      status: "pending",
      lastLoginAt: null,
    };
    members.push(member);
    this.mockTenantMembers.set(tenantId, members);
    return member;
  }

  addProjectMember(
    projectId: string,
    email: string,
    role: "admin" | "editor" | "viewer",
    name?: string,
  ): MockMember {
    const members = this.mockProjectMembers.get(projectId) ?? [];
    const userId = "user-" + Math.random().toString(36).slice(2, 11);
    const member: MockMember = {
      userId,
      email,
      name: name ?? email.split("@")[0].replace(/\./g, " "),
      role,
      status: "pending",
      lastLoginAt: null,
    };
    members.push(member);
    this.mockProjectMembers.set(projectId, members);
    return member;
  }

  hasTenantMember(tenantId: string, email: string): boolean {
    const members = this.mockTenantMembers.get(tenantId) ?? [];
    return members.some((m) => m.email.toLowerCase() === email.toLowerCase());
  }

  hasProjectMember(projectId: string, email: string): boolean {
    const members = this.mockProjectMembers.get(projectId) ?? [];
    return members.some((m) => m.email.toLowerCase() === email.toLowerCase());
  }

  removeTenantMember(tenantId: string, userId: string): boolean {
    const members = this.mockTenantMembers.get(tenantId) ?? [];
    const idx = members.findIndex((m) => m.userId === userId);
    if (idx === -1) return false;
    members.splice(idx, 1);
    this.mockTenantMembers.set(tenantId, members);
    return true;
  }

  removeProjectMember(projectId: string, userId: string): boolean {
    const members = this.mockProjectMembers.get(projectId) ?? [];
    const idx = members.findIndex((m) => m.userId === userId);
    if (idx === -1) return false;
    members.splice(idx, 1);
    this.mockProjectMembers.set(projectId, members);
    return true;
  }

  updateTenantMemberRole(
    tenantId: string,
    userId: string,
    newRole: string,
  ): MockMember | null {
    const members = this.mockTenantMembers.get(tenantId) ?? [];
    const member = members.find((m) => m.userId === userId);
    if (!member) return null;
    member.role = newRole;
    return member;
  }

  updateProjectMemberRole(
    projectId: string,
    userId: string,
    newRole: string,
  ): MockMember | null {
    const members = this.mockProjectMembers.get(projectId) ?? [];
    const member = members.find((m) => m.userId === userId);
    if (!member) return null;
    member.role = newRole;
    return member;
  }

  updateProject(
    projectId: string,
    updates: { name?: string; description?: string; isActive?: boolean },
  ): MockProjectDetails | null {
    const project = this.mockProjects.get(projectId);
    if (!project) return null;
    if (updates.name !== undefined) project.name = updates.name;
    if (updates.description !== undefined)
      project.description = updates.description;
    if (updates.isActive !== undefined) project.isActive = updates.isActive;
    return project;
  }

  updateTenant(
    tenantId: string,
    updates: { name?: string; description?: string },
  ): MockTenantDetails | null {
    const tenant = this.mockTenants.get(tenantId);
    if (!tenant) return null;
    if (updates.name !== undefined) tenant.name = updates.name;
    if (updates.description !== undefined)
      tenant.description = updates.description;
    return tenant;
  }

  getProjectApiKeys(projectId: string): MockApiKey[] {
    const keys = this.mockProjectApiKeys.get(projectId) ?? [];
    return keys.filter((k) => k.isActive);
  }

  addProjectApiKey(
    projectId: string,
    displayName: string,
    createdBy: string,
  ): MockApiKey & { rawKey: string } {
    const rawKey =
      "pk_mock_" + projectId + "_" + Math.random().toString(36).slice(2, 18);
    const key: MockApiKey = {
      apiKeyId: this.nextApiKeyId++,
      projectId,
      displayName,
      apiKey: rawKey,
      isActive: true,
      expiresAt: null,
      gracePeriodEndsAt: null,
      createdBy,
      createdAt: new Date().toISOString(),
      deactivatedAt: null,
      deactivatedBy: null,
      deactivationReason: null,
    };
    const keys = this.mockProjectApiKeys.get(projectId) ?? [];
    keys.push(key);
    this.mockProjectApiKeys.set(projectId, keys);
    return { ...key, rawKey };
  }

  revokeProjectApiKey(
    projectId: string,
    apiKeyId: number,
    revokedBy: string,
  ): boolean {
    const keys = this.mockProjectApiKeys.get(projectId) ?? [];
    const key = keys.find((k) => k.apiKeyId === apiKeyId);
    if (!key || !key.isActive) return false;
    key.isActive = false;
    key.deactivatedAt = new Date().toISOString();
    key.deactivatedBy = revokedBy;
    key.deactivationReason = "Revoked by user";
    return true;
  }

  getProjectSettings(projectId: string): MockProjectSettings {
    const stored = this.mockProjectSettings.get(projectId);
    if (stored) return stored;
    return {
      retentionDays: 90,
      samplingRate: 0.5,
    };
  }

  updateProjectSettings(
    projectId: string,
    settings: Partial<MockProjectSettings>,
  ): MockProjectSettings {
    const current = this.getProjectSettings(projectId);
    const updated = { ...current, ...settings };
    this.mockProjectSettings.set(projectId, updated);
    return updated;
  }

  lookupTenantByDomain(domain: string): MockTenantDetails | null {
    const defaultTenant = this.mockTenants.get("tenant-mock-1");
    if (!defaultTenant) return null;
    return {
      ...defaultTenant,
      tenantId: defaultTenant.tenantId,
      name: defaultTenant.name,
    };
  }

  /** Default mock tenant for dev-id-token login */
  getDefaultMockTenant(): MockTenantContext {
    return {
      tenantId: "tenant-mock-1",
      tenantName: "Acme Corp",
      projects: [
        {
          projectId: "proj-mock-1",
          name: "Mobile App",
          description: "Main mobile application",
          isActive: true,
          role: "admin",
        },
        {
          projectId: "proj-mock-2",
          name: "Web Dashboard",
          description: "Web analytics dashboard",
          isActive: true,
          role: "admin",
        },
        {
          projectId: "proj-mock-3",
          name: "API Services",
          description: "Backend API monitoring",
          isActive: false,
          role: "admin",
        },
      ],
    };
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
    return this.data.jobs.find(
      (job) => job.interactionName === interactionName,
    );
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

  getEventDefinitions(params: {
    search?: string;
    category?: string;
    limit?: number;
    offset?: number;
  }): { eventDefinitions: MockEventDefinition[]; totalCount: number } {
    let filtered = this.eventDefinitions.filter((d) => !d.isArchived);

    if (params.search) {
      const s = params.search.toLowerCase();
      filtered = filtered.filter(
        (d) =>
          d.eventName.toLowerCase().includes(s) ||
          d.displayName.toLowerCase().includes(s) ||
          d.description.toLowerCase().includes(s),
      );
    }

    if (params.category) {
      filtered = filtered.filter((d) => d.category === params.category);
    }

    const limit = params.limit ?? 50;
    const offset = params.offset ?? 0;
    return {
      eventDefinitions: filtered.slice(offset, offset + limit),
      totalCount: filtered.length,
    };
  }

  getEventDefinitionById(id: number): MockEventDefinition | undefined {
    return this.eventDefinitions.find((d) => d.id === id);
  }

  addEventDefinition(def: Omit<MockEventDefinition, "id" | "createdAt" | "updatedAt" | "isArchived">): MockEventDefinition {
    const now = new Date().toISOString();
    const newDef: MockEventDefinition = {
      ...def,
      id: getNextEventDefId(),
      isArchived: false,
      createdAt: now,
      updatedAt: now,
      attributes: (def.attributes || []).map((a, i) => ({
        ...a,
        id: Date.now() + i,
        isArchived: false,
      })),
    };
    this.eventDefinitions.push(newDef);
    return newDef;
  }

  updateEventDefinition(id: number, updates: Partial<MockEventDefinition>): MockEventDefinition | null {
    const idx = this.eventDefinitions.findIndex((d) => d.id === id);
    if (idx === -1) return null;
    this.eventDefinitions[idx] = {
      ...this.eventDefinitions[idx],
      ...updates,
      updatedAt: new Date().toISOString(),
    };
    if (updates.attributes) {
      this.eventDefinitions[idx].attributes = updates.attributes.map((a, i) => ({
        ...a,
        id: a.id || Date.now() + i,
        isArchived: a.isArchived ?? false,
      }));
    }
    return this.eventDefinitions[idx];
  }

  archiveEventDefinition(id: number): boolean {
    const idx = this.eventDefinitions.findIndex((d) => d.id === id);
    if (idx === -1) return false;
    this.eventDefinitions[idx] = {
      ...this.eventDefinitions[idx],
      isArchived: true,
      updatedAt: new Date().toISOString(),
    };
    return true;
  }

  getEventDefinitionCategories(): string[] {
    const cats = new Set(
      this.eventDefinitions
        .filter((d) => !d.isArchived && d.category)
        .map((d) => d.category),
    );
    return Array.from(cats).sort();
  }

  // Initialize config history with mock data
  private initializeConfigHistory(): PulseConfigWithMeta[] {
    const now = Date.now();
    const baseConfig = this.getDefaultSdkConfig();

    // Create historical versions
    const history: PulseConfigWithMeta[] = [
      {
        ...baseConfig,
        version: 1,
        _meta: {
          version: 1,
          createdAt: new Date(now - 30 * 24 * 60 * 60 * 1000).toISOString(),
          createdBy: "admin@example.com",
          description: "Initial configuration",
          isActive: false,
        },
      },
      {
        ...baseConfig,
        version: 2,
        samplingConfig: {
          ...baseConfig.samplingConfig,
          default: { session_sample_rate: 0.3 },
        },
        _meta: {
          version: 2,
          createdAt: new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString(),
          createdBy: "admin@example.com",
          description: "Added blacklist filters for sensitive data",
          isActive: false,
        },
      },
      {
        ...baseConfig,
        version: 3,
        samplingConfig: {
          ...baseConfig.samplingConfig,
          default: { session_sample_rate: 0.5 },
        },
        _meta: {
          version: 3,
          createdAt: new Date(now - 3 * 24 * 60 * 60 * 1000).toISOString(),
          createdBy: "john.doe@example.com",
          description: "Reduced default sample rate to 50%",
          isActive: false,
        },
      },
      {
        ...baseConfig,
        version: 4,
        _meta: {
          version: 4,
          createdAt: new Date(now - 24 * 60 * 60 * 1000).toISOString(),
          createdBy: "jane.smith@example.com",
          description: "Added payment_error to critical events",
          isActive: false,
        },
      },
      {
        ...baseConfig,
        version: 5,
        _meta: {
          version: 5,
          createdAt: new Date(now - 2 * 60 * 60 * 1000).toISOString(),
          createdBy: "john.doe@example.com",
          description: "Increased crash reporting sample rate",
          isActive: true,
        },
      },
    ];

    return history;
  }

  // SDK Configuration methods
  getSdkConfig(): PulseConfig {
    // Return the active version
    const activeConfig = this.configHistory.find((c) => c._meta.isActive);
    if (activeConfig) {
      const { _meta, ...config } = activeConfig;
      return JSON.parse(JSON.stringify(config));
    }
    return JSON.parse(JSON.stringify(this.sdkConfig));
  }

  getSdkConfigVersions(): ConfigVersionMeta[] {
    return this.configHistory
      .map((c) => c._meta)
      .sort((a, b) => b.version - a.version);
  }

  getSdkConfigByVersion(version: number): PulseConfig | null {
    const config = this.configHistory.find((c) => c.version === version);
    if (config) {
      const { _meta, ...configWithoutMeta } = config;
      return JSON.parse(JSON.stringify(configWithoutMeta));
    }
    return null;
  }

  updateSdkConfig(updates: Partial<PulseConfig>): PulseConfig {
    // Deactivate current active version
    this.configHistory.forEach((c) => {
      c._meta.isActive = false;
    });

    // Calculate new version number
    const maxVersion = Math.max(...this.configHistory.map((c) => c.version));
    const newVersion = maxVersion + 1;

    // Create new config
    const newConfig: PulseConfigWithMeta = {
      ...this.sdkConfig,
      ...updates,
      version: newVersion,
      _meta: {
        version: newVersion,
        createdAt: new Date().toISOString(),
        createdBy: "current.user@example.com",
        description:
          (updates as any).description || `Configuration v${newVersion}`,
        isActive: true,
      },
    };

    // Add to history
    this.configHistory.push(newConfig);

    // Update current config
    const { _meta, ...configWithoutMeta } = newConfig;
    this.sdkConfig = configWithoutMeta;

    return JSON.parse(JSON.stringify(this.sdkConfig));
  }

  createSdkConfig(config: Partial<PulseConfig>): PulseConfig {
    const defaultConfig = this.getDefaultSdkConfig();
    const newConfig = {
      ...defaultConfig,
      ...config,
      version: 1,
    };

    // Clear history and start fresh
    this.configHistory = [
      {
        ...newConfig,
        _meta: {
          version: 1,
          createdAt: new Date().toISOString(),
          createdBy: "current.user@example.com",
          description: "New configuration",
          isActive: true,
        },
      },
    ];

    this.sdkConfig = newConfig;
    return JSON.parse(JSON.stringify(this.sdkConfig));
  }

  // ============================================================================
  // SDK Configuration V1 API Methods (New schema matching backend PulseConfig)
  // ============================================================================

  private configHistoryV1: PulseConfigV1WithMeta[] = [];

  private getDefaultConfigV1(): PulseConfigV1 {
    const generateId = () => Math.random().toString(36).substring(2, 11);

    return {
      version: 1,
      description: "Default SDK configuration",
      sampling: {
        default: { sessionSampleRate: 0.5 },
        rules: [
          {
            id: generateId(),
            name: "app_version",
            sdks: ["android_java", "ios_native"],
            value: "^2\\..*",
            sessionSampleRate: 1.0,
          },
        ],
        criticalEventPolicies: {
          alwaysSend: [
            {
              id: generateId(),
              name: "crash",
              props: [{ name: "severity", value: "critical" }],
              scopes: ["traces", "logs"],
              sdks: ["android_java", "android_rn", "ios_native", "ios_rn"],
            },
            {
              id: generateId(),
              name: "payment_error",
              props: [{ name: "error_type", value: "^payment.*" }],
              scopes: ["traces"],
              sdks: ["android_java", "ios_native"],
            },
          ],
        },
        criticalSessionPolicies: {
          alwaysSend: [],
        },
      },
      signals: {
        filters: {
          mode: "blacklist",
          values: [
            {
              id: generateId(),
              name: "^debug_.*",
              props: [{ name: "level", value: "debug" }],
              scopes: ["logs"],
              sdks: ["android_java", "ios_native"],
            },
          ],
        },
        scheduleDurationMs: 5000,
        logsCollectorUrl: "http://10.0.2.2:4318/v1/logs",
        metricCollectorUrl: "http://10.0.2.2:4318/v1/metrics",
        spanCollectorUrl: "http://10.0.2.2:4318/v1/traces",
        customEventCollectorUrl: "http://10.0.2.2:4318/v1/events",
        attributesToDrop: [
          {
            id: generateId(),
            values: ["user.email", "auth_token", "credit_card"],
            condition: {
              name: "",
              props: [],
              scopes: ["logs", "traces"],
              sdks: ["android_java", "android_rn", "ios_native", "ios_rn"],
            },
          },
          {
            id: generateId(),
            values: ["ssn", "password"],
            condition: {
              name: "^http\\.request$",
              props: [],
              scopes: ["logs", "traces", "metrics"],
              sdks: ["android_java", "android_rn", "ios_native", "ios_rn"],
            },
          },
        ],
        attributesToAdd: [],
      },
      interaction: {
        collectorUrl: "http://10.0.2.2:4318/v1/interactions/",
        configUrl: "http://10.0.2.2:8080/v1/interaction-configs/",
        beforeInitQueueSize: 100,
      },
      features: [
        {
          id: generateId(),
          featureName: "interaction",
          sessionSampleRate: 1,
          sdks: ["android_java", "android_rn", "ios_native", "ios_rn"],
        },
        {
          id: generateId(),
          featureName: "java_crash",
          sessionSampleRate: 1,
          sdks: ["android_java", "android_rn"],
        },
        {
          id: generateId(),
          featureName: "js_crash",
          sessionSampleRate: 1,
          sdks: ["android_rn", "ios_rn"],
        },
        {
          id: generateId(),
          featureName: "network_instrumentation",
          sessionSampleRate: 1,
          sdks: ["android_java", "ios_native"],
        },
        {
          id: generateId(),
          featureName: "screen_session",
          sessionSampleRate: 0,
          sdks: ["android_java", "ios_native"],
        },
        {
          id: generateId(),
          featureName: "rn_screen_load",
          sessionSampleRate: 0,
          sdks: ["android_rn", "ios_rn"],
        },
        {
          id: generateId(),
          featureName: "rn_screen_interactive",
          sessionSampleRate: 0,
          sdks: ["android_rn", "ios_rn"],
        },
      ],
    };
  }

  private initializeConfigHistoryV1(): void {
    if (this.configHistoryV1.length > 0) return;

    const defaultConfig = this.getDefaultConfigV1();
    this.configHistoryV1 = [
      {
        ...defaultConfig,
        version: 1,
        _meta: {
          version: 1,
          isactive: false,
          description: "Initial SDK configuration",
          createdBy: "admin@example.com",
          createdAt: new Date(
            Date.now() - 7 * 24 * 60 * 60 * 1000,
          ).toISOString(),
        },
      },
      {
        ...defaultConfig,
        version: 2,
        description: "Updated sampling rates",
        sampling: {
          ...defaultConfig.sampling,
          default: { sessionSampleRate: 0.75 },
        },
        _meta: {
          version: 2,
          isactive: false,
          description: "Updated sampling rates",
          createdBy: "dev@example.com",
          createdAt: new Date(
            Date.now() - 3 * 24 * 60 * 60 * 1000,
          ).toISOString(),
        },
      },
      {
        ...defaultConfig,
        version: 3,
        description: "Added new filter rules",
        _meta: {
          version: 3,
          isactive: true,
          description: "Added new filter rules",
          createdBy: "dev@example.com",
          createdAt: new Date(
            Date.now() - 1 * 24 * 60 * 60 * 1000,
          ).toISOString(),
        },
      },
    ];
  }

  getActiveConfigV1(): PulseConfigV1 | null {
    this.initializeConfigHistoryV1();
    const active = this.configHistoryV1.find((c) => c._meta.isactive);
    if (active) {
      const { _meta, ...config } = active;
      return JSON.parse(JSON.stringify(config));
    }
    return null;
  }

  getConfigByVersionV1(version: number): PulseConfigV1 | null {
    this.initializeConfigHistoryV1();
    const config = this.configHistoryV1.find((c) => c.version === version);
    if (config) {
      const { _meta, ...configWithoutMeta } = config;
      return JSON.parse(JSON.stringify(configWithoutMeta));
    }
    return null;
  }

  getAllConfigsV1(): ConfigVersionMetaV1[] {
    this.initializeConfigHistoryV1();
    return this.configHistoryV1
      .map((c) => c._meta)
      .sort((a, b) => b.version - a.version);
  }

  createConfigV1(config: Partial<PulseConfigV1>, userEmail: string): number {
    this.initializeConfigHistoryV1();

    // Deactivate all existing configs
    this.configHistoryV1.forEach((c) => {
      c._meta.isactive = false;
    });

    // Calculate new version
    const maxVersion =
      this.configHistoryV1.length > 0
        ? Math.max(...this.configHistoryV1.map((c) => c.version || 0))
        : 0;
    const newVersion = maxVersion + 1;

    // Create new config
    const defaultConfig = this.getDefaultConfigV1();
    const newConfig: PulseConfigV1WithMeta = {
      ...defaultConfig,
      ...config,
      version: newVersion,
      _meta: {
        version: newVersion,
        isactive: true,
        description: config.description || `Configuration v${newVersion}`,
        createdBy: userEmail,
        createdAt: new Date().toISOString(),
      },
    };

    this.configHistoryV1.push(newConfig);
    return newVersion;
  }
}

// ============================================================================
// V1 Config Types (matching new backend PulseConfig schema)
// ============================================================================

type SdkEnumV1 = "android_java" | "android_rn" | "ios_native" | "ios_rn";
type ScopeEnumV1 = "logs" | "traces" | "metrics" | "baggage";
type FilterModeV1 = "blacklist" | "whitelist";
type SamplingRuleNameV1 =
  | "os_version"
  | "app_version"
  | "country"
  | "platform"
  | "state"
  | "device"
  | "network";
type FeatureNameV1 =
  | "interaction"
  | "java_crash"
  | "js_crash"
  | "java_anr"
  | "network_change"
  | "network_instrumentation"
  | "screen_session"
  | "custom_events"
  | "rn_screen_load"
  | "rn_screen_interactive";

interface EventPropMatchV1 {
  name: string;
  value: string;
}

interface EventFilterV1 {
  id?: string;
  name: string;
  props: EventPropMatchV1[];
  scopes: ScopeEnumV1[];
  sdks: SdkEnumV1[];
}

interface AttributeValueV1 {
  name: string;
  value: string;
}

interface AttributeToAddV1 {
  id?: string;
  values: AttributeValueV1[];
  condition: EventFilterV1;
}

interface FilterConfigV1 {
  mode: FilterModeV1;
  values: EventFilterV1[];
}

interface SamplingRuleV1 {
  id?: string;
  name: SamplingRuleNameV1;
  sdks: SdkEnumV1[];
  value: string;
  sessionSampleRate: number;
}

interface CriticalPolicyRuleV1 {
  id?: string;
  name: string;
  props: EventPropMatchV1[];
  scopes: ScopeEnumV1[];
  sdks: SdkEnumV1[];
}

interface SamplingConfigV1 {
  default: { sessionSampleRate: number };
  rules: SamplingRuleV1[];
  criticalEventPolicies: { alwaysSend: CriticalPolicyRuleV1[] };
  criticalSessionPolicies: { alwaysSend: CriticalPolicyRuleV1[] };
}

interface AttributeToDropV1 {
  id?: string;
  values: string[];
  condition: EventFilterV1;
}

interface SignalsConfigV1 {
  filters: FilterConfigV1;
  scheduleDurationMs: number;
  logsCollectorUrl?: string;
  metricCollectorUrl?: string;
  spanCollectorUrl?: string;
  customEventCollectorUrl?: string;
  attributesToDrop: AttributeToDropV1[];
  attributesToAdd?: AttributeToAddV1[];
}

interface InteractionConfigV1 {
  collectorUrl?: string;
  configUrl?: string;
  beforeInitQueueSize: number;
}

interface FeatureConfigV1 {
  id?: string;
  featureName: FeatureNameV1;
  sessionSampleRate: number;
  sdks: SdkEnumV1[];
}

interface PulseConfigV1 {
  version?: number;
  description: string;
  sampling: SamplingConfigV1;
  signals: SignalsConfigV1;
  interaction: InteractionConfigV1;
  features: FeatureConfigV1[];
}

interface ConfigVersionMetaV1 {
  version: number;
  isactive: boolean;
  description: string;
  createdBy: string;
  createdAt: string;
}

interface PulseConfigV1WithMeta extends PulseConfigV1 {
  _meta: ConfigVersionMetaV1;
}
