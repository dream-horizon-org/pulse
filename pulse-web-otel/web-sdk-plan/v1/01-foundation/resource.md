# 01.C — OTEL Resource & Global Attributes

**What this covers:** Two layers of attributes that appear on every signal — the static OTEL Resource (set once at SDK init) and the dynamic global attributes (added per-signal by processors). Together they replicate what Android's `GlobalAttributesSpanAppender`, `SessionIdSpanAppender`, `ScreenAttributesSpanProcessor`, and `NetworkAttributesSpanAppender` provide.

**Files produced:** `src/resource.ts`, `src/processors/global-attributes-processor.ts`, `src/utils/ua-parser.ts`

---

## 4a. Static Resource Attributes

Set once at `PulseWeb.start()` — attached to the OTEL Resource and stamped on every span/log/metric automatically by the SDK.

| Attribute Key | Type | Value / Source | Android Equivalent |
|---|---|---|---|
| `service.name` | string | `config.serviceName` | `service.name` |
| `service.version` | string | `config.serviceVersion \|\| 'unknown'` | `service.version` |
| `platform` | string | `"web"` (hardcoded) | `"android"` |
| `rum.sdk.name` | string | `"pulse_web_js"` (hardcoded) | `"pulse_android_java"` |
| `rum.sdk.version` | string | Injected at build time | `rum.sdk.version` |
| `project.id` | string | Extracted from API key | `project.id` |
| `installation.id` | string | `localStorage` → `sessionStorage` → memory (see [identity.md](./identity.md)) | `installation.id` |
| `browser.name` | string | `navigator.userAgentData.brands` / UA parse | replaces `device.model.name` |
| `browser.version` | string | `navigator.userAgentData` / UA parse | replaces `device.model.identifier` |
| `browser.vendor` | string | `navigator.vendor` | replaces `device.manufacturer` |
| `browser.language` | string | `navigator.language` | — (web-only) |
| `os.name` | string | `userAgentData.platform` / UA parse | `os.name` |
| `os.version` | string | UA parse | `os.version` |
| `os.type` | string | `"browser"` (hardcoded) | `"linux"` |
| `device.type` | string | UA detection: `"desktop"` \| `"mobile"` \| `"tablet"` | — (web-only) |
| `device.screen.width` | long | `screen.width` | `device.screen.width` |
| `device.screen.height` | long | `screen.height` | `device.screen.height` |
| `device.screen.aspect_ratio` | string | Computed e.g. `"16:9"` | `device.screen.aspect_ratio` |

---

## 4b. Dynamic Global Attributes

Added to every span and log record by processors — **not** part of the OTEL Resource. These change over the life of the session (screen name changes on navigation, network type can change).

| Attribute Key | Type | Value / Source |
|---|---|---|
| `session.id` | string | `sessionStorage['pulse_session_id']` |
| `session.previous_id` | string | Last session ID before rotation |
| `pulse.metering.session.id` | string | Generated UUID per billing period |
| `screen.name` | string | Current URL path / active route (see resolution chain below) |
| `last.screen.name` | string | Previous route before navigation |
| `network.connection.type` | string | `navigator.connection.type` |
| `network.effective_type` | string | `navigator.connection.effectiveType` (`4g`/`3g`/`2g`/`slow-2g`) |
| `network.rtt` | long | `navigator.connection.rtt` (ms) |
| `network.downlink` | double | `navigator.connection.downlink` (Mbps) |
| `page.url` | string | `window.location.href` (sanitised — query params stripped by default) |
| `page.title` | string | `document.title` |
| `page.referrer` | string | `document.referrer` |
| `url.path` | string | `window.location.pathname` |

Any `globalAttributes` from `config.globalAttributes` are also merged into every signal.

### `screen.name` resolution chain (4-step, first non-empty wins)

1. Manual override — `PulseWeb.setScreenName('ProductDetail')`
2. Route pattern config — `routePatterns: [{ pattern: '/products/:id', name: 'ProductDetail' }]`
3. Heuristic — strip numeric IDs / UUIDs / hex segments from pathname (`/products/123` → `/products/:id`)
4. Raw `window.location.pathname` fallback

---

## 4c. Implementation

```typescript
// src/resource.ts — built once at SDK init
import { Resource } from '@opentelemetry/resources';

export function buildResource(config: PulseWebConfig): Resource {
  const ua = parseUserAgent(); // userAgentData first, fallback to navigator.userAgent

  return new Resource({
    'service.name':              config.serviceName,
    'service.version':           config.serviceVersion ?? 'unknown',
    'platform':                  'web',
    'rum.sdk.name':              'pulse_web_js',
    'rum.sdk.version':           SDK_VERSION,
    'project.id':                extractProjectId(config.apiKey),
    'installation.id':           getOrCreateInstallationId(),
    'browser.name':              ua.browserName,
    'browser.version':           ua.browserVersion,
    'browser.vendor':            navigator.vendor,
    'browser.language':          navigator.language,
    'os.name':                   ua.osName,
    'os.version':                ua.osVersion,
    'os.type':                   'browser',
    'device.type':               ua.deviceType,
    'device.screen.width':       screen.width,
    'device.screen.height':      screen.height,
    'device.screen.aspect_ratio': computeAspectRatio(screen.width, screen.height),
  });
}
```

```typescript
// src/processors/global-attributes-processor.ts — added per-signal
export class PulseGlobalAttributesProcessor implements SpanProcessor, LogRecordProcessor {
  onStart(span: Span): void {
    span.setAttributes({
      'session.id':              getSessionId(),
      'screen.name':             getCurrentScreenName(),
      'last.screen.name':        getLastScreenName(),
      'network.connection.type': getNetworkType(),
      'network.effective_type':  getEffectiveType(),
      'page.url':                sanitizeUrl(window.location.href),
      'page.title':              document.title,
      'url.path':                window.location.pathname,
      ...config.globalAttributes,
    });
  }
  onEnd(): void {}
  shutdown(): Promise<void> { return Promise.resolve(); }
  forceFlush(): Promise<void> { return Promise.resolve(); }
}
```

Use `navigator.userAgentData` (Client Hints API) where available; fall back to UA string parsing via a lightweight inline parser (no heavy library needed, ~1 KB).

---

## 4d. Android Attributes NOT Available on Web

| Android Attribute | Reason Not Available |
|---|---|
| `android.os.api_level` | Android-specific |
| `device.model.identifier` / `device.model.name` | No device model concept in browser |
| `app.build_name` / `app.build_id` | No native build system on web |
| `android.app.state` | Replaced by `page.visibility_state` via `visibilitychange` |
| `network.carrier.name` / `mcc` / `mnc` / `icc` | Carrier info not exposed to browsers |
| `storage.free` | `StorageManager` gives quota only, not free space |
| `thread.id` / `thread.name` | JS is single-threaded (Web Workers separate) |

---

## Done Criteria

- [ ] All static resource attributes present on every span: `service.name`, `rum.sdk.name`, `rum.sdk.version`, `project.id`, `installation.id`, `browser.name`, `browser.version`, `os.name`, `os.version`, `device.type`, `device.screen.width`, `device.screen.height`
- [ ] Dynamic attributes present on every span: `session.id`, `screen.name`, `url.path`, `page.url`, `network.connection.type`
- [ ] `project.id` correctly extracted from API key format
- [ ] `screen.name` resolution chain applied in order (manual → pattern → heuristic → raw path)
- [ ] `config.globalAttributes` merged into every signal
- [ ] Unit tests: all expected attributes present; fallback UA parsing works
