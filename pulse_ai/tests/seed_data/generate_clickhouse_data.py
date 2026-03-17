#!/usr/bin/env python3
"""Generate ClickHouse seed data for Pulse AI agent testing.

Produces SQL INSERT statements for `otel.otel_traces` that cover every
scenario exercised by the 6 Phase 1A agent tools.

Usage:
    python generate_clickhouse_data.py > clickhouse_seed.sql
    # Then: clickhouse-client --host 127.0.0.1 --port 9000 < clickhouse_seed.sql
    # Or:   curl 'http://localhost:8123/' --data-binary @clickhouse_seed.sql

Scenario Coverage Matrix:
─────────────────────────────────────────────────────────────────
  Tool / Query                     | Data Required
─────────────────────────────────────────────────────────────────
  query_interaction_health(top_n)  | Multiple interactions, varying frequency
  query_interaction_health(names)  | Specific interaction data
  query_interaction_metrics(apdex) | apdex_score SpanAttribute
  query_interaction_metrics(latency)| Varying Duration values
  query_interaction_metrics(error_rate) | Mix of StatusCode OK/Error
  query_interaction_metrics(user_categories) | user_category SpanAttribute
  query_interaction_metrics(composite) | All of the above + frames
  query_interaction_metrics(timeseries) | Data spread across time buckets
  query_interaction_sessions(list) | Individual traces with device metadata
  query_interaction_sessions(stats)| Aggregate-friendly volume
  query_interaction_sessions(crash)| Events.Name containing 'device.crash'
  query_interaction_sessions(error)| Events.Name containing 'error'
  breakdown(device)                | Multiple DeviceModel values
  breakdown(region)                | Multiple GeoState values
  breakdown(release)               | Multiple AppVersion values
  breakdown(platform)              | Android + iOS
  breakdown(os)                    | Multiple OsVersion values
  breakdown(network)               | Multiple NetworkProvider values
  Filters (platform, version)      | Filterable dimension values
  Empty results                    | FeedRefresh has no recent data
─────────────────────────────────────────────────────────────────
"""

import random
import uuid
from datetime import datetime, timedelta, timezone

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

TENANT_PROJECT_ID = "default"

# Interaction profiles: (name, count, success_rate, apdex_range, duration_range_ms, crash_pct, anr_pct)
INTERACTIONS = [
    {
        "name": "ProfileLoad",
        "count": 200,
        "success_rate": 0.98,
        "apdex_range": (0.90, 1.0),      # Excellent
        "duration_range_ms": (200, 1500),  # Fast
        "crash_pct": 0.0,
        "anr_pct": 0.0,
        "user_cat_dist": {"Excellent": 0.60, "Good": 0.25, "Average": 0.10, "Poor": 0.05},
    },
    {
        "name": "ContestJoin",
        "count": 150,
        "success_rate": 0.85,
        "apdex_range": (0.70, 0.95),      # Healthy
        "duration_range_ms": (500, 6000),
        "crash_pct": 0.013,  # ~2 crashes
        "anr_pct": 0.007,   # ~1 ANR
        "user_cat_dist": {"Excellent": 0.40, "Good": 0.30, "Average": 0.20, "Poor": 0.10},
    },
    {
        "name": "UserLogin",
        "count": 120,
        "success_rate": 0.75,
        "apdex_range": (0.50, 0.80),      # Degraded
        "duration_range_ms": (800, 10000),
        "crash_pct": 0.042,  # ~5 crashes
        "anr_pct": 0.025,   # ~3 ANRs
        "user_cat_dist": {"Excellent": 0.20, "Good": 0.25, "Average": 0.30, "Poor": 0.25},
    },
    {
        "name": "PaymentCheckout",
        "count": 80,
        "success_rate": 0.50,
        "apdex_range": (0.20, 0.50),      # Problematic
        "duration_range_ms": (2000, 18000),
        "crash_pct": 0.125,  # ~10 crashes
        "anr_pct": 0.063,   # ~5 ANRs
        "user_cat_dist": {"Excellent": 0.05, "Good": 0.15, "Average": 0.30, "Poor": 0.50},
    },
    {
        "name": "FeedRefresh",
        "count": 30,
        "success_rate": 0.90,
        "apdex_range": (0.75, 0.95),
        "duration_range_ms": (300, 3000),
        "crash_pct": 0.0,
        "anr_pct": 0.0,
        "user_cat_dist": {"Excellent": 0.50, "Good": 0.30, "Average": 0.15, "Poor": 0.05},
        "old_data": True,  # All data > 7 days old (tests empty results)
    },
]

# Dimension pools for realistic breakdowns
PLATFORMS = [
    {"os": "Android", "os_versions": ["12", "13", "14", "15"], "weight": 0.60},
    {"os": "iOS", "os_versions": ["16.5", "17.0", "17.2", "18.0"], "weight": 0.40},
]

DEVICES = {
    "Android": ["Pixel 7", "Pixel 8 Pro", "Samsung Galaxy S23", "Samsung Galaxy A54", "OnePlus 12", "Xiaomi 14"],
    "iOS": ["iPhone 14", "iPhone 15", "iPhone 15 Pro", "iPhone 16", "iPad Air"],
}

REGIONS = [
    ("US-CA", "US"), ("US-NY", "US"), ("US-TX", "US"),
    ("IN-KA", "IN"), ("IN-MH", "IN"), ("IN-DL", "IN"),
    ("GB-LND", "GB"), ("DE-BE", "DE"),
]

NETWORKS = ["Jio", "Airtel", "Verizon", "T-Mobile", "Vodafone", "WiFi", "AT&T"]

APP_VERSIONS = ["5.28.0", "5.29.1", "5.30.0", "5.30.1"]

USERS = [f"user_{i:04d}" for i in range(1, 51)]  # 50 unique users
SESSIONS = [f"sess_{uuid.uuid4().hex[:12]}" for _ in range(100)]  # 100 unique sessions

# Time range: last 7 days, spread across hours
NOW = datetime.now(timezone.utc).replace(microsecond=0)
SEVEN_DAYS_AGO = NOW - timedelta(days=7)
FOURTEEN_DAYS_AGO = NOW - timedelta(days=14)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def random_timestamp(start: datetime, end: datetime) -> datetime:
    """Random timestamp between start and end."""
    delta = end - start
    random_seconds = random.uniform(0, delta.total_seconds())
    return start + timedelta(seconds=random_seconds)


def format_ts(dt: datetime) -> str:
    """Format datetime to ClickHouse DateTime64(9) string."""
    return dt.strftime("%Y-%m-%d %H:%M:%S.") + f"{dt.microsecond:06d}000"


def escape_str(s: str) -> str:
    """Escape single quotes for ClickHouse SQL."""
    return s.replace("'", "\\'")


def choose_weighted(items: list[dict], key: str = "weight") -> dict:
    """Choose an item from list based on weights."""
    weights = [item[key] for item in items]
    return random.choices(items, weights=weights, k=1)[0]


def choose_user_category(dist: dict[str, float]) -> str:
    """Choose a user category based on distribution."""
    categories = list(dist.keys())
    weights = list(dist.values())
    return random.choices(categories, weights=weights, k=1)[0]


def generate_trace_id() -> str:
    """Generate a 32-char hex trace ID."""
    return uuid.uuid4().hex


def generate_span_id() -> str:
    """Generate a 16-char hex span ID (padded to FixedString(16))."""
    return uuid.uuid4().hex[:16]


def build_map_literal(d: dict[str, str]) -> str:
    """Build a ClickHouse Map literal from a Python dict.

    Example: {'key1': 'val1', 'key2': 'val2'} -> {'key1':'val1','key2':'val2'}
    """
    if not d:
        return "map()"
    pairs = ", ".join(f"'{escape_str(k)}', '{escape_str(v)}'" for k, v in d.items())
    return f"map({pairs})"


def build_array_literal(items: list[str]) -> str:
    """Build a ClickHouse Array(String) literal.

    Example: ['a', 'b'] -> ['a','b']
    """
    if not items:
        return "[]"
    return "[" + ", ".join(f"'{escape_str(i)}'" for i in items) + "]"


# ---------------------------------------------------------------------------
# Row generator
# ---------------------------------------------------------------------------

def generate_row(interaction: dict, row_index: int) -> str:
    """Generate a single INSERT VALUES tuple for otel_traces."""

    name = interaction["name"]
    is_old = interaction.get("old_data", False)

    # Timestamp
    if is_old:
        ts = random_timestamp(FOURTEEN_DAYS_AGO, FOURTEEN_DAYS_AGO + timedelta(days=5))
    else:
        ts = random_timestamp(SEVEN_DAYS_AGO, NOW)

    # IDs
    trace_id = generate_trace_id()
    span_id = generate_span_id()
    parent_span_id = "0" * 16

    # Platform & device
    platform_info = choose_weighted(PLATFORMS)
    os_name = platform_info["os"]
    os_version = random.choice(platform_info["os_versions"])
    device_model = random.choice(DEVICES[os_name])

    # Region
    region, country = random.choice(REGIONS)

    # Network
    network = random.choice(NETWORKS)

    # App version
    app_version = random.choice(APP_VERSIONS)

    # User and session
    user_id = random.choice(USERS)
    session_id = random.choice(SESSIONS)

    # Success/error
    is_success = random.random() < interaction["success_rate"]
    status_code = "Ok" if is_success else "Error"

    # Duration (nanoseconds)
    min_ms, max_ms = interaction["duration_range_ms"]
    if is_success:
        # Success durations: mostly in lower range
        duration_ms = random.triangular(min_ms, max_ms, min_ms + (max_ms - min_ms) * 0.3)
    else:
        # Error durations: shifted higher
        duration_ms = random.triangular(min_ms, max_ms, max_ms * 0.7)
    duration_ns = int(duration_ms * 1_000_000)

    # Apdex score (pre-computed by SDK, stored as SpanAttribute)
    apdex_low, apdex_high = interaction["apdex_range"]
    if is_success:
        apdex_score = round(random.uniform(apdex_low, apdex_high), 4)
    else:
        apdex_score = round(random.uniform(0.0, apdex_low), 4)

    # User category
    user_category = choose_user_category(interaction["user_cat_dist"])

    # Frame counts (interaction-related)
    frozen_frames = random.randint(0, 5) if random.random() < 0.3 else 0
    analysed_frames = random.randint(50, 200)
    unanalysed_frames = random.randint(0, 20)

    # Events (crashes, ANRs, network events)
    events_names: list[str] = []
    events_timestamps: list[str] = []
    events_attributes: list[str] = []

    # Crash event
    if random.random() < interaction["crash_pct"]:
        events_names.append("device.crash")
        events_timestamps.append(format_ts(ts + timedelta(milliseconds=duration_ms * 0.8)))
        events_attributes.append("map()")

    # ANR event
    if random.random() < interaction["anr_pct"]:
        events_names.append("device.anr")
        events_timestamps.append(format_ts(ts + timedelta(milliseconds=duration_ms * 0.9)))
        events_attributes.append("map()")

    # Occasional network events (for composite metrics)
    if random.random() < 0.2:
        net_code = random.choice(["network.200", "network.200", "network.200", "network.404", "network.500"])
        events_names.append(net_code)
        events_timestamps.append(format_ts(ts + timedelta(milliseconds=duration_ms * 0.5)))
        events_attributes.append("map()")

    # Occasional error event (for session filtering)
    if not is_success and random.random() < 0.5:
        events_names.append("error")
        events_timestamps.append(format_ts(ts + timedelta(milliseconds=duration_ms * 0.6)))
        events_attributes.append("map()")

    # Build SpanAttributes map
    span_attrs = {
        "pulse.type": "interaction",
        "pulse.interaction.apdex_score": str(apdex_score),
        "pulse.interaction.user_category": user_category,
        "session.id": session_id,
        "user.id": user_id,
        "geo.region.iso_code": region,
        "geo.country.iso_code": country,
        "network.carrier.name": network,
        "app.interaction.frozen_frame_count": str(frozen_frames),
        "app.interaction.analysed_frame_count": str(analysed_frames),
        "app.interaction.unanalysed_frame_count": str(unanalysed_frames),
    }

    # Build ResourceAttributes map
    resource_attrs = {
        "project.id": TENANT_PROJECT_ID,
        "os.name": os_name,
        "os.version": os_version,
        "device.model.name": device_model,
        "app.build_name": app_version,
        "rum.sdk.version": "1.4.2",
        "service.name": "pulse-mobile-app",
    }

    # Build Events arrays
    events_ts_array = "[]" if not events_timestamps else \
        "[" + ", ".join(f"'{t}'" for t in events_timestamps) + "]"
    events_name_array = build_array_literal(events_names)
    events_attr_array = "[]" if not events_attributes else \
        "[" + ", ".join(events_attributes) + "]"

    # Assemble the VALUES tuple
    return (
        f"("
        f"'{format_ts(ts)}', "          # Timestamp
        f"'{trace_id}', "               # TraceId
        f"'{span_id}', "                # SpanId
        f"'{'0' * 16}', "              # ParentSpanId
        f"'', "                         # TraceState
        f"'{escape_str(name)}', "       # SpanName
        f"'SPAN_KIND_CLIENT', "         # SpanKind
        f"'pulse-mobile-app', "         # ServiceName
        f"{build_map_literal(resource_attrs)}, "  # ResourceAttributes
        f"'io.opentelemetry.sdk', "     # ScopeName
        f"'1.4.2', "                    # ScopeVersion
        f"{build_map_literal(span_attrs)}, "      # SpanAttributes
        f"{duration_ns}, "              # Duration
        f"'{status_code}', "            # StatusCode
        f"'', "                         # StatusMessage
        f"{events_ts_array}, "          # Events.Timestamp
        f"{events_name_array}, "        # Events.Name
        f"{events_attr_array}, "        # Events.Attributes
        f"[], [], [], []"               # Links.* (empty)
        f")"
    )


# ---------------------------------------------------------------------------
# Main — generate the SQL
# ---------------------------------------------------------------------------

def main():
    random.seed(42)  # Reproducible data

    print("-- ============================================================================")
    print("-- Pulse AI Agent — ClickHouse Seed Data (auto-generated)")
    print("-- ============================================================================")
    print(f"-- Generated: {NOW.isoformat()}")
    print(f"-- Total interactions: {len(INTERACTIONS)}")
    total_rows = sum(i['count'] for i in INTERACTIONS)
    print(f"-- Total trace rows: {total_rows}")
    print("-- ")
    print("-- MATERIALIZED columns (auto-computed from maps):")
    print("--   PulseType    <- SpanAttributes['pulse.type']")
    print("--   Platform     <- ResourceAttributes['os.name']")
    print("--   DeviceModel  <- ResourceAttributes['device.model.name']")
    print("--   AppVersion   <- ResourceAttributes['app.build_name']")
    print("--   GeoState     <- SpanAttributes['geo.region.iso_code']")
    print("--   SessionId    <- SpanAttributes['session.id']")
    print("--   UserId       <- SpanAttributes['user.id']")
    print("-- ============================================================================")
    print()

    for interaction in INTERACTIONS:
        name = interaction["name"]
        count = interaction["count"]
        old_tag = " (OLD DATA — tests empty results)" if interaction.get("old_data") else ""
        print(f"-- --- {name}: {count} rows{old_tag} ---")
        print()

        # Generate in batches of 50 to keep SQL statements manageable
        batch_size = 50
        rows = []
        for i in range(count):
            rows.append(generate_row(interaction, i))

            if len(rows) >= batch_size or i == count - 1:
                print("INSERT INTO otel.otel_traces (")
                print("    Timestamp, TraceId, SpanId, ParentSpanId, TraceState,")
                print("    SpanName, SpanKind, ServiceName, ResourceAttributes,")
                print("    ScopeName, ScopeVersion, SpanAttributes, Duration,")
                print("    StatusCode, StatusMessage,")
                print("    `Events.Timestamp`, `Events.Name`, `Events.Attributes`,")
                print("    `Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`")
                print(") VALUES")
                print(",\n".join(rows) + ";")
                print()
                rows = []

    # Print summary
    print("-- ============================================================================")
    print("-- Verification queries (run after seeding):")
    print("-- ============================================================================")
    print("-- SELECT SpanName, COUNT() as cnt, ")
    print("--        countIf(StatusCode = 'Error') as errors,")
    print("--        countIf(has(Events.Name, 'device.crash')) as crashes")
    print("-- FROM otel.otel_traces")
    print("-- WHERE PulseType = 'interaction'")
    print("-- GROUP BY SpanName ORDER BY cnt DESC;")
    print("-- ============================================================================")


if __name__ == "__main__":
    main()
