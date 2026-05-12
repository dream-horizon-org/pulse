# Personal Tokens

## Purpose

Manage personal API tokens for the signed-in user (CLI / programmatic
access).

## Source location

`pulse-ui/src/screens/PersonalTokens/`.

## Routes

- `PERSONAL_TOKENS` -> `/account/tokens`

## Data fetched

- `useUserApiKeys` - list tokens.
- Token create/revoke mutations exposed via `useUserApiKeys` (or
  sibling hooks in the same folder).

## State management

- `react-hook-form` for the create modal.
- `useSearchParams` - optional show-once token id.

## Key UI components

- `PageHeader`, Mantine `Table`, `Modal`, `Code` (show-once token),
  `ConfirmationModal` for revoke.

## Notable interactions

- New token: server returns the secret once; UI displays + copies it
  and does not store it.
- Revoke is irreversible; uses `ConfirmationModal`.

## Tests

`PersonalTokens.test.tsx`.

## Rebuild recipe

1. List tokens.
2. Create modal -> show-once secret view.
3. Revoke flow.
