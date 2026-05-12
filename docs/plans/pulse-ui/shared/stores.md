# Stores

Zustand stores in `pulse-ui/src/stores/`. Each is created with the
`devtools` middleware. Convention: declare separate `State` and
`Actions` interfaces, then `create<State & Actions>()(devtools(...))`.

## `useFilterStore.ts`

Drives the global time range + critical-interaction filter state used
by most list/detail screens. Imports utilities from
`src/utils/DateUtil`, constants
(`CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS`, `DATE_FORMAT`,
`DEFAULT_QUICK_TIME_FILTER_INDEX`), and helpers
(`getCriticalInteractionDetailsFilterOptions`,
`filtersToQueryString`).

Holds (selected):

- `filterValues?: CriticalInteractionDetailsFilterValues`
- start/end time strings (formatted via
  `getStartAndEndDateTimeString`, normalised via
  `getLocalStringFromUTCDateTimeValue`).
- selected quick time filter index.
- filter option pools (`DEFAULT_FILTER_OPTIONS`: `PLATFORM`,
  `APP_VERSION`, `NETWORK_PROVIDER`, `STATE`, `OS_VERSION`).

Actions update the time range, set filter values, reset filters, and
serialise current filter set into a query string via
`filtersToQueryString` for sharing.

Consumers: ScreenList, ScreenDetail, NetworkList, NetworkDetail,
AlertListingPage, AlertDetail, AppVitals, CriticalInteractionList,
CriticalInteractionDetails, FunnelJourney pages, SessionReplay pages,
UniversalEventQuery, RealTimeQuery, Home.

## `useChatStore.ts`

AI Chat state.

State:

- `sessions: ChatSession[]`
- `activeSessionId: string | null`
- `messages: Record<string, ChatMessage[]>` (keyed by session id)
- `isStreaming: boolean`
- `error: string | null`

Actions:

- `createSession`, `deleteSession`, `switchSession`, `setSessions`.
- `addMessage`, `updateLastMessage`, `appendToLastMessage` (SSE token
  append), `markLastMessageComplete`, `markLastMessageError`.
- `updateLastMessageCharts(sessionId, charts: AiChartConfig[])`,
  `updateLastMessageTables(sessionId, tables: AiTableConfig[])` (for
  AI-emitted visualisations parsed out of the stream).

Types live in `src/types/chat.ts`
(`ChatMessage`, `ChatRole`, `ChatSession`, `AiChartConfig`,
`AiTableConfig`).

Consumer: `AiChat` screen (only).

## Conventions

- Stores hold only client-side state. Server state lives in TanStack
  Query keys (see [`../core/state-management.md`](../core/state-management.md)).
- Action methods are explicit (no inline `set` with arbitrary
  partials); each action is documented on the `Actions` interface.
- Devtools name should match the store filename so the DevTools panel
  is readable.

## Rebuild recipe

1. Implement `useFilterStore` first; ScreenList and the dashboard pull
   its time range immediately.
2. Add `useChatStore` only when wiring the AI Chat screen.
3. Keep new client state in stores or `useSearchParams`, not Context.
