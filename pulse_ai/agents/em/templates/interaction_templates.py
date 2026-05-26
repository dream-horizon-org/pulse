"""Interaction analytics templates — QueryRequest skeletons for each metric type.

Each function builds a complete QueryRequest dict using build_query_request().
The analytics tools (Step 7) call these directly with LLM-provided params.

Source of truth: Frontend hooks in pulse-ui/src/hooks/ and backend
ClickhouseMetricService.java for field names and functions.
"""

from datetime import datetime, timezone

from pulse_ai.agents.em.templates.base import build_query_request


# ---------------------------------------------------------------------------
# Time bucket calculator — ported from pulse-ui/src/utils/TimeBucketUtil.ts
# ---------------------------------------------------------------------------

# Ordered from smallest to largest
BUCKET_ORDER = ["1m", "5m", "10m", "30m", "1h", "3h", "6h", "12h", "1d"]

BUCKET_SIZES_MS = {
    "1m": 1 * 60 * 1000,
    "5m": 5 * 60 * 1000,
    "10m": 10 * 60 * 1000,
    "30m": 30 * 60 * 1000,
    "1h": 1 * 60 * 60 * 1000,
    "3h": 3 * 60 * 60 * 1000,
    "6h": 6 * 60 * 60 * 1000,
    "12h": 12 * 60 * 60 * 1000,
    "1d": 1 * 24 * 60 * 60 * 1000,
}

MAX_POINTS = 20
MIN_BUCKET_SIZE_MS = 1 * 60 * 1000  # 1 minute


def get_time_bucket_size(start_time: str, end_time: str) -> str:
    """Determine appropriate time bucket size for a given time range.

    Matches the frontend algorithm: max 20 data points, min 1-minute buckets.
    Source: pulse-ui/src/utils/TimeBucketUtil.ts

    Args:
        start_time: ISO 8601 start timestamp.
        end_time: ISO 8601 end timestamp.

    Returns:
        Bucket size string (e.g. "5m", "1h", "1d").
    """
    if not start_time or not end_time:
        return "5m"

    try:
        start_dt = datetime.fromisoformat(start_time.replace("Z", "+00:00"))
        end_dt = datetime.fromisoformat(end_time.replace("Z", "+00:00"))
    except (ValueError, TypeError):
        return "5m"

    diff_ms = int((end_dt - start_dt).total_seconds() * 1000)

    # Clamp to maximum 90 days
    max_range_ms = 90 * 24 * 60 * 60 * 1000
    if diff_ms > max_range_ms:
        diff_ms = max_range_ms

    # Calculate ideal bucket size to get exactly MAX_POINTS
    ideal_bucket_ms = diff_ms / MAX_POINTS

    # Ensure minimum bucket size
    required_bucket_ms = max(ideal_bucket_ms, MIN_BUCKET_SIZE_MS)

    # Find the smallest bucket size that is >= required
    for bucket in BUCKET_ORDER:
        if BUCKET_SIZES_MS[bucket] >= required_bucket_ms:
            return bucket

    # Fallback to largest bucket
    return BUCKET_ORDER[-1]


# ---------------------------------------------------------------------------
# Common select items — reused across templates
# ---------------------------------------------------------------------------

SELECT_APDEX = {"function": "APDEX", "alias": "apdex"}
SELECT_SUCCESS_COUNT = {"function": "INTERACTION_SUCCESS_COUNT", "alias": "success_count"}
SELECT_ERROR_COUNT = {"function": "INTERACTION_ERROR_COUNT", "alias": "error_count"}
SELECT_P50 = {"function": "DURATION_P50", "alias": "p50"}
SELECT_P95 = {"function": "DURATION_P95", "alias": "p95"}
SELECT_P99 = {"function": "DURATION_P99", "alias": "p99"}
SELECT_FROZEN_FRAME = {"function": "FROZEN_FRAME", "alias": "frozen_frame"}
SELECT_ANR = {"function": "ANR", "alias": "anr"}
SELECT_CRASH = {"function": "CRASH", "alias": "crash"}
SELECT_USER_EXCELLENT = {"function": "USER_CATEGORY_EXCELLENT", "alias": "user_excellent"}
SELECT_USER_GOOD = {"function": "USER_CATEGORY_GOOD", "alias": "user_good"}
SELECT_USER_AVG = {"function": "USER_CATEGORY_AVERAGE", "alias": "user_avg"}
SELECT_USER_POOR = {"function": "USER_CATEGORY_POOR", "alias": "user_poor"}

# Grouped selections
SELECT_USER_CATEGORIES = [SELECT_USER_EXCELLENT, SELECT_USER_GOOD, SELECT_USER_AVG, SELECT_USER_POOR]
SELECT_LATENCY = [SELECT_P50, SELECT_P95]
SELECT_ERROR_RATE = [SELECT_SUCCESS_COUNT, SELECT_ERROR_COUNT]

# Composite: all metric types in one request
# Source: useGetInteractionDetailsGraphs.ts — metricsRequestBody
SELECT_COMPOSITE = [
    SELECT_APDEX,
    SELECT_SUCCESS_COUNT,
    SELECT_ERROR_COUNT,
    SELECT_P50,
    SELECT_P95,
    SELECT_FROZEN_FRAME,
    {"function": "UNANALYSED_FRAME", "alias": "unanalysed_frame"},
    {"function": "ANALYSED_FRAME", "alias": "analysed_frame"},
    SELECT_CRASH,
    SELECT_ANR,
    {"function": "NET_0", "alias": "net_0"},
    {"function": "NET_2XX", "alias": "net_2xx"},
    {"function": "NET_4XX", "alias": "net_4xx"},
    {"function": "NET_5XX", "alias": "net_5xx"},
    *SELECT_USER_CATEGORIES,
]

# Metric type → select items mapping
METRIC_SELECT_MAP = {
    "apdex": [SELECT_APDEX],
    "latency": SELECT_LATENCY,
    "error_rate": SELECT_ERROR_RATE,
    "user_categories": SELECT_USER_CATEGORIES,
    "composite": SELECT_COMPOSITE,
}


# ---------------------------------------------------------------------------
# Template: Health overview (Tool 5)
# Source: useGetTopInteractionsHealthData.ts
# ---------------------------------------------------------------------------

def build_health_query(
    top_n: int = 10,
    interaction_names: list[str] | None = None,
    time_range: str = "last_24h",
    start_time: str | None = None,
    end_time: str | None = None,
    user_filters: dict | None = None,
) -> dict:
    """Build QueryRequest for interaction health overview.

    Shows top interactions ranked by frequency with key metrics:
    Apdex, success/error counts, user categories, P50.
    """
    select = [
        {"function": "COL", "param": {"field": "SpanName"}, "alias": "interaction_name"},
        {"function": "CUSTOM", "param": {"expression": "COUNT()"}, "alias": "spanfreq"},
        SELECT_APDEX,
        SELECT_SUCCESS_COUNT,
        SELECT_ERROR_COUNT,
        *SELECT_USER_CATEGORIES,
        SELECT_P50,
    ]

    # If specific interaction names, filter by them
    base_filters = []
    if interaction_names:
        base_filters.append({
            "field": "SpanName",
            "operator": "IN",
            "value": interaction_names,
        })

    return build_query_request(
        select=select,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        user_filters=user_filters,
        base_filters=base_filters if base_filters else None,
        group_by=["interaction_name"],
        order_by=[{"field": "spanfreq", "direction": "DESC"}],
        limit=top_n,
        inject_interaction_filters=True,
    )


# ---------------------------------------------------------------------------
# Template: Specific metrics (Tool 6)
# Source: useGetInteractionDetailsGraphs.ts
# ---------------------------------------------------------------------------

def build_metrics_query(
    metric_type: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str | None = None,
    end_time: str | None = None,
    timeseries: bool = False,
    user_filters: dict | None = None,
) -> dict:
    """Build QueryRequest for a specific interaction metric.

    Args:
        metric_type: One of apdex, latency, error_rate, user_categories, composite.
        interaction_name: The interaction name to query.
        time_range: Time range enum.
        start_time: Custom start (only when time_range="custom").
        end_time: Custom end (only when time_range="custom").
        timeseries: If True, prepend TIME_BUCKET for trend data.
        user_filters: Optional dimension filters.

    Raises:
        ValueError: If metric_type is not valid.
    """
    if metric_type not in METRIC_SELECT_MAP:
        raise ValueError(
            f"Unknown metric_type '{metric_type}'. "
            f"Valid values: {', '.join(METRIC_SELECT_MAP.keys())}"
        )

    select = list(METRIC_SELECT_MAP[metric_type])  # copy to avoid mutation

    group_by = None
    order_by = None

    if timeseries:
        # Compute the actual time range to determine bucket size
        from pulse_ai.agents.em.templates.base import compute_time_range
        computed_start, computed_end = compute_time_range(time_range, start_time, end_time)
        bucket_size = get_time_bucket_size(computed_start, computed_end)

        time_bucket_select = {
            "function": "TIME_BUCKET",
            "param": {"bucket": bucket_size, "field": "Timestamp"},
            "alias": "t1",
        }
        select.insert(0, time_bucket_select)
        group_by = ["t1"]
        order_by = [{"field": "t1", "direction": "ASC"}]

    return build_query_request(
        select=select,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        user_filters=user_filters,
        group_by=group_by,
        order_by=order_by,
        inject_interaction_filters=True,
        interaction_name=interaction_name,
    )


# ---------------------------------------------------------------------------
# Template: Metric trends (Interaction Details graphs)
# Source: useGetInteractionDetailsGraphs.ts — graphDataRequestBody
# ---------------------------------------------------------------------------

def build_interaction_metric_trends_query(
    interaction_name: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    user_filters: dict | None = None,
) -> dict:
    """Build time-bucketed Apdex, error, and user-category trends for Block 5."""
    from pulse_ai.agents.em.templates.base import compute_time_range

    computed_start, computed_end = compute_time_range(time_range, start_time, end_time)
    bucket_size = get_time_bucket_size(computed_start, computed_end)

    select = [
        {
            "function": "TIME_BUCKET",
            "param": {"bucket": bucket_size, "field": "Timestamp"},
            "alias": "t1",
        },
        SELECT_APDEX,
        SELECT_SUCCESS_COUNT,
        SELECT_ERROR_COUNT,
        *SELECT_USER_CATEGORIES,
    ]

    return build_query_request(
        select=select,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        user_filters=user_filters,
        group_by=["t1"],
        order_by=[{"field": "t1", "direction": "ASC"}],
        inject_interaction_filters=True,
        interaction_name=interaction_name,
    )


# ---------------------------------------------------------------------------
# Template: Latency percentiles including P99
# Source: pulse-mcp get_interaction_time
# ---------------------------------------------------------------------------

def build_interaction_latency_percentiles_query(
    interaction_name: str,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    user_filters: dict | None = None,
) -> dict:
    """Build aggregate P50 / P95 / P99 latency for Block 5 latency lens."""
    return build_query_request(
        select=[SELECT_P50, SELECT_P95, SELECT_P99],
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        user_filters=user_filters,
        inject_interaction_filters=True,
        interaction_name=interaction_name,
    )


# ---------------------------------------------------------------------------
# Template: Breakdowns (Tool 8)
# Source: useGetDevicePerformance.ts, useGetRegionalInsights.ts,
#         useGetReleasePerformance.ts, useGetPlatformInsights.ts
# ---------------------------------------------------------------------------

# dimension → (column_field, alias, select_items)
DIMENSION_CONFIG = {
    "device": {
        "field": "DeviceModel",
        "alias": "deviceModel",
        "select": [SELECT_FROZEN_FRAME, SELECT_ANR, SELECT_CRASH],
    },
    "region": {
        "field": "GeoState",
        "alias": "region",
        "select": [SELECT_SUCCESS_COUNT, SELECT_ERROR_COUNT, SELECT_USER_POOR],
    },
    "release": {
        "field": "AppVersion",
        "alias": "release",
        "select": [SELECT_APDEX, SELECT_CRASH, SELECT_ANR, SELECT_SUCCESS_COUNT, SELECT_ERROR_COUNT],
    },
    "platform": {
        "field": "Platform",
        "alias": "platform",
        "select": [SELECT_ERROR_COUNT, SELECT_USER_POOR],
    },
    "os": {
        "field": "OsVersion",
        "alias": "os_version",
        "select": [SELECT_ERROR_COUNT, SELECT_USER_POOR],
    },
    "network": {
        "field": "NetworkProvider",
        "alias": "network",
        "select": [SELECT_SUCCESS_COUNT, SELECT_ERROR_COUNT],
    },
    "latency_by_network": {
        "field": "NetworkProvider",
        "alias": "network",
        "select": [SELECT_P50, SELECT_P95],
    },
    "latency_by_device": {
        "field": "DeviceModel",
        "alias": "deviceModel",
        "select": [SELECT_P50, SELECT_P95],
    },
    "latency_by_os": {
        "field": "OsVersion",
        "alias": "os_version",
        "select": [SELECT_P50, SELECT_P95],
    },
}


def build_breakdown_query(
    dimension: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str | None = None,
    end_time: str | None = None,
    user_filters: dict | None = None,
) -> dict:
    """Build QueryRequest for an interaction breakdown by dimension.

    Args:
        dimension: One of device, region, release, platform, os, network,
                   latency_by_network, latency_by_device, latency_by_os.
        interaction_name: The interaction name to query.
        time_range: Time range enum.
        start_time: Custom start (only when time_range="custom").
        end_time: Custom end (only when time_range="custom").
        user_filters: Optional dimension filters.

    Raises:
        ValueError: If dimension is not valid.
    """
    config = DIMENSION_CONFIG.get(dimension)
    if config is None:
        raise ValueError(
            f"Unknown dimension '{dimension}'. "
            f"Valid values: {', '.join(DIMENSION_CONFIG.keys())}"
        )

    col_select = {
        "function": "COL",
        "param": {"field": config["field"]},
        "alias": config["alias"],
    }

    select = [*config["select"], col_select]

    return build_query_request(
        select=select,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        user_filters=user_filters,
        group_by=[config["alias"]],
        limit=10,
        inject_interaction_filters=True,
        interaction_name=interaction_name,
    )


# ---------------------------------------------------------------------------
# Template: Sessions (Tool 7)
# Source: buildQuery.ts (SessionTimeline)
# ---------------------------------------------------------------------------

VALID_SESSION_SCOPES = ("sessions", "stats")


def build_sessions_query(
    scope: str,
    interaction_name: str,
    time_range: str = "last_24h",
    start_time: str | None = None,
    end_time: str | None = None,
    event_type: str | None = None,
    user_filters: dict | None = None,
    limit: int = 10,
) -> dict:
    """Build QueryRequest for interaction session data.

    Args:
        scope: "sessions" for individual session list, "stats" for aggregate statistics.
        interaction_name: The interaction name to query.
        time_range: Time range enum.
        start_time: Custom start (only when time_range="custom").
        end_time: Custom end (only when time_range="custom").
        event_type: Filter by event type: crash, error, completed, or None for all.
        user_filters: Optional dimension filters.
        limit: Max sessions to return (scope="sessions", default 10).

    Raises:
        ValueError: If scope is not valid.
    """
    if scope not in VALID_SESSION_SCOPES:
        raise ValueError(
            f"Unknown scope '{scope}'. "
            f"Valid values: {', '.join(VALID_SESSION_SCOPES)}"
        )

    # Map user-friendly event types to ClickHouse event names.
    # Events.Name is Array(String), so we must use has() not LIKE.
    EVENT_TYPE_MAP = {
        "crash": "device.crash",
        "anr": "device.anr",
        "error": "error",
        "non_fatal": "non_fatal",
        "frozen_frame": "app.jank.frozen",
        "network_error": "network_error",
    }

    base_filters = []
    if event_type:
        ch_event = EVENT_TYPE_MAP.get(event_type, event_type)
        base_filters.append({
            "field": "",
            "operator": "ADDITIONAL",
            "value": [f"has(Events.Name, '{ch_event}')"],
        })

    if scope == "sessions":
        select = [
            {"function": "COL", "param": {"field": "Timestamp"}, "alias": "timestamp"},
            {"function": "COL", "param": {"field": "Duration"}, "alias": "duration"},
            {"function": "COL", "param": {"field": "TraceId"}, "alias": "trace_id"},
            {"function": "COL", "param": {"field": "SpanId"}, "alias": "span_id"},
            {"function": "COL", "param": {"field": "StatusCode"}, "alias": "status_code"},
            {"function": "COL", "param": {"field": "Platform"}, "alias": "platform"},
            {"function": "COL", "param": {"field": "DeviceModel"}, "alias": "device"},
            {"function": "COL", "param": {"field": "OsVersion"}, "alias": "os_version"},
            {"function": "COL", "param": {"field": "AppVersion"}, "alias": "app_version"},
        ]

        return build_query_request(
            select=select,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            user_filters=user_filters,
            base_filters=base_filters if base_filters else None,
            order_by=[{"field": "timestamp", "direction": "DESC"}],
            limit=limit,
            inject_interaction_filters=True,
            interaction_name=interaction_name,
        )

    else:  # scope == "stats"
        select = [
            {"function": "CUSTOM", "param": {"expression": "COUNT()"}, "alias": "total_sessions"},
            SELECT_SUCCESS_COUNT,
            SELECT_ERROR_COUNT,
            SELECT_CRASH,
            SELECT_ANR,
            SELECT_APDEX,
            SELECT_P50,
        ]

        return build_query_request(
            select=select,
            time_range=time_range,
            start_time=start_time,
            end_time=end_time,
            user_filters=user_filters,
            base_filters=base_filters if base_filters else None,
            inject_interaction_filters=True,
            interaction_name=interaction_name,
        )


# ---------------------------------------------------------------------------
# Template: Problematic interaction spans (Interaction Details / Session samples)
# Source: useGetProblematicInteractions.ts
# ---------------------------------------------------------------------------

VALID_PROBLEMATIC_SPAN_KINDS = (
    "error",
    "poor",
    "crash",
    "anr",
    "frozen_frame",
    "non_fatal",
)

_SPAN_KIND_EVENT_MAP = {
    "crash": "device.crash",
    "anr": "device.anr",
    "frozen_frame": "app.jank.frozen",
    "non_fatal": "non_fatal",
}

_CH_USER_CATEGORY_POOR = (
    "ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor'"
)

_PROBLEMATIC_SPAN_SELECT = [
    {
        "function": "CUSTOM",
        "param": {
            "expression": "arrayStringConcat(arrayMap(x -> toString(x), Events.Name), ',')",
        },
        "alias": "event_names",
    },
    {
        "function": "CUSTOM",
        "param": {
            "expression": "arrayStringConcat(arrayMap(x -> toString(x), Events.Timestamp), ',')",
        },
        "alias": "event_timestamps",
    },
    {"function": "COL", "param": {"field": "Timestamp"}, "alias": "interaction_timestamp"},
    {"function": "COL", "param": {"field": "SpanId"}, "alias": "spanid"},
    {"function": "COL", "param": {"field": "TraceId"}, "alias": "traceid"},
    {"function": "COL", "param": {"field": "SessionId"}, "alias": "sessionid"},
    {"function": "COL", "param": {"field": "DeviceModel"}, "alias": "device"},
    {"function": "COL", "param": {"field": "Duration"}, "alias": "duration"},
    {"function": "COL", "param": {"field": "AppInstallationId"}, "alias": "userid"},
    {
        "function": "COL",
        "param": {"field": "ResourceAttributes['device.manufacturer']"},
        "alias": "manufacturer",
    },
    {"function": "COL", "param": {"field": "Platform"}, "alias": "os_name"},
    {
        "function": "COL",
        "param": {"field": "ResourceAttributes['os.type']"},
        "alias": "os_type",
    },
    {"function": "COL", "param": {"field": "OsVersion"}, "alias": "os_version"},
    {
        "function": "COL",
        "param": {"field": "ResourceAttributes['os.description']"},
        "alias": "os_description",
    },
    {"function": "COL", "param": {"field": "GeoState"}, "alias": "state"},
    {"function": "COL", "param": {"field": "GeoCountry"}, "alias": "country"},
    {
        "function": "CUSTOM",
        "param": {
            "expression": "toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])",
        },
        "alias": "frozen_frame",
    },
    {"function": "COL", "param": {"field": "SpanAttributes['isError']"}, "alias": "is_error"},
    {"function": "COL", "param": {"field": "StatusCode"}, "alias": "status_code"},
    {
        "function": "COL",
        "param": {"field": "SpanAttributes['pulse.interaction.user_category']"},
        "alias": "user_category",
    },
]


def _span_kind_filter(span_kind: str) -> dict:
    if span_kind == "error":
        expr = "(StatusCode = 'Error')"
    elif span_kind == "poor":
        expr = _CH_USER_CATEGORY_POOR
    else:
        event = _SPAN_KIND_EVENT_MAP.get(span_kind)
        if not event:
            raise ValueError(
                f"Unknown span_kind '{span_kind}'. "
                f"Valid values: {', '.join(VALID_PROBLEMATIC_SPAN_KINDS)}"
            )
        expr = f"has(Events.Name, '{event}')"
    return {"field": "", "operator": "ADDITIONAL", "value": [expr]}


def build_problematic_spans_query(
    interaction_name: str,
    span_kind: str = "error",
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    user_filters: dict | None = None,
    limit: int = 5,
) -> dict:
    """Build QueryRequest for individual problematic interaction spans (UI-aligned).

    Args:
        interaction_name: Interaction span name.
        span_kind: error, poor, crash, anr, frozen_frame, or non_fatal.
        time_range: Time range enum.
        start_time: Custom start when time_range=custom.
        end_time: Custom end when time_range=custom.
        user_filters: Optional dimension filters.
        limit: Max spans (capped at 10 by callers).
    """
    if span_kind not in VALID_PROBLEMATIC_SPAN_KINDS:
        raise ValueError(
            f"Unknown span_kind '{span_kind}'. "
            f"Valid values: {', '.join(VALID_PROBLEMATIC_SPAN_KINDS)}"
        )

    return build_query_request(
        select=list(_PROBLEMATIC_SPAN_SELECT),
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        user_filters=user_filters,
        base_filters=[_span_kind_filter(span_kind)],
        order_by=[{"field": "interaction_timestamp", "direction": "DESC"}],
        limit=limit,
        inject_interaction_filters=True,
        interaction_name=interaction_name,
    )


# ---------------------------------------------------------------------------
# Template: Session trace snapshot (SessionTimeline buildQuery)
# ---------------------------------------------------------------------------

VALID_SESSION_TRACE_DATA_TYPES = ("traces", "logs")


def build_session_trace_snapshot_query(
    session_id: str,
    data_type: str = "logs",
    trace_id: str | None = None,
    time_range: str = "last_7d",
    start_time: str | None = None,
    end_time: str | None = None,
    limit: int = 100,
) -> dict:
    """Build QueryRequest for a compact session or trace timeline sample.

    Args:
        session_id: Pulse session id.
        data_type: traces or logs.
        trace_id: Optional trace id to narrow results.
        time_range: Time range enum.
        start_time: Custom start when time_range=custom.
        end_time: Custom end when time_range=custom.
        limit: Max rows (callers cap at 200).
    """
    if data_type not in VALID_SESSION_TRACE_DATA_TYPES:
        raise ValueError(
            f"Unknown data_type '{data_type}'. "
            f"Valid values: {', '.join(VALID_SESSION_TRACE_DATA_TYPES)}"
        )

    session_id = (session_id or "").strip()
    if not session_id:
        raise ValueError("session_id is required")

    filters: list[dict] = [
        {"field": "SessionId", "operator": "EQ", "value": [session_id]},
    ]
    if trace_id and trace_id.strip():
        filters.append(
            {"field": "TraceId", "operator": "EQ", "value": [trace_id.strip()]},
        )

    if data_type == "logs":
        select = [
            {"function": "COL", "param": {"field": "TraceId"}, "alias": "trace_id"},
            {"function": "COL", "param": {"field": "SpanId"}, "alias": "span_id"},
            {"function": "COL", "param": {"field": "Timestamp"}, "alias": "timestamp"},
            {"function": "COL", "param": {"field": "SeverityText"}, "alias": "severity"},
            {"function": "COL", "param": {"field": "Body"}, "alias": "body"},
            {"function": "COL", "param": {"field": "PulseType"}, "alias": "pulse_type"},
        ]
    else:
        select = [
            {"function": "COL", "param": {"field": "TraceId"}, "alias": "trace_id"},
            {"function": "COL", "param": {"field": "SpanId"}, "alias": "span_id"},
            {"function": "COL", "param": {"field": "ParentSpanId"}, "alias": "parent_span_id"},
            {"function": "COL", "param": {"field": "SpanName"}, "alias": "span_name"},
            {"function": "COL", "param": {"field": "Timestamp"}, "alias": "timestamp"},
            {"function": "COL", "param": {"field": "Duration"}, "alias": "duration"},
            {"function": "COL", "param": {"field": "StatusCode"}, "alias": "status_code"},
            {"function": "COL", "param": {"field": "PulseType"}, "alias": "pulse_type"},
        ]

    return build_query_request(
        select=select,
        time_range=time_range,
        start_time=start_time,
        end_time=end_time,
        base_filters=filters,
        order_by=[{"field": "timestamp", "direction": "ASC"}],
        limit=limit,
        data_type=data_type.upper(),
    )
