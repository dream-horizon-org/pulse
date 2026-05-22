# Reverted: trackPulseEvent per-screen calls

## Why they were added

Commit `eed143b2b` — "feat(ui): add pulse-web RUM events, identity sync, and interaction seeds" — wired `trackPulseEvent` and `useTrackScreenLoadedOnce` across screens to seed Pulse Web RUM interaction sequences defined in `PULSE_UI_INTERACTIONS.md`.

## What was removed

### Per-screen `trackPulseEvent` calls (deleted, not replaced)

| File | Event name | Attributes |
|------|-----------|------------|
| `screens/Login/Login.tsx` | `user_logged_in` | `method`, `needs_onboarding`, `system_role` |
| `screens/Onboarding/Onboarding.tsx` | `onboarding_completed` | `org_name`, `project_id`, `tenant_id` |
| `screens/OnboardingSuccess/OnboardingSuccess.tsx` | `go_to_dashboard_clicked` | `source: "onboarding_success"` |
| `screens/CreateProject/CreateProject.tsx` | `project_created` | `project_id` |
| `screens/OrganizationProjects/OrganizationProjects.tsx` | `project_selected` | `project_id`, `source` |
| `screens/SessionReplaySessions/SessionReplaySessions.tsx` | `session_replay_opened` | `session_id` |
| `screens/CriticalInteractionForm/CriticalInteractionForm.tsx` | `interaction_updated` | `interaction_name` |
| `screens/CriticalInteractionForm/CriticalInteractionForm.tsx` | `interaction_created` | `interaction_name` |
| `screens/UniversalEventQuery/UniversalEventQuery.tsx` | `universal_query_executed` | `query_length` |
| `screens/AiChat/hooks/useHandleSend/useHandleSend.ts` | `ai_chat_message_sent` | `session_id`, `message_length` |
| `components/Navbar/Navbar.tsx` | `user_logged_out` | — |

### `useTrackScreenLoadedOnce` hook + test (deleted entirely)

Hook fired a one-shot screen-loaded event after data was ready. Was used in:

| File | Event name | Trigger condition |
|------|-----------|------------------|
| `screens/Home/Home.tsx` | `dashboard_home_viewed` | `projectId` truthy |
| `screens/CriticalInteractionList/CriticalInteractionList.tsx` | `interactions_list_loaded` | data loaded, page 0 |
| `screens/OnboardingSuccess/OnboardingSuccess.tsx` | `onboarding_success_viewed` | project name + id loaded |

## What was kept

- `PulseRumProvider` + `PulseProvider` wrapping the app
- `PulseRumUserSync` — user identity sync from cookies on boot
- `trackNavItemClicked` — navbar journey step (inlined into `pulseRumAnalytics.ts`, no longer delegates to `trackPulseEvent`)
- All identity sync functions (`syncPulseUserIdentity`, `flushPulseUserIdentityWhenReady`, `clearPulseUserIdentity`)

## Next step

Re-wire these events via `logEvent` with a `pulse_event` param key once `logEvent` is updated to forward to `Pulse.trackEvent` internally. That way screens have a single call for both GA and Pulse.
