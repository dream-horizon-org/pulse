/**
 * Mock Data Store
 *
 * Centralized data store for mock responses
 * Maintains state across API calls for realistic behavior
 */

import { MockDataStore as IMockDataStore } from "./types";

// SDK Config types matching the PulseConfig schema
type SdkEnum = 'android_native' | 'android_rn' | 'ios_native' | 'ios_rn';
type ScopeEnum = 'logs' | 'traces' | 'metrics' | 'baggage';
type FilterMode = 'blacklist' | 'whitelist';
type SamplingMatchType = 'app_version_min' | 'app_version_max';

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
  enabled: boolean;
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

export class MockDataStore {
  private static instance: MockDataStore;
  private data: IMockDataStore;
  private sdkConfig: PulseConfig;
  private configHistory: PulseConfigWithMeta[];

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
    this.initializeData();
  }

  private getDefaultSdkConfig(): PulseConfig {
    const generateId = () => Math.random().toString(36).substring(2, 11);
    
    return {
      version: 1,
      filtersConfig: {
        mode: 'blacklist',
        whitelist: [
          {
            id: generateId(),
            name: 'test_event',
            props: [{ name: 'user_id', value: '.*test.*' }],
            scope: ['logs', 'traces'],
            sdks: ['android_native', 'ios_native'],
          },
        ],
        blacklist: [
          {
            id: generateId(),
            name: 'sensitive_event',
            props: [{ name: 'contains_pii', value: 'true' }],
            scope: ['logs', 'traces', 'metrics'],
            sdks: ['android_native', 'android_rn', 'ios_native', 'ios_rn'],
          },
          {
            id: generateId(),
            name: 'debug_log',
            props: [{ name: 'level', value: 'debug' }],
            scope: ['logs'],
            sdks: ['android_native', 'ios_native'],
          },
        ],
      },
      samplingConfig: {
        default: { session_sample_rate: 0.5 },
        rules: [
          {
            id: generateId(),
            name: 'high_value_users',
            match: {
              type: 'app_version_min',
              sdks: ['android_native', 'ios_native'],
              app_version_min_inclusive: '2.0.0',
            },
            session_sample_rate: 1.0,
          },
          {
            id: generateId(),
            name: 'legacy_users',
            match: {
              type: 'app_version_max',
              sdks: ['android_native', 'android_rn'],
              app_version_max_inclusive: '1.5.0',
            },
            session_sample_rate: 0.1,
          },
        ],
        criticalEventPolicies: {
          alwaysSend: [
            {
              id: generateId(),
              name: 'crash',
              props: [{ name: 'severity', value: 'critical' }],
              scope: ['traces', 'logs'],
            },
            {
              id: generateId(),
              name: 'payment_error',
              props: [{ name: 'error_type', value: 'payment.*' }],
              scope: ['traces'],
            },
            {
              id: generateId(),
              name: 'auth_failure',
              props: [{ name: 'error_code', value: '401|403' }],
              scope: ['traces', 'logs'],
            },
          ],
        },
      },
      signals: {
        scheduleDurationMs: 5000,
        collectorUrl: 'https://collector.pulse.io/v1/traces',
        attributesToDrop: ['password', 'credit_card', 'ssn', 'auth_token'],
      },
      interaction: {
        collectorUrl: 'https://collector.pulse.io/v1/interactions',
        configUrl: 'https://config.pulse.io/v1/configs/latest',
        beforeInitQueueSize: 100,
      },
      featureConfigs: [
        {
          id: generateId(),
          featureName: 'crash_reporting',
          enabled: true,
          session_sample_rate: 1.0,
          sdks: ['android_native', 'android_rn', 'ios_native', 'ios_rn'],
        },
        {
          id: generateId(),
          featureName: 'network_monitoring',
          enabled: true,
          session_sample_rate: 0.8,
          sdks: ['android_native', 'android_rn', 'ios_native', 'ios_rn'],
        },
        {
          id: generateId(),
          featureName: 'performance_monitoring',
          enabled: true,
          session_sample_rate: 0.6,
          sdks: ['android_native', 'ios_native'],
        },
        {
          id: generateId(),
          featureName: 'user_interaction_tracking',
          enabled: false,
          session_sample_rate: 0.3,
          sdks: ['android_native', 'ios_native'],
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
          createdBy: 'admin@example.com',
          description: 'Initial configuration',
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
          createdBy: 'admin@example.com',
          description: 'Added blacklist filters for sensitive data',
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
          createdBy: 'john.doe@example.com',
          description: 'Reduced default sample rate to 50%',
          isActive: false,
        },
      },
      {
        ...baseConfig,
        version: 4,
        _meta: {
          version: 4,
          createdAt: new Date(now - 24 * 60 * 60 * 1000).toISOString(),
          createdBy: 'jane.smith@example.com',
          description: 'Added payment_error to critical events',
          isActive: false,
        },
      },
      {
        ...baseConfig,
        version: 5,
        _meta: {
          version: 5,
          createdAt: new Date(now - 2 * 60 * 60 * 1000).toISOString(),
          createdBy: 'john.doe@example.com',
          description: 'Increased crash reporting sample rate',
          isActive: true,
        },
      },
    ];
    
    return history;
  }

  // SDK Configuration methods
  getSdkConfig(): PulseConfig {
    // Return the active version
    const activeConfig = this.configHistory.find(c => c._meta.isActive);
    if (activeConfig) {
      const { _meta, ...config } = activeConfig;
      return JSON.parse(JSON.stringify(config));
    }
    return JSON.parse(JSON.stringify(this.sdkConfig));
  }

  getSdkConfigVersions(): ConfigVersionMeta[] {
    return this.configHistory
      .map(c => c._meta)
      .sort((a, b) => b.version - a.version);
  }

  getSdkConfigByVersion(version: number): PulseConfig | null {
    const config = this.configHistory.find(c => c.version === version);
    if (config) {
      const { _meta, ...configWithoutMeta } = config;
      return JSON.parse(JSON.stringify(configWithoutMeta));
    }
    return null;
  }

  updateSdkConfig(updates: Partial<PulseConfig>): PulseConfig {
    // Deactivate current active version
    this.configHistory.forEach(c => {
      c._meta.isActive = false;
    });
    
    // Calculate new version number
    const maxVersion = Math.max(...this.configHistory.map(c => c.version));
    const newVersion = maxVersion + 1;
    
    // Create new config
    const newConfig: PulseConfigWithMeta = {
      ...this.sdkConfig,
      ...updates,
      version: newVersion,
      _meta: {
        version: newVersion,
        createdAt: new Date().toISOString(),
        createdBy: 'current.user@example.com',
        description: (updates as any).description || `Configuration v${newVersion}`,
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
    this.configHistory = [{
      ...newConfig,
      _meta: {
        version: 1,
        createdAt: new Date().toISOString(),
        createdBy: 'current.user@example.com',
        description: 'New configuration',
        isActive: true,
      },
    }];
    
    this.sdkConfig = newConfig;
    return JSON.parse(JSON.stringify(this.sdkConfig));
  }
}
