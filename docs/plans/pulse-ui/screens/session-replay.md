# Session Replay

Covers four screens that share the replay subtree:
`SessionReplay`, `SessionReplaySessions`, `SessionReplayDetail`,
`SessionReplayInsights`.

## Purpose

Replay user sessions with timeline, interaction overlay and insights.
Gated behind `useSessionReplayFromActiveConfig` /
`SessionReplayRouteGuard`.

## Source locations

- `pulse-ui/src/screens/SessionReplay/` - landing.
- `pulse-ui/src/screens/SessionReplaySessions/` - searchable list.
- `pulse-ui/src/screens/SessionReplayDetail/` - player.
- `pulse-ui/src/screens/SessionReplayInsights/` - aggregated insights.

## Routes

Project-scoped:

- `PROJECT_SESSION_REPLAY` -> `/projects/:projectId/session-replay`
- `PROJECT_SESSION_REPLAY_SESSIONS` ->
  `/projects/:projectId/session-replay/sessions`
- `PROJECT_SESSION_REPLAY_DETAIL` ->
  `/projects/:projectId/session-replay/:sessionId`

Flat (share-link) variants:

- `SESSION_REPLAY`, `SESSION_REPLAY_INSIGHTS`,
  `SESSION_REPLAY_SESSIONS`, `SESSION_REPLAY_DETAIL`.

All wrapped in `SessionReplayRouteGuard`.

## Data fetched

- `useGetSessionReplays` - list of replayable sessions for the project.
- `useGetSessionData` - per-session header (start, duration, device).
- `useGetSessionDetails` - per-session spans, interactions, logs;
  feeds the timeline player.
- `useGetActiveSessionsData` - live sessions (Insights tile).
- `useGetSpanDetails` - drawer detail.

Sessions list is filtered + searched; replay assets are streamed from
the backend.

## State management

- `useFilterStore` - time range.
- `SessionReplayFilterContext` - replay-specific filters (device,
  platform, has-error, app version).
- `useSearchParams` for share-link state (time cursor, filters).

## Key UI components

- `SessionCard`, `SessionReplayRouteGuard`, custom replay player,
  Mantine `Slider` for scrubbing, `Drawer` for span details.
- `Charts` for the Insights screen.

## Notable interactions

- Player scrub seeks both DOM frames and the synchronised event log.
- "Jump to interaction" focuses the timeline + opens span details.

## Tests

Per screen: `*.test.tsx` mocking each hook and the guard.

## Rebuild recipe

1. Implement `useSessionReplayFromActiveConfig` + guard.
2. Build sessions list screen (table + search).
3. Build the player with two synchronized panes (DOM player and
   timeline).
4. Insights screen aggregates via `useGetDataQuery` over replay events.
