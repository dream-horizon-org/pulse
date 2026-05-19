# pulse-ui — default-project interaction seeds

Maintenance contract for **critical interactions** on `default-project` that match `Pulse.trackEvent()` names emitted by pulse-ui RUM.

Canonical MySQL seed: **`backend/db/shared/mysql-default-project-interactions.sql`** (ids **600–610**).

## Requirements

- pulse-ui `REACT_APP_PULSE_WEB_API_KEY` must route to **`default-project`** (e.g. `default-project_devkey01`).
- Pulse stack running (collector + server) so the Web SDK fetches interaction configs.
- For existing Docker volumes, re-apply the seed or reset MySQL — init runs only on first DB bootstrap.

## Interaction map

| ID | Name | Event sequence | How to trigger |
| --- | --- | --- | --- |
| **600** | UI Onboarding Success to Dashboard | `onboarding_success_viewed` → `go_to_dashboard_clicked` → `dashboard_home_viewed` | New user onboarding → SDK setup page → **Go to Dashboard** |
| **601** | UI Nav to Interactions Loaded | `nav_item_clicked` (`destination=interactions`) → `interactions_list_loaded` | Sidebar **Interactions** |
| **602** | UI Onboarding Complete to Success | `onboarding_completed` → `onboarding_success_viewed` | Submit onboarding form (org + project) |
| **603** | UI Project Select to Home | `project_selected` → `dashboard_home_viewed` | Org **Projects** → pick a project |
| **604** | UI Nav Home to Dashboard | `nav_item_clicked` (`destination=home`) → `dashboard_home_viewed` | Sidebar **Home** from a sub-route |
| **605** | UI Session Replay Open | `nav_item_clicked` (`destination=session_replay`) → `session_replay_opened` | Session Replay nav → **Watch** a session |
| **606** | UI Create Interaction | `nav_item_clicked` (`destination=interactions`) → `interaction_created` | Interactions → create new interaction |
| **607** | UI AI Chat Message Sent | `nav_item_clicked` (`destination=ai_chat`) → `ai_chat_message_sent` | AI Chat nav → send a message (`REACT_APP_ENABLE_AI_CHAT=true`) |
| **608** | UI Universal Query Executed | `universal_query_executed` | Universal Event Query → **Run** |
| **609** | UI User Logged In | `user_logged_in` | Login page sign-in |
| **610** | UI User Logged Out | `user_logged_out` | Navbar → Logout |

## Event renames

If you rename a `trackPulseEvent` name or a prop used in a filtered step (e.g. `destination` on `nav_item_clicked`), update:

1. This doc
2. `backend/db/shared/mysql-default-project-interactions.sql` (ids 600–610)

## Auto context attrs

`project_id` and `tenant_id` are attached automatically in `trackPulseEvent()` — interaction steps match **event names** only; no prop filter on those unless added explicitly in SQL.
