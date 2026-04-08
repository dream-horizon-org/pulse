# Performance metric query API (`QueryRequest`)

This document describes the JSON request body **`QueryRequest`** used by the performance metric distribution endpoint and related flows.

## Endpoint

| Item | Value |
|------|--------|
| Method | `POST` |
| Path | `/v1/interactions/performance-metric/distribution` |
| Content-Type | `application/json` |
| Response | `application/json` — wrapper with `data` / `error` (see [Response shape](#response-shape)) |

### Authorization and tenancy

- **Permission:** `can_view` on the target project (OpenFGA). This endpoint is a read-only analytics query exposed as POST.
- **Project:** Send **`X-Project-ID`**. The server sets `projectId` on the request from `ProjectContext`; a `projectId` field in the JSON body is **not** authoritative for scoping.

---

## `QueryRequest` (root object)

| Field | Type | Description |
|-------|------|-------------|
| `dataType` | string (enum) | Selects the ClickHouse table (see [DataType](#datatype)). |
| `timeRange` | object | Required for the time filter on `Timestamp` (see [TimeRange](#timerange)). |
| `select` | array | Optional. Each item selects a metric/dimension (see [SelectItem](#selectitem)). If omitted or empty, the server may default the SELECT clause. |
| `filters` | array | Optional. Extra WHERE predicates (see [Filter](#filter)). |
| `groupBy` | string[] | Optional. `GROUP BY` column names. |
| `orderBy` | array | Optional. Sort order (see [OrderBy](#orderby)). |
| `limit` | integer | Optional. Max rows; server default **100** if null. |
| `projectId` | string | Present on the model but **overwritten** by the server for the distribution API — use `X-Project-ID`. |

**Serialization notes:** Unknown JSON properties are ignored. Null fields are omitted on serialization where `@JsonInclude(NON_NULL)` applies.

---

## `DataType`

JSON values are the **enum names** (e.g. `"TRACES"`).

| Value | ClickHouse table |
|-------|------------------|
| `TRACES` | `otel_traces` |
| `LOGS` | `otel_logs` |
| `METRICS` | `otel_metrics` |
| `EXCEPTIONS` | `stack_trace_events` |

---

## `TimeRange`

| Field | Type | Description |
|-------|------|-------------|
| `start` | string | Range start (ISO-8601). |
| `end` | string | Range end (ISO-8601). |

---

## `SelectItem`

| Field | Type | Description |
|-------|------|-------------|
| `function` | string (enum) | Name of `Functions` — see [Functions enum](#functions-enum). |
| `param` | object (string → string) | Function-specific parameters. Examples: `COL` → `field`; `CUSTOM` → `expression`; `TIME_BUCKET` → `bucket`, `field`. |
| `alias` | string | Optional SQL column alias. |

---

## `Filter`

| Field | Type | Description |
|-------|------|-------------|
| `field` | string | Column or expression on the left side of the predicate. |
| `operator` | string (enum) | `LIKE`, `IN`, `EQ`, or `ADDITIONAL` — see [Operator](#operator). |
| `value` | array | Operand(s): list for `IN`; first element for `EQ`; for `ADDITIONAL`, raw SQL fragment in `value[0]`. |

### Operator

| Value | Usage |
|-------|--------|
| `LIKE` | Pattern match (`like`). |
| `IN` | Membership in a list. |
| `EQ` | Equality to `value[0]`. |
| `ADDITIONAL` | Raw predicate fragment: `AND ( <value[0]> )`. |

---

## `OrderBy`

| Field | Type | Description |
|-------|------|-------------|
| `field` | string | Column or alias to sort by. |
| `direction` | string (enum) | `ASC` or `DESC`. |

---

## `Functions` enum

Use the **Java enum constant name** in JSON (e.g. `"APDEX"`, `"DURATION_P99"`).

Defined in `resources/performance/models/Functions.java`. Includes, among others:

- **Core / latency:** `APDEX`, `CRASH`, `ANR`, `FROZEN_FRAME`, `ANALYSED_FRAME`, `UNANALYSED_FRAME`, `DURATION_P99`, `DURATION_P50`, `DURATION_P95`
- **Helpers:** `COL`, `CUSTOM`, `TIME_BUCKET`, `ARR_TO_STR`
- **Interactions:** `INTERACTION_SUCCESS_COUNT`, `INTERACTION_ERROR_COUNT`, `INTERACTION_ERROR_DISTINCT_USERS`
- **User categories:** `USER_CATEGORY_EXCELLENT`, `USER_CATEGORY_GOOD`, `USER_CATEGORY_AVERAGE`, `USER_CATEGORY_POOR`
- **Network (status buckets):** `NET_0`, `NET_2XX`, `NET_3XX`, `NET_4XX`, `NET_5XX`, `NET_COUNT`, and `NET_*_RATE`
- **Network by PulseType (alerts):** `NET_0_BY_PULSE_TYPE`, `NET_2XX_BY_PULSE_TYPE`, … `NET_COUNT_BY_PULSE_TYPE`
- **Rates / App vitals:** `CRASH_RATE`, `ANR_RATE`, `FROZEN_FRAME_RATE`, `ERROR_RATE`, user-rate variants, `LOAD_TIME`, `SCREEN_TIME`, `SCREEN_DAILY_USERS`
- **Crash / ANR / non-fatal aggregates:** `CRASH_FREE_*`, `CRASH_USERS`, `CRASH_SESSIONS`, `ALL_USERS`, `ALL_SESSIONS`, `ANR_*`, `NON_FATAL_*`, etc.

Exact SQL fragments are in `ClickhouseConstants` / `ClickhouseMetricService`.

---

## Response shape

Success payload type **`PerformanceMetricDistributionRes`**:

| Field | Type | Description |
|-------|------|-------------|
| `fields` | string[] | Column names in order. |
| `rows` | string[][] | Each inner array is one row; cell values are strings (nulls may appear as empty strings). |

Wrapped in the standard **`Response<T>`** type: `data` holds the result object; `error` is null on success. On failure, `error` contains `code` and `message`.

---

## Example request

```http
POST /v1/interactions/performance-metric/distribution
Content-Type: application/json
X-Project-ID: <your-project-id>
```

```json
{
  "dataType": "TRACES",
  "timeRange": {
    "start": "2025-11-07T08:40:00Z",
    "end": "2025-11-12T14:40:00Z"
  },
  "select": [
    { "function": "COL", "param": { "field": "os.version" }, "alias": "osVersion" },
    { "function": "DURATION_P99", "alias": "duration_p99" },
    { "function": "APDEX" }
  ],
  "filters": [
    { "field": "span.name", "operator": "IN", "value": ["page_load"] }
  ],
  "groupBy": ["osVersion"],
  "orderBy": [
    { "field": "duration_p99", "direction": "DESC" }
  ],
  "limit": 100
}
```

---

## Related code

| Item | Location |
|------|----------|
| Request model | `resources/performance/models/QueryRequest.java` |
| Functions enum | `resources/performance/models/Functions.java` |
| Response model | `resources/performance/models/PerformanceMetricDistributionRes.java` |
| Resource | `resources/performance/PerformanceMetricDistribution.java` |
| Query execution | `service/interaction/ClickhouseMetricService.java` |
| Long examples & function descriptions | `README.md` (search for `performance-metric/distribution`) |

---

## Implementation notes

- ClickHouse query execution uses a short timeout and optional async job creation; see `ClickhouseMetricService#getMetricDistribution`.
- Ensure `timeRange`, `select`, and table/column names match the chosen `dataType` and your schema.
