# pulse-ui

## What

React 18 + Mantine v7 dashboard for the Pulse observability platform. Renders
sessions, crashes, screens, network, alerts, funnels, journeys, AI chat and
admin surfaces over the backend REST API (`backend/server/`).

## Path

`pulse-ui/`

## Tech stack

- React 18 + TypeScript (strict).
- Mantine v7 (CSS variables; `var(--mantine-spacing-*)`).
- Zustand (devtools middleware) for client state.
- TanStack Query v5 for server state.
- React Router v6.
- CSS Modules (`*.module.css`) per component.
- Jest + React Testing Library.

## Port

`3000` (dev server via CRA / `react-scripts`).

## Build commands

```bash
cd pulse-ui
yarn install
yarn start          # dev server :3000
yarn build          # production bundle
yarn lint           # eslint
yarn test           # jest
yarn test --testPathPattern=ScreenList   # single test
```

Environment: `REACT_APP_PULSE_SERVER_URL` points at backend (`backend/server/`,
default `:8080`). See `pulse-ui/.env.example`.

## Inputs / outputs

- **Input:** browser session + Google OAuth (or mock-user dev mode).
- **Backend:** REST API via `src/helpers/makeRequest/makeRequest.ts`. Every
  call routes through `makeRequest<T>()`; 401 triggers a refresh-token retry
  then a forced redirect to `ROUTES.LOGIN.basePath`.
- **AI Agent:** SSE stream via `streamAiRunSseWithAuth` (same auth path) into
  `pulse_ai/` on `:8000`.
- **Output:** rendered dashboard; no direct ClickHouse access (queries are
  proxied via `API_ROUTES.DATA_QUERY` against `backend/server/`).

## Key files

- `src/App.tsx` - root composition.
- `src/index.tsx` - bootstrap, providers.
- `src/screens/index.tsx` - barrel for screen exports (partial).
- `src/routes/routes.tsx` - resolved route table (binds elements to
  `ROUTES` from `src/constants/Constants.ts`).
- `src/helpers/makeRequest/makeRequest.ts` - fetch wrapper with auth refresh.
- `src/constants/Constants.ts` - `ROUTES`, `API_ROUTES`, `NAVBAR_ROUTES`,
  `COMMON_CONSTANTS`.
- `src/constants/API.ts` - leaf constants (`HTTP_STATUS`, RCA routes).
- `src/constants/PulseOtelSemcov.ts` - column/attribute names.
- `src/stores/useChatStore.ts`, `src/stores/useFilterStore.ts` - Zustand stores.
- `src/contexts/` - `ProjectContext`, `TenantContext`, `PersonaContext`,
  `AppContextProvider`, `SessionReplayFilterContext`.
- `src/theme/Theme.ts` - Mantine theme overrides.

## Plan

Detailed rebuild plan: [`../plans/pulse-ui/index.md`](../plans/pulse-ui/index.md).
