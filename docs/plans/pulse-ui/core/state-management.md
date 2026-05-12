# State management

Two distinct kinds of state:

- **Server state** -> TanStack Query v5 (`@tanstack/react-query`).
- **Client state** -> Zustand stores in `pulse-ui/src/stores/`.

React Context is reserved for cross-cutting identity (`ProjectContext`,
`TenantContext`, `PersonaContext`, `AppContextProvider`) under
`pulse-ui/src/contexts/`. No business state lives in Context.

## TanStack Query

- `QueryClientProvider` is mounted in `src/index.tsx`.
- All HTTP calls go through `useQuery` / `useMutation` wrappers under
  `src/hooks/useXxx/`.
- Query keys are arrays of the route key plus the request inputs (see
  `useGetDataQuery.ts` for the canonical shape).
- Refetch intervals default off; some screens opt in (real-time views).
- Mutations call `queryClient.invalidateQueries` against the route key on
  success.

`useGetDataQuery` (the generic ClickHouse-proxy hook) keys on
`[dataQuery.key, dataType, start, end, JSON.stringify(select),
JSON.stringify(groupBy), JSON.stringify(filters)]`.

## Zustand

Stores live in `pulse-ui/src/stores/`:

- `useFilterStore.ts` - dashboard time range + critical-interaction
  filter values + helpers (`getStartAndEndDateTimeString`,
  `filtersToQueryString`). Drives most list/detail screens.
- `useChatStore.ts` - AI Chat sessions, messages, streaming state. Holds
  `sessions`, `activeSessionId`, `messages` keyed by session.

Both are created with `devtools(...)` middleware. Convention: separate
`State` and `Actions` interfaces, then `create<State & Actions>()(devtools(...))`.

## Search params as state

Detail screens (Network, Screen, Alert, App Vitals, Funnel/Journey,
Session Replay, etc.) keep view state in URL search params:
- `timeRange`, `start`, `end`
- `tab`, `filters` (encoded JSON)
- `groupId`, `occurrenceId`, `sessionId`

Use `useSearchParams` from React Router. Pair with `filtersToQueryString`
when serialising.

## Contexts

- `ProjectContext` - current `projectId`, project metadata, switcher.
- `TenantContext` - current tenant / organization.
- `PersonaContext` - current user role flags (internal, superadmin).
- `AppContextProvider` - composes the above + logout-event subscription.
- `SessionReplayFilterContext` - replay-specific filter state for the
  replay sub-tree.

## Rebuild recipe

1. Mount `QueryClientProvider` with sensible defaults
   (`refetchOnWindowFocus: false`, retry: 1) in `index.tsx`.
2. Add `useFilterStore` first; most screens depend on it for the time
   range.
3. Add `useChatStore` only when AI Chat is enabled.
4. Use `useSearchParams` for any state that should survive reloads or be
   shared via URL.
