# Sampling Config

Project-level telemetry sampling rules editor — per-signal sampling rates that the Pulse SDKs honor via remote config.

Brief: [../../../components/pulse-ui.md](../../../components/pulse-ui.md) · Peers: [project-settings](./project-settings.md), [../core/api-client](../core/api-client.md).

## Purpose

Lets project owners view/edit sampling configs (per-project JSON) that Pulse SDKs fetch on boot to decide what fraction of each signal type (errors, traces, clicks, web vitals, screen sessions) to emit. Changes propagate through `backend/server/.../resources/configs/` → SDK remote-config polls.

## Source location

- `pulse-ui/src/screens/SamplingConfig/`
  - `SamplingConfig.tsx` — page shell, load + save flow
  - `ConfigEditor.tsx` — JSON editor component
  - `SamplingConfig.interface.ts` — shape of the config payload
  - `SamplingConfig.constants.ts` — limits, defaults, validation strings
  - `components/` — sub-controls
- Route: `ROUTES.SAMPLING_CONFIG` in `src/constants/Constants.ts`.

## Routes

`/projects/:projectId/settings/sampling` (see `Constants.ts` for the exact path).

## Data fetched

- `GET /v1/projects/{projectId}/configs/sampling` — current config.
- `PUT /v1/projects/{projectId}/configs/sampling` — save.

All calls via `makeRequest` (see [../core/api-client.md](../core/api-client.md)). No ClickHouse; MySQL-backed control plane.

## State management

Local component state for the editor buffer; TanStack Query for the server-state read/save. No Zustand store required.

## Key UI components

Mantine `JsonInput`/`Textarea`/`Code`, `Button`, `Alert` (for validation errors). Save triggers a confirmation toast.

## Notable interactions / side-effects

- Save is optimistic: writes the new config, then invalidates the query.
- Invalid JSON blocks submit.
- SDKs poll `remote-config` (see `pulse-web-otel/src/remote-config.ts`) and hot-swap rates without reload.

## Tests

Component test at `pulse-ui/src/screens/SamplingConfig/__tests__/SamplingConfig.test.tsx` (if present) covers render + validation + save path via a mocked `makeRequest`.

## Rebuild recipe

1. Scaffold `SamplingConfig/` following the screen folder pattern (`index.ts`, `Name.tsx`, `Name.module.css`, `Name.interface.ts`, `Name.constants.ts`).
2. Fetch via TanStack Query with `queryKey: ['sampling-config', projectId]`.
3. Render a JSON editor with schema hints; validate against `SamplingConfig.interface.ts`.
4. On submit, call `makeRequest<SamplingConfig>({ method: 'PUT', url: API_ROUTES.SAMPLING_CONFIG(projectId), body })`.
5. Add a route in `ROUTES` and link from `ProjectSettings`.

## History / decisions

Introduced alongside remote config in the web SDK plan to let operators tune signal volume without deploys.
