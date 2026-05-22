# integrate-pulse-web-sdk — reference

## Example apps in monorepo

| App | Framework | Key files |
|-----|-----------|-----------|
| `pulse-web-otel/examples/web-sdk-docs` | Vanilla JS | `src/main.js`, `src/pulseConfig.js` |
| `pulse-web-otel/examples/ecommerce-demo` | React + Vite + RR | `src/Root.tsx`, `vite.config.ts` |
| `pulse-web-otel/examples/nextjs-demo` | Next App + Pages | `app/pulse-provider.tsx`, `pages/_app.tsx`, `next.config.ts` |
| `lottery-demo` (sibling repo) | Next.js + Capacitor | `app/providers/PulseProvider.tsx` |
| `pulse-ui` | CRA + React Router | `src/pulse-web-rum/` |

## Recommended host-app folder layout

```
src/pulse-rum/                    # or pulse-web-rum/
├── pulseRumConfig.ts             # read env → PulseWebConfig | null
├── pulseRumProvider.tsx          # PulseProvider + optional user sync
├── pulseRumAnalytics.ts          # trackPulseEvent, setUserId wrappers
├── pulseEventContext.ts          # auto project_id / tenant_id on events
└── pulseRumConstants.ts          # cookie keys / nav slugs (avoid circular imports)
```

## Env var naming by bundler

| Bundler / framework | Prefix | Example |
|---------------------|--------|---------|
| Vite | `VITE_` | `VITE_PULSE_API_KEY` |
| Create React App | `REACT_APP_` | `REACT_APP_PULSE_WEB_API_KEY` |
| Next.js | `NEXT_PUBLIC_` | `NEXT_PUBLIC_PULSE_API_KEY` |

## Package exports cheat sheet

```typescript
import { Pulse, PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";
import { PulseProvider, usePulse } from "@dreamhorizonorg/pulse-web/react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import { PulseProvider, PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
import { withPulseConfig } from "@dreamhorizonorg/pulse-web/next-config";
```

## Public API surface (post-init)

| Method | Use |
|--------|-----|
| `Pulse.trackEvent(name, attrs?)` | Custom product events → interaction configs |
| `Pulse.setUserId(id \| null)` | Logged-in user on all signals |
| `Pulse.setUserProperty(k, v)` | Custom user fields |
| `Pulse.clearUserIdentity()` | Logout |
| `Pulse.reportException(err, attrs?)` | Non-fatal errors |
| `Pulse.setScreenName(name)` | Manual screen name (vanilla / non-router) |

## Local dev API keys

| Key | Project | Collector |
|-----|---------|-----------|
| `default-project_devkey01` | `default-project` | `http://localhost:4318` |

Requires Pulse Docker stack (`deploy/scripts/start.sh`).

## CORS / remote config (prod)

New customer origins must be allowlisted on `pulse-otel-config` S3 bucket for remote config fetch — see integration SPEC §R5. Symptom when missing: SDK silently uses defaults; `OPTIONS /config/*` returns 403.

## Docker UI build (Pulse monorepo)

```dockerfile
ARG REACT_APP_PULSE_WEB_API_KEY
ENV REACT_APP_PULSE_WEB_API_KEY=$REACT_APP_PULSE_WEB_API_KEY
```

`deploy/docker-compose.yml` → `pulse-ui` service build args. Rebuild image after changing key.

## Verification queries (ClickHouse)

Use with **`query-clickhouse`** command after browser session.

```sql
-- Recent web traces for a service
SELECT Timestamp, PulseType, SessionId, SpanName
FROM otel.otel_traces
WHERE ProjectId = 'default-project'
  AND Platform = 'web'
  AND ServiceName = 'my-app'
  AND Timestamp > now() - INTERVAL 15 MINUTE
ORDER BY Timestamp DESC
LIMIT 20;

-- Custom trackEvent logs
SELECT Timestamp, LogAttributes['event.name'] AS event_name, SessionId
FROM otel.otel_logs
WHERE ProjectId = 'default-project'
  AND Platform = 'web'
  AND PulseType = 'custom_event'
  AND Timestamp > now() - INTERVAL 15 MINUTE
LIMIT 20;
```

## Dev debug components (copy into host app)

| Component | Source | Purpose |
|-----------|--------|---------|
| `PulseHealthCheck` | `lottery-demo/app/components/PulseHealthCheck.tsx` | Console P0 checklist on mount |
| `PulseDebugPanel` | `pulse-web-otel/examples/ecommerce-demo/src/components/PulseDebugPanel.tsx` | Shift+P OTLP live traffic |

Mount only in `NODE_ENV === 'development'`.
