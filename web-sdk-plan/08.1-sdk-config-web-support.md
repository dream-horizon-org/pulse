# 08.1 — SDK Config: Web Support

**Goal:** Extend the existing SDK Config system (backend + UI) to support `pulse_web_js` — adding web-specific SDK enum values, feature names, sampling rule attributes, a web session replay config shape, and a web default config template. No new tables or API endpoints needed.

**Depends on:** `01.5-sdk-config.md` (web SDK consumption side)
**Touches:**
- `backend/server/.../resources/configs/models/` — Java enums and DTOs
- `backend/server/.../service/configs/DefaultSdkConfigTemplate.java` — default template
- `pulse-ui/src/screens/SamplingConfig/` — UI components and interfaces

---

## What Already Exists (No Change Needed)

The following features/SDK names already exist in the codebase and work for web today:

| Existing | Reused for Web |
|---|---|
| `js_crash` FeatureName | 02.1 error instrumentation |
| `network_instrumentation` FeatureName | 02.2 network instrumentation |
| `click` FeatureName | 02.3 click instrumentation |
| `screen_session` FeatureName | 02.5 navigation instrumentation |
| `network_change` FeatureName | 02.8 online/offline |
| `session_replay` FeatureName | 04.x session replay |
| `interaction` FeatureName | 03.x interactions |
| `custom_events` FeatureName | manual `trackEvent()` |
| `BLACKLIST`/`WHITELIST` filter modes | signal filtering |
| `attributesToDrop` / `attributesToAdd` | attribute manipulation |
| Version management, cache, DAO | unchanged |

---

## Changes Required

### 1. Backend — `SdkEnum`

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/configs/models/SdkEnum.java`

```java
// BEFORE
public enum SdkEnum {
    pulse_android_java,
    pulse_android_rn,
    pulse_ios_swift,
    pulse_ios_rn,
    unknown;
}

// AFTER — add one value
public enum SdkEnum {
    pulse_android_java,
    pulse_android_rn,
    pulse_ios_swift,
    pulse_ios_rn,
    pulse_web_js,       // ← NEW
    unknown;
}
```

**Impact:** This single change flows through every enum check in sampling rules, feature configs, and signal filters. The `/v1/configs/scopes-sdks` endpoint returns this enum — UI picks it up automatically.

---

### 2. Backend — `FeatureName` Enum

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/configs/models/FeatureName.java`

```java
// Add web-exclusive features not already present
public enum FeatureName {
    // Existing (shared with Android/iOS)
    interaction,
    java_crash,
    js_crash,
    java_anr,
    cpp_crash,
    cpp_anr,
    network_change,
    network_instrumentation,
    screen_session,
    custom_events,
    rn_screen_load,
    rn_screen_interactive,
    session_replay,
    click,

    // NEW — web-exclusive features
    web_vitals,          // LCP, CLS, INP, TTFB, FCP (02.4)
    long_task,           // PerformanceObserver longtask (02.6)
    resource_timing,     // PerformanceObserver resource (02.7)
    visibility,          // visibilitychange + online/offline (02.8)
    websocket,           // WebSocket instrumentation (02.9)
    bfcache;             // Back/Forward Cache detection (02.10)
}
```

---

### 3. Backend — `SamplingRuleName` Enum

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/configs/models/SamplingRuleName.java`

```java
// BEFORE
public enum SamplingRuleName {
    os_version,
    app_version,
    country,
    platform,
    state,
    device,
    network;
}

// AFTER — add web-specific rule attributes
public enum SamplingRuleName {
    os_version,
    app_version,
    country,
    platform,
    state,
    device,
    network,
    browser_name,        // ← NEW  maps to browser.name (Chrome, Firefox, Safari)
    browser_version,     // ← NEW  maps to browser.version
    url_path,            // ← NEW  maps to window.location.pathname (regex)
    device_type;         // ← NEW  maps to device.type (mobile/tablet/desktop)
}
```

**Web SDK resolves these attributes as:**

| Rule name | Web SDK source |
|---|---|
| `browser_name` | `navigator.userAgent` parsed → `"Chrome"`, `"Firefox"`, `"Safari"` |
| `browser_version` | `navigator.userAgent` parsed → `"120"`, `"121"` |
| `url_path` | `window.location.pathname` — regex match |
| `device_type` | Screen width heuristic: <768px = `mobile`, <1024px = `tablet`, else `desktop` |
| `os_version` | `navigator.userAgent` (existing) |
| `platform` | Always `"web"` for `pulse_web_js` |

---

### 4. Backend — `WebSessionReplayFeatureConfig`

Mobile session replay config has `screenshotScale`, `screenshotQuality` — irrelevant for web. Web has CSS masking classes. The `config` field on `FeatureConfig` is already a polymorphic `Object` in JSON — add a web-specific shape.

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/configs/models/WebSessionReplayFeatureConfig.java` (new file)

```java
@Data
@JsonTypeName("web_session_replay")
public class WebSessionReplayFeatureConfig {
    // Privacy
    Boolean maskAllInputs;          // default: true
    String  maskTextClass;          // default: "pulse-mask"
    String  blockClass;             // default: "pulse-block"
    String  blockSelector;          // CSS selector, e.g. "[data-private]"
    Boolean maskURLs;               // default: true — strip query params from recorded URLs

    // Batching / transport
    Integer flushIntervalSeconds;   // default: 30
    Integer maxBufferMb;            // default: 5 (MB rolling buffer)
    Float   sampleRate;             // 0.0–1.0 per-feature override

    // Recording
    Boolean recordCanvas;           // default: false (expensive)
}
```

**Deserialisation discriminator:** The existing `SessionReplayFeatureConfig` is for mobile. The JSON field `type: "web_session_replay"` vs `type: "session_replay"` disambiguates.

---

### 5. Backend — `DefaultSdkConfigTemplate`

**File:** `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/configs/DefaultSdkConfigTemplate.java`

Add a `createDefaultWebConfig()` method. This is called when a new project is created — it now creates **one default config** that covers both mobile and web SDKs.

```java
public static List<FeatureConfig> webDefaultFeatures() {
    return List.of(
        // ── Inherited from mobile (existing feature names) ──────────────
        feature("js_crash",              1.0f, List.of(pulse_web_js)),
        feature("network_instrumentation",1.0f, List.of(pulse_web_js)),
        feature("click",                 1.0f, List.of(pulse_web_js),
            new ClickFeatureConfig(700L, 3, null)),   // rage: 700ms, 3 clicks
        feature("screen_session",        1.0f, List.of(pulse_web_js)),
        feature("network_change",        1.0f, List.of(pulse_web_js)),
        feature("interaction",           1.0f, List.of(pulse_web_js)),
        feature("custom_events",         1.0f, List.of(pulse_web_js)),
        feature("session_replay",        1.0f, List.of(pulse_web_js),
            new WebSessionReplayFeatureConfig(
                true,   // maskAllInputs
                "pulse-mask",
                "pulse-block",
                null,   // blockSelector
                true,   // maskURLs
                30,     // flushIntervalSeconds
                5,      // maxBufferMb
                1.0f,   // sampleRate
                false   // recordCanvas
            )),

        // ── Web-exclusive features (new feature names) ──────────────────
        feature("web_vitals",       1.0f, List.of(pulse_web_js)),
        feature("long_task",        1.0f, List.of(pulse_web_js)),
        feature("resource_timing",  0.0f, List.of(pulse_web_js)),  // off by default (high volume)
        feature("visibility",       1.0f, List.of(pulse_web_js)),
        feature("websocket",        0.0f, List.of(pulse_web_js)),  // off by default (opt-in)
        feature("bfcache",          1.0f, List.of(pulse_web_js))
    );
}
```

**Default off rationale:**
- `resource_timing`: Every page emits 20–100 resource entries — noisy by default; enable when needed
- `websocket`: Most apps don't use WebSockets; opt-in to avoid capturing internal infra traffic

---

### 6. Backend — `/v1/configs/rules-features` Response

**File:** `backend/server/.../service/configs/ConfigRulesFeaturesService.java`

This endpoint drives the UI dropdowns. It must return the new enums. Since enum values are serialised as strings, adding to the enum is sufficient — no code change needed here. Verify the serialisation:

```json
{
  "sdks": [
    "pulse_android_java", "pulse_android_rn",
    "pulse_ios_swift", "pulse_ios_rn",
    "pulse_web_js"
  ],
  "features": [
    "js_crash", "network_instrumentation", "click", "screen_session",
    "network_change", "session_replay", "interaction", "custom_events",
    "web_vitals", "long_task", "resource_timing", "visibility",
    "websocket", "bfcache"
  ],
  "samplingRuleNames": [
    "os_version", "app_version", "country", "platform",
    "browser_name", "browser_version", "url_path", "device_type"
  ]
}
```

---

### 7. UI — TypeScript Interfaces

**File:** `pulse-ui/src/screens/SamplingConfig/SamplingConfig.interface.ts`

```typescript
// BEFORE
export enum SdkEnum {
  pulse_android_java = 'pulse_android_java',
  pulse_android_rn   = 'pulse_android_rn',
  pulse_ios_swift    = 'pulse_ios_swift',
  pulse_ios_rn       = 'pulse_ios_rn',
}

// AFTER
export enum SdkEnum {
  pulse_android_java = 'pulse_android_java',
  pulse_android_rn   = 'pulse_android_rn',
  pulse_ios_swift    = 'pulse_ios_swift',
  pulse_ios_rn       = 'pulse_ios_rn',
  pulse_web_js       = 'pulse_web_js',       // ← NEW
}

// Add to FeatureName enum
export enum FeatureName {
  // existing...
  web_vitals       = 'web_vitals',
  long_task        = 'long_task',
  resource_timing  = 'resource_timing',
  visibility       = 'visibility',
  websocket        = 'websocket',
  bfcache          = 'bfcache',
}

// Add to SamplingRuleName enum
export enum SamplingRuleName {
  // existing...
  browser_name    = 'browser_name',
  browser_version = 'browser_version',
  url_path        = 'url_path',
  device_type     = 'device_type',
}

// New web session replay config shape
export interface WebSessionReplayFeatureConfig {
  maskAllInputs?:       boolean;
  maskTextClass?:       string;
  blockClass?:          string;
  blockSelector?:       string;
  maskURLs?:            boolean;
  flushIntervalSeconds?: number;
  maxBufferMb?:         number;
  sampleRate?:          number;
  recordCanvas?:        boolean;
}
```

---

### 8. UI — `FeatureToggles` Component

**File:** `pulse-ui/src/screens/SamplingConfig/components/FeatureToggles/FeatureToggles.tsx`

Add display metadata for each new web feature — icon, label, description, and which SDK platform badge to show:

```typescript
const FEATURE_META: Record<FeatureName, FeatureMeta> = {
  // ... existing entries ...

  // ── New web features ────────────────────────────────────────────────────
  [FeatureName.web_vitals]: {
    label: 'Web Vitals',
    description: 'LCP, CLS, INP, TTFB, FCP — Core Web Vitals metrics',
    icon: <SpeedIcon />,
    platform: 'web',
  },
  [FeatureName.long_task]: {
    label: 'Long Tasks',
    description: 'Main-thread blocks > 50ms (jank detection)',
    icon: <TimerIcon />,
    platform: 'web',
  },
  [FeatureName.resource_timing]: {
    label: 'Resource Timing',
    description: 'Script, stylesheet, image, font load performance',
    icon: <CloudDownloadIcon />,
    platform: 'web',
  },
  [FeatureName.visibility]: {
    label: 'Tab Visibility',
    description: 'Tab hide/show and online/offline transitions',
    icon: <VisibilityIcon />,
    platform: 'web',
  },
  [FeatureName.websocket]: {
    label: 'WebSocket',
    description: 'WebSocket connection lifecycle and message counts',
    icon: <SyncAltIcon />,
    platform: 'web',
  },
  [FeatureName.bfcache]: {
    label: 'BFCache Restore',
    description: 'Back/Forward Cache page restore detection',
    icon: <HistoryIcon />,
    platform: 'web',
  },
};
```

**Session Replay modal for web:** When `session_replay` is toggled on for `pulse_web_js`, open a modal with web-specific fields instead of the mobile screenshot fields:

```typescript
// In the session replay config modal — detect if editing for web SDK
const isWebReplay = selectedSdks.includes(SdkEnum.pulse_web_js);

if (isWebReplay) {
  // Show: maskAllInputs toggle, maskTextClass input, blockClass input,
  //       blockSelector input, maskURLs toggle, flushIntervalSeconds, maxBufferMb
} else {
  // Show: textAndInputPrivacy select, imagePrivacy select,
  //       screenshotScale, screenshotQuality, flushAt, maxBatchSize
}
```

---

### 9. UI — Sampling Rules — Web Rule Names

**File:** `pulse-ui/src/screens/SamplingConfig/components/SamplingRulesConfig/SamplingRulesConfig.tsx`

The rule name dropdown must display human-friendly labels for new web attributes:

```typescript
const RULE_NAME_LABELS: Record<SamplingRuleName, string> = {
  // existing...
  [SamplingRuleName.browser_name]:    'Browser Name (Chrome, Firefox, Safari)',
  [SamplingRuleName.browser_version]: 'Browser Version',
  [SamplingRuleName.url_path]:        'URL Path (regex)',
  [SamplingRuleName.device_type]:     'Device Type (mobile / tablet / desktop)',
};
```

---

## Summary of All Changes

| Layer | File | Change |
|---|---|---|
| Backend | `SdkEnum.java` | Add `pulse_web_js` |
| Backend | `FeatureName.java` | Add 6 web features |
| Backend | `SamplingRuleName.java` | Add `browser_name`, `browser_version`, `url_path`, `device_type` |
| Backend | `WebSessionReplayFeatureConfig.java` | New DTO (web replay privacy fields) |
| Backend | `DefaultSdkConfigTemplate.java` | Add `createDefaultWebFeatures()` — 12 feature entries for web |
| Backend | `/v1/configs/scopes-sdks` response | Automatically includes `pulse_web_js` from enum |
| Backend | `/v1/configs/rules-features` response | Automatically includes new enums |
| UI | `SamplingConfig.interface.ts` | Add `pulse_web_js`, 6 feature names, 4 rule names, `WebSessionReplayFeatureConfig` |
| UI | `FeatureToggles.tsx` | Add icons/labels/descriptions for 6 web features |
| UI | Session Replay modal | Bifurcate config form for web vs mobile |
| UI | `SamplingRulesConfig.tsx` | Add labels for 4 web rule names |

**No DB schema changes needed.** `config_json` is stored as JSON — new fields serialise/deserialise automatically.

---

## Testing

### Backend Tests

```java
@Test
void sdkEnumIncludesPulseWebJs() {
    assertTrue(Arrays.asList(SdkEnum.values()).contains(SdkEnum.pulse_web_js));
}

@Test
void defaultWebConfigHasAllWebFeatures() {
    List<FeatureConfig> features = DefaultSdkConfigTemplate.webDefaultFeatures();
    List<String> names = features.stream().map(f -> f.getFeatureName().name()).toList();

    assertTrue(names.contains("js_crash"));
    assertTrue(names.contains("web_vitals"));
    assertTrue(names.contains("long_task"));
    assertTrue(names.contains("resource_timing"));
    assertTrue(names.contains("visibility"));
    assertTrue(names.contains("websocket"));
    assertTrue(names.contains("bfcache"));
    assertTrue(names.contains("session_replay"));
}

@Test
void defaultWebConfigResourceTimingIsDisabled() {
    List<FeatureConfig> features = DefaultSdkConfigTemplate.webDefaultFeatures();
    FeatureConfig resourceTiming = features.stream()
        .filter(f -> f.getFeatureName() == FeatureName.resource_timing)
        .findFirst().orElseThrow();
    assertEquals(0.0f, resourceTiming.getSessionSampleRate());
}

@Test
void activeConfigEndpointReturnsForWebSdk() {
    // Integration test: POST config with pulse_web_js features, GET /v1/configs/active
    // Verify config_json contains web features
}
```

### UI Tests

```typescript
it('shows pulse_web_js option in SDK selector', () => {
  render(<FeatureToggles />);
  expect(screen.getByText('pulse_web_js')).toBeInTheDocument();
});

it('shows web replay config fields when pulse_web_js selected for session_replay', () => {
  render(<SessionReplayModal sdks={[SdkEnum.pulse_web_js]} />);
  expect(screen.getByLabelText('Mask All Inputs')).toBeInTheDocument();
  expect(screen.getByLabelText('Mask Text Class')).toBeInTheDocument();
  expect(screen.queryByLabelText('Screenshot Quality')).not.toBeInTheDocument();
});

it('shows web_vitals, long_task, websocket in feature list', () => {
  render(<FeatureToggles />);
  expect(screen.getByText('Web Vitals')).toBeInTheDocument();
  expect(screen.getByText('Long Tasks')).toBeInTheDocument();
  expect(screen.getByText('WebSocket')).toBeInTheDocument();
});

it('shows browser_name in sampling rule dropdown', () => {
  render(<SamplingRulesConfig />);
  // Open rule name dropdown
  expect(screen.getByText('Browser Name (Chrome, Firefox, Safari)')).toBeInTheDocument();
});
```

---

## Done Criteria

### Backend
- [ ] `pulse_web_js` added to `SdkEnum`
- [ ] 6 web features added to `FeatureName` enum
- [ ] 4 web rule names added to `SamplingRuleName` enum
- [ ] `WebSessionReplayFeatureConfig` DTO created with privacy and batching fields
- [ ] `DefaultSdkConfigTemplate.webDefaultFeatures()` produces 12 feature configs
- [ ] `resource_timing` and `websocket` default to `sessionSampleRate: 0` (off)
- [ ] `/v1/configs/scopes-sdks` returns `pulse_web_js` in SDK list
- [ ] `/v1/configs/rules-features` returns all new features and rule names
- [ ] Existing configs with no web features still load without error

### UI
- [ ] `pulse_web_js` selectable in SDK checkboxes across all config sections
- [ ] All 6 new web features shown in `FeatureToggles` with icons and descriptions
- [ ] Session Replay config modal shows web fields (CSS masking) when `pulse_web_js` selected
- [ ] Sampling rule dropdown shows human-friendly labels for web rule names
- [ ] Creating a new config with web features round-trips correctly (save → reload matches)
- [ ] All UI tests passing
