# Login

Google OAuth sign-in landing page + dev-mode mock login.

Brief: [../../../components/pulse-ui.md](../../../components/pulse-ui.md) · Peers: [../core/auth-flow](../core/auth-flow.md), [onboarding](./onboarding.md).

## Purpose

Entry point for unauthenticated users: shows the "Sign in with Google" button (prod) or a mock-user picker (dev). On success, exchanges the Google code for a JWT pair and routes to `/home` or `/onboarding` depending on account state.

## Source location

- `pulse-ui/src/screens/Login/`
  - `Login.tsx` — page shell
  - `index.ts` — barrel
  - `Login.module.css`

## Routes

`/login` (public).

## Data fetched

- `GET /v1/auth/google/url` — fetches the Google OAuth consent URL.
- Redirect callback: `POST /v1/auth/google/callback` with `code` param → returns `{accessToken, refreshToken, user}`.
- Dev mode (`REACT_APP_GOOGLE_OAUTH_ENABLED=false`): `POST /v1/auth/mock` with mock user id.

See [../core/auth-flow.md](../core/auth-flow.md) for the full refresh lifecycle.

## State management

Writes tokens to cookies via `src/helpers/cookies.ts`; user profile goes into the Zustand auth store.

## Key UI components

Mantine `Button` with Google icon, `Alert` for error states, `Loader` during redirect round-trip.

## Notable interactions

- Query-string error codes (e.g. `?error=access_denied`) render a user-facing banner.
- Successful login seeds TanStack Query cache with the `/me` endpoint before navigating.
- Mock login only appears when the env flag is off.

## Tests

`__tests__/Login.test.tsx` — covers render, click handler, error banner, and mock-user path under dev mode.

## Rebuild recipe

1. Create `Login/` with the standard screen-folder pattern.
2. Wire `onClick` → `makeRequest('/v1/auth/google/url')` → `window.location.assign(url)`.
3. On the callback route, exchange `code` → tokens, persist cookies, hydrate auth store, navigate.
4. Add dev-mode branch gated on `process.env.REACT_APP_GOOGLE_OAUTH_ENABLED`.

## History / decisions

Google OAuth chosen for enterprise SSO fit; JWT access = 24h, refresh = 30d (refresh handled by `makeRequest`).
