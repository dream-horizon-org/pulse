# Create Project

## Purpose

Create a new project inside the current organization.

## Source location

`pulse-ui/src/screens/CreateProject/`.

## Routes

- `CREATE_PROJECT` -> `/:organizationId/projects/new`

## Data fetched

- `useCreateProject` - mutation; returns the new project id + API key.
- `useGetProject` / `useUserProjects` - invalidated on success.
- `useTierLimits` - guards against quota.

## State management

- `react-hook-form` for the form (name, platform, description).
- `TenantContext` - resolves the active organization.

## Key UI components

- `PageHeader`, Mantine `TextInput`, `Select`, `Textarea`, `Button`,
  `Code` for the post-create SDK snippet.

## Notable interactions

- On success: show SDK install snippet (with API key), then route to
  `PROJECT_ONBOARDING_SUCCESS` or `PROJECT_DASHBOARD`.
- Quota exceeded shows `Pricing` redirect.

## Tests

`CreateProject.test.tsx`.

## Rebuild recipe

1. Form with validation.
2. On submit, mutate and invalidate `useUserProjects`.
3. Show snippet card + CTA to dashboard.
