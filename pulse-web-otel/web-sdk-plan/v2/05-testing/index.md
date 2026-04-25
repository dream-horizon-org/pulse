# Phase 6 — Testing & Quality

**Goal:** Establish a comprehensive test suite covering unit correctness, real-browser behaviour, cross-browser compatibility, and performance budgets — all running in CI.

**Estimated duration:** Week 9–10 (runs in parallel with Phase 5)
**Prerequisites:** Phases 1–4 code complete. Phase 5 build pipeline in place.

---

## Scope

**In:**
- Vitest unit test suite for all instrumentations and utilities
- Vitest + JSDOM integration tests for SDK lifecycle
- Playwright e2e tests in real Chrome (headless)
- Playwright cross-browser: Firefox, WebKit
- BrowserStack device/OS matrix (Chrome Android, iOS Safari)
- Lighthouse CI bundle size tracking
- Mock OTLP receiver for assertion-based signal testing

**Out:**
- Load/stress testing (deferred)
- Accessibility testing (not in scope for SDK)

---

## Deliverable

A `pnpm test` command that runs all unit and integration tests in under 30 seconds. A separate `pnpm test:e2e` that runs Playwright tests. CI is green on every PR. BrowserStack suite runs on merge to `main`.

---

## Implementation Steps

### 1. Unit Test Setup (Vitest)

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/**'],
      thresholds: { lines: 80, branches: 75 },
    },
  },
});
```

**`tests/setup.ts`** — global mocks:
```typescript
// Mock performance.now() for deterministic timing tests
vi.spyOn(performance, 'now').mockImplementation(() => Date.now());

// Mock localStorage / sessionStorage (jsdom provides these)
// Mock navigator.sendBeacon
vi.stubGlobal('navigator', {
  ...navigator,
  sendBeacon: vi.fn().mockReturnValue(true),
});
```

---

### 2. Unit Test Coverage Map

| Module | Tests |
|---|---|
| `session.ts` | Installation ID persistence; session ID rotation after 30 min; new session on first call |
| `resource.ts` | All resource attributes present; `project.id` extracted correctly; fallback for missing UA |
| `config.ts` | Invalid config throws; defaults applied; `beforeSendData` returning null drops signal |
| `sdk.ts` | Double `start()` is no-op; `shutdown()` flushes; consent DENIED drops all signals |
| `instrumentations/errors.ts` | `window.onerror` → `device.crash` log record with stack; `unhandledrejection` → `non_fatal` |
| `instrumentations/network.ts` | Pulse OTLP endpoints excluded from tracing; GraphQL op name extracted |
| `instrumentations/interactions-ui.ts` | Rage click: 3 clicks in 700ms → `click.is_rage: true`; dead click detection |
| `instrumentations/web-vitals.ts` | Each metric emitted as gauge with correct `metric.rating` |
| `instrumentations/navigation.ts` | `pushState` → `screen_session` span ends and new one starts |
| `instrumentations/long-tasks.ts` | PerformanceObserver entry → `app.jank.slow` log record |
| `instrumentations/interaction/util.ts` | All 6 operators; timeout; blacklist abort (see Phase 2.5 for full matrix) |
| `instrumentations/interaction/tracker.ts` | State machine: IDLE → ONGOING → COMPLETE / ERROR |
| `instrumentations/session-replay/recorder.ts` | Batch flush interval; `sendBeacon` on pagehide; masked inputs not in events |

---

### 3. Mock OTLP Receiver

A lightweight test server that captures OTLP requests for assertion:

```typescript
// tests/helpers/mock-otlp-receiver.ts
import { createServer } from 'http';

export class MockOtlpReceiver {
  spans: any[] = [];
  logs: any[] = [];
  metrics: any[] = [];
  private server: http.Server;

  async start(port = 4318): Promise<void> {
    this.server = createServer((req, res) => {
      let body = '';
      req.on('data', d => body += d);
      req.on('end', () => {
        const payload = JSON.parse(body);
        if (req.url === '/v1/traces') this.spans.push(...extractSpans(payload));
        if (req.url === '/v1/logs')   this.logs.push(...extractLogs(payload));
        if (req.url === '/v1/metrics') this.metrics.push(...extractMetrics(payload));
        res.writeHead(200).end('{}');
      });
    });
    await new Promise(r => this.server.listen(port, r));
  }

  reset(): void {
    this.spans = []; this.logs = []; this.metrics = [];
  }

  async stop(): Promise<void> {
    await new Promise(r => this.server.close(r));
  }
}
```

---

### 4. Playwright E2E Setup

```typescript
// playwright.config.ts
export default defineConfig({
  testDir: './tests/e2e',
  projects: [
    { name: 'chromium', use: devices['Desktop Chrome'] },
    { name: 'firefox',  use: devices['Desktop Firefox'] },
    { name: 'webkit',   use: devices['Desktop Safari'] },
  ],
  webServer: {
    command: 'pnpm --filter example-react dev',
    port: 5173,
  },
});
```

**Example E2E test:**
```typescript
test('click tracking emits app.click log record', async ({ page }) => {
  const receiver = new MockOtlpReceiver();
  await receiver.start();

  await page.goto('http://localhost:5173');
  await page.click('[data-testid="track-me"]');
  await page.waitForTimeout(6000);  // wait for batch flush

  const clickLog = receiver.logs.find(l => l['pulse.type'] === 'app.click');
  expect(clickLog).toBeDefined();
  expect(clickLog['app.click.context']).toBeTruthy();

  await receiver.stop();
});
```

---

### 5. E2E Test Matrix

| Test | Chrome | Firefox | WebKit |
|---|---|---|---|
| SDK initialises without errors | ✓ | ✓ | ✓ |
| Span reaches mock OTLP receiver | ✓ | ✓ | ✓ |
| Fetch request creates HTTP span | ✓ | ✓ | ✓ |
| Click creates `app.click` log | ✓ | ✓ | ✓ |
| JS error creates `device.crash` log | ✓ | ✓ | ✓ |
| Route change creates `screen_session` span | ✓ | ✓ | ✓ |
| Interaction sequence match creates span | ✓ | ✓ | ✓ |
| Long task creates `app.jank.slow` log | ✓ | — | — |
| Web Vitals emitted on page load | ✓ | ✓ | ✓ |
| Session replay records and sends chunks | ✓ | ✓ | ✓ |

Long task (`longtask` PerformanceObserver) is Chromium-only — expected skip in Firefox/WebKit.

---

### 6. BrowserStack Suite

Run on merge to `main` (not on every PR — too slow):

**Device matrix:**

| Device | Browser | Version |
|---|---|---|
| Windows 11 | Chrome | Latest |
| Windows 11 | Edge | Latest |
| macOS Sonoma | Safari | 17 |
| macOS Sonoma | Chrome | Latest |
| iPhone 15 | Safari | iOS 17 |
| Samsung Galaxy S23 | Chrome | Latest |

BrowserStack Automate with Playwright:
```yaml
# .github/workflows/browserstack.yml
on:
  push:
    branches: [main]
jobs:
  browserstack:
    env:
      BROWSERSTACK_USERNAME: ${{ secrets.BS_USERNAME }}
      BROWSERSTACK_ACCESS_KEY: ${{ secrets.BS_ACCESS_KEY }}
    steps:
      - pnpm test:e2e:browserstack
```

---

### 7. Lighthouse CI Bundle Tracking

```yaml
# lighthouserc.js
module.exports = {
  ci: {
    assert: {
      assertions: {
        'uses-rel-preload': 'off',
        'total-byte-weight': ['error', { maxNumericValue: 80000 }],  // 80 KB max
      },
    },
  },
};
```

Tracks bundle impact on a reference page over time. Fails PR if bundle causes Lighthouse score regression.

---

## Testing Cycle Summary

| Suite | When | Time | Failure action |
|---|---|---|---|
| Unit (Vitest) | Every PR | < 10s | Block merge |
| Integration (Vitest + JSDOM) | Every PR | < 20s | Block merge |
| Bundle size (size-limit) | Every PR | < 5s | Block merge |
| E2E Chrome (Playwright) | Every PR | ~2 min | Block merge |
| E2E Firefox + WebKit | Every PR | ~4 min | Block merge |
| BrowserStack | Merge to main | ~15 min | Post-merge alert |
| Lighthouse CI | Merge to main | ~5 min | Post-merge alert |

---

## Done Criteria

- [ ] Unit test coverage ≥ 80% lines, ≥ 75% branches
- [ ] All unit tests pass in < 30s
- [ ] Playwright e2e passes in Chrome, Firefox, WebKit
- [ ] BrowserStack passes on iPhone Safari and Chrome Android
- [ ] Bundle size < 30 KB gzip for core SDK (enforced by size-limit in CI)
- [ ] Zero regressions introduced by any Phase 1–4 feature

---

## Known Risks

- **JSDOM limitations**: some browser APIs (PerformanceObserver, IntersectionObserver) need manual mocks in JSDOM. Document all mocks in `tests/setup.ts`.
- **Flaky Playwright tests**: timer-dependent tests (batch flush after 5s) can be flaky. Use mock timers (`vi.useFakeTimers()`) in unit tests; use `waitForRequest` matchers in Playwright instead of `waitForTimeout`.
- **BrowserStack quota**: if the team doesn't have a BrowserStack account, substitute with Playwright's built-in WebKit for Safari coverage.
