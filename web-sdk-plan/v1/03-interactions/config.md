# 03.1 — Interaction Config Fetcher

**Goal:** Fetch and cache the interaction definition JSON from CloudFront CDN at SDK init, refresh in the background every 30 minutes, and expose the config to the interaction matching engine (03.2).

**File:** `src/interactions/config-fetcher.ts`
**Android equivalent:** `InteractionConfigFetcher.kt` — exact port with `fetch()` replacing Retrofit

---

## Config Schema

The server delivers an array of interaction definitions:

```typescript
interface InteractionConfig {
  interactions: InteractionDefinition[];
}

interface InteractionDefinition {
  id: string;                     // e.g. "checkout_flow"
  name: string;                   // Human-readable label
  steps: InteractionStep[];       // Ordered steps to match
  timeout_ms: number;             // Max time to complete (ms)
  apdex_threshold_ms: number;     // Satisfactory duration threshold
}

interface InteractionStep {
  event_name: string;             // Must match PulseSDK.trackEvent() call
  attributes?: Record<string, string | number | boolean>;  // Optional filter
  required: boolean;              // If false, step is optional
}
```

### Example Config

```json
{
  "interactions": [
    {
      "id": "checkout_flow",
      "name": "Checkout Flow",
      "timeout_ms": 120000,
      "apdex_threshold_ms": 5000,
      "steps": [
        { "event_name": "cart_viewed",     "required": true },
        { "event_name": "checkout_started","required": true },
        { "event_name": "payment_entered", "required": false },
        { "event_name": "order_placed",    "required": true }
      ]
    }
  ]
}
```

---

## Implementation

```typescript
// src/interactions/config-fetcher.ts

const CACHE_KEY = 'pulse_interaction_config';
const REFRESH_INTERVAL_MS = 30 * 60 * 1000;  // 30 minutes

export class InteractionConfigFetcher {
  private config: InteractionConfig | null = null;
  private refreshTimer?: ReturnType<typeof setTimeout>;
  private listeners: Array<(config: InteractionConfig) => void> = [];

  constructor(
    private readonly configUrl: string,   // e.g. https://cdn.pulse.io/config/{projectId}.json
    private readonly projectId: string,
  ) {}

  async init(): Promise<void> {
    // 1. Load from cache immediately (non-blocking for app startup)
    this.loadFromCache();

    // 2. Fetch fresh config in background
    await this.refresh();

    // 3. Schedule periodic refresh
    this.scheduleRefresh();
  }

  getConfig(): InteractionConfig | null {
    return this.config;
  }

  /** Subscribe to config updates (called when fresh config arrives) */
  onChange(listener: (config: InteractionConfig) => void): void {
    this.listeners.push(listener);
  }

  destroy(): void {
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
  }

  // ─── Private ──────────────────────────────────────────────────────────────

  private async refresh(): Promise<void> {
    try {
      const response = await fetch(this.configUrl, {
        headers: { 'x-pulse-project-id': this.projectId },
        cache: 'no-store',   // Always get fresh from CloudFront
      });

      if (!response.ok) {
        console.warn(`[Pulse] Config fetch failed: ${response.status}`);
        return;
      }

      const json: InteractionConfig = await response.json();
      this.setConfig(json);
      this.saveToCache(json);
    } catch (err) {
      // Network failure — keep using cached config
      console.warn('[Pulse] Config fetch error:', err);
    }
  }

  private setConfig(config: InteractionConfig): void {
    this.config = config;
    this.listeners.forEach(fn => fn(config));
  }

  private loadFromCache(): void {
    try {
      const raw = localStorage.getItem(CACHE_KEY);
      if (raw) {
        this.config = JSON.parse(raw) as InteractionConfig;
      }
    } catch {
      // Corrupt cache — ignore
    }
  }

  private saveToCache(config: InteractionConfig): void {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(config));
    } catch {
      // localStorage full or blocked (private browsing) — no-op
    }
  }

  private scheduleRefresh(): void {
    this.refreshTimer = setTimeout(() => {
      this.refresh().then(() => this.scheduleRefresh());
    }, REFRESH_INTERVAL_MS);
  }
}
```

---

## Config URL Construction

```typescript
// In SDK init
const configUrl = `${config.cdnBaseUrl}/interactions/${config.projectId}.json`;
```

Example: `https://cdn.pulse.io/interactions/proj_abc123.json`

The CDN CloudFront distribution is already used by the Android/iOS SDKs for the same purpose — no infrastructure change needed.

---

## Edge Cases

| Case | Handling |
|---|---|
| First load — no cache, network slow | SDK starts with `config: null`; interaction matching is a no-op until config arrives |
| Corrupt localStorage cache | `JSON.parse` catch block discards it; fresh fetch proceeds |
| localStorage blocked (private browsing, storage quota) | Caught and ignored; always falls back to in-memory |
| CDN returns 404 (project has no interactions) | Treated as empty config `{ interactions: [] }` |
| CDN returns 5xx | Logs warning; keeps using cached config |
| Config changes between refreshes | `onChange` listeners are called with new config; existing in-progress interactions continue using old config until completed or timed out |
| SSR (server-side rendering) | Guard with `typeof window !== 'undefined'` before accessing `localStorage` and `fetch` |

---

## Testing

### Unit Tests (Vitest)

```typescript
it('loads config from cache on init', async () => {
  localStorage.setItem('pulse_interaction_config', JSON.stringify(mockConfig));
  vi.spyOn(global, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify(mockConfig)));

  const fetcher = new InteractionConfigFetcher('https://cdn.test/config.json', 'proj_test');
  await fetcher.init();

  expect(fetcher.getConfig()?.interactions).toHaveLength(1);
});

it('calls onChange listeners when fresh config arrives', async () => {
  const newConfig = { interactions: [{ id: 'new_flow' }] };
  vi.spyOn(global, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify(newConfig)));

  const fetcher = new InteractionConfigFetcher('https://cdn.test/config.json', 'proj_test');
  const received: InteractionConfig[] = [];
  fetcher.onChange(cfg => received.push(cfg));
  await fetcher.init();

  expect(received).toHaveLength(1);
  expect(received[0].interactions[0].id).toBe('new_flow');
});

it('handles fetch failure gracefully', async () => {
  localStorage.setItem('pulse_interaction_config', JSON.stringify(mockConfig));
  vi.spyOn(global, 'fetch').mockRejectedValueOnce(new Error('network error'));

  const fetcher = new InteractionConfigFetcher('https://cdn.test/config.json', 'proj_test');
  await expect(fetcher.init()).resolves.not.toThrow();
  // Should still return cached config
  expect(fetcher.getConfig()).not.toBeNull();
});

it('saves fresh config to localStorage', async () => {
  const freshConfig = { interactions: [{ id: 'flow_a' }] };
  vi.spyOn(global, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify(freshConfig)));

  const fetcher = new InteractionConfigFetcher('https://cdn.test/config.json', 'proj_test');
  await fetcher.init();

  const stored = JSON.parse(localStorage.getItem('pulse_interaction_config')!);
  expect(stored.interactions[0].id).toBe('flow_a');
});
```

---

## Done Criteria

- [ ] Config fetched from CDN on `init()` with correct project ID header
- [ ] Cached config loaded immediately from `localStorage` before network round-trip
- [ ] Fresh config saved to `localStorage` after successful fetch
- [ ] `onChange` listeners notified when new config arrives
- [ ] Network errors do not throw — fall back silently to cached config
- [ ] `localStorage` errors do not throw (private browsing, quota exceeded)
- [ ] Config refresh scheduled every 30 minutes
- [ ] `destroy()` clears the refresh timer
- [ ] All unit tests passing
