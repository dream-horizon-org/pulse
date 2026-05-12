# Support Queries

In-app support form — users submit bug reports / feature requests / help tickets.

Brief: [../../../components/pulse-ui.md](../../../components/pulse-ui.md) · Peers: [../core/api-client](../core/api-client.md).

## Purpose

Collects free-text queries plus contextual metadata (currently-viewed project, screen, error trace if present) and forwards to the Pulse support backend or a third-party ticket system.

## Source location

- `pulse-ui/src/screens/SupportQueries/`
  - `SupportQueries.tsx`
  - `SupportQueries.interface.ts`
  - `SupportQueries.module.css`
  - `index.ts`

## Routes

`/support` (authenticated).

## Data fetched

- `POST /v1/support/queries` — submit.
- `GET /v1/support/queries?mine=true` — list own past queries (if implemented).

## State management

React Hook Form for the submit form; no persistent store.

## Key UI components

Mantine `Textarea`, `Select` (category), `FileInput` (optional attachments), `Button`, `List` for recent queries.

## Notable interactions

- On submit, auto-attaches `projectId`, current URL, user email, build version.
- Success toast + form reset.
- Attachment size capped at configured limit; validation via Zod.

## Tests

Test covers happy path and validation error messages.

## Rebuild recipe

1. Standard screen folder.
2. `useForm<SupportQuery>` with Zod resolver.
3. On submit → `makeRequest('/v1/support/queries', { method: 'POST', body })`.
4. Append auto-context just before the request.

## History / decisions

Kept inside the app (vs external helpdesk) to preserve tenancy + context attachment.
