# pulse-mcp / crashes (App Vitals) tools

Parent plan: [../index.md](../index.md). Component brief: [../../../components/pulse-mcp.md](../../../components/pulse-mcp.md).

## Purpose

Surface mobile App Vitals: crash, ANR, and non-fatal issue lists, per-issue
summaries, time-bucketed trends, stack-trace samples, screen breakdowns, and
first/last-seen timestamps. These tools mirror the dashboard's `pulse-ui` App
Vitals screens (`useExceptionListData`, `useGetAppStats`, `useIssueDetailData`,
`useIssueTrendData`, `useIssueStackTraces`, `useIssueScreenBreakdown`,
`useExceptionTimestamps`).

## Source location

- `pulse-mcp/src/tools/appVitals.ts` — `registerAppVitalsTools(server)`.
- `pulse-mcp/src/tools/appVitalsHelpers.ts` — query builder helpers
  (`buildExceptionListBody`, `buildCommonFilters`, `runDistribution`,
  `postDistribution`, `resolveTimeRange`, `getTimeBucketSize`,
  `formatToolError`).
- `pulse-mcp/src/tools/appVitalsConstants.ts` — column name constants and
  `PULSE_TYPE_SESSION_START`.

There is no separate `crashes.ts` file in the source — crash tooling lives
inside `appVitals.ts`.

## Public surface

All tools require `projectId` and post to the Pulse "distribution" query API.
Time arguments default to the **last 7 days**.

### `list_app_vitals_crash_issues`

- `kind = "crash"` (PulseType `device.crash`).
- Common filter args: `startTime?`, `endTime?`, `appVersion?`, `osVersion?`,
  `device?`, `platform?`, `networkProvider?`, `state?`, `screenName?`, `limit?`
  (1…`MAX_LIST_LIMIT`, default `DEFAULT_LIST_LIMIT`).
- Endpoint: pulse-server distribution endpoint via `postDistribution` (helper).

### `list_app_vitals_anr_issues`

- Same shape as crash list, `kind = "anr"` (PulseType `device.anr`).

### `list_app_vitals_nonfatal_issues`

- Same shape, `kind = "nonfatal"` (PulseType `non_fatal`).

### `get_app_vitals_user_session_totals`

- Returns unique users + unique sessions over the range — the denominator for
  crash-free style metrics. Builds filters from `PulseType = session.start` and
  `buildCommonFilters(...)`. Selects:
  - `uniqCombined64(nullIf(UserId, ''))` → `unique_users`
  - `uniqCombined64(nullIf(SessionId, ''))` → `unique_sessions`
- `dataType: "LOGS"`.

### `get_app_vitals_issue_summary`

- Params: `projectId`, `groupId`, `startTime?`, `endTime?`.
- Returns one row: `group_id`, `event_name`, `error_message`, `error_type`,
  `title`, `app_versions`, `occurrences`, `first_seen`, `last_seen`,
  `affected_users`.
- `dataType: "EXCEPTIONS"`, `groupBy: ["group_id","title","error_type"]`,
  ordered by `occurrences DESC`, `limit: 1`.

### `get_app_vitals_issue_trend`

- Params: `projectId`, `groupId`, `trendView` (`aggregated` | `appVersion` | `os`),
  optional time + telemetry filters.
- Time bucket comes from `getTimeBucketSize(start, end)`.
- For `appVersion`/`os` trend views, an extra `COL` selector and groupBy column
  is appended.
- Wraps the response with `{ ok, empty, hint, bucketSize, trendView, …data }`.

### `get_app_vitals_issue_stack_traces`

- Params: `projectId`, `groupId`, time range, `limit` (1…50, default 10).
- Selects raw sample rows: `TraceId`, `SpanId`, `Timestamp`, `DeviceModel`,
  `OsVersion`, `AppVersion`, `ExceptionStackTrace`, `ExceptionStackTraceRaw`,
  `ExceptionMessage`, `ExceptionType`, `Title`, `ScreenName`, `Platform`,
  `SessionId`, `SdkVersion`, `AppVersionCode`,
  `ResourceAttributes['network.carrier.name']`, `UserId`,
  `arrayStringConcat(Interactions, ', ')`, `BundleId`.
- Ordered by `timestamp DESC`.

### `get_app_vitals_issue_screen_breakdown`

- Top 10 screens by `count()` for the GroupId. `dataType: "EXCEPTIONS"`,
  `groupBy: ["screen_name"]`, `orderBy: occurrences DESC`.

### `get_app_vitals_exception_first_last_seen`

- Params: `projectId`, `groupIds` (≤ `MAX_GROUP_IDS = 50`),
  `eventName?` (`device.crash` | `device.anr`; omit for non-fatal), optional
  app/os/device/screen filters.
- Time window is hard-coded to the last ~6 months.
- Returns one row per GroupId with `first_seen` / `last_seen`.

## Underlying Pulse API

All App Vitals tools post to the pulse-server distribution query endpoint via
`postDistribution(projectId, body)` in `appVitalsHelpers.ts`. The body matches
the same `DistributionRequestBody` schema (`dataType`, `timeRange`, `filters`,
`select`, `groupBy?`, `orderBy?`, `limit?`) used by the `pulse-ui` exception
list hooks. The server then translates the request into the ClickHouse query
against `otel_logs` / `stack_trace_events`.

## Internal design

- `listTool(server, name, description, kind)` is a higher-order helper that
  registers all three issue-list tools from the same `commonListArgs` schema.
- `buildExceptionListBody({kind, …})` produces the canonical distribution body
  for a `kind` of `crash | anr | nonfatal`.
- `buildCommonFilters(appVersion, osVersion, device, platform, networkProvider, state)`
  drops any filter whose value is `"all"`.
- `runDistribution(projectId, body, emptyHint)` posts, wraps the response in
  `{ ok, empty, hint, …data }`, catches errors via `formatToolError`.
- The trend tool calls `postDistribution` directly because it needs to merge
  `bucketSize` + `trendView` into the wrapper.

## Dependencies

- `appVitalsHelpers.ts` — exports types `DistributionRequestBody`,
  `ExceptionKind`, `FilterField`, `SelectField`.
- `appVitalsConstants.ts` — `COLUMN_NAME` (PulseType, UserId, SessionId,
  AppVersion, Timestamp) and `PULSE_TYPE_SESSION_START = "session.start"`.
- `getClient()` from `src/client.ts`.

## Data contracts

- PulseType filter values: `device.crash`, `device.anr`, `non_fatal`,
  `session.start`.
- All select expressions use ClickHouse syntax delegated to pulse-server.
- The list tools have **no offset**; narrow the time range to see more issues.

## Tests

E2E only — no unit tests in this package. Hooks they mirror are tested in
`pulse-ui/`.

## History / decisions

- Shared `listTool` factory was added so the three issue-kind tools stay byte-
  identical in shape — divergence here previously caused incident drift between
  crash and ANR views.
- The 6-month hard window on `get_app_vitals_exception_first_last_seen` matches
  the dashboard hook `useExceptionTimestamps` and bounds the cost of the
  cross-issue join.
- The trend wrapper includes `empty` + `hint` because the LLM otherwise
  hallucinated trend points when the array was `[]`.

## Rebuild recipe

```ts
// list-style tools
const listTool = (server, name, desc, kind) =>
  server.tool(name, desc, commonListArgs, async (args) =>
    runDistribution(args.projectId,
      buildExceptionListBody({ kind, ...args }),
      "No exception rows for this time range and filters."));

listTool(server, "list_app_vitals_crash_issues",  "…", "crash");
listTool(server, "list_app_vitals_anr_issues",    "…", "anr");
listTool(server, "list_app_vitals_nonfatal_issues","…", "nonfatal");
```

For detail tools, hand-write the `select`/`groupBy`/`orderBy` payloads to match
the dashboard hook of the same purpose.
