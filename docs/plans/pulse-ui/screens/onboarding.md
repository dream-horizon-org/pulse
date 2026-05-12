# Onboarding

Covers the three onboarding-path screens: `Onboarding`, `OnboardingSuccess`, and `TncAcceptance`.

Brief: [../../../components/pulse-ui.md](../../../components/pulse-ui.md) · Peers: [login](./login.md), [create-project](./create-project.md), [../core/auth-flow](../core/auth-flow.md).

## Purpose

Walk a freshly-authenticated user through (1) Terms & Conditions acceptance, (2) initial project/org setup, (3) a success landing page that hands off to the dashboard.

## Source location

- `pulse-ui/src/screens/Onboarding/{index.ts,Onboarding.tsx,Onboarding.module.css}`
- `pulse-ui/src/screens/OnboardingSuccess/{index.ts,OnboardingSuccess.tsx,OnboardingSuccess.module.css}`
- `pulse-ui/src/screens/TncAcceptance/{index.ts,TncAcceptance.tsx,TncAcceptance.module.css}`

## Routes

From `ROUTES` in `src/constants/Constants.ts`:
- `/onboarding`
- `/onboarding/success`
- `/onboarding/tnc` (exact slug per constants)

## Data fetched

- TnC: `GET /v1/users/me/tnc` + `POST .../accept`.
- Onboarding: `POST /v1/organizations` (create org) + `POST /v1/projects` (create first project); uses `tenants` and `apikeys` backend domains (see [../../backend-server/domains/tenants.md](../../backend-server/domains/tenants.md), [../../backend-server/domains/apikeys.md](../../backend-server/domains/apikeys.md)).
- Success: shows the newly-minted API ingestion key for SDK copy-paste.

## State management

Transient local state; on completion, redirects to `/projects/:projectId/home`. No persistent Zustand store — the auth store updates with the fresh JWT/org membership.

## Key UI components

Mantine `Stepper`, `TextInput`, `Button`, `Code` (for the key reveal + copy).

## Notable interactions

- Rejecting TnC signs the user out.
- After project creation, the UI seeds install-snippet samples for each SDK (web, Android, iOS, RN).

## Tests

`__tests__/` alongside each screen file; wrap renders in `MantineProvider` and mock `makeRequest` at the helper level.

## Rebuild recipe

1. Three screen folders with the standard pattern.
2. Gate the dashboard router: unauthenticated → `/login`; authenticated + no-tnc → `/onboarding/tnc`; authenticated + no-project → `/onboarding`; else `/home`.
3. After success, store projectId in the filter store and route to home.

## History / decisions

Separated TnC from Onboarding to let legal gate TnC updates without blocking org/project creation.
