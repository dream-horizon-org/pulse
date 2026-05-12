# AI Chat

## Purpose

Conversational analytics assistant. Streams answers, renders charts and
tables emitted by the agent (`pulse_ai/`). Gated by `ENABLE_AI_CHAT`.

## Source location

`pulse-ui/src/screens/AiChat/`.

## Routes

- `ROUTES.AI_CHAT` -> `/projects/:projectId/ai-chat` (only present when
  `ENABLE_AI_CHAT` is true).

## Data fetched

- `useAiQuery` - calls `streamAiRunSseWithAuth` to stream tokens.
- `useGetSuggestedQueries` - starter prompts.
- `useGetUserEvents` - event names available to the agent.
- Embedded charts/tables resolved by parsing JSON markers in the
  stream; rendered via the chat store
  (`updateLastMessageCharts`, `updateLastMessageTables`).

Auth path uses the same 401-refresh contract as `makeRequest`.

## State management

- `useChatStore` - sessions, messages, streaming flag, errors, embedded
  charts/tables.
- `useSearchParams` - active session id (for shareable URLs).

## Key UI components

- Composer (textarea + send), message list, `MarkdownContent` for
  answers, `Charts` for embedded visualisations, `Table` for embedded
  tables, suggested-prompt chips.

## Notable interactions

- Submit triggers SSE; tokens append to the active session via
  `appendToLastMessage`.
- Cancel button aborts the SSE; store flips `isStreaming=false`.
- New session button calls `createSession`.

## Tests

`AiChat.test.tsx`. Mock `streamAiRunSseWithAuth` to drive token batches.

## Rebuild recipe

1. Implement `useChatStore`.
2. Implement `useAiQuery` mutation wrapping `streamAiRunSseWithAuth`.
3. Parse SSE chunks: text tokens vs `chart`/`table` envelopes.
4. Render sessions sidebar + chat thread + composer.
