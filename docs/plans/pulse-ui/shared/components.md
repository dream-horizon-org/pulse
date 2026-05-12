# Shared components

Folder pattern under `pulse-ui/src/components/`:

```
Name/
  index.ts
  Name.tsx
  Name.module.css
  Name.interface.ts   # optional
  Name.constants.ts   # optional
```

Barrel: `src/components/index.ts`.

## Layout

- `Layout/` - top-level shell (sidebar + header + content slot).
- `Main/` - main content frame inside layout.
- `Navbar/` - sidebar; reads `NAVBAR_ROUTES`, prepends
  `/projects/:projectId` per item on click.
- `Header/` - top bar (project switcher, user menu, tenant switcher,
  search).
- `Footer/` - footer.
- `PageHeader/` - per-screen page title + breadcrumbs.

## Loading / error / empty

- `LoaderWithMessage/`
- `SkeletonLoader/`, `Skeletons/`, `GraphSkeleton/`, `StatsSkeleton/`
- `ErrorAndEmptyState/` - canonical empty + error states for lists and
  charts.
- `ErrorBoundary/` - top-level boundary; mounted in `App.tsx`.
- `QueryState/` - wraps `useQuery` result and renders skeleton/error
  helpers based on `isLoading`/`isError`/`data`.
- `NotFound/` - 404 screen.

## Routing guards / utilities

- `ProjectGuard/` - blocks render until `projectId` resolves.
- `SessionReplayRouteGuard/` - feature gates the replay tree.
- `InternalRouteGuard/` - role gate for `/internal/*`.
- `ProjectInitializingModal/` - shown while a project is bootstrapping.
- `ScrollToTop/` - scrolls to top on route change.

## Forms / inputs

- `PhoneSearchBox/` - searchable phone-number input.
- `InviteCollaboratorsInput/` - tag-style email picker for invites.
- `TenantMembersNotOnProjectPicker/` - picker reused by Members and
  Project settings screens.

## Modals / notifications

- `Modal/` - thin Mantine `Modal` wrapper.
- `ConfirmationModal/` - confirm/cancel modal.
- `ContactUsModal/` - support modal; calls `useContactUs`.

## Charts / visualizations

- `Charts/` - chart primitives (line, area, bar, stacked bar). Always
  pair with `GraphSkeleton` and `ErrorAndEmptyState`.
- `Sparkline/` - inline sparkline used in list rows.
- `SessionCard/` - session summary card used on Home, Session Replay
  list, and Session Timeline.

## Markdown / content

- `MarkdownContent/` - safe markdown renderer used in AI Chat answers
  and RCA narratives.

## Tests

Component tests live next to the component
(`Name.test.tsx`) and wrap in `MantineProvider` via
`src/test-utils/`. Mock at the `makeRequest` level - never at `fetch`.
