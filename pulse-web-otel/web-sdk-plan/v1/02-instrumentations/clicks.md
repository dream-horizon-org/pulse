# 02.3 — Click Tracking Instrumentation

**Goal:** Capture every user click as a log record with normalised coordinates, element context, rage click detection, and dead click detection — matching the full attribute set of the Android/iOS SDKs.

**File:** `src/instrumentations/clicks.ts`
**Android equivalent:** `ViewClickInstrumentation`, `ComposeClickInstrumentation`

---

## Signals Produced

### `pulse.type: app.click` — one log record per click

| Attribute | Type | Source | Android Equivalent |
|---|---|---|---|
| `pulse.type` | string | `"app.click"` | `pulse.type: app.click` |
| `app.screen.coordinate.x` | long | `MouseEvent.clientX` | `app.screen.coordinate.x` |
| `app.screen.coordinate.y` | long | `MouseEvent.clientY` | `app.screen.coordinate.y` |
| `app.screen.coordinate.nx` | double | `clientX / window.innerWidth` (0.0–1.0) | `app.screen.coordinate.nx` |
| `app.screen.coordinate.ny` | double | `clientY / window.innerHeight` (0.0–1.0) | `app.screen.coordinate.ny` |
| `click.type` | string | `"good"` or `"dead"` | `click.type` |
| `click.is_rage` | boolean | 3+ clicks on same target within 700ms | `click.is_rage` |
| `click.rage_count` | long | Count of clicks in rage cluster | `click.rage_count` |
| `app.click.context` | string | `aria-label` → `data-testid` → `innerText` (max 64 chars) | `app.click.context` |
| `app.widget.name` | string | `tagName.className` (e.g. `"button.submit-btn"`) | `app.widget.name` |
| `app.widget.id` | string | `element.id` | `app.widget.id` |
| `device.screen.width` | long | `screen.width` | `device.screen.width` |
| `device.screen.height` | long | `screen.height` | `device.screen.height` |

All 13 attributes are portable from Android. ✅

---

## Implementation

```typescript
// src/instrumentations/clicks.ts

export class ClickInstrumentation {
  private rage = new RageClickDetector();
  private dead = new DeadClickDetector();

  install(): void {
    // Capture phase (true) — fires before any app handlers, never missed
    document.addEventListener('click', this.onClick, { capture: true });
  }

  uninstall(): void {
    document.removeEventListener('click', this.onClick, { capture: true });
  }

  private onClick = (e: MouseEvent): void => {
    const target = getNearestInteractiveElement(e.target as Element);
    if (!target) return;

    const isRage = this.rage.track(target);
    // Dead click is resolved async (1s after click)
    this.dead.track(target, (isDead) => {
      emitLogRecord({
        'pulse.type':                  'app.click',
        'app.screen.coordinate.x':     Math.round(e.clientX),
        'app.screen.coordinate.y':     Math.round(e.clientY),
        'app.screen.coordinate.nx':    +(e.clientX / window.innerWidth).toFixed(4),
        'app.screen.coordinate.ny':    +(e.clientY / window.innerHeight).toFixed(4),
        'click.type':                  isDead ? 'dead' : 'good',
        'click.is_rage':               isRage,
        'click.rage_count':            isRage ? this.rage.count : 0,
        'app.click.context':           getClickLabel(target),
        'app.widget.name':             getWidgetName(target),
        'app.widget.id':               target.id ?? '',
        'device.screen.width':         screen.width,
        'device.screen.height':        screen.height,
      });
    });
  };
}

// ─── Rage Click ──────────────────────────────────────────────────────────────
class RageClickDetector {
  count = 0;
  private history: { target: Element; time: number }[] = [];

  track(target: Element): boolean {
    const now = Date.now();
    // Keep only clicks within 700ms on the same element
    this.history = this.history.filter(
      c => now - c.time < 700 && c.target === target
    );
    this.history.push({ target, time: now });
    this.count = this.history.length;
    return this.count >= 3;
  }
}

// ─── Dead Click ──────────────────────────────────────────────────────────────
// A click is "dead" if no DOM change, navigation, or network request happens
// within 1 second of the click.
class DeadClickDetector {
  track(target: Element, callback: (isDead: boolean) => void): void {
    let reacted = false;

    const mutationObs = new MutationObserver(() => { reacted = true; });
    mutationObs.observe(document.body, {
      childList: true, subtree: true, attributes: true, characterData: true,
    });

    // Navigation also counts as a reaction
    const onNav = () => { reacted = true; };
    window.addEventListener('popstate', onNav, { once: true });

    setTimeout(() => {
      mutationObs.disconnect();
      window.removeEventListener('popstate', onNav);
      callback(!reacted);
    }, 1000);
  }
}

// ─── Label extraction ─────────────────────────────────────────────────────────
// Priority: aria-label → data-testid → button/link text → parent text
function getClickLabel(el: Element): string {
  const candidates = [
    el.getAttribute('aria-label'),
    el.getAttribute('data-testid'),
    el.getAttribute('data-test-id'),
    el.getAttribute('title'),
    (el as HTMLElement).innerText?.trim(),
    el.getAttribute('alt'),           // images
    el.getAttribute('placeholder'),   // inputs
  ];
  const label = candidates.find(c => c && c.trim().length > 0) ?? '';
  return label.slice(0, 64);
}

function getWidgetName(el: Element): string {
  const tag = el.tagName.toLowerCase();
  const classes = Array.from(el.classList)
    .filter(c => !c.startsWith('pulse-'))   // exclude our own CSS classes
    .slice(0, 3)
    .join('.');
  return classes ? `${tag}.${classes}` : tag;
}

// Walk up DOM to find the nearest interactive element
function getNearestInteractiveElement(el: Element | null): Element | null {
  const interactive = ['a', 'button', 'input', 'select', 'textarea', 'label'];
  while (el && el !== document.body) {
    if (interactive.includes(el.tagName.toLowerCase())) return el;
    if ((el as HTMLElement).onclick) return el;
    if (el.getAttribute('role') === 'button') return el;
    el = el.parentElement;
  }
  return el;  // fall back to whatever was clicked
}
```

---

## Edge Cases

| Case | Handling |
|---|---|
| Click inside SVG | `el.tagName` may be uppercase or namespaced — normalise with `.toLowerCase()` |
| Shadow DOM elements | `e.composedPath()[0]` to get true target through shadow root |
| Clicks on `<canvas>` | No useful DOM label available; `app.click.context` will be empty |
| `window.innerWidth === 0` during SSR | Guard: if `innerWidth === 0`, set `nx/ny` to `0` |
| Rage on rapidly re-rendered element | Compare by `target` reference — re-rendered elements are new nodes |
| Dead click + CSS animation | CSS changes don't trigger MutationObserver — may false-positive as dead |

---

## Testing

### Unit Tests (Vitest + JSDOM)

```typescript
it('emits app.click with normalised coordinates', () => {
  Object.defineProperty(window, 'innerWidth', { value: 1000 });
  Object.defineProperty(window, 'innerHeight', { value: 800 });
  const records = captureLogRecords();
  document.dispatchEvent(new MouseEvent('click', { clientX: 500, clientY: 400, bubbles: true }));
  expect(records[0]['app.screen.coordinate.nx']).toBe(0.5);
  expect(records[0]['app.screen.coordinate.ny']).toBe(0.5);
});

it('detects rage click after 3 rapid clicks', () => {
  const detector = new RageClickDetector();
  const el = document.createElement('button');
  expect(detector.track(el)).toBe(false); // 1st
  expect(detector.track(el)).toBe(false); // 2nd
  expect(detector.track(el)).toBe(true);  // 3rd — rage!
  expect(detector.count).toBe(3);
});

it('extracts aria-label as click context', () => {
  const el = document.createElement('button');
  el.setAttribute('aria-label', 'Submit order');
  expect(getClickLabel(el)).toBe('Submit order');
});

it('truncates long labels to 64 chars', () => {
  const el = document.createElement('button');
  el.textContent = 'A'.repeat(100);
  expect(getClickLabel(el).length).toBe(64);
});
```

### E2E (Playwright)

```typescript
test('click on button emits app.click log record', async ({ page }) => {
  await page.goto('/test-page');
  await page.click('[data-testid="buy-now"]');
  await page.waitForTimeout(1500); // wait for dead click timeout + flush
  const log = await waitForLog(receiver, 'app.click');
  expect(log['app.click.context']).toBe('buy-now');
  expect(log['click.type']).toBe('good');
});

test('3 rapid clicks triggers rage detection', async ({ page }) => {
  await page.goto('/test-page');
  await page.click('button', { clickCount: 3, delay: 100 });
  const log = await waitForLog(receiver, 'app.click', { 'click.is_rage': true });
  expect(log['click.rage_count']).toBeGreaterThanOrEqual(3);
});
```

---

## Done Criteria

- [ ] Every click emits `app.click` with `x/y`, `nx/ny`, `click.type`, `app.click.context`, `app.widget.name`
- [ ] 3+ clicks on same element within 700ms → `click.is_rage: true`
- [ ] Click with no DOM reaction within 1s → `click.type: 'dead'`
- [ ] `app.click.context` priority: `aria-label` → `data-testid` → `innerText`
- [ ] Label truncated at 64 characters
- [ ] Coordinates normalised correctly (0.0–1.0)
- [ ] All unit tests passing
