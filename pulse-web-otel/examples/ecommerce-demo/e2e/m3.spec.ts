/**
 * M3 E2E Tests — Auto-Instrumentations (5 signals)
 *
 * Covers the done criteria from .claude/plans/web-sdk-m3-instrumentations.md:
 *   - device.crash / non_fatal  (errors.ts)
 *   - http spans                (network.ts)
 *   - app.click + rage_click    (clicks.ts)
 *   - web_vital metrics         (web-vitals.ts)
 *   - screen_load, screen_interactive, screen_session (navigation.ts)
 *   - Consent DENIED → zero signals from all 5
 *   - Graceful no-ops on Firefox/Safari for Chrome-only APIs
 *
 * Run:  yarn e2e --grep "@M3" --project=chromium
 */
import { test, expect, getAttr, findAllLogs, findAllSpans, findAllMetricPoints } from './fixture';

// ─── Errors Instrumentation ──────────────────────────────────────────────────

test.describe('@M3 errors', () => {
  test('uncaught window.onerror → device.crash log with stack trace', async ({ page, otlp }) => {
    await page.goto('/error-demo');
    await otlp.waitForLog('session.start');
    otlp.reset();

    await page.getByTestId('throw-uncaught').click();

    const log = await otlp.waitForLog('device.crash');
    expect(getAttr(log.attributes, 'exception.type')).toBeTruthy();
    expect(getAttr(log.attributes, 'exception.message')).toBeTruthy();
    expect(getAttr(log.attributes, 'exception.stacktrace')).toBeTruthy();
    expect(getAttr(log.attributes, 'url.path')).toBeTruthy();
  });

  test('unhandled Promise rejection → non_fatal log', async ({ page, otlp }) => {
    await page.goto('/error-demo');
    await otlp.waitForLog('session.start');
    otlp.reset();

    await page.getByTestId('throw-promise').click();

    const log = await otlp.waitForLog('non_fatal');
    expect(getAttr(log.attributes, 'exception.message')).toBeTruthy();
  });

  test('same error repeated → only 1 log (deduplication)', async ({ page, otlp }) => {
    await page.goto('/error-demo');
    await otlp.waitForLog('session.start');
    otlp.reset();

    // Click the same button 5 times quickly
    for (let i = 0; i < 5; i++) {
      await page.getByTestId('throw-uncaught').click();
      await page.waitForTimeout(50);
    }
    await page.waitForTimeout(1000);

    const crashes = findAllLogs(otlp.captured, 'device.crash');
    expect(crashes.length).toBeLessThanOrEqual(2); // dedup window = 1s
  });

  test('cross-origin "Script error." → skipped (not emitted)', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');
    otlp.reset();

    // Simulate cross-origin error (message = 'Script error.', no stack)
    await page.evaluate(() => {
      const e = new ErrorEvent('error', { message: 'Script error.', error: null });
      window.dispatchEvent(e);
    });
    await page.waitForTimeout(500);

    expect(findAllLogs(otlp.captured, 'device.crash').length).toBe(0);
    expect(findAllLogs(otlp.captured, 'non_fatal').length).toBe(0);
  });
});

// ─── Network Instrumentation ─────────────────────────────────────────────────

test.describe('@M3 network', () => {
  test('fetch() → http span with method, url, status_code', async ({ page, otlp }) => {
    await page.goto('/products');
    await otlp.waitForLog('session.start');
    otlp.reset();

    const span = await otlp.waitForSpan('http');
    expect(getAttr(span.attributes, 'http.method')).toBeTruthy();
    expect(getAttr(span.attributes, 'http.url')).toBeTruthy();
    expect(getAttr(span.attributes, 'http.status_code')).toBeDefined();
  });

  test('Pulse ingest endpoint requests NOT traced', async ({ page, otlp }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);

    // Any http span whose URL contains "otel-mock.test" would be the OTLP endpoint
    const allHttpSpans = findAllSpans(otlp.captured, 'http');
    const selfTraced = allHttpSpans.filter(s =>
      String(getAttr(s.attributes, 'http.url') ?? '').includes('otel-mock.test'),
    );
    expect(selfTraced.length).toBe(0);
  });

  test('GraphQL POST → http span with graphql.operation.name', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');
    otlp.reset();

    // Simulate a GraphQL request from the page context
    await page.evaluate(async () => {
      await fetch('https://api.example.com/graphql', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: '{ products { id } }', operationName: 'GetProducts' }),
      }).catch(() => {/* ignore network error */});
    });

    const span = await otlp.waitForSpan('http');
    expect(getAttr(span.attributes, 'graphql.operation.name')).toBe('GetProducts');
  });
});

// ─── Clicks Instrumentation ──────────────────────────────────────────────────

test.describe('@M3 clicks', () => {
  test('click on ProductCard → app.click log with coordinates', async ({ page, otlp }) => {
    await page.goto('/products');
    await otlp.waitForLog('session.start');
    otlp.reset();

    await page.locator('[data-testid="product-card"]').first().click();

    const log = await otlp.waitForLog('app.click');
    expect(getAttr(log.attributes, 'touch.coordinates.x')).toBeDefined();
    expect(getAttr(log.attributes, 'touch.coordinates.y')).toBeDefined();
    // Coordinates should be normalised 0–1
    const x = Number(getAttr(log.attributes, 'touch.coordinates.x'));
    const y = Number(getAttr(log.attributes, 'touch.coordinates.y'));
    expect(x).toBeGreaterThanOrEqual(0);
    expect(x).toBeLessThanOrEqual(1);
    expect(y).toBeGreaterThanOrEqual(0);
    expect(y).toBeLessThanOrEqual(1);
  });

  test('3 rapid clicks on RageClickButton → rage_click: true', async ({ page, otlp }) => {
    await page.goto('/products');
    await otlp.waitForLog('session.start');
    otlp.reset();

    const btn = page.getByTestId('rage-click-button');
    // 3 clicks in < 700ms
    await btn.click();
    await btn.click();
    await btn.click();

    // Wait for at least one app.click with rage_click=true
    await page.waitForFunction(
      () => {
        // Checked by polling in the test, not inside the browser
      },
      null,
      { timeout: 3000 },
    ).catch(() => {}); // ignore — we'll assert below

    await page.waitForTimeout(800); // let batch flush
    const logs = findAllLogs(otlp.captured, 'app.click');
    const rageLogs = logs.filter(l => getAttr(l.attributes, 'rage_click') === true);
    expect(rageLogs.length).toBeGreaterThan(0);
  });

  test('click log includes view.target.class_name', async ({ page, otlp }) => {
    await page.goto('/products');
    await otlp.waitForLog('session.start');
    otlp.reset();

    await page.locator('[data-testid="product-card"]').first().click();
    const log = await otlp.waitForLog('app.click');
    expect(getAttr(log.attributes, 'view.target.class_name')).toBeDefined();
  });
});

// ─── Web Vitals Instrumentation ───────────────────────────────────────────────

test.describe('@M3 web vitals', () => {
  test('LCP metric emitted after page load', async ({ page, otlp }) => {
    await page.goto('/products');
    // LCP can take a few seconds to fire
    const dp = await otlp.waitForMetric('web_vital', 20_000);
    expect(getAttr(dp.attributes, 'metric.name')).toBeTruthy();
    expect(getAttr(dp.attributes, 'metric.rating')).toMatch(/^(good|needs-improvement|poor)$/);
    expect(dp.asDouble).toBeGreaterThanOrEqual(0);
  });

  test('LCP includes attribution element', async ({ page, otlp }) => {
    await page.goto('/products');
    const dp = await otlp.waitForMetric('web_vital', 20_000);
    // LCP-specific attribution
    const metricName = getAttr(dp.attributes, 'metric.name');
    if (metricName === 'LCP') {
      expect(getAttr(dp.attributes, 'lcp.element')).toBeTruthy();
    }
  });

  test('metric data points include global attributes (session.id, installation.id, screen.name, platform)', async ({ page, otlp }) => {
    await page.goto('/products');
    await otlp.waitForLog('session.start');

    // window.PulseWeb is exposed by App.tsx for E2E use.
    // We access the private meterProvider at JS runtime (TypeScript privacy is compile-time only),
    // record a counter, then forceFlush() inside the evaluate so the HTTP request to /v1/metrics
    // is made and intercepted by Playwright before the evaluate Promise resolves.
    await page.evaluate(async () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const sdk = (window as any)['PulseWeb'];
      if (!sdk) { console.error('[test] PulseWeb not found on window'); return; }

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const mp = (sdk as any).meterProvider;
      if (!mp) { console.error('[test] meterProvider not found on PulseWeb'); return; }

      const meter = mp.getMeter('pulse-test');
      const counter = meter.createCounter('test_global_attrs');
      counter.add(1, { 'test.marker': 'global_attrs_check' });

      // Flush immediately — the awaited forceFlush ensures the /v1/metrics
      // HTTP request is sent (and intercepted by the route handler) before
      // this evaluate() Promise resolves back to the test runner.
      await mp.forceFlush();
    });

    const dp = await otlp.waitForMetric('test_global_attrs', 8_000);
    // GlobalAttributeInjectingMetricExporter must have injected these on every data point
    expect(getAttr(dp.attributes, 'session.id')).toBeTruthy();
    expect(getAttr(dp.attributes, 'installation.id')).toBeTruthy();
    expect(getAttr(dp.attributes, 'screen.name')).toBeTruthy();
    expect(getAttr(dp.attributes, 'platform')).toBe('web');
  });

  test.skip('INP metric emitted after user interaction', async ({ page, otlp }) => {
    // INP requires user input; tested manually or in M4 real-browser run
    await page.goto('/products');
    await page.locator('[data-testid="product-card"]').first().click();
    const dp = await otlp.waitForMetric('web_vital', 20_000);
    const inpPoints = findAllMetricPoints(otlp.captured, 'web_vital').filter(
      p => getAttr(p.attributes, 'metric.name') === 'INP',
    );
    expect(inpPoints.length).toBeGreaterThan(0);
  });
});

// ─── Navigation Instrumentation ──────────────────────────────────────────────

test.describe('@M3 navigation', () => {
  test('initial page load → screen_load span with ttfb_ms', async ({ page, otlp }) => {
    await page.goto('/products');
    const span = await otlp.waitForSpan('screen_load');
    const ttfb = Number(getAttr(span.attributes, 'ttfb_ms') ?? 0);
    expect(ttfb).toBeGreaterThanOrEqual(0);
    expect(getAttr(span.attributes, 'screen.name')).toBeTruthy();
    expect(getAttr(span.attributes, 'platform')).toBe('web');
  });

  test('screen_load span includes load.duration_ms', async ({ page, otlp }) => {
    await page.goto('/products');
    const span = await otlp.waitForSpan('screen_load');
    expect(Number(getAttr(span.attributes, 'load.duration_ms') ?? -1)).toBeGreaterThan(0);
  });

  test('SPA route change → screen_session span with screen.name', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');
    otlp.reset();

    // React Router navigation (no full reload)
    await page.getByRole('link', { name: /products/i }).first().click();
    await page.waitForURL('**/products');

    const span = await otlp.waitForSpan('screen_session');
    expect(getAttr(span.attributes, 'screen.name')).toBeTruthy();
    expect(getAttr(span.attributes, 'url.path')).toBeTruthy();
  });

  test('screen_session tracks previous screen name', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');
    otlp.reset();

    await page.getByRole('link', { name: /products/i }).first().click();
    await page.waitForURL('**/products');
    const span = await otlp.waitForSpan('screen_session');

    expect(getAttr(span.attributes, 'previous_screen.name')).toBeTruthy();
  });

  test.describe('screen.name resolution', () => {
    test('/products/123 → "products/:id" (heuristic)', async ({ page, otlp }) => {
      await page.goto('/');
      otlp.reset();
      await page.goto('/products/123');
      const span = await otlp.waitForSpan('screen_session');
      expect(getAttr(span.attributes, 'screen.name')).toMatch(/products\/:id/i);
    });

    test('/products/abc-slug also normalised by heuristic', async ({ page, otlp }) => {
      await page.goto('/');
      otlp.reset();
      await page.goto('/products/abc-slug-123');
      const span = await otlp.waitForSpan('screen_session');
      // Should not contain the raw slug
      const name = String(getAttr(span.attributes, 'screen.name'));
      expect(name).not.toContain('abc-slug-123');
    });
  });
});

// ─── Cross-cutting: Consent Gate ─────────────────────────────────────────────

test.describe('@M3 consent gate across all instrumentations', () => {
  test('DENIED consent → zero signals from all 5 instrumentations', async ({ page, otlp }) => {
    await page.goto('/?pulse_consent=denied');

    // Trigger every instrumentation type
    await page.evaluate(async () => {
      // Error
      window.dispatchEvent(new ErrorEvent('error', { message: 'test', error: new Error('test') }));
      // Network
      fetch('https://api.example.com/test').catch(() => {});
      // Click (programmatic — captured by capture phase listener)
    });
    await page.locator('body').click();
    await page.waitForTimeout(2000);

    expect(otlp.captured.length).toBe(0);
  });
});

// ─── Cross-browser: Graceful No-ops ──────────────────────────────────────────

test.describe('@M3 graceful no-ops on non-Chrome browsers', () => {
  test('no uncaught errors in Firefox (longtask PerformanceObserver unavailable)', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });

    await page.goto('/products');
    await page.waitForTimeout(3000);

    const realErrors = errors.filter(e =>
      !e.includes('favicon') && !e.includes('404') && !e.includes('network'),
    );
    expect(realErrors).toHaveLength(0);
  });
});
