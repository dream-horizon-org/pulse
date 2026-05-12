# Organization (Dashboard + Members + Projects + Settings)

## Purpose

Org-level surfaces: tenant dashboard, members & invites, project list,
tenant settings.

## Source locations

- `pulse-ui/src/screens/OrganizationDashboard/`
- `pulse-ui/src/screens/OrganizationMembers/`
- `pulse-ui/src/screens/OrganizationProjects/`
- `pulse-ui/src/screens/OrganizationSettings/`

## Routes

- `ORGANIZATION_DASHBOARD` -> `/organization`
- `ORGANIZATION_SETTINGS` -> `/organization/settings`
- `ORGANIZATION_MEMBERS` -> `/:organizationId/members/*`
- `ORGANIZATION_PROJECTS` -> `/:organizationId/projects`

## Data fetched

- `useUserProjects`, `useGetProject` - project list / detail.
- `useTenantMembers`, `useProjectMembers` - member rosters.
- `useInternalTenants` - tenant switcher (internal only).
- `useTierLimits`, `useUserExperiments`, `usePermissions` - feature
  gating + quota.

## State management

- `TenantContext` - active org id.
- `PersonaContext` - role flags (gate Members admin actions).
- `useSearchParams` - tab on Members (`/active`, `/invited`, etc.).

## Key UI components

- `PageHeader`, Mantine `Tabs`, `Table`, `Modal` for invite,
  `InviteCollaboratorsInput`, `TenantMembersNotOnProjectPicker`,
  `ConfirmationModal`.

## Notable interactions

- Invite collaborators (multi-email).
- Promote / demote members.
- Project list links to `CREATE_PROJECT` and to each
  `PROJECT_DASHBOARD`.

## Tests

Per-screen `*.test.tsx`.

## Rebuild recipe

1. Dashboard: usage tiles + projects + members summary.
2. Members: tabs (Active, Invited, Removed); invite modal.
3. Projects: table + create CTA.
4. Settings: tenant-level config form.
