# Project Settings

## Purpose

Project-scoped settings hub: general info, API keys, SDK config,
sampling config, members, integrations, danger zone.

## Source location

`pulse-ui/src/screens/ProjectSettings/` (and `Settings/` shell).

## Routes

- `PROJECT_SETTINGS_ROUTE` -> `/projects/:projectId/settings/*`
  (nested tabs).
- `PROJECT_SDK_CONFIG` -> `/projects/:projectId/sdk-config`.
- `PROJECT_SETTINGS` (legacy) -> `/settings`.

## Data fetched

- `useGetProject` - project record.
- `useProjectApiKeys` - API keys.
- `useProjectMembers`, `useTenantMembers` - membership editing.
- `useSdkConfig`, `useSessionReplayFromActiveConfig`,
  `useHeatmapFromActiveConfig` - SDK runtime config.
- `useTierLimits` - quotas.

## State management

- `react-hook-form` per sub-tab.
- `useSearchParams` for active sub-tab.
- `ProjectContext`.

## Key UI components

- Mantine `Tabs`, `Switch`, `Select`, `NumberInput`, `JsonInput`,
  `ConfirmationModal` (danger zone), `Code` for snippets.

## Notable interactions

- Regenerate API key (irreversible).
- Toggle session replay / heatmap (writes SDK config).
- Member management mirrors org members but scoped to project.

## Tests

`ProjectSettings.test.tsx` per tab.

## Rebuild recipe

1. Wrap in `Settings` layout shell.
2. Sub-tabs: General, API Keys, SDK Config, Sampling, Members,
   Integrations, Danger Zone.
3. Wire each tab to hooks above.
