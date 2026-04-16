"""Template engine base — time range helpers.

Provides compute_time_range() and TIME_RANGE_DOC used by all analytics tools
to translate LLM-friendly time range strings to ISO 8601 pairs.
"""

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
# Comma-separated list for tool docstrings; single source of truth so new ranges only added here.
TIME_RANGE_DOC = ", ".join(sorted(ALL_VALID_RANGES))


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
