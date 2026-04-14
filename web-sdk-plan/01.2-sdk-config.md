# 01.5 — SDK Config (Remote Config)

**Goal:** Fetch and apply server-driven configuration that controls session sampling, per-feature enable/disable, signal filtering, attribute manipulation, and collector endpoint overrides — the direct TypeScript port of the Android `PulseSdkConfig` system.

**File:** `src/config/sdk-config.ts`
**Android equivalent:** `PulseSdkConfig.kt`, `PulseSdkConfigRefresher.kt`, `PulseSamplingSignalProcessors.kt`
**Depends on:** `01-foundation.md` (OTel SDK init must exist before config is applied)

---

## Why This Matters

Without remote config, every SDK behavior change requires a new SDK release and customer upgrade. With it:

- Reduce sampling on a high-traffic customer from 100% → 5% **without a deploy**
- Disable session replay for a specific app version that has a replay bug
- Strip a PII attribute that a customer accidentally included — remotely
- Turn on click instrumentation for 10% of sessions to A/B test the overhead
- Route logs to a different collector for a specific customer project

---

## Config Contract

### Root Model

```typescript
interface PulseSdkConfig {
  version: number;               // Monotonic integer — only persist if version changes
  description?: string;          // Human-readable description
  sampling: PulseSamplingConfig;
  signals: PulseSignalConfig;
  interaction: PulseInteractionConfig;
  features: PulseFeatureConfig[];
}
```

### 1. Sampling Config

```typescript
interface PulseSamplingConfig {
  default: {
    sessionSampleRate: number;   // 0.0–1.0 global fallback
  };
  rules: PulseSessionSamplingRule[];
  criticalEventPolicies?: PulseCriticalEventPolicies;
  criticalSessionPolicies?: PulseCriticalEventPolicies;
}

interface PulseSessionSamplingRule {
  name: PulseDeviceAttributeName;   // Which attribute to match
  value: string;                    // Regex pattern
  sdks: PulseSdkName[];            // Apply only to matching SDKs
  sessionSampleRate: number;        // Sample rate if rule matches
}

// Web-specific attribute names (superset of Android)
type PulseDeviceAttributeName =
  | 'OS_VERSION'           // browser.version
  | 'APP_VERSION'          // sdk version
  | 'COUNTRY'              // from IP (server-injected, not browser)
  | 'BROWSER_NAME'         // browser.name (Chrome, Firefox, Safari)
  | 'DEVICE_TYPE'          // 'mobile' | 'tablet' | 'desktop'
  | 'URL_PATH'             // window.location.pathname pattern

type PulseSdkName =
  | 'pulse_android_java'
  | 'pulse_android_rn'
  | 'pulse_ios_swift'
  | 'pulse_ios_rn'
  | 'pulse_web_js'           // ← new for web

interface PulseCriticalEventPolicies {
  alwaysSend: PulseSignalMatchCondition[];  // Always export these even if session unsampled
}
```

### 2. Signal Config

```typescript
interface PulseSignalConfig {
  scheduleDurationMs: number;                 // Export batch interval (e.g. 5000ms)
  logsCollectorUrl?: string;                  // Override /v1/logs endpoint
  metricCollectorUrl?: string;                // Override /v1/metrics endpoint
  spanCollectorUrl?: string;                  // Override /v1/traces endpoint
  attributesToDrop: PulseAttributesToDropEntry[];
  attributesToAdd: PulseAttributesToAddEntry[];
  filters: PulseSignalFilter;
}

interface PulseSignalFilter {
  mode: 'BLACKLIST' | 'WHITELIST';
  values: PulseSignalMatchCondition[];
}

interface PulseAttributesToDropEntry {
  values: string[];                           // Regex patterns of attribute names to drop
  condition: PulseSignalMatchCondition;       // When to apply
}

interface PulseAttributesToAddEntry {
  values: PulseAttributeValue[];              // Attributes to inject
  condition: PulseSignalMatchCondition;       // When to apply
}

interface PulseAttributeValue {
  name: string;
  value: string;
  type: 'STRING' | 'BOOLEAN' | 'LONG' | 'DOUBLE' | 'STRING_ARRAY';
}

interface PulseSignalMatchCondition {
  name: string;                               // Regex pattern on signal name / pulse.type
  props: Array<{ key: string; value: string }>; // Signal attribute filters (AND logic)
  scopes: Array<'LOGS' | 'TRACES' | 'METRICS'>;
  sdks: PulseSdkName[];
}
```

### 3. Feature Config

```typescript
interface PulseFeatureConfig {
  featureName: PulseFeatureName;
  sessionSampleRate: number;      // 0.0 = disabled, 1.0 = all sessions, 0.1 = 10%
  sdks: PulseSdkName[];
  config?: PulseFeatureConfigData;
}

type PulseFeatureName =
  | 'js_crash'                 // window.onerror / unhandledrejection (02.1)
  | 'network_instrumentation'  // fetch + XHR patching (02.2)
  | 'click'                    // click tracking with rage/dead detection (02.3)
  | 'web_vitals'               // LCP, CLS, INP, etc. (02.4)
  | 'screen_session'           // navigation tracking (02.5)
  | 'long_task'                // PerformanceObserver longtask (02.6)
  | 'resource_timing'          // PerformanceObserver resource (02.7)
  | 'visibility'               // visibilitychange + online/offline (02.8)
  | 'websocket'                // WebSocket instrumentation (02.9)
  | 'bfcache'                  // back/forward cache detection (02.10)
  | 'interaction'              // Pulse Interactions (03.x)
  | 'session_replay'           // rrweb session replay (04.x)
  | 'network_change'           // online/offline events (02.8)
  | 'custom_events'            // manual trackEvent() calls

// Feature-specific config (polymorphic by featureName)
type PulseFeatureConfigData =
  | SessionReplayFeatureConfig
  | ClickFeatureConfig
  | NetworkFeatureConfig
  | InteractionFeatureConfig;

interface SessionReplayFeatureConfig {
  type: 'session_replay';
  maskAllInputs?: boolean;
  flushIntervalSeconds?: number;
  maxBufferBytes?: number;       // default: 5MB
  sampleRate?: number;           // per-feature override
}

interface ClickFeatureConfig {
  type: 'click';
  rage?: {
    timeWindowMs?: number;       // default: 700ms
    threshold?: number;          // default: 3 clicks
  };
  deadClickTimeoutMs?: number;   // default: 1000ms
}

interface NetworkFeatureConfig {
  type: 'network_instrumentation';
  blockedUrls?: string[];        // Additional URL patterns to ignore
  capturedRequestHeaders?: string[];
  capturedResponseHeaders?: string[];
}

interface InteractionFeatureConfig {
  type: 'interaction';
  configUrl?: string;
  beforeInitQueueSize?: number;
}
```

---

## Config Fetch & Cache Strategy

```
SDK init
   │
   ├─ Step 1: Load from localStorage (sync, instant)
   │          key: 'pulse_sdk_config'
   │          Apply immediately to set up processors
   │
   └─ Step 2: Fetch /v1/configs/active/ (async, background)
              │
              ├─ 200 OK + version changed → persist to localStorage
              │                           → apply on NEXT session
              │
              ├─ 200 OK + same version    → no-op (already up to date)
              │
              └─ error / 4xx / 5xx        → keep using cached config
```

**Important: Config applies on next session, not mid-session.** This prevents sampling decisions from changing under a user's feet (a session either is or isn't sampled — it shouldn't flip mid-session).

**Exception:** Signal filters and attribute manipulation apply immediately (they're stateless per-signal, not per-session).

---

## Implementation

### Config Fetcher

```typescript
// src/config/sdk-config-fetcher.ts

const STORAGE_KEY = 'pulse_sdk_config';

export class SdkConfigFetcher {
  private config: PulseSdkConfig = DEFAULT_SDK_CONFIG;

  constructor(
    private readonly endpointBaseUrl: string,
    private readonly projectId: string,
  ) {}

  /** Called at SDK init. Returns immediately with cached config. */
  loadCached(): PulseSdkConfig {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        this.config = JSON.parse(raw) as PulseSdkConfig;
      }
    } catch { /* corrupt cache — use defaults */ }
    return this.config;
  }

  /** Fetch fresh config in background. Does NOT block SDK init. */
  async fetchInBackground(): Promise<void> {
    try {
      const response = await fetch(`${this.endpointBaseUrl}/v1/configs/active/`, {
        headers: {
          'x-pulse-project-id': this.projectId,
          'x-pulse-sdk-name':   'pulse_web_js',
        },
      });
      if (!response.ok) return;

      const fresh: PulseSdkConfig = await response.json();

      // Only persist if version changed
      if (fresh.version !== this.config.version) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(fresh));
        // Config takes effect on next page load (next session)
      }
    } catch { /* network failure — use cached */ }
  }

  getConfig(): PulseSdkConfig {
    return this.config;
  }
}
```

### Session Sampling Processor

```typescript
// src/config/sampling-processor.ts

export class PulseSamplingProcessor implements SpanProcessor, LogRecordProcessor {
  private readonly shouldSample: boolean;

  constructor(private readonly config: PulseSdkConfig, private readonly sdkName: PulseSdkName) {
    // Sampling decision is made ONCE per session (not per signal)
    const rate = this.resolveSampleRate();
    this.shouldSample = Math.random() <= rate;
  }

  onStart(span: Span): void {
    if (!this.shouldSample && !this.isCritical(span)) {
      span.setAttribute('pulse.sampled', false);
      // OTEL doesn't support dropping mid-flight — mark and filter at export
    }
  }

  onEmit(logRecord: LogRecord): void {
    if (!this.shouldSample && !this.isLogCritical(logRecord)) {
      logRecord.setAttribute('pulse.sampled', false);
    }
  }

  private resolveSampleRate(): number {
    const rules = this.config.sampling.rules ?? [];

    // Evaluate rules in order — first match wins
    for (const rule of rules) {
      if (!rule.sdks.includes(this.sdkName)) continue;
      if (this.matchesDeviceAttribute(rule.name, rule.value)) {
        return rule.sessionSampleRate;
      }
    }

    return this.config.sampling.default.sessionSampleRate;
  }

  private matchesDeviceAttribute(attr: PulseDeviceAttributeName, pattern: string): boolean {
    const value = this.getDeviceAttributeValue(attr);
    return new RegExp(pattern, 'i').test(value);
  }

  private getDeviceAttributeValue(attr: PulseDeviceAttributeName): string {
    switch (attr) {
      case 'BROWSER_NAME':  return getBrowserName();
      case 'OS_VERSION':    return navigator.userAgent;
      case 'DEVICE_TYPE':   return getDeviceType();
      case 'URL_PATH':      return window.location.pathname;
      default:              return '';
    }
  }

  private isCritical(span: Span): boolean {
    const policies = this.config.sampling.criticalEventPolicies?.alwaysSend ?? [];
    return policies.some(condition => this.matchesSignal(span, condition));
  }

  onEnd(): void {}
  shutdown(): Promise<void> { return Promise.resolve(); }
  forceFlush(): Promise<void> { return Promise.resolve(); }
}
```

### Feature Gate

```typescript
// src/config/feature-gate.ts

export class FeatureGate {
  constructor(private readonly config: PulseSdkConfig) {}

  isEnabled(feature: PulseFeatureName): boolean {
    const featureConfig = this.config.features.find(
      f => f.featureName === feature && f.sdks.includes('pulse_web_js')
    );
    if (!featureConfig) return true;  // Not configured = enabled by default
    return featureConfig.sessionSampleRate > 0;
  }

  getSampleRate(feature: PulseFeatureName): number {
    const featureConfig = this.config.features.find(
      f => f.featureName === feature && f.sdks.includes('pulse_web_js')
    );
    return featureConfig?.sessionSampleRate ?? 1.0;
  }

  getFeatureConfig<T extends PulseFeatureConfigData>(feature: PulseFeatureName): T | undefined {
    const featureConfig = this.config.features.find(f => f.featureName === feature);
    return featureConfig?.config as T | undefined;
  }
}
```

### Signal Filter Processor

```typescript
// src/config/signal-filter-processor.ts

export class SignalFilterProcessor implements SpanProcessor, LogRecordProcessor {
  constructor(private readonly signalConfig: PulseSignalConfig) {}

  onStart(span: Span): void {
    // Attribute injection
    for (const entry of this.signalConfig.attributesToAdd) {
      if (this.matchesCondition(span, entry.condition)) {
        for (const attr of entry.values) {
          span.setAttribute(attr.name, this.castValue(attr));
        }
      }
    }
  }

  onEnd(span: ReadableSpan): void {
    // Attribute dropping (applied at export time)
    for (const entry of this.signalConfig.attributesToDrop) {
      if (this.matchesCondition(span, entry.condition)) {
        for (const pattern of entry.values) {
          dropMatchingAttributes(span, pattern);
        }
      }
    }
  }

  private castValue(attr: PulseAttributeValue): AttributeValue {
    switch (attr.type) {
      case 'BOOLEAN': return attr.value === 'true';
      case 'LONG':    return parseInt(attr.value, 10);
      case 'DOUBLE':  return parseFloat(attr.value);
      default:        return attr.value;
    }
  }
}
```

### Wiring Config Into SDK Init

```typescript
// src/sdk.ts — updated init flow

export class PulseSDK {
  async init(): Promise<void> {
    // 1. Load cached config synchronously
    const configFetcher = new SdkConfigFetcher(this.options.otlpEndpoint, this.options.projectId);
    const sdkConfig = configFetcher.loadCached();

    // 2. Feature gate — only install enabled instrumentations
    const gate = new FeatureGate(sdkConfig);

    // 3. Session sampling decision — once per page load
    const samplingProcessor = new PulseSamplingProcessor(sdkConfig, 'pulse_web_js');

    // 4. Signal filter processor
    const filterProcessor = new SignalFilterProcessor(sdkConfig.signals);

    // 5. Override collector URLs if config specifies them
    const logsUrl   = sdkConfig.signals.logsCollectorUrl   ?? `${this.options.otlpEndpoint}/v1/logs`;
    const tracesUrl = sdkConfig.signals.spanCollectorUrl    ?? `${this.options.otlpEndpoint}/v1/traces`;
    const metricsUrl= sdkConfig.signals.metricCollectorUrl  ?? `${this.options.otlpEndpoint}/v1/metrics`;

    // 6. Build OTel SDK with processors
    this.tracerProvider = new WebTracerProvider({
      spanProcessors: [
        samplingProcessor,
        filterProcessor,
        globalAttributeProcessor,
        new BatchSpanProcessor(
          new OTLPTraceExporter({ url: tracesUrl }),
          { scheduledDelayMillis: sdkConfig.signals.scheduleDurationMs }
        ),
      ],
    });

    // 7. Install only enabled instrumentations
    if (gate.isEnabled('js_crash'))           this.errors.install();
    if (gate.isEnabled('network_instrumentation')) this.network.install();
    if (gate.isEnabled('click'))              this.clicks.install(gate.getFeatureConfig('click'));
    if (gate.isEnabled('web_vitals'))         this.webVitals.install();
    if (gate.isEnabled('screen_session'))     this.navigation.install();
    if (gate.isEnabled('long_task'))          this.longTasks.install();
    if (gate.isEnabled('resource_timing'))    this.resourceTiming.install();
    if (gate.isEnabled('visibility'))         this.visibilityOnline.install();
    if (gate.isEnabled('websocket'))          this.websocket.install();
    if (gate.isEnabled('bfcache'))            this.bfcache.install();

    if (gate.isEnabled('session_replay')) {
      const replayCfg = gate.getFeatureConfig<SessionReplayFeatureConfig>('session_replay');
      this.replay.start(replayCfg);
    }

    if (gate.isEnabled('interaction')) {
      await this.interactions.init();
    }

    // 8. Fetch fresh config in background (takes effect next session)
    configFetcher.fetchInBackground();
  }
}
```

---

## Default Config (When No Remote Config Available)

```typescript
const DEFAULT_SDK_CONFIG: PulseSdkConfig = {
  version: -1,               // -1 = default (never been fetched)
  description: 'Default web SDK config',
  sampling: {
    default: { sessionSampleRate: 1.0 },  // 100% sampling by default
    rules: [],
    criticalEventPolicies: undefined,
  },
  signals: {
    scheduleDurationMs: 5000,
    attributesToDrop: [],
    attributesToAdd: [],
    filters: { mode: 'BLACKLIST', values: [] },
  },
  interaction: {
    beforeInitQueueSize: 5000,
  },
  features: [],              // Empty = all features enabled at 100%
};
```

---

## Edge Cases

| Case | Handling |
|---|---|
| First ever load — no localStorage cache | Use `DEFAULT_SDK_CONFIG` (all features on, 100% sampling) |
| Corrupt localStorage JSON | `JSON.parse` catch block → use default |
| `/v1/configs/active/` returns 404 | Keep using cached; don't overwrite localStorage |
| Config version unchanged | Skip localStorage write (avoid churn) |
| `sessionSampleRate: 0` on a feature | Feature completely disabled — instrumentation not installed |
| `criticalEventPolicies` matches a crash signal | Error always exported even in unsampled session |
| `scheduleDurationMs` changed remotely | Takes effect on next session (BatchSpanProcessor created with new value) |
| SSR environment | `localStorage` guarded with `typeof window !== 'undefined'` |
| localStorage blocked (private browsing) | Caught; falls back to in-memory default for session |
| SDK name not in rule's `sdks` list | Rule is skipped — web SDK only processes rules that include `'pulse_web_js'` |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('loads cached config from localStorage on init', () => {
  localStorage.setItem('pulse_sdk_config', JSON.stringify(mockConfig));
  const fetcher = new SdkConfigFetcher('https://ingest.test', 'proj_test');
  const config = fetcher.loadCached();
  expect(config.version).toBe(mockConfig.version);
});

it('persists fresh config only when version changes', async () => {
  localStorage.setItem('pulse_sdk_config', JSON.stringify({ version: 5 }));
  vi.spyOn(global, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ version: 6, ...mockConfig }))
  );

  const fetcher = new SdkConfigFetcher('https://ingest.test', 'proj_test');
  fetcher.loadCached();
  await fetcher.fetchInBackground();

  const stored = JSON.parse(localStorage.getItem('pulse_sdk_config')!);
  expect(stored.version).toBe(6);
});

it('does not overwrite localStorage if version is same', async () => {
  localStorage.setItem('pulse_sdk_config', JSON.stringify({ version: 5 }));
  vi.spyOn(global, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ version: 5 }))
  );
  const writeSpy = vi.spyOn(localStorage, 'setItem');

  const fetcher = new SdkConfigFetcher('https://ingest.test', 'proj_test');
  fetcher.loadCached();
  await fetcher.fetchInBackground();

  expect(writeSpy).not.toHaveBeenCalled();
});

it('sampling decision is 0% when sessionSampleRate is 0', () => {
  const config = { ...DEFAULT_SDK_CONFIG, sampling: { default: { sessionSampleRate: 0 }, rules: [] } };
  // Run 100 times — should never sample
  const results = Array.from({ length: 100 }, () =>
    new PulseSamplingProcessor(config, 'pulse_web_js').shouldSample
  );
  expect(results.every(r => r === false)).toBe(true);
});

it('feature gate disables instrumentation when sessionSampleRate is 0', () => {
  const config = {
    ...DEFAULT_SDK_CONFIG,
    features: [{ featureName: 'click', sessionSampleRate: 0, sdks: ['pulse_web_js'] }],
  };
  const gate = new FeatureGate(config);
  expect(gate.isEnabled('click')).toBe(false);
});

it('drops attributes matching regex pattern', () => {
  const span = createMockSpan({ 'user.email': 'test@test.com', 'user.id': '123' });
  const processor = new SignalFilterProcessor({
    ...DEFAULT_SIGNAL_CONFIG,
    attributesToDrop: [{
      values: ['user\\.email'],
      condition: { name: '.*', props: [], scopes: ['TRACES'], sdks: ['pulse_web_js'] },
    }],
  });
  processor.onEnd(span);
  expect(span.attributes['user.email']).toBeUndefined();
  expect(span.attributes['user.id']).toBe('123');
});
```

---

## Done Criteria

- [ ] `SdkConfigFetcher.loadCached()` reads from `localStorage` on init
- [ ] `fetchInBackground()` hits `/v1/configs/active/` with SDK name header
- [ ] Config persisted only when `version` changes
- [ ] `PulseSamplingProcessor` makes a single sampling decision per session
- [ ] Sampling rules evaluated in order; first match wins
- [ ] Critical event policies bypass session sampling
- [ ] `FeatureGate.isEnabled()` returns `false` when `sessionSampleRate === 0`
- [ ] Feature-specific config (rage-click params, replay settings) passed to instrumentations
- [ ] `SignalFilterProcessor` drops regex-matching attributes
- [ ] `SignalFilterProcessor` injects attributes with correct type casting
- [ ] Collector URL overrides applied when set in config
- [ ] `scheduleDurationMs` used for `BatchSpanProcessor` delay
- [ ] Default config used when nothing in localStorage
- [ ] All unit tests passing
