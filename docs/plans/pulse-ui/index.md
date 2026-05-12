# pulse-ui rebuild plan

Documentation index for `pulse-ui/`. Each linked file is a self-contained spec
of one slice of the dashboard.

## Summary

`pulse-ui` is the React 18 + Mantine v7 + TypeScript dashboard. It is a CRA
project that talks to `backend/server/` exclusively through `makeRequest<T>`,
holds client state in Zustand, server state in TanStack Query v5, and routes
via React Router v6 against the `ROUTES` constant in
`src/constants/Constants.ts`. Project scope is encoded in the path
(`/projects/:projectId/...`).

## Reading order

1. [`core/routing.md`](core/routing.md) - URL shape, `ROUTES`, guards.
2. [`core/auth-flow.md`](core/auth-flow.md) - Google OAuth, JWT, mock mode.
3. [`core/api-client.md`](core/api-client.md) - `makeRequest`, `useGetDataQuery`.
4. [`core/state-management.md`](core/state-management.md) - Zustand + TanStack.
5. [`core/theming.md`](core/theming.md) - Mantine + CSS modules.
6. [`shared/hooks.md`](shared/hooks.md) - shared `src/hooks/` patterns.
7. [`shared/components.md`](shared/components.md) - shared `src/components/`.
8. [`shared/stores.md`](shared/stores.md) - Zustand stores.
9. Screens in any order; each is self-contained.

## Table of contents

### Core

| File | Topic |
|---|---|
| [`core/routing.md`](core/routing.md) | React Router v6, `ROUTES`, guards |
| [`core/state-management.md`](core/state-management.md) | Zustand + TanStack Query |
| [`core/api-client.md`](core/api-client.md) | `makeRequest`, `useGetDataQuery`, `API_ROUTES` |
| [`core/theming.md`](core/theming.md) | Mantine theme, CSS Modules |
| [`core/auth-flow.md`](core/auth-flow.md) | OAuth + JWT refresh + dev mock |

### Shared

| File | Topic |
|---|---|
| [`shared/hooks.md`](shared/hooks.md) | `src/hooks/` patterns and inventory |
| [`shared/components.md`](shared/components.md) | shared layout/UI components |
| [`shared/stores.md`](shared/stores.md) | Zustand stores |

### Screens

| File | Source folder |
|---|---|
| [`screens/home.md`](screens/home.md) | `src/screens/Home/` |
| [`screens/screen-list.md`](screens/screen-list.md) | `src/screens/ScreenList/` |
| [`screens/screen-detail.md`](screens/screen-detail.md) | `src/screens/ScreenDetail/` |
| [`screens/network-list.md`](screens/network-list.md) | `src/screens/NetworkList/` |
| [`screens/network-detail.md`](screens/network-detail.md) | `src/screens/NetworkDetail/` |
| [`screens/session-replay.md`](screens/session-replay.md) | `SessionReplay*` family |
| [`screens/session-timeline.md`](screens/session-timeline.md) | `src/screens/SessionTimeline/` |
| [`screens/alert.md`](screens/alert.md) | `AlertForm` / `AlertFormWizard` / `AlertListingPage` / `AlertDetail` |
| [`screens/critical-interaction.md`](screens/critical-interaction.md) | `CriticalInteraction*` |
| [`screens/funnel-journey.md`](screens/funnel-journey.md) | `FunnelJourney*` |
| [`screens/app-vitals.md`](screens/app-vitals.md) | `src/screens/AppVitals/` |
| [`screens/ai-chat.md`](screens/ai-chat.md) | `src/screens/AiChat/` |
| [`screens/real-time-query.md`](screens/real-time-query.md) | `src/screens/RealTimeQuery/` |
| [`screens/universal-event-query.md`](screens/universal-event-query.md) | `src/screens/UniversalEventQuery/` |
| [`screens/event-catalog.md`](screens/event-catalog.md) | `src/screens/EventCatalog/` |
| [`screens/user-engagement.md`](screens/user-engagement.md) | `src/screens/UserEngagement/` |
| [`screens/organization.md`](screens/organization.md) | `Organization*` |
| [`screens/project-settings.md`](screens/project-settings.md) | `src/screens/ProjectSettings/` |
| [`screens/create-project.md`](screens/create-project.md) | `src/screens/CreateProject/` |
| [`screens/personal-tokens.md`](screens/personal-tokens.md) | `src/screens/PersonalTokens/` |
| [`screens/sampling-config.md`](screens/sampling-config.md) | `src/screens/SamplingConfig/` |
| [`screens/onboarding.md`](screens/onboarding.md) | `Onboarding` + `OnboardingSuccess` + `TncAcceptance` |
| [`screens/login.md`](screens/login.md) | `src/screens/Login/` |
| [`screens/pricing.md`](screens/pricing.md) | `src/screens/Pricing/` |
| [`screens/support-queries.md`](screens/support-queries.md) | `src/screens/SupportQueries/` |
| [`screens/internal.md`](screens/internal.md) | `src/screens/internal/` |

## Rebuild checklist

1. Scaffold CRA + TS, install Mantine v7, Zustand, TanStack Query v5, React
   Router v6, react-hook-form, dayjs.
2. Wire `index.tsx` providers: `MantineProvider`, `QueryClientProvider`,
   `BrowserRouter`, `AppContextProvider`, tenant/project/persona contexts.
3. Implement `src/constants/Constants.ts` (`ROUTES`, `API_ROUTES`,
   `NAVBAR_ROUTES`, `COMMON_CONSTANTS`) and `src/constants/API.ts`.
4. Build `src/helpers/makeRequest/` + `getAccessTokenFromRefreshToken/` +
   `cookies/`. Confirm 401 retry path.
5. Add `src/helpers/login/`, `logout/`, `authenticateUser/`,
   `setCookiesAfterAuthentication/`, `gcpAuth/`.
6. Add base layout (`components/Layout`, `Navbar`, `Header`, `Main`, `Footer`).
7. Add `routes/routes.tsx` (binds `ROUTES` to screen components) + guards
   (`ProjectGuard`, `SessionReplayRouteGuard`, `InternalRouteGuard`).
8. Add screens in order of dependency: Login -> Onboarding -> Home ->
   ScreenList -> ScreenDetail -> AlertListingPage -> rest.
9. Per screen, wire hooks under `src/hooks/<useXxx>/`.
10. Add Zustand stores (`useFilterStore`, `useChatStore`).
11. Add tests using `MantineProvider` wrappers and `makeRequest` mocks.
