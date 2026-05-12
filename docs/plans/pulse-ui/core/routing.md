# Routing

React Router v6. Route table is data-driven from `ROUTES` in
`pulse-ui/src/constants/Constants.ts` and bound to screen components in
`pulse-ui/src/routes/routes.tsx`.

## Shape

Project-scoped routes use the pattern `/projects/:projectId/<feature>`.
Organization-scoped routes use `/:organizationId/<feature>` or
`/organization/...`. Global routes (`/login`, `/onboarding`,
`/coming-soon`, `/account/tokens`, `/support-queries`,
`/internal/...`) are unscoped.

Each `ROUTES` entry has:

```ts
{
  key: string;       // stable identifier
  basePath: string;  // used to build links via navigation helpers
  path: string;      // the React Router pattern (may end with /*)
}
```

Routes that nest sub-pages declare `path: "/.../*"` (e.g.
`PROJECT_ALERTS_FORM`, `PROJECT_SETTINGS_ROUTE`,
`PROJECT_INTERACTION_FORM`, `PROJECT_INTERACTION_DETAILS`,
`ORGANIZATION_MEMBERS`).

## Inventory (selected)

| Key | Path |
|---|---|
| `LOGIN` | `/login` |
| `ONBOARDING` | `/onboarding` |
| `ORGANIZATION_DASHBOARD` | `/organization` |
| `ORGANIZATION_SETTINGS` | `/organization/settings` |
| `ORGANIZATION_MEMBERS` | `/:organizationId/members/*` |
| `ORGANIZATION_PROJECTS` | `/:organizationId/projects` |
| `CREATE_PROJECT` | `/:organizationId/projects/new` |
| `PRICING` | `/:organizationId/pricing` |
| `PROJECT_DASHBOARD` | `/projects/:projectId` |
| `PROJECT_ONBOARDING_SUCCESS` | `/projects/:projectId/onboarding` |
| `PROJECT_USER_ENGAGEMENT` | `/projects/:projectId/user-engagement` |
| `PROJECT_INTERACTIONS` | `/projects/:projectId/interactions` |
| `PROJECT_INTERACTION_FORM` | `/projects/:projectId/critical-interaction-form/*` |
| `PROJECT_ALL_INTERACTION_DETAILS` | `/projects/:projectId/user-experience` |
| `PROJECT_INTERACTION_DETAILS` | `/projects/:projectId/interaction-details/*` |
| `PROJECT_UNIVERSAL_QUERYING` | `/projects/:projectId/universal-querying` |
| `PROJECT_APP_VITALS` | `/projects/:projectId/app-vitals` |
| `PROJECT_APP_VITALS_ISSUE_DETAIL` | `/projects/:projectId/app-vitals/:groupId` |
| `PROJECT_APP_VITALS_OCCURRENCE_DETAIL` | `/projects/:projectId/app-vitals/:issueId/occurrence/:occurrenceId` |
| `PROJECT_SESSION_TIMELINE` | `/projects/:projectId/session/:id` |
| `PROJECT_SCREENS` | `/projects/:projectId/screens` |
| `PROJECT_SCREEN_DETAILS` | `/projects/:projectId/screens/:screenName` |
| `PROJECT_NETWORK_LIST` | `/projects/:projectId/network-apis` |
| `PROJECT_NETWORK_DETAIL` | `/projects/:projectId/network-apis/:apiId` |
| `PROJECT_SDK_CONFIG` | `/projects/:projectId/sdk-config` |
| `PROJECT_SETTINGS_ROUTE` | `/projects/:projectId/settings/*` |
| `PROJECT_ALERTS` | `/projects/:projectId/alerts` |
| `PROJECT_ALERT_DETAIL` | `/projects/:projectId/alerts/:alertId` |
| `PROJECT_ALERTS_FORM` | `/projects/:projectId/configure-alert/*` |
| `PROJECT_SESSION_REPLAY` | `/projects/:projectId/session-replay` |
| `PROJECT_SESSION_REPLAY_SESSIONS` | `/projects/:projectId/session-replay/sessions` |
| `PROJECT_SESSION_REPLAY_DETAIL` | `/projects/:projectId/session-replay/:sessionId` |
| `PROJECT_EVENT_CATALOG` | `/projects/:projectId/event-catalog` |
| `FUNNELS_LIST` / `FUNNEL_DETAIL` / `FUNNELS_CREATE` | `/projects/:projectId/funnels[...]` |
| `JOURNEYS_LIST` / `JOURNEY_DETAIL` / `JOURNEYS_CREATE` | `/projects/:projectId/journeys[...]` |
| `AI_CHAT` (gated by `ENABLE_AI_CHAT`) | `/projects/:projectId/ai-chat` |
| `PERSONAL_TOKENS` | `/account/tokens` |
| `COMING_SOON` | `/coming-soon` |
| `SUPPORT_QUERIES` | `/support-queries` |
| `INTERNAL_TENANT_SELECTOR` | `/internal/tenant-selector` |
| `INTERNAL_DEVELOPER_SETTINGS` | `/internal/developer-settings` |

`SESSION_REPLAY*` also has flat (non-project-scoped) variants used by
shareable links.

## Navbar mapping

`NAVBAR_ROUTES` (in `Constants.ts`) is a flat list of paths used by the
sidebar. The `Navbar` component (`onItemClick`) prepends
`/projects/:projectId` at click time so the sidebar items stay project
agnostic.

## Route file

`pulse-ui/src/routes/routes.tsx` re-exports `ROUTES` merged with
`element` bindings. Guards wrap elements:

- `ProjectGuard` (`components/ProjectGuard/`) ensures `projectId` is
  resolvable before mounting a project-scoped screen.
- `SessionReplayRouteGuard` gates session-replay screens behind config
  (`useSessionReplayFromActiveConfig`).
- `InternalRouteGuard` gates `/internal/*` routes; supports
  `requireSuperadmin`.

## Navigation helpers

`src/helpers/navigation/` builds project-scoped URLs from `basePath`
templates (substitutes `:projectId`, `:organizationId`, etc.). Always
build URLs via these helpers; do not concatenate strings.

## Constants reference

- `ROUTES` - route definitions.
- `NAVBAR_ROUTES` - flat sidebar paths.
- `API_ROUTES` - backend endpoint table (see
  [`api-client.md`](api-client.md)).

## Rebuild recipe

1. Define `ROUTES` exactly matching the inventory.
2. In `routes.tsx`, build a `[{ ...route, element }]` array.
3. Render with `<Routes>` + `<Route path={r.path} element={<r.element/>}/>`
   inside `BrowserRouter` (configured in `index.tsx`).
4. Wrap project-scoped elements in `<ProjectGuard>`.
5. Wrap internal/session-replay routes in their respective guards.
