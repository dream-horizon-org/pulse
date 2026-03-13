"""Template engine base — time range, filters, and QueryRequest builder.

These functions are internal helpers, NOT ADK tools. They translate
simple LLM parameters into the complex QueryRequest JSON the backend expects.
"""

from calendar import monthrange
from datetime import datetime, timedelta, timezone


# ---------------------------------------------------------------------------
# Time range enum → ISO 8601
# ---------------------------------------------------------------------------

# Simple "now minus delta" ranges
TIME_RANGE_MAP: dict[str, timedelta] = {
    "last_5m": timedelta(minutes=5),
    "last_15m": timedelta(minutes=15),
    "last_30m": timedelta(minutes=30),
    "last_1h": timedelta(hours=1),
    "last_3h": timedelta(hours=3),
    "last_6h": timedelta(hours=6),
    "last_12h": timedelta(hours=12),
    "last_24h": timedelta(hours=24),
    "last_2d": timedelta(days=2),
    "last_7d": timedelta(days=7),
    "last_30d": timedelta(days=30),
    "last_90d": timedelta(days=90),
}

# Calendar-relative ranges that need special boundary logic
CALENDAR_RANGES = {
    "yesterday", "previous_week", "previous_month",
    "today_so_far", "this_week", "this_month_so_far",
}

ALL_VALID_RANGES = set(TIME_RANGE_MAP.keys()) | CALENDAR_RANGES | {"custom"}


def _to_iso_utc(dt: datetime) -> str:
    """Format datetime as ISO 8601 with Z suffix (never +00:00)."""
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def _compute_calendar_range(time_range: str, now: datetime) -> tuple[str, str]:
    """Compute start/end for calendar-relative ranges.

    These use calendar boundaries (start of day, start of week, etc.)
    rather than simple deltas. Matches the frontend DateUtil.tsx behavior.
    """
    if time_range == "yesterday":
        yesterday = now - timedelta(days=1)
        start = yesterday.replace(hour=0, minute=0, second=0, microsecond=0)
        end = yesterday.replace(hour=23, minute=59, second=59, microsecond=0)
        return _to_iso_utc(start), _to_iso_utc(end)

    if time_range == "today_so_far":
        start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        return _to_iso_utc(start), _to_iso_utc(now)

    if time_range == "previous_week":
        # Monday = 0 in weekday(). Go back to start of this week, then one more week.
        days_since_monday = now.weekday()
        this_monday = now - timedelta(days=days_since_monday)
        prev_monday = this_monday - timedelta(weeks=1)
        start = prev_monday.replace(hour=0, minute=0, second=0, microsecond=0)
        prev_sunday = this_monday - timedelta(days=1)
        end = prev_sunday.replace(hour=23, minute=59, second=59, microsecond=0)
        return _to_iso_utc(start), _to_iso_utc(end)

    if time_range == "this_week":
        days_since_monday = now.weekday()
        this_monday = now - timedelta(days=days_since_monday)
        start = this_monday.replace(hour=0, minute=0, second=0, microsecond=0)
        return _to_iso_utc(start), _to_iso_utc(now)

    if time_range == "previous_month":
        # Go to first of current month, then back one day → last month
        first_of_this_month = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        last_month_end = first_of_this_month - timedelta(days=1)
        last_month_start = last_month_end.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        last_month_end = last_month_end.replace(hour=23, minute=59, second=59, microsecond=0)
        return _to_iso_utc(last_month_start), _to_iso_utc(last_month_end)

    if time_range == "this_month_so_far":
        start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        return _to_iso_utc(start), _to_iso_utc(now)

    raise ValueError(f"Unhandled calendar range: {time_range}")


def compute_time_range(
    time_range: str,
    start_time: str | None = None,
    end_time: str | None = None,
) -> tuple[str, str]:
    """Convert a time_range enum to (start, end) ISO 8601 UTC strings.

    Supports three kinds of time ranges:
    - Simple deltas: last_5m, last_15m, last_30m, last_1h, last_3h, last_6h,
      last_12h, last_24h, last_2d, last_7d, last_30d, last_90d
    - Calendar-relative: yesterday, previous_week, previous_month,
      today_so_far, this_week, this_month_so_far
    - Custom: pass-through of start_time/end_time

    Args:
        time_range: One of the supported range strings, or "custom".
        start_time: Required when time_range="custom".
        end_time: Required when time_range="custom".

    Returns:
        Tuple of (start, end) ISO 8601 strings with Z suffix.

    Raises:
        ValueError: If time_range is unknown or custom without start/end.
    """
    if time_range == "custom":
        if not start_time or not end_time:
            raise ValueError(
                "start_time and end_time are required when time_range='custom'"
            )
        return start_time, end_time

    now = datetime.now(timezone.utc)

    # Calendar-relative ranges (use boundaries, not simple deltas)
    if time_range in CALENDAR_RANGES:
        return _compute_calendar_range(time_range, now)

    # Simple delta ranges
    delta = TIME_RANGE_MAP.get(time_range)
    if delta is None:
        raise ValueError(
            f"Unknown time_range '{time_range}'. "
            f"Valid values: {', '.join(sorted(ALL_VALID_RANGES))}"
        )

    start = now - delta
    return _to_iso_utc(start), _to_iso_utc(now)


# ---------------------------------------------------------------------------
# Filters — LLM key-value → backend format
# ---------------------------------------------------------------------------

FILTER_FIELD_MAP: dict[str, str] = {
    "platform": "Platform",
    "app_version": "AppVersion",
    "device": "DeviceModel",
    "os_version": "OsVersion",
    "network": "NetworkProvider",
    "region": "GeoState",
}


def build_filters(user_filters: dict | None) -> list[dict]:
    """Translate simple key-value filters to backend filter format.

    Args:
        user_filters: Dict like {"platform": "Android", "app_version": "5.29.1"}.
                      Values can be strings or lists of strings.

    Returns:
        List of backend filter objects: [{"field": "...", "operator": "EQ", "value": [...]}]

    Raises:
        ValueError: If an unknown filter key is used.
    """
    if not user_filters:
        return []

    backend_filters = []
    for key, value in user_filters.items():
        backend_field = FILTER_FIELD_MAP.get(key)
        if backend_field is None:
            raise ValueError(
                f"Unknown filter key '{key}'. "
                f"Valid keys: {', '.join(FILTER_FIELD_MAP.keys())}"
            )
        backend_filters.append({
            "field": backend_field,
            "operator": "EQ",
            "value": value if isinstance(value, list) else [value],
        })
    return backend_filters


# ---------------------------------------------------------------------------
# QueryRequest builder
# ---------------------------------------------------------------------------

def build_query_request(
    select: list[dict],
    time_range: str = "last_24h",
    start_time: str | None = None,
    end_time: str | None = None,
    user_filters: dict | None = None,
    base_filters: list[dict] | None = None,
    group_by: list[str] | None = None,
    order_by: list[dict] | None = None,
    limit: int | None = None,
    data_type: str = "TRACES",
    inject_interaction_filters: bool = False,
    interaction_name: str | None = None,
) -> dict:
    """Build a complete QueryRequest JSON body for the backend.

    Args:
        select: List of select items [{"function": "APDEX", "alias": "apdex"}].
        time_range: Time range enum.
        start_time: Custom start (only when time_range="custom").
        end_time: Custom end (only when time_range="custom").
        user_filters: LLM-provided key-value filters.
        base_filters: Pre-built filter objects to include.
        group_by: List of groupBy field names.
        order_by: List of orderBy objects [{"field": "...", "direction": "DESC"}].
        limit: Query limit.
        data_type: ClickHouse data type (default TRACES).
        inject_interaction_filters: If True, auto-inject PulseType=interaction.
        interaction_name: If provided with inject_interaction_filters, adds SpanName filter.

    Returns:
        Complete QueryRequest dict ready for JSON serialization.
    """
    computed_start, computed_end = compute_time_range(time_range, start_time, end_time)

    # Build filters list
    filters = []

    # Auto-inject interaction base filters (PulseType + SpanName)
    if inject_interaction_filters:
        filters.append({
            "field": "PulseType",
            "operator": "EQ",
            "value": ["interaction"],
        })
        if interaction_name:
            filters.append({
                "field": "SpanName",
                "operator": "EQ",
                "value": [interaction_name],
            })

    # Add any pre-built base filters
    if base_filters:
        filters.extend(base_filters)

    # Add user-provided filters (translated)
    filters.extend(build_filters(user_filters))

    # Assemble the request
    request: dict = {
        "dataType": data_type,
        "timeRange": {
            "start": computed_start,
            "end": computed_end,
        },
        "select": select,
        "filters": filters,
    }

    if group_by:
        request["groupBy"] = group_by
    if order_by:
        request["orderBy"] = order_by
    if limit is not None:
        request["limit"] = limit

    return request
