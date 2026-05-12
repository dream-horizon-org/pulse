# Internal Tooling

Staff-only routes — tenant selector and developer settings. Not exposed to customers.

Brief: [../../../components/pulse-ui.md](../../../components/pulse-ui.md) · Peers: [../core/auth-flow](../core/auth-flow.md).

## Purpose

Gives Pulse staff a way to (a) impersonate / switch tenants for support debugging, (b) toggle developer-only feature flags, and (c) hit internal endpoints that regular project members cannot.

## Source location

- `pulse-ui/src/screens/internal/`
  - `TenantSelector/` — tenant switcher
  - `DeveloperSettings/` — dev flag panel

## Routes

`/internal/tenants` and `/internal/dev` (feature-gated; check `user.roles.includes('staff')` in the route guard).

## Data fetched

- `TenantSelector`: `GET /v1/internal/tenants` — full cross-tenant list.
- `DeveloperSettings`: `GET /v1/internal/feature-flags` + `PUT` to toggle.

Both routes guarded server-side in `backend/server/.../resources/internal/`.

## State management

Tenant-switch updates the auth store with the impersonated `projectId`/`orgId`; filter store resets on switch.

## Key UI components

Mantine `Table` with search, `Switch` for each flag, `Badge` to highlight the active tenant.

## Notable interactions

- Switching tenants invalidates all TanStack Query caches (`queryClient.clear()`).
- Staff-only banner pinned at the top of the app while impersonating.
- Actions are audit-logged server-side.

## Tests

Integration test asserts non-staff users are redirected to `/home`.

## Rebuild recipe

1. Create two sub-folders under `src/screens/internal/` each with the standard pattern.
2. Wrap routes in a `StaffGuard` component that reads the auth store.
3. After a tenant switch, call `queryClient.clear()` then navigate to `/home`.

## History / decisions

Kept under a deep `/internal` prefix (rather than a separate build) to avoid dual deploys while still being gated.
