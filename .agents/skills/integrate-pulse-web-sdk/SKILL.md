---
name: integrate-pulse-web-sdk
description: >-
  Integrate @dreamhorizonorg/pulse-web into a host app (vanilla JS, React SPA,
  React Router, Next.js App/Pages Router). Use when adding Pulse Web RUM/OTEL to
  a new or existing frontend, wiring PulseProvider, trackEvent, setUserId,
  verifying integration success, or debugging OTLP export failures.
---

# Integrate Pulse Web SDK

Add **`@dreamhorizonorg/pulse-web`** RUM to a **host application**. This skill is for **consumers** of the npm package — not for implementing instrumentations inside `pulse-web-otel/` (use **`web-sdk-instrument`** for that).

**Canonical specs:** `pulse-web-otel/docs/instrumentations/integration/SPEC.md`  
**Reference patterns:** [reference.md](reference.md)

---

## 1. Pick the integration path

| Host | Import path | Init |
|------|-------------|------|
| **Vanilla JS** (Vite, webpack, static HTML) | `@dreamhorizonorg/pulse-web` | `await Pulse.init(config)` once before app boot |
| **React SPA** (CRA, Vite + React) | `@dreamhorizonorg/pulse-web/react` | `<PulseProvider config={...}>` at root |
| **React + React Router v6** | above + `@dreamhorizonorg/pulse-web/react/router` | `<PulseRouterEvents />` inside `<BrowserRouter>` |
| **Next.js App Router** | `@dreamhorizonorg/pulse-web/next` | Client `PulseProvider` + `PulseRouterEvents` in layout |
| **Next.js Pages Router** | `@dreamhorizonorg/pulse-web/next` | `PulseProvider` in `_app.tsx` + pages router hook |

**Do not** call `Pulse.init` and wrap `PulseProvider` for the same app — the provider calls init once.

---

## 2. Install

```bash
yarn add @dreamhorizonorg/pulse-web
# or: npm install @dreamhorizonorg/pulse-web
```

Pin version from the consuming app's needs (monorepo: check `pulse-web-otel/package.json`).

---

## 3. Minimal config (all frameworks)

Required on **`PulseWebConfig`**:

| Field | Notes |
|-------|--------|
| `apiKey` | Project API key (e.g. `default-project_devkey01` for local Pulse stack) |
| `dataCollectionState` | `PulseDataCollectionConsent.ALLOWED` to enable collectors |
| `serviceName` | App identifier in Pulse (e.g. `"my-storefront"`) |
| `serviceVersion` | Optional but recommended (app version string) |

**Do not** pass `endpoint` from env unless there is a documented edge case (Capacitor WebView, custom collector). The SDK resolves OTLP from the API key:

- `default-project_*` → `http://localhost:4318`
- prod keys → Pulse cloud collector

---

## 4. Framework workflows

### 4a. Vanilla JS

```javascript
import { Pulse, PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

const config = {
  apiKey: import.meta.env.VITE_PULSE_API_KEY,
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
  serviceName: "my-app",
  serviceVersion: "1.0.0",
};

await Pulse.init(config);
// optional: expose for debugging
globalThis.Pulse = Pulse;
```

- Init **after** DOM ready (or in `main()` before routing).
- Manual navigation: call `Pulse.setScreenName(name)` on route changes if not using a router adapter.
- Reference: `pulse-web-otel/examples/web-sdk-docs/src/main.js`

### 4b. React SPA (Vite / CRA)

1. Create a small config module (read API key from env; no-op when key missing).
2. Wrap the app root:

```tsx
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

<PulseProvider config={{
  apiKey: process.env.REACT_APP_PULSE_WEB_API_KEY!,
  dataCollectionState: PulseDataCollectionConsent.ALLOWED,
  serviceName: "my-app",
  serviceVersion: "1.0.0",
}}>
  {children}
</PulseProvider>
```

3. **React Router v6** — inside `BrowserRouter`:

```tsx
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";

<BrowserRouter>
  <PulseRouterEvents />
  {routes}
</BrowserRouter>
```

4. **Singleton bundling:** ensure one copy of `@dreamhorizonorg/pulse-web` (webpack/vite alias if linking local SDK — see ecommerce-demo `vite.config.ts`).

Reference: `pulse-web-otel/examples/ecommerce-demo/src/Root.tsx`, `pulse-ui/src/pulse-web-rum/`

### 4c. Next.js (App Router)

1. **`"use client"`** provider wrapper (SDK touches `window`):

```tsx
// app/providers/PulseProvider.tsx
"use client";
import { PulseProvider, PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

export function AppPulseProvider({ children }: { children: React.ReactNode }) {
  return (
    <PulseProvider config={{
      apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY!,
      dataCollectionState: PulseDataCollectionConsent.ALLOWED,
      serviceName: "my-app",
      serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "1.0.0",
    }}>
      <PulseRouterEvents />
      {children}
    </PulseProvider>
  );
}
```

2. Mount in root `layout.tsx` (outside RSC-only boundaries).
3. **Optional build:** `withPulseConfig` from `@dreamhorizonorg/pulse-web/next-config` for source maps (prod crash deobfuscation).
4. **Webpack alias** when developing against monorepo SDK — use **ESM** `dist/*.js` entries so one Pulse singleton (see `nextjs-demo/next.config.ts`).

Reference: `pulse-web-otel/examples/nextjs-demo/app/pulse-provider.tsx`, `lottery-demo/app/providers/PulseProvider.tsx`

### 4d. Next.js (Pages Router)

- `PulseProvider` in `pages/_app.tsx`
- Use `useNextPagesRouterTracking` or exported pages helper from `@dreamhorizonorg/pulse-web/next`
- Reference: `pulse-web-otel/examples/nextjs-demo/pages/_app.tsx`

---

## 5. User identity & custom events (all frameworks)

After login (when user id is known):

```ts
import { Pulse } from "@dreamhorizonorg/pulse-web";

Pulse.setUserId(userId);
Pulse.setUserProperties({ email, tenant_id: tenantId }); // optional
```

On logout:

```ts
Pulse.clearUserIdentity();
```

Product analytics:

```ts
Pulse.trackEvent("checkout_started", { cart_items: 3 });
```

**Host-app patterns** (centralize in one helper module):

- Auto-attach `project_id` / `tenant_id` from session storage or cookies in a `trackEvent` wrapper — avoid repeating on every call site.
- Avoid importing heavy app `constants` barrels from RUM helpers (circular deps) — use a tiny local constants file.
- Fire `*_loaded` events once per screen visit for interaction configs.

See `pulse-ui/src/pulse-web-rum/`.

---

## 6. Environment variables (build-time)

| Tool | API key env var |
|------|-----------------|
| Vite | `VITE_PULSE_API_KEY` |
| CRA / webpack | `REACT_APP_PULSE_WEB_API_KEY` |
| Next.js | `NEXT_PUBLIC_PULSE_API_KEY` |

Values are inlined at **build** time for static/SPA deploys. Docker: pass as `ARG`/`ENV` in the UI Dockerfile (see `pulse-ui/Dockerfile`, `deploy/docker-compose.yml`).

Leave key **empty** to disable RUM (provider no-op pattern).

---

## 7. Success criteria

Integration is **successful** only when **P0 (must-pass)** checks pass. P1/P2 are follow-ups for product analytics and interaction configs.

### P0 — Core SDK alive (integration successful)

| # | Criterion | How to verify |
|---|-----------|---------------|
| 1 | SDK initialized | `Pulse.isInitialized() === true` in browser console after load |
| 2 | Consent allows collection | `dataCollectionState === ALLOWED` in config |
| 3 | Session created | `localStorage.pulse_session_id` set within ~2s of init |
| 4 | Installation ID | `localStorage.pulse_installation_id` set |
| 5 | OTLP export succeeds | DevTools Network: `POST …/v1/traces` or `/v1/logs` → **2xx** (not blocked/CORS/failed) |
| 6 | `session.start` emitted | Session keys present (see lottery-demo `PulseHealthCheck` step 7) |
| 7 | No init errors | Browser console: no TDZ/circular-import errors from RUM module; no duplicate-init warnings |

**If all P0 pass → integration is successful.** Stop and report pass/fail table.

### P1 — Navigation & identity (SPA / auth apps)

| # | Criterion | How to verify |
|---|-----------|---------------|
| 8 | Router adapter mounted | Navigate between routes → new `screen_load` / `screen_session` OTLP batches |
| 9 | `setUserId` after login | User id on subsequent spans/logs (Pulse Debug Panel or ClickHouse `UserId`) |
| 10 | `clearUserIdentity` on logout | User id cleared on next session signals |

### P2 — Product events & Pulse UI (optional)

| # | Criterion | How to verify |
|---|-----------|---------------|
| 11 | Custom `trackEvent` | ClickHouse `otel_logs` with expected event name + `Platform = 'web'` |
| 12 | Interaction configs | Pulse UI → Interactions shows journey completion (requires MySQL seeds) |

Local stack: `cd deploy && ./scripts/start.sh -d` — collector **4318**, UI **3000**.

---

## 8. Autonomous verify & debug loop

**After wiring integration, the agent MUST run this loop without asking the user** (unless blocked by login, missing env, or app won't start).

### Step A — Preconditions

```bash
# Pulse stack (monorepo)
cd deploy && ./scripts/start.sh -d
docker ps --filter name=pulse-otel-collector --format '{{.Status}}'

# Host app (example)
cd <host-app> && yarn dev   # note port
```

Confirm API key env var is set at **build/runtime** (empty key = RUM disabled by design).

### Step B — Browser verification (use browser MCP or ask user to open app)

1. Navigate to app URL (e.g. `http://localhost:3001`).
2. Wait 2–3s for SDK init.
3. **`browser_console_messages`** — scan for Pulse/RUM errors.
4. **`browser_network_requests`** — filter for `4318` or `/v1/traces`, `/v1/logs`; expect **2xx**.
5. Optional: trigger one navigation + one click to confirm extra exports.

**Dev-only helpers** (copy from reference apps if missing):

- `PulseHealthCheck` — logs P0 steps 1–7 to console (`lottery-demo/app/components/PulseHealthCheck.tsx`)
- `PulseDebugPanel` — Shift+P OTLP traffic overlay (`pulse-web-otel/examples/ecommerce-demo/src/components/PulseDebugPanel.tsx`)

Expose SDK in dev: `globalThis.Pulse = Pulse` after init for console checks.

### Step C — Backend confirmation (ClickHouse)

Run **`query-clickhouse`** command (SELECT only). Example — recent web sessions for project:

```sql
SELECT Timestamp, ServiceName, PulseType, SessionId, UserId
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND Platform = 'web'
  AND ServiceName = '<your-serviceName>'
  AND Timestamp > now() - INTERVAL 15 MINUTE
ORDER BY Timestamp DESC
LIMIT 20
```

Custom events:

```sql
SELECT Timestamp, LogAttributes['event.name'] AS event_name, SessionId
FROM otel.otel_logs
WHERE ProjectId = 'default-project'
  AND Platform = 'web'
  AND PulseType = 'custom_event'
  AND Timestamp > now() - INTERVAL 15 MINUTE
LIMIT 20
```

Replace `ServiceName` / `ProjectId` from `apiKey` prefix.

### Step D — Diagnose failures (fix and re-run loop)

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Pulse.isInitialized() === false` | Missing key, provider not mounted, SSR init | Client boundary; check env var inlined at build |
| No OTLP requests | Consent denied, key empty, collector down | `ALLOWED`; start stack; check `4318` |
| OTLP **403/401** | Bad API key | Use `default-project_devkey01` locally |
| OTLP **CORS/network error** | Collector not reachable from browser host | Start collector; Capacitor → LAN IP + optional endpoint |
| **Duplicate singleton** / weird init | Two `@dreamhorizonorg/pulse-web` copies | Webpack/vite alias to one ESM dist |
| TDZ / `Cannot access 'X' before initialization` | Circular import in RUM helpers | Split constants; never import app barrels from RUM |
| Session keys missing | Init race or consent off | Delay health check 500ms; verify ALLOWED |
| Traces in Network but not ClickHouse | Collector → CH pipeline lag | Wait 30s; check `docker logs pulse-otel-collector` |
| Remote config 403 (prod origin) | CORS allowlist | See integration SPEC §R5; local keys skip this |

**Loop:** fix → rebuild if env changed → reload app → re-run Steps B–C until **P0 all pass** or report blocker.

### Step E — Report

Output a short table:

```
Pulse Web SDK integration verification
- P0: 7/7 pass | FAIL: <list>
- P1: n/a or x/y
- P2: n/a or x/y
- Blockers: <none | description>
```

---

## 9. Anti-patterns

| Don't | Do instead |
|-------|------------|
| Import `@dreamhorizonorg/pulse-web` from deep internal paths | Use documented `exports` subpaths only |
| Pass OTLP `endpoint` in normal web deploys | Let SDK resolve from `apiKey` |
| `Pulse.init` + `PulseProvider` together | Provider only |
| Init in Server Components / SSR | Client-only boundary (`"use client"`, `useEffect`, or provider) |
| Raw `fetch` for app API if network spans needed | Use instrumented fetch or SDK network config |
| Invent new `pulse.type` values | Use existing semconv; see `docs/sdk-core/data-contract/SPEC.md` |

---

## 10. When done in Pulse monorepo

- Host app outside `pulse-web-otel/`: commit integration in that repo only.
- If adding **default-project interaction seeds** for new `trackEvent` names: update `backend/db/shared/mysql-default-project-interactions.sql` + host doc (see lottery-demo / pulse-ui interaction docs).
- **Do not** use **`web-sdk-ship`** for host-only integration — that skill is for SDK package changes.

---

## Additional resources

- [reference.md](reference.md) — env matrix, file layout template, example paths
- `pulse-web-otel/docs/instrumentations/integration/SPEC.md`
- `pulse-web-otel/docs/instrumentations/nextjs-integration/SPEC.md`
- `pulse-web-otel/docs/sdk-core/config-and-public-api/SPEC.md`
