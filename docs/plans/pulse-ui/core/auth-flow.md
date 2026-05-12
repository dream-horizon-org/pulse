# Auth flow

Two modes:

- **Production:** Google OAuth 2.0 -> backend issues JWT (access 24h,
  refresh 30d).
- **Dev mode** (`GOOGLE_OAUTH_ENABLED=false` on backend): mock users
  `mock-user-1` / `mock-user-2`, project `default-project`, API key
  `default-project_devkey01`.

## Cookies

`pulse-ui/src/helpers/cookies/` exposes:

- `setCookies({ accessToken, refreshToken, ... })`
- `getCookies()` -> typed accessors
- `removeCookie(name)` / `removeAllCookies()`

Tokens are stored in cookies (not `localStorage`) so the refresh-token
flow can survive a tab reload but be wiped on logout.

## Login

`src/screens/Login/` renders the OAuth button. Flow:

1. `useLogin` hook (`src/hooks/useLogin/`) calls
   `helpers/gcpAuth` to launch the Google popup.
2. On success, `helpers/authenticateUser` POSTs the Google id-token to
   `API_ROUTES.LOGIN`.
3. `helpers/setCookiesAfterAuthentication` writes access + refresh cookies.
4. `helpers/login` resolves the user's project list
   (`getUserProjects`) and redirects:
   - no projects -> `ROUTES.ONBOARDING`.
   - one project -> `PROJECT_DASHBOARD` for that project.
   - multiple -> `ORGANIZATION_PROJECTS`.

## Refresh flow

Every `makeRequest` 401 triggers `getAndSetAccessTokenFromRefreshToken`:

1. POST to `API_ROUTES.REFRESH_TOKEN` with the refresh cookie.
2. On 200: update access cookie, return `true`. Caller retries the
   original request.
3. On any non-200: return `false`. Caller wipes cookies + sessionStorage,
   dispatches the `logout` event, and redirects to
   `ROUTES.LOGIN.basePath`.

`helpers/checkRefreshTokenExpiration` runs periodically (from
`AppContextProvider`) to pre-empt mid-action expiry.

## Logout

`helpers/logout` clears cookies, clears `sessionStorage`, dispatches
the logout event (so contexts reset), and navigates to `LOGIN`.

## Mock mode

When backend runs with `GOOGLE_OAUTH_ENABLED=false`, the Login screen
short-circuits via a "mock login" button (visible only when
`REACT_APP_ENV` is dev). It POSTs `mock-user-1` / `mock-user-2` to
`API_ROUTES.LOGIN`; the backend returns a real JWT signed by the same
key as prod. Cookies are written identically. Project context lands on
`default-project`.

## Internal routes

`InternalRouteGuard` (`src/components/InternalRouteGuard/`) reads role
flags from `PersonaContext` (via `useInternalRoles` /
`useIsInternalRoute`) and blocks `/internal/*` for non-internal users.
`requireSuperadmin` further restricts `INTERNAL_DEVELOPER_SETTINGS`.

## Permissions

`useUserExperiments`, `useTierLimits`, `usePermissions`, and
`checkUserPermissions` gate UI features (AI Chat, session replay,
sampling config) based on tenant tier + feature flags. Hidden routes do
not appear in `NAVBAR_ROUTES` for that tenant.

## Rebuild recipe

1. Implement cookie helpers.
2. Implement `gcpAuth` (Google identity client) + `authenticateUser`.
3. Implement `getAccessTokenFromRefreshToken`.
4. Implement `setCookiesAfterAuthentication` + `login` + `logout`.
5. Wire `AppContextProvider` to subscribe to the logout event and run
   the periodic `checkRefreshTokenExpiration`.
6. Build `Login` and `Onboarding` screens.
7. Add guards (`ProjectGuard`, `InternalRouteGuard`,
   `SessionReplayRouteGuard`).
