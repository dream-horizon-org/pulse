# Pulse UI — UX Analysis Context

**Date:** 2026-04-09  
**Analyst:** Claude (Sonnet 4.6)  
**App:** Pulse — Mobile App Observability Platform  
**Server:** `http://localhost:3003` (mock data, `REACT_APP_USE_MOCK_SERVER=true`)  
**Project:** Mobile App (`proj-mock-1`)  
**Branch:** `chore/remove-active-cache`

---

## Product Context

Pulse is a mobile app observability and session replay tool. Primary users are:

- **Mobile engineers** — debugging crashes, ANRs, network failures
- **Product managers** — tracking user experience quality and engagement
- **QA teams** — investigating reproduction paths for bugs

Core product pillars: Session Replay · Interaction Health · App Vitals · Screen Health · User Engagement

---

## Pages Analysed

| Page                                    | Route                                             | Status  |
| --------------------------------------- | ------------------------------------------------- | ------- |
| Session Listing                         | `/projects/proj-mock-1/session-replay/sessions`   | ✅ Done |
| Session Detail                          | `/projects/proj-mock-1/session-replay/:id`        | ✅ Done |
| Home Dashboard                          | `/projects/proj-mock-1`                           | ✅ Done |
| Engagement Overview                     | `/projects/proj-mock-1/user-engagement`           | ✅ Done |
| Critical Interactions                   | `/projects/proj-mock-1/interactions`              | ✅ Done |
| App Vitals                              | `/projects/proj-mock-1/app-vitals`                | ✅ Done |
| Screens                                 | `/projects/proj-mock-1/screens`                   | ✅ Done |
| Query Builder                           | `/projects/proj-mock-1/query-builder`             | ✅ Done |
| Network                                 | `/projects/proj-mock-1/network-apis`              | ✅ Done |
| Network Details                         | `/projects/proj-mock-1/network-apis/:id`          | ✅ Done |
| Alerts                                  | `/projects/proj-mock-1/alerts`                    | ✅ Done |
| Event Catalog                           | `/projects/proj-mock-1/event-catalog`             | ✅ Done |
| Organization (Projects/Members/Pricing) | `/tenant-mock-1/projects`, `/members`, `/pricing` | ✅ Done |
| Current Project Settings                | `/projects/proj-mock-1/settings`                  | ✅ Done |
| About Pulse                             | External link → docs                              | ✅ Done |
| Contact Us                              | `/support-queries`                                | ✅ Done |

---

## Stack Notes

- React 18 + TypeScript
- Mantine v7 (UI components)
- Tailwind CSS + Sass
- TanStack React Query v5
- ECharts (`echarts-for-react`) for all charts
- Zustand for global state
- React Router v6
- Virtualized session list (`@tanstack/react-virtual`)

---

## Session Listing Page

**Route:** `/projects/proj-mock-1/session-replay/sessions`  
**Key Components:** `SessionReplaySessions.tsx`, `SessionsVirtualList.tsx`, `SessionsVirtualListRow.tsx`

### What the page contains

- Left sidebar: icon-only navigation (10 icons, no labels)
- Top header: org name + project selector dropdown
- Page title: "Session List" + long description paragraph
- Filter bar: Time range dropdown (Last 24 hours), Quick filters (HAS ERRORS, HAS CRASHES), Advanced Filters text link, Search input
- Table columns: Start Time (sortable), Duration (sortable), User, Quality (sortable), Issues, Platform, Impacted Interactions
- 12 sessions in mock data, ending with "End of results"

### Data observed

- Quality scores: 0.86 (green), 0.72 (orange), 0.95 (green), NA, 0.68 (orange), 0.58 (red), 0.91 (green), NA, 0.82 (green)
- Issue types: CRASHES, NETWORK ERRORS, INTERACTION ERRORS, NON-FATALS, SLOW INTERACTIONS, FROZEN FRAMES, ANRS
- Platforms: ANDROID, IOS, WEB
- Users: user_3456, Anonymous, user_1234, user_5678, vip_user_01, qa_bot_session

### Issues Found

| #   | Issue                                                                             | Priority | Category                 |
| --- | --------------------------------------------------------------------------------- | -------- | ------------------------ |
| 1   | No session count / results summary                                                | P1       | Information Architecture |
| 2   | Issue badges overflow rows — no truncation/expand                                 | P1       | Visual Density           |
| 3   | Table columns clipped at 1440px (Issues, Platform, Impacted Interactions cut off) | P1       | Layout                   |
| 4   | Quality score: raw decimal, no visual scale or label                              | P2       | Data Legibility          |
| 5   | Icon-only sidebar — no labels or hover tooltips                                   | P2       | Navigation               |
| 6   | Quick filters: only 2 presets, unclear active/inactive state                      | P2       | Filtering                |
| 7   | "Advanced Filters" styled as tertiary text link, not a button                     | P2       | Discoverability          |
| 8   | Impacted Interactions column always truncated (path cut off)                      | P3       | Data Legibility          |
| 9   | Search placeholder too generic — unclear search scope                             | P3       | Search                   |
| 10  | "NA" vs "N/A" inconsistency + no tooltip explaining why                           | P3       | Polish                   |

---

## Session Detail Page

**Route:** `/projects/proj-mock-1/session-replay/sess_mock_001`  
**Key Components:** `SessionReplayDetail.tsx`, `SessionHeader.tsx`, `SessionPlayerSection.tsx`, `SessionTabs.tsx`

### What the page contains

- "← Back" button
- Metadata header: Session ID (`sess_mock_001`), User ID (`user_3456`) + IDENTIFIED badge, Start Time, Duration (1m 32s), Quality (0.65), Platform (ANDROID)
- Device frame: "Android Pixel 6 • 14" above the player
- Replay player: blurred/wireframe mobile content, timeline scrubber (00:00–00:17), playback controls (skip, play, forward, fullscreen, 0.5x/1x/1.5x/2x speed)
- Right panel tabs: All, Interaction, Network, App Vitals, User Journey, Console
- "All" tab content: Session timeline — unified stream of events with types: Session, Event, Network, Interactions

### Events observed in timeline (All tab)

- `Session Started` — Apr 9, 02:33:28 PM
- `Event /dream11-home` — Apr 9, 02:33:29 PM
- `Event MainActivity resumed` — Apr 9, 02:33:29 PM
- `Network GET /api/v1/bootstrap → 200` — Apr 9, 02:33:30 PM
- `Network GET /api/v1/user/me → 200` — Apr 9, 02:33:32 PM
- `Interaction search field` — Apr 9, 02:33:34 PM
- `Interaction Search button` — Apr 9, 02:33:34 PM

### Issues Found

| #   | Issue                                                                                        | Priority | Category                 |
| --- | -------------------------------------------------------------------------------------------- | -------- | ------------------------ |
| 11  | "Session Time" label is incorrect — displays raw session ID                                  | P1       | Labelling Bug            |
| 12  | Critical issues (crashes, ANRs) not surfaced in session header                               | P1       | Information Architecture |
| 13  | Events timeline panel (right side) clipped at viewport edge                                  | P1       | Layout                   |
| 14  | Header metadata stacks vertically — hard to scan at a glance                                 | P2       | Information Architecture |
| 15  | Quality score loses colour coding present on session list page                               | P2       | Visual Consistency       |
| 16  | Device info duplicated in header badge ("ANDROID") and player label ("Android Pixel 6 · 14") | P2       | Redundancy               |
| 17  | "Back" button lacks destination context ("Back to Session List")                             | P2       | Navigation               |
| 18  | No skip-inactivity in playback controls                                                      | P3       | Player UX                |
| 19  | Event timeline has no severity-based visual hierarchy                                        | P3       | Data Legibility          |
| 20  | No share / copy session link                                                                 | P3       | Collaboration            |

---

## Home Dashboard

**Route:** `/projects/proj-mock-1`  
**Key Components:** Project home screen with multiple widget sections

### What the page contains

#### Section 1 — Overall User Experience

- **User Experience Distribution** card: Excellent 25.93%, Good 61.73%, Average 0.00%, Poor 12.35% + stacked area chart
- **Interaction Apdex Score** card: Apdex 0.79, P50 100ms, P95 400ms, Frozen Frames 3.33%
- **Error Rate** card: Error Rate 7.14%, Crashes 1.95%, ANR 3.25%, Network Errors 2.60%

#### Section 2 — User Engagement & Active Sessions

- **User Engagement**: Avg Daily 11,366 · Weekly 11,367 · Monthly 11,538 + trend chart
- **Active Sessions**: Current 33,082 · Peak 38,533 · Average 34,478 + trend chart

#### Section 3 — Screen Health (horizontal carousel)

- HomeScreen: 48.6s avg time, 0.0% error, 474ms load, 10.6K users
- ProductListScreen: 31.8s, 0.0% error, 511ms, 12.3K users
- ProductDetailScreen: 37.9s, **3.1% error rate**, 463ms, 8.8K users
- CheckoutFormScreen: 43.5s, 1.2% error, 695ms, 10.1K users
- PaymentScreen: 29.1s, 0.0% error, 291ms, 8.6K users

#### Section 4 — Top Interactions Health (horizontal carousel)

- MatchScheduleAPICall: **EXCELLENT** badge, Apdex 0.94, **Error Rate 13.33%**, P50 100ms, **Poor Users 13.68%**
- WalletBalanceFetch: **EXCELLENT**, Apdex 0.89, Error Rate 7.69%, P50 267ms, Poor Users 21.77%
- PlayerSelectTap: **EXCELLENT**, Apdex 0.83, **Error Rate 20.81%**, P50 100ms, **Poor Users 17.20%**
- ProfileSaveClick: **EXCELLENT**, Apdex 0.95, **Error Rate 22.04%**, P50 324ms, **Poor Users 24.68%**

#### Section 5 — Quick Access

- Interactions · App Vitals · Screens · Network APIs (4 shortcut cards)

### Issues Found

| #   | Issue                                                                                                                              | Priority | Category                 |
| --- | ---------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------ |
| 1   | "EXCELLENT" badge assigned purely on Apdex — ignores error rate (22% error = EXCELLENT is dangerously misleading)                  | P1       | Data Integrity           |
| 2   | No global time range selector on home dashboard                                                                                    | P1       | Filtering                |
| 3   | "Average: 0.00%" in UX Distribution — silent zero with no explanation                                                              | P1       | Data Legibility          |
| 4   | Screen Health carousel hides most problematic screens off-screen (3.1% error on ProductDetailScreen not visible at default scroll) | P2       | Layout                   |
| 5   | DAU ≈ WAU ≈ MAU (11,366 / 11,367 / 11,538) — potential retention anomaly with no visual flag                                       | P2       | Data Legibility          |
| 6   | "Current: 33,082 active sessions" — no definition of "current" time window                                                         | P2       | Labelling                |
| 7   | Metrics have no delta / trend arrows (is Error Rate 7.14% up or down vs yesterday?)                                                | P2       | Data Legibility          |
| 8   | Top Interactions Health carousel: cards partially cut off, users don't know more exist                                             | P2       | Layout                   |
| 9   | Screen names are technical code names (ProductDetailScreen vs human-readable names)                                                | P3       | Product Polish           |
| 10  | Quick Access section is redundant with sidebar navigation                                                                          | P3       | Information Architecture |
| 11  | No "last refreshed" timestamp anywhere on the dashboard                                                                            | P3       | Trust / Transparency     |

---

## Engagement Overview Page

**Route:** `/projects/proj-mock-1/user-engagement`  
**Key Components:** `UserEngagement.tsx`, `EngagementBreakdown.tsx`

### What the page contains

#### Section 1 — User Engagement KPI widget

- Avg Daily Users: **8,283** · Weekly Users: **8,183** · Monthly Users: **8,000**
- Single-line trend chart (Apr 03–08), flat ~8K line

#### Section 2 — Active Sessions widget (right of User Engagement, cut off)

- Current: **29,858** · Peak: **32,480** · Average: **28,456**

#### Section 3 — Detailed Engagement Analysis

- Subtitle: "Dive deeper into how each cohort contributes to DAU/WAU/MAU and sessions."
- Summary pill: **8 segments • 299,036 sessions • 77,685 MAU**
- Dimension switcher (radio buttons): Regions · Networks · Platforms · OS · Device · Custom attributes
- Grouped bar chart (3 series — teal, dark navy, purple — **no legend**)
- Table: SEGMENT | DAU | WAU | MAU | SESSIONS

#### Segment table data (Regions view)

| Segment       | DAU    | WAU    | MAU    | Sessions |
| ------------- | ------ | ------ | ------ | -------- |
| Karnataka     | 11,517 | 11,290 | 11,432 | 26,636   |
| Rajasthan     | 11,571 | 11,142 | 11,061 | 37,476   |
| Delhi         | 10,445 | 9,935  | 10,332 | 40,469   |
| Maharashtra   | 10,797 | 10,630 | 10,309 | 33,566   |
| West Bengal   | 9,210  | 9,766  | 9,301  | 37,790   |
| Uttar Pradesh | 9,269  | 8,970  | 8,818  | 48,870   |
| Tamil Nadu    | 8,000  | 8,000  | 8,432  | 38,720   |
| Gujarat       | 8,000  | 8,208  | 8,000  | 35,509   |

### Issues Found

| Page                | Issue                                                                                                             | Priority |
| ------------------- | ----------------------------------------------------------------------------------------------------------------- | -------- |
| Engagement Overview | DAU > WAU in some regions (Rajasthan: 11,571 DAU > 11,142 WAU) — mathematically impossible for unique user counts | P1       |
| Engagement Overview | MAU in top widget (8,000) vs MAU in breakdown summary (77,685) — 10x discrepancy on same page                     | P1       |
| Engagement Overview | Grouped bar chart has no legend — 3 colour series with no label for DAU/WAU/MAU                                   | P1       |
| Engagement Overview | No time range selector on the page                                                                                | P1       |
| Engagement Overview | KPI numbers have no time period context — "Avg Daily Users: 8,283" of which period?                               | P2       |
| Engagement Overview | Segment table has no % of total column — raw counts with no relative share                                        | P2       |
| Engagement Overview | No delta or trend column in the breakdown table                                                                   | P2       |
| Engagement Overview | "Current: 29,858 active sessions" — time window for Current undefined                                             | P2       |
| Engagement Overview | Bar chart clips segments off-screen with no scroll indicator                                                      | P2       |
| Engagement Overview | "Custom attributes" dimension option shows no indicator that it requires setup                                    | P2       |
| Engagement Overview | Terminology inconsistency: "Avg Daily Users" (widget) vs "DAU" (table) — same metric, different label             | P2       |
| Engagement Overview | Page subtitle is jargon-heavy ("benchmark north-star engagement KPIs")                                            | P3       |
| Engagement Overview | "By region" sub-label repeats on every row in Regions view — redundant                                            | P3       |
| Engagement Overview | Uttar Pradesh: 48,870 sessions for 9,269 DAU (5.3 sessions/user/day) — anomaly not flagged                        | P3       |

---

## Critical Interactions Page

**Route:** `/projects/proj-mock-1/interactions`  
**Key Components:** `CriticalInteractions` screen, interaction cards grid

### What the page contains

- Heading: "Critical Interactions" + "12 Interactions" pill badge
- Toolbar: Search input · "My interactions" toggle · sort/filter icon button · "Add new interaction" button
- 2-column grid of 12 interaction cards

### All 12 interactions (from DOM)

| Name                   | Badge     | Apdex | Error Rate | P50   | Poor Users |
| ---------------------- | --------- | ----- | ---------- | ----- | ---------- |
| JoinContestButtonClick | EXCELLENT | 0.84  | 35.48%     | 100ms | 20.69%     |
| SaveTeamButtonClick    | EXCELLENT | 0.90  | 16.05%     | 248ms | 13.55%     |
| PlayerSelectTap        | GOOD      | 0.79  | 33.33%     | 393ms | 22.31%     |
| ContestListAPIFetch    | EXCELLENT | 0.82  | 36.00%     | 433ms | 15.57%     |
| PaymentSubmitClick     | EXCELLENT | 0.82  | 18.18%     | 378ms | 20.86%     |
| WalletBalanceFetch     | EXCELLENT | 0.83  | 20.00%     | 142ms | 16.85%     |
| MatchScheduleAPICall   | EXCELLENT | 0.86  | 12.07%     | 153ms | 19.19%     |
| LeaderboardRefreshTap  | GOOD      | 0.78  | 18.57%     | 203ms | 7.19%      |
| ProfileSaveClick       | EXCELLENT | 0.80  | 12.29%     | 399ms | 18.34%     |
| NotificationTap        | GOOD      | 0.80  | 14.69%     | 183ms | 18.06%     |
| FilterApplyTap         | EXCELLENT | 0.85  | 18.05%     | 194ms | 19.73%     |
| LiveScoreRefresh       | EXCELLENT | 0.93  | 10.42%     | 378ms | 22.97%     |

### Issues Found

| Page                  | Issue                                                                                                                                        | Priority |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| Critical Interactions | EXCELLENT badge on interactions with 35%+ error rates — JoinContestButtonClick (35.48%) and ContestListAPIFetch (36%) are labelled Excellent | P1       |
| Critical Interactions | No sorting — worst interactions (highest error rate) are not surfaced first                                                                  | P1       |
| Critical Interactions | Right column cards clipped at viewport — Poor Users and other data invisible for right-side cards                                            | P1       |
| Critical Interactions | No time range selector                                                                                                                       | P2       |
| Critical Interactions | "My interactions" toggle has no tooltip — purpose is completely opaque                                                                       | P2       |
| Critical Interactions | Card description always truncated at ~35 chars — no expand or tooltip                                                                        | P2       |
| Critical Interactions | Bottom progress bar on each card has no label — unknown what it represents                                                                   | P2       |
| Critical Interactions | Only P50 shown — no P95/P99 to surface tail-end user experience                                                                              | P2       |
| Critical Interactions | "Poor Users" shown as % with no absolute count — 20% of unknown total                                                                        | P2       |
| Critical Interactions | Interaction names are camelCase code identifiers with no human-readable label                                                                | P3       |
| Critical Interactions | No click-through to interaction detail — cards appear static with no drill-down                                                              | P3       |
| Critical Interactions | Badge range never shown below GOOD — users don't know what a Poor/Critical state looks like                                                  | P3       |
| Critical Interactions | "12 Interactions" count styled as pill badge — looks like a clickable filter chip                                                            | P3       |

---

## App Vitals Page

**Route:** `/projects/proj-mock-1/app-vitals`  
**Tabs:** Crashes · ANRs · Non-Fatal  
**Key Sections:** KPI cards (Crash Metrics, ANR Metrics, Alert Status) · Trend chart · Issue table · Issue detail (Occurrence sub-tabs: Aggregated / App Version / OS Version / By Screen · Stack Trace / Breadcrumbs)

### What the page contains

#### KPI cards (always visible, all 3 tabs)

- **Crash Metrics**: Crash-Free Users 98.19–98.63% · Crash-Free Sessions 98.81–98.93%
- **ANR Metrics**: ANR-Free Users 53.78–88.46% · ANR-Free Sessions 88.35–97.24% (notably lower — ANRs are a bigger problem)
- **Alert Status**: Firing Alerts 0 · Active Alerts 0

#### Crashes tab

- Crashes Trend chart (red line)
- Crashes table: Title | App Versions | Occurrences | Affected Users | First Seen | Last Seen
- 10 crash entries, all spanning versions 2.3.0–2.4.0

#### ANRs tab

- ANRs Trend chart (orange line)
- ANRs (Application Not Responding) table — same columns
- NullPointerException: 140 occ, 98 users; OutOfMemoryError: 156 occ, 109 users; etc.

#### Non-Fatal tab

- Non-Fatal Issues Trend chart (blue line)
- Non-Fatal Issues table — adds a **Type** column
- All 10 entries show Type = "UNKN…" (UNKNOWN, truncated in badge)

#### Crash / ANR / Non-Fatal detail view

- Header: error name badge, error message, version range, occurrence count, date range
- Occurrence sub-tabs: Aggregated (line chart) | App Version | OS Version | By Screen (horizontal bars)
- Metadata row: Platform: unknown · Device · OS · Version: unknown · Screen: unknown
- SDK: unknown · Network: port · Bundle: unknown · Session: unknown · Interactions: count
- Error Trace / Stack Trace tab + Breadcrumbs tab
- "By Screen" bar chart shows bars with NO count labels

#### Time range picker

- Opened via "Last 24 hours" button
- Quick filter options: Last 5 min · Last 15 min · Last 30 min · Last 1 hour · Last 3 hours
- "Last 24 hours" (the active default) is NOT in the quick options list
- Custom range: Start time / End time date pickers + Reset / Apply buttons

#### Filters panel

- App Version · OS Version · Device dropdowns only

### Issues Found

| Page       | Issue                                                                                                                                 | Priority |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| App Vitals | KPI cards (Crash Metrics, ANR Metrics) remain visible on all tabs — switching to Non-Fatal doesn't surface Non-Fatal-specific metrics | P1       |
| App Vitals | Crash/ANR/Non-Fatal detail shows Platform, SDK, Screen, and Bundle all as "unknown" with no setup guide or instrumentation prompt     | P1       |
| App Vitals | Time range quick options cap at "Last 3 hours" — "Last 24 hours" (the active default), "Last 7 days", "Last 30 days" are absent       | P2       |
| App Vitals | "By Screen" occurrence bar chart shows no count labels — bars visible but users can't quantify which screen has the most crashes      | P2       |
| App Vitals | Non-Fatal Type column shows "UNKN…" (UNKNOWN) for every entry — truncated badge adds no value; classification is broken or missing    | P2       |
| App Vitals | Filters panel limited to App Version, OS Version, Device — no Screen filter or User filter                                            | P2       |
| App Vitals | Filters button has no active-state indicator (badge, dot, colour change) when filters are applied                                     | P2       |
| App Vitals | Trend charts only plot Occurrences — no toggle for Affected Users trend, which is the more actionable severity signal                 | P2       |
| App Vitals | Table columns (Occurrences, Affected Users) not sortable — users can't surface highest-impact issues                                  | P2       |
| App Vitals | Alert Status card shows 0/0 with no "Configure Alerts" CTA — zero-state gives no path to set up alerting                              | P3       |
| App Vitals | Section header labelled "Error Trace" in Non-Fatal detail but no equivalent header in Crash detail — inconsistent terminology         | P3       |
| App Vitals | Trend chart Y-axis has no label — users see numbers but can't tell if they represent occurrences, sessions, or users                  | P3       |

---

## Screens Page

**Route:** `/projects/proj-mock-1/screens`  
**Sub-route:** `/projects/proj-mock-1/screens/:screenName` (Screen Detail)  
**Sub-route:** `/projects/proj-mock-1/screens/:screenName/network/:apiUrl` (Network Details)

### Screens Listing

- "Screens" heading + "12 Screens" count badge
- Search input: "Search screens..."
- Time range: "Last 24 hours" + refresh (icon) button
- 4-column card grid of 12 screen cards
- Each card: monitor icon · screen name · Avg Time Spent · Error Rate · Avg Load Time · Users

#### All 12 screens (post-refresh snapshot)

| Screen              | Avg Time Spent | Error Rate | Avg Load Time | Users |
| ------------------- | -------------- | ---------- | ------------- | ----- |
| HomeScreen          | 49.7s          | 3.9%       | 628ms         | 8.5K  |
| ProductListScreen   | 27.3s          | 0.0%       | 297ms         | 10.1K |
| ProductDetailScreen | 33.0s          | 0.0%       | 712ms         | 9.7K  |
| CheckoutFormScreen  | 48.5s          | 0.0%       | 398ms         | 8.2K  |
| PaymentScreen       | 42.1s          | 0.0%       | 474ms         | 10.7K |
| ProfileScreen       | 30.0s          | 1.0%       | 309ms         | 11.2K |
| SearchResultsScreen | 33.0s          | 3.5%       | 361ms         | 11.1K |
| OrderListScreen     | 28.0s          | 0.0%       | 288ms         | 9.1K  |
| CartScreen          | 48.1s          | 3.4%       | 353ms         | 8.1K  |
| WishlistScreen      | 28.3s          | 0.0%       | 297ms         | 8.0K  |
| SettingsScreen      | 34.9s          | 1.2%       | 647ms         | 11.5K |
| NotificationsScreen | 31.9s          | 2.0%       | 291ms         | 10.2K |

### Screen Detail (HomeScreen)

**Tabs:** User Engagement | Performance & Stability | Network

#### User Engagement tab

- Average Time Spent widget: Avg Time Spent (s) + Avg Load Time (ms), dual-line Duration trend chart
- User Engagement widget: Daily Users, Weekly Users, Monthly Users + Users chart
- Active Sessions widget: Current, Peak, Average + Sessions chart

#### Performance & Stability tab

- Sub-tabs: Crashes | ANRs | Non-Fatal (same structure as App Vitals, but scoped to this screen)
- KPI cards: Crash Metrics (97.53% crash-free) + ANR Metrics (**47.43% ANR-free** = 52.57% of users hitting ANRs) + Performance (Screen Load Time: 450ms)
- KPI cards do NOT update when switching sub-tabs
- Filters: App Version | Platform | OS Version | Network Provider | State (different from App Vitals)

#### Network tab

- List of API calls made from this screen
- Columns (unlabelled): API URL | Response Time + FAST/MODERATE badge | Requests | Success Rate | Error Rate (shown only for some)
- Some rows have ">" chevron for drill-down, others don't — no visible pattern

### Network Details (/screens/HomeScreen/network/leaderboard/global)

- Header: "Network Details" + URL + "← Back" button
- Filter builder: "Select filter type" dropdown (App Version, Device Model, Platform, Geo State, OS Version, Interaction Name, HTTP Status Code) + value input + "+ Add"
- Active filter chip: "SCREEN NAME: HOMESCREEN ×"
- KPI cards: Network Performance (Avg Request Time 196ms, Total Requests 47,166) | Success Rate (97.5% success, 2.5% failed) | Response Time (P50 129ms, P95 423ms, P99 717ms)
- Status Code Distribution: donut chart + table (2xx, 3xx, 4xx, 5xx, Connection Error) — TOTAL: 48,670
- HTTP Method Distribution: donut chart + table (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS) — TOTAL: 46,258 (**discrepancy: 2,412 vs status code total**)
- Status Code Trend chart (stacked area, %)
- Latency Trend chart (Avg, P50, P95, P99 lines)
- HTTP Method Trend chart (stacked area, requests count)
- Client Errors (4xx) breakdown — 2,844 TOTAL (vs 920 in KPI — inconsistency)
- Server Errors (5xx) breakdown — 1,047 TOTAL (vs 121 in KPI)
- Network Issues by Provider — 3 bar charts (Connection & Timeout, 5xx, 4xx) grouped by BSNL, Vi, Airtel, **Airtel (duplicate)**, Jio

### Issues Found

| Page            | Issue                                                                                                                         | Priority |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------- | -------- |
| Screens         | Search input accepts text but does not filter cards — all 12 remain visible and count stays "12 Screens"                      | P1       |
| Screens         | 4th column cards (PaymentScreen, WishlistScreen) clipped at right edge — Load Time and Users truncated at 1440px              | P1       |
| Screen Detail   | ANR-Free Users drops to 47% (53% of users experiencing ANRs) with zero visual alert — displayed same style as healthy metrics | P1       |
| Screen Detail   | KPI cards (Crash Metrics, ANR Metrics) don't update when switching Crashes/ANRs/Non-Fatal sub-tabs                            | P1       |
| Screens         | No sort on listing — highest-error screens not surfaced; users must scan all 12 cards manually                                | P2       |
| Screens         | No health badge on cards — 3.9% error/628ms load looks identical to 0.0%/297ms                                                | P2       |
| Screens         | Error Rate not colour-coded — "3.9%" same neutral text as "0.0%"                                                              | P2       |
| Screens         | Time range picker doesn't close on outside click — requires Reset button; blocks right side of page                           | P2       |
| Screens         | No "last refreshed" timestamp — data changes on refresh with no indication of when last fetched                               | P2       |
| Screen Detail   | Performance card only shows Screen Load Time — no Frozen Frames, FPS, or Jank data                                            | P2       |
| Screen Detail   | Filter panel inconsistent with App Vitals — different dimensions across pages for same type of content                        | P2       |
| Screen Network  | Network API list has no column headers — users can't identify which column is Response Time vs Requests                       | P2       |
| Screen Network  | Error Rate only shown for MODERATE rows, hidden for FAST rows — same metric should appear consistently                        | P2       |
| Screen Network  | Row clickability inconsistent — some rows have ">" chevron, others don't, with no visible pattern                             | P2       |
| Network Details | Status code total (48,670) doesn't match HTTP method total (46,258) — unexplained 2,412 discrepancy with no note              | P2       |
| Screens         | Screen names are code identifiers ("SearchResultsScreen") with no human-readable labels                                       | P3       |
| Screens         | All cards share a generic monitor icon — no preview or visual differentiation between screen types                            | P3       |
| Screen Detail   | "← Back" button shows no destination context — should read "← Back to Screens"                                                | P3       |
| Network Details | "Airtel" listed as two separate bars in Network Issues by Provider charts — duplicate provider entry                          | P3       |
| Network Details | Error percentages (e.g., "22%") have no denominator label — unclear if % of 4xx or % of all requests                          | P3       |

---

## Query Builder Page

**Route:** `/projects/proj-mock-1/query-builder`  
**Modes:** AI · Builder · Code  
**Key Features:** AI chat with suggestion chips, Builder form (Dataset/Table/Time Range/Columns/Filter/Group/Order), Code mode with SQL editor + schema browser, Query History modal, multi-tab sessions

### AI Mode

- Mode buttons: AI (active) | Builder | Code | History (clock icon)
- Left panel: tab bar (+ add tab, ↗ copy, × close) + chat area
- Right panel: response detail view
- Empty state: "Ask a question about your data" + 4 suggestion chips
- Suggestion chips: "Why did the conversion drop?", "What are the top interactions with problems?", "Did this user perform purchase_complete in the last 24 hours?", "Show me error events from the last hour"
- Chat bubble: AI response summary + "N key findings · Click to view details" link
- Right panel: full narrative answer + metric change badges (Error Rate 12%→4%, Device Share 62%→34%, etc.) + KEY FINDINGS list
- Each key finding expands to show: detailed analysis, "Data Considered" bullet list with specific metrics, inline trend chart
- Below findings: Sources (PULSE_ATHENA_DB.OTEL_DATA_1), date range
- "Show Raw Data (100 rows)" / "Hide Raw Data" toggle button
- Raw data section: "100 OF 100 ROWS · 7.21 SEC · 47.13 MB · MORE AVAILABLE" + table + "Showing 1-25 of 100 loaded (100 total)" + Load More
- Raw data table: 27 OTEL columns (event_name, project_id, user_id, installation_id, android_os_api_level, os_version, app_build_id, ..., span_id, trace_id, timestamp, etc.)
- Follow-up input: "Ask a follow-up question..." + send button (at bottom of left chat panel)

### Builder Mode

- Adds green "Run Query" button (replaces with red "Cancel" during execution)
- Section toggles: Filter | Group | Order | Preview (all on by default)
- Fields: Dataset (pulse_athena_db) | Table (otel_data_1) — text inputs
- Time Range: REQUIRED badge → "Last 24 hours" dropdown
- Columns (optional): "+ Add column" → row with aggregate dropdown (Count, Count distinct, Sum, Avg, Min, Max) + Column select + Alias input + delete
- Filter by column value (optional): "+ Add filter" → Column | = (operator) | Value | delete
- Group by column: "+" button
- Order by: "Select column" dropdown + ASC/DESC buttons + Limit input ("No limit" default)
- Query Preview: auto-generated SQL with syntax highlighting + copy icon
- Results: showed "50 OF 50 ROWS · 7.21 SEC · 47.13 MB · MORE AVAILABLE" after running

### Code Mode

- Layout: left schema browser + right SQL editor
- Schema browser: table name (otel_data_1 / pulse_athena_db) + copy icon, COLUMNS count (27), "Search columns..." input (non-functional), scrollable column list with VARCHAR/number type badges
- SQL editor: Monaco-style, carries over Builder SQL, syntax highlighting, "192 chars · 1 lines" footer, copy + clear icons
- Clicking column names in schema does NOT insert into editor

### Query History Modal

- Opens from clock icon button
- "Query History · 8 QUERIES" heading + search input + refresh icon + close button
- Each entry: COMPLETED badge + relative time + SQL preview + query time + data scanned + copy + re-run icons
- Re-run loads SQL into editor and closes modal
- "8 queries shown" footer

### Issues Found

| Page          | Issue                                                                                                                                            | Priority |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | -------- |
| Query Builder | Clipboard copy throws unhandled `NotAllowedError` — shows full dev-style stack trace overlay to users; no graceful fallback                      | P1       |
| Query Builder | Builder mode: clicking any dropdown also opens the global project selector — click propagates to header                                          | P1       |
| Query Builder | Code mode: schema column search accepts text but does not filter — all 27 columns remain, count unchanged                                        | P1       |
| Query Builder | AI mode: metric change badges in right panel truncate ("iOS Conv…") — key metrics cut off, no tooltip or expand                                  | P2       |
| Query Builder | AI mode: right-panel answer body truncates mid-sentence with no "Show more"                                                                      | P2       |
| Query Builder | Builder mode: Results empty state says "Write a SQL query and click Run Query" — wrong instruction for Builder mode (Code mode only)             | P2       |
| Query Builder | No default row Limit — Builder/Code queries run SELECT \* with no cap; 47 MB+ scan triggered with no upfront cost/performance warning            | P2       |
| Query Builder | "MORE AVAILABLE" badge shown but no path to retrieve additional data beyond 50–100 rows                                                          | P2       |
| Query Builder | Raw data table has 27 raw OTEL column names (span_id, trace_id, vector_observed_timestamp) — not user-friendly; only 5 visible, no column picker | P2       |
| Query Builder | "100 OF 100 ROWS" and "MORE AVAILABLE" shown simultaneously — contradictory                                                                      | P2       |
| Query Builder | Clicking a column name in schema browser does not insert it at cursor in SQL editor                                                              | P2       |
| Query Builder | Data scanned label (47.13 MB) has no explanation — unclear if it's result size, bytes scanned, or cost-relevant volume                           | P3       |
| Query Builder | Query History has no status filter or date filter — can't filter by COMPLETED/FAILED or date range                                               | P3       |
| Query Builder | AI suggestion chips are static — same 4 chips every new tab, no personalisation or history-aware suggestions                                     | P3       |
| Query Builder | Tab titles truncate at ~20 chars with no full-text tooltip on hover                                                                              | P3       |

---

## Cross-Page / Global Issues

| Issue                                                               | Priority | Affects                      |
| ------------------------------------------------------------------- | -------- | ---------------------------- |
| Sidebar icon-only navigation — no labels or hover tooltips          | P2       | All pages                    |
| No global time range filter that persists across pages              | P2       | Home, Session List           |
| Quality score convention inconsistent between list and detail pages | P2       | Session List, Session Detail |
| No breadcrumb navigation — users lose context of where they are     | P3       | All pages                    |

---

## Exported Artefacts

| File                             | Contents                                                                                                    |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `session-ux-improvements.xlsx`   | Session List · Session Detail · Home · Engagement · Critical Interactions · App Vitals issues, colour-coded |
| `.claude/ux-analysis-context.md` | This file — full analysis context for all analysed pages                                                    |

---

---

## Alerts Page

**Routes:** `/projects/proj-mock-1/alerts` (listing), `/projects/proj-mock-1/alerts/:id` (detail), create/edit wizard  
**CTAs Clicked:** All filter tabs (All/Firing/Normal/Snoozed/No Data), search box, filter panel (Created By/Scope/Updated By), View on alert cards, Edit wizard (6 steps), Snooze dropdown, Create Alert, Back to Alerts

### What the page contains

- Alert listing: grid of 12 alert cards, filter tabs, search, advanced filter panel
- Alert detail: conditions, evaluation history (expandable), evaluation settings, metadata
- Create/Update wizard: 6-step modal (Name & Description → Select Scope → Conditions → Global Filters → Evaluation → Severity & Notification)
- Snooze button: dropdown with 6 preset durations

### Issues Found

| Page   | Issue                                                                                                                                      | Priority |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------ | -------- |
| Alerts | Search bar non-functional — typing does not filter alert list                                                                              | P1       |
| Alerts | Global document click handler crashes with `target.hasAttribute is not a function` — any non-element click target causes CRA error overlay | P1       |
| Alerts | Filter panel and nav profile dropdown can be open simultaneously — two overlays at once                                                    | P2       |
| Alerts | Filter panel has no close (X) button — only dismissed via Apply or clicking elsewhere                                                      | P2       |
| Alerts | No active filter indicator on filter button after applying filters — can't tell if filters are active                                      | P2       |
| Alerts | Closing Update Alert wizard returns to list, not to alert detail — user loses context                                                      | P2       |
| Alerts | Snooze only offers 6 fixed presets (1h, 4h, 8h, 24h, 2d, 1w) — no custom duration                                                          | P2       |
| Alerts | Wizard stepper steps locked — cannot jump to a later step without completing prior steps                                                   | P2       |
| Alerts | "View →" CTA on alert card is very subtle — low discoverability                                                                            | P3       |
| Alerts | No bulk actions on alert listing (bulk snooze, bulk delete)                                                                                | P3       |
| Alerts | Alert condition endpoint URL truncated on card — hard to identify specific API endpoint                                                    | P3       |
| Alerts | Pagination control rendered even with single page — unnecessary chrome                                                                     | P3       |

---

## Event Catalog Page

**Route:** `/projects/proj-mock-1/event-catalog`  
**CTAs Clicked:** Search, category filter dropdown, attribute badge, event name/row, edit icon, delete icon (→ archive confirmation), Upload CSV (→ Bulk Upload modal, Download template), Add Event modal

### What the page contains

- Table of 14 events: name, description, category badge, attribute count, created date, edit/delete actions
- Category filter dropdown (predefined: authentication, commerce, engagement, errors, navigation, notifications)
- Bulk Upload modal: CSV file picker, format preview, Download CSV template
- Edit Event Definition modal: name, display name, description, category (free-text), attributes list (name, type, required toggle, description per attr), Add Attribute, Cancel, Update Event
- Archive confirmation modal (soft delete — not permanent)

### Issues Found

| Page          | Issue                                                                                              | Priority |
| ------------- | -------------------------------------------------------------------------------------------------- | -------- |
| Event Catalog | Text search non-functional — does not filter event list                                            | P1       |
| Event Catalog | No event detail view — clicking event name/row does nothing; attributes only visible in edit modal | P2       |
| Event Catalog | Attribute count badge is not interactive — clicking the number does nothing                        | P2       |
| Event Catalog | CSV expected format string truncated in Upload modal — not all columns visible                     | P2       |
| Event Catalog | "Download CSV template" shows no feedback — no toast, loading state, or confirmation               | P2       |
| Event Catalog | Category in Add Event modal is free-text; category filter uses predefined dropdown — inconsistency | P2       |
| Event Catalog | Adding new attribute doesn't auto-scroll to reveal the new empty row                               | P2       |
| Event Catalog | Trash icon implies permanent delete; actual action is soft "Archive" — iconography mismatch        | P2       |
| Event Catalog | No sortable columns in event table (EVENT, DESCRIPTION, CATEGORY, CREATED all non-sortable)        | P3       |
| Event Catalog | No bulk archive/manage functionality                                                               | P3       |

---

## Profile

**Finding:** No dedicated user profile page exists.  
"Dev User" displayed in the nav dropdown is not clickable and does not link anywhere.

### Issues Found

| Page    | Issue                                                                                | Priority |
| ------- | ------------------------------------------------------------------------------------ | -------- |
| Profile | No user profile page — "Dev User" in nav dropdown is not a link                      | P2       |
| Profile | No way to edit profile info (name, email, password, avatar) from anywhere in the app | P2       |

---

## Organization (Projects / Members / Pricing & Plans)

**Routes:** `/tenant-mock-1/projects`, members, pricing  
**CTAs Clicked:** Create Project (→ full-page form), project cards (active + inactive), Invite Member, edit role icon, Remove member, Pricing & Plans

### What the pages contain

- Projects listing: 3 project cards (2 ACTIVE, 1 INACTIVE), Create Project button
- Members: 5 org members table, Invite Member modal (multi-email + role), edit role icons, Remove with confirmation
- Pricing & Plans: static read-only Enterprise plan benefits list

### Issues Found

| Page                    | Issue                                                                                               | Priority |
| ----------------------- | --------------------------------------------------------------------------------------------------- | -------- |
| Organization / Members  | Edit role icon incorrectly opens "Invite Team Members" modal — wrong action triggered               | P1       |
| Organization / Projects | INACTIVE project opens full dashboard normally — no warning, visual behavior identical to ACTIVE    | P2       |
| Organization / Projects | No project management from listing card (no edit, settings, or delete option)                       | P2       |
| Organization / Pricing  | Pricing page is entirely read-only — no billing management, plan comparison, or "Contact Sales" CTA | P3       |
| Organization / Members  | No search or filter in members table                                                                | P3       |

---

## Current Project Settings

**Route:** `/projects/proj-mock-1/settings`  
**Sections:** SDK Configuration, API Keys, Team Members, Notifications, Security & Access  
**CTAs Clicked:** View version, Duplicate, Create New Version, Edit (wizard), JSON modal, Reset/Save; Copy API key, Regenerate Key; Invite Member; Add Channel, edit channel, delete channel; all settings sidebar items

### What the pages contain

- **SDK Config**: version history (v1/v2/v3), active version card with View/Duplicate, edit wizard to create new version from existing, JSON view modal
- **API Keys**: masked project API key with copy, Regenerate Key with warning
- **Team Members**: project-level member management (2 members), Invite Member
- **Notifications**: 4 Slack webhook channels, Add Channel / Edit / Delete
- **Security & Access**: "COMING SOON" placeholder

### Issues Found

| Page                     | Issue                                                                                                   | Priority |
| ------------------------ | ------------------------------------------------------------------------------------------------------- | -------- |
| Settings / SDK Config    | Copy/paste wording conflict: header says "Click Edit", info banner says "Click Create from this"        | P2       |
| Settings / API Keys      | Copying API key gives no feedback — no "Copied!" toast or visual confirmation                           | P2       |
| Settings / API Keys      | Masked API key has no reveal toggle — cannot view it, only copy (fails silently in unfocused contexts)  | P2       |
| Settings / Notifications | Email channel type is visible but non-interactive — clicking it does not select it                      | P2       |
| Settings / Team Members  | Project-level roles (ADMIN/VIEWER) differ from org-level (ADMIN/MEMBER) — inconsistent role terminology | P2       |
| Settings / Security      | "Security & Access" shows "COMING SOON" with no ETA or any placeholder info                             | P3       |
| Settings / Notifications | Only Slack and (broken) Email channel types supported — no PagerDuty, Teams, Discord etc.               | P3       |

---

## About Pulse & Contact Us

**About Pulse:** External anchor `https://pulse.dreamhorizon.org/docs/intro` opening in new tab — not an in-app page  
**Contact Us:** Routes to `/support-queries` — crashes entire app

### Issues Found

| Page        | Issue                                                                                                    | Priority |
| ----------- | -------------------------------------------------------------------------------------------------------- | -------- |
| Contact Us  | Clicking "Contact Us" crashes entire app — `incidents.map is not a function` in SupportQueries component | P1       |
| About Pulse | "About Pulse" opens external docs in new tab — no in-app version info, changelog, or release notes       | P2       |

---

## Next Steps (Suggested)

1. Fix P1 issues on Session Listing (table layout, badge overflow, session count)
2. Fix P1 label bug on Session Detail ("Session Time" → "Session ID")
3. Fix "EXCELLENT" badge logic on Home to incorporate error rate into composite health score
4. Add global time range control to Home dashboard
5. Fix "Contact Us" crash (`incidents.map is not a function` in SupportQueries)
6. Fix Edit role icon opening wrong modal (Invite instead of Change Role)
7. Fix non-functional search bars across Alerts and Event Catalog
