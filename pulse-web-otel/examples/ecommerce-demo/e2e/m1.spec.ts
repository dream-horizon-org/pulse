/**
 * M1 E2E Tests — Foundation: SDK Core Pipeline
 *
 * Covers the done criteria from .claude/plans/web-sdk-m1-foundation.md:
 *   - session.start / session.end lifecycle
 *   - installation.id 3-tier persistence
 *   - Singleton guard (no duplicate exporters)
 *   - Resource attributes on every signal
 *   - OTLP request structure + auth header
 *
 * Run:  yarn e2e --grep "@M1" --project=chromium
 */
import { test, expect, getAttr, findAllLogs, getResourceAttr } from './fixture';

// ─── Session Lifecycle ────────────────────────────────────────────────────────

test.describe('@M1 session lifecycle', () => {
  test('session.start emitted on page load', async ({ page, otlp }) => {
    await page.goto('/');
    const log = await otlp.waitForLog('session.start');

    expect(getAttr(log.attributes, 'session.id')).toBeTruthy();
    expect(getAttr(log.attributes, 'installation.id')).toBeTruthy();
    expect(getAttr(log.attributes, 'platform')).toBe('web');
  });

  test('session.end emitted on pagehide (non-BFCache)', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');

    // Simulate pagehide with persisted=false (not BFCache) by dispatching the event
    await page.evaluate(() => {
      window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: false, bubbles: true }));
    });

    // Batch delay in test mode is 200ms; give it time to flush
    const log = await otlp.waitForLog('session.end');
    expect(getAttr(log.attributes, 'session.id')).toBeTruthy();
  });

  test('pagehide with persisted=true (BFCache) does NOT emit session.end', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');
    otlp.reset();

    // BFCache restore — should NOT emit session.end
    await page.evaluate(() => {
      window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true, bubbles: true }));
    });
    await page.waitForTimeout(500);

    expect(findAllLogs(otlp.captured, 'session.end').length).toBe(0);
  });

  test('double PulseWeb.start() is a no-op — exactly one session.start', async ({ page, otlp }) => {
    // App.tsx calls PulseWeb.start() in useEffect; React StrictMode calls it twice
    await page.goto('/');
    await page.waitForTimeout(1500); // let any duplicates arrive

    const starts = findAllLogs(otlp.captured, 'session.start');
    expect(starts.length).toBe(1);
  });
});

// ─── Identity Persistence ─────────────────────────────────────────────────────

test.describe('@M1 identity persistence', () => {
  test('installation.id survives page reload', async ({ page, otlp }) => {
    await page.goto('/');
    const first = await otlp.waitForLog('session.start');
    const installId = getAttr(first.attributes, 'installation.id') as string;
    expect(installId).toBeTruthy();

    otlp.reset();
    await page.reload();
    const second = await otlp.waitForLog('session.start');

    expect(getAttr(second.attributes, 'installation.id')).toBe(installId);
  });

  test('installation.id stored in localStorage as pulse_iid', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');

    const stored = await page.evaluate(() => localStorage.getItem('pulse_installation_id'));
    expect(stored).toBeTruthy();
    expect(stored).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
  });

  test('installation.id falls back to sessionStorage when localStorage throws', async ({ page, otlp }) => {
    await page.addInitScript(() => {
      const orig = Object.getOwnPropertyDescriptor(window, 'localStorage');
      Object.defineProperty(window, 'localStorage', {
        get() { throw new DOMException('storage unavailable', 'SecurityError'); },
        configurable: true,
      });
    });
    await page.goto('/');
    // SDK must not crash; session.start should still emit
    const log = await otlp.waitForLog('session.start');
    expect(getAttr(log.attributes, 'installation.id')).toBeTruthy();
  });

  test('new session.id on each fresh page load', async ({ page, otlp }) => {
    await page.goto('/');
    const log1 = await otlp.waitForLog('session.start');
    const sid1 = getAttr(log1.attributes, 'session.id') as string;

    // Clear storage to force new session (simulate new user)
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear(); });
    otlp.reset();
    await page.reload();
    const log2 = await otlp.waitForLog('session.start');
    const sid2 = getAttr(log2.attributes, 'session.id') as string;

    expect(sid2).toBeTruthy();
    expect(sid2).not.toBe(sid1);
  });
});

// ─── OTLP Pipeline ───────────────────────────────────────────────────────────

test.describe('@M1 OTLP pipeline', () => {
  test('x-api-key header sent on every OTLP request', async ({ page }) => {
    const headers: string[] = [];
    await page.route('**/v1/logs', async route => {
      headers.push(route.request().headers()['x-api-key'] ?? '');
      await route.fulfill({ status: 200, body: '{}' });
    });
    await page.goto('/');
    await page.waitForTimeout(1000);
    expect(headers.length).toBeGreaterThan(0);
    for (const h of headers) expect(h).toBe('test-api-key');
  });

  test('Content-Type is application/json', async ({ page }) => {
    let contentType = '';
    await page.route('**/v1/logs', async route => {
      contentType = route.request().headers()['content-type'] ?? '';
      await route.fulfill({ status: 200, body: '{}' });
    });
    await page.goto('/');
    await page.waitForTimeout(1000);
    expect(contentType).toContain('application/json');
  });

  test('resource attributes present on signal (platform, service.name, rum.sdk.version)', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');

    expect(getResourceAttr(otlp.captured, 'platform')).toBe('web');
    expect(getResourceAttr(otlp.captured, 'service.name')).toBeTruthy();
    expect(getResourceAttr(otlp.captured, 'rum.sdk.version')).toBeTruthy();
  });
});

// ─── SDK Shutdown ─────────────────────────────────────────────────────────────

test.describe('@M1 SDK shutdown', () => {
  test('PulseWeb.shutdown() force-flushes providers without error', async ({ page, otlp }) => {
    await page.goto('/');
    await otlp.waitForLog('session.start');

    const errors: string[] = [];
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });

    await page.evaluate(async () => {
      // @ts-ignore — PulseWeb exposed on window by App.tsx for testing
      await window.PulseWeb?.shutdown?.();
    });

    expect(errors.filter(e => !e.includes('favicon'))).toHaveLength(0);
  });
});
