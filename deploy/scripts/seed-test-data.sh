#!/bin/bash
# ============================================================================
# Seed Test Data for Root Cause Analysis
# ============================================================================
# This script inserts:
# 1. A test interaction ("app_launch") in MySQL
# 2. ~5000 realistic telemetry spans in ClickHouse with intentional "bad segments"
#
# Bad segments planted (for the AI to discover):
#   - Android 10 + Jio network → very high error rate & poor APDEX
#   - Android 10 + Jio + Andhra Pradesh → worst segment (crashes + ANR)
#   - Samsung SM-A135F (budget device) → slow durations
#   - App version 3.1.0 → elevated crash rate
#
# Usage: ./deploy/scripts/seed-test-data.sh
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"

# Load .env if present
if [ -f "$DEPLOY_DIR/.env" ]; then
  set -a
  source "$DEPLOY_DIR/.env"
  set +a
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-my-secret-pw}"
MYSQL_DB="${MYSQL_DB:-pulse_db}"

CH_HOST="${CH_HOST:-127.0.0.1}"
CH_PORT="${CH_PORT:-8123}"
CH_USER="${OTEL_CLICKHOUSE_USER:-pulse_user}"
CH_PASSWORD="${OTEL_CLICKHOUSE_PASSWORD:-pulse_password}"
CH_DB="${OTEL_CLICKHOUSE_DATABASE:-otel}"

echo "========================================="
echo "  Seeding Test Data for Root Cause Analysis"
echo "========================================="

# ── Step 1: Insert test interaction in MySQL ────────────────────────────────
echo ""
echo "[1/2] Inserting test interaction 'app_launch' in MySQL..."

mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" <<'MYSQL_EOF'
INSERT INTO interaction (tenant_id, name, status, details, created_by, updated_by)
VALUES (
  'default',
  'app_launch',
  'RUNNING',
  '{
    "description": "App launch interaction - measures cold start time from app open to home screen render",
    "uptimeLowerLimitInMs": 1000,
    "uptimeMidLimitInMs": 2000,
    "uptimeUpperLimitInMs": 3000,
    "thresholdInMs": 5000,
    "events": [
      {"name": "app_start", "screenName": "SplashScreen"},
      {"name": "home_rendered", "screenName": "HomeScreen"}
    ],
    "globalBlacklistedEvents": []
  }',
  'seed-script',
  'seed-script'
)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  details = VALUES(details),
  updated_by = VALUES(updated_by);
MYSQL_EOF

echo "  ✓ Interaction 'app_launch' created"

# ── Step 2: Insert telemetry data in ClickHouse ────────────────────────────
echo ""
echo "[2/2] Inserting ~5000 telemetry spans in ClickHouse..."
echo "  This includes interaction traces, crash events, and ANR events"
echo "  with intentional bad segments for the AI to discover..."

# We use a Python script for complex data generation
python3 - "$CH_HOST" "$CH_PORT" "$CH_USER" "$CH_PASSWORD" "$CH_DB" <<'PYTHON_EOF'
import sys
import random
import uuid
import json
from datetime import datetime, timedelta, timezone
import urllib.request
import urllib.error

ch_host = sys.argv[1]
ch_port = sys.argv[2]
ch_user = sys.argv[3]
ch_password = sys.argv[4]
ch_db = sys.argv[5]

random.seed(42)

def ch_query(query, data=None):
    url = f"http://{ch_host}:{ch_port}/?database={ch_db}&user={ch_user}&password={ch_password}"
    req = urllib.request.Request(url, data=query.encode('utf-8') if data is None else data.encode('utf-8'))
    if data is not None:
        req = urllib.request.Request(url, data=data.encode('utf-8'))
    else:
        req = urllib.request.Request(url, data=query.encode('utf-8'))
    try:
        resp = urllib.request.urlopen(req)
        return resp.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        print(f"  ClickHouse error: {e.read().decode('utf-8')}")
        sys.exit(1)

# ── Configuration ──────────────────────────────────────────────────────────
NUM_SPANS = 5000
now = datetime.now(timezone.utc)
start_time = now - timedelta(hours=24)

platforms = ["android", "ios"]
android_versions = ["10", "11", "12", "13", "14"]
ios_versions = ["16.0", "17.0", "17.1", "17.2"]
app_versions = ["3.0.0", "3.1.0", "3.2.0", "3.2.1"]
android_devices = ["SM-A135F", "SM-S911B", "Pixel 7", "Pixel 8", "OnePlus 11", "Redmi Note 12"]
ios_devices = ["iPhone14,5", "iPhone15,2", "iPhone15,3", "iPhone16,1"]
networks = ["Jio", "Airtel", "Vi", "BSNL", "wifi"]
states = ["IN-AP", "IN-MH", "IN-KA", "IN-TN", "IN-DL", "IN-UP", "IN-RJ", "IN-GJ", "IN-WB", "IN-KL"]

def weighted_choice(choices, weights):
    return random.choices(choices, weights=weights, k=1)[0]

# ── Generate spans ─────────────────────────────────────────────────────────
rows = []
for i in range(NUM_SPANS):
    ts = start_time + timedelta(seconds=random.randint(0, 86400))
    ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")

    trace_id = uuid.uuid4().hex
    span_id = uuid.uuid4().hex[:16]
    session_id = f"session_{random.randint(1, 500)}"
    user_id = f"user_{random.randint(1, 200)}"

    # Platform distribution: 70% android, 30% ios
    platform = weighted_choice(platforms, [70, 30])

    if platform == "android":
        os_version = weighted_choice(android_versions, [25, 20, 25, 20, 10])
        device = weighted_choice(android_devices, [30, 15, 15, 10, 15, 15])
    else:
        os_version = weighted_choice(ios_versions, [15, 30, 35, 20])
        device = weighted_choice(ios_devices, [25, 30, 25, 20])

    network = weighted_choice(networks, [30, 25, 15, 10, 20])
    state = weighted_choice(states, [15, 15, 12, 10, 10, 10, 8, 8, 7, 5])
    app_version = weighted_choice(app_versions, [20, 25, 35, 20])

    # ── Determine quality based on segment ──────────────────────────────
    # Base: good performance
    is_error = random.random() < 0.05
    base_duration_ms = random.gauss(1200, 400)
    has_crash = False
    has_anr = False
    frozen_frames = 0

    # BAD SEGMENT 1: Android 10 + Jio → bad APDEX, high errors
    if platform == "android" and os_version == "10" and network == "Jio":
        is_error = random.random() < 0.35
        base_duration_ms = random.gauss(3500, 800)
        has_crash = random.random() < 0.12
        has_anr = random.random() < 0.08
        frozen_frames = random.randint(2, 8) if random.random() < 0.4 else 0

        # BAD SEGMENT 2 (deeper): Android 10 + Jio + Andhra Pradesh → worst
        if state == "IN-AP":
            is_error = random.random() < 0.50
            base_duration_ms = random.gauss(4500, 1000)
            has_crash = random.random() < 0.20
            has_anr = random.random() < 0.15
            frozen_frames = random.randint(5, 15) if random.random() < 0.6 else 0

    # BAD SEGMENT 3: Samsung SM-A135F → slow durations
    elif device == "SM-A135F":
        base_duration_ms = random.gauss(2800, 600)
        is_error = random.random() < 0.12
        frozen_frames = random.randint(1, 5) if random.random() < 0.3 else 0

    # BAD SEGMENT 4: App version 3.1.0 → elevated crash rate
    elif app_version == "3.1.0":
        has_crash = random.random() < 0.08
        base_duration_ms = random.gauss(1800, 500)

    # Clamp duration
    duration_ms = max(200, base_duration_ms)
    duration_ns = int(duration_ms * 1e6)

    # Calculate APDEX score (mirrors SDK logic)
    # Excellent: < 1000ms, Good: 1000-2000, Average: 2000-3000, Poor: > 3000
    if is_error:
        apdex_score = "0"
        user_category = ""
        status_code = "Error"
    elif duration_ms < 1000:
        apdex_score = "1.0"
        user_category = "Excellent"
        status_code = "Ok"
    elif duration_ms < 2000:
        apdex_score = "0.75"
        user_category = "Good"
        status_code = "Ok"
    elif duration_ms < 3000:
        apdex_score = "0.25"
        user_category = "Average"
        status_code = "Ok"
    else:
        apdex_score = "0.0"
        user_category = "Poor"
        status_code = "Ok"

    # Build events array
    event_names = []
    if has_crash:
        event_names.append("device.crash")
    if has_anr:
        event_names.append("device.anr")

    events_name_str = "[" + ",".join(f"'{e}'" for e in event_names) + "]"
    events_ts_str = "[" + ",".join(f"'{ts_str}'" for _ in event_names) + "]"
    events_attr_str = "[" + ",".join("{}" for _ in event_names) + "]"

    analysed = frozen_frames + random.randint(50, 200) if frozen_frames > 0 else 0
    unanalysed = random.randint(0, 10) if frozen_frames > 0 else 0

    # Build SpanAttributes map
    span_attrs = {
        "pulse.type": "interaction",
        "session.id": session_id,
        "user.id": user_id,
        "geo.region.iso_code": state,
        "geo.country.iso_code": "IN",
        "network.carrier.name": network,
        "pulse.interaction.apdex_score": apdex_score,
        "pulse.interaction.user_category": user_category,
    }
    if frozen_frames > 0:
        span_attrs["app.interaction.frozen_frame_count"] = str(frozen_frames)
        span_attrs["app.interaction.analysed_frame_count"] = str(analysed)
        span_attrs["app.interaction.unanalysed_frame_count"] = str(unanalysed)

    span_attr_str = "{" + ",".join(f"'{k}':'{v}'" for k, v in span_attrs.items()) + "}"

    resource_attrs = {
        "os.name": platform,
        "os.version": os_version,
        "app.build_name": app_version,
        "device.model.name": device,
        "project.id": "default",
        "rum.sdk.version": "1.0.0",
    }
    resource_attr_str = "{" + ",".join(f"'{k}':'{v}'" for k, v in resource_attrs.items()) + "}"

    row = (
        f"('{ts_str}','{trace_id}','{span_id}','','','app_launch','CLIENT','pulse-sdk',"
        f"{resource_attr_str},'pulse','1.0',{span_attr_str},"
        f"{duration_ns},'{status_code}','',{events_ts_str},{events_name_str},{events_attr_str},"
        f"[],[],[],[])"
    )
    rows.append(row)

# Also generate ~200 standalone crash/ANR RUM events
for i in range(200):
    ts = start_time + timedelta(seconds=random.randint(0, 86400))
    ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")
    trace_id = uuid.uuid4().hex
    span_id = uuid.uuid4().hex[:16]
    session_id = f"session_{random.randint(1, 500)}"
    user_id = f"user_{random.randint(1, 200)}"

    platform = weighted_choice(platforms, [70, 30])
    if platform == "android":
        os_version = weighted_choice(android_versions, [35, 15, 20, 20, 10])
        device = weighted_choice(android_devices, [35, 10, 15, 10, 15, 15])
    else:
        os_version = weighted_choice(ios_versions, [15, 30, 35, 20])
        device = weighted_choice(ios_devices, [25, 30, 25, 20])

    # Crashes skewed toward Android 10 + Jio
    if platform == "android" and os_version == "10":
        network = weighted_choice(["Jio", "Airtel", "Vi"], [60, 25, 15])
    else:
        network = weighted_choice(networks, [30, 25, 15, 10, 20])

    state = weighted_choice(states, [20, 12, 12, 10, 10, 10, 8, 8, 5, 5])
    app_version = weighted_choice(app_versions, [15, 35, 30, 20])

    pulse_type = weighted_choice(["device.crash", "device.anr"], [65, 35])

    span_attrs = {
        "pulse.type": pulse_type,
        "session.id": session_id,
        "user.id": user_id,
        "geo.region.iso_code": state,
        "geo.country.iso_code": "IN",
        "network.carrier.name": network,
    }
    span_attr_str = "{" + ",".join(f"'{k}':'{v}'" for k, v in span_attrs.items()) + "}"

    resource_attrs = {
        "os.name": platform,
        "os.version": os_version,
        "app.build_name": app_version,
        "device.model.name": device,
        "project.id": "default",
        "rum.sdk.version": "1.0.0",
    }
    resource_attr_str = "{" + ",".join(f"'{k}':'{v}'" for k, v in resource_attrs.items()) + "}"

    row = (
        f"('{ts_str}','{trace_id}','{span_id}','','','{pulse_type}','CLIENT','pulse-sdk',"
        f"{resource_attr_str},'pulse','1.0',{span_attr_str},"
        f"0,'Ok','',[''],[''],[''],[],'',[])"
    )
    rows.append(row)

# Insert in batches
BATCH_SIZE = 500
total = len(rows)
for batch_start in range(0, total, BATCH_SIZE):
    batch = rows[batch_start:batch_start + BATCH_SIZE]
    insert_sql = (
        "INSERT INTO otel_traces "
        "(Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind, ServiceName, "
        "ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes, "
        "Duration, StatusCode, StatusMessage, `Events.Timestamp`, `Events.Name`, `Events.Attributes`, "
        "`Links.TraceId`, `Links.SpanId`, `Links.TraceState`, `Links.Attributes`) "
        "VALUES " + ",".join(batch)
    )
    ch_query(insert_sql)
    done = min(batch_start + BATCH_SIZE, total)
    print(f"  Inserted {done}/{total} rows...")

print(f"  ✓ Total {total} spans inserted")

# Verify
count = ch_query(f"SELECT count() FROM otel_traces WHERE SpanName = 'app_launch'").strip()
print(f"  ✓ Verification: {count} interaction traces in ClickHouse")
crash_count = ch_query(f"SELECT count() FROM otel_traces WHERE PulseType = 'device.crash'").strip()
print(f"  ✓ Verification: {crash_count} crash events in ClickHouse")
PYTHON_EOF

echo ""
echo "========================================="
echo "  Seed Data Complete!"
echo "========================================="
echo ""
echo "What was planted (for the AI to discover):"
echo "  1. Android 10 + Jio → high error rate (~35%), poor APDEX"
echo "  2. Android 10 + Jio + Andhra Pradesh → worst segment (~50% errors, crashes, ANRs)"
echo "  3. Samsung SM-A135F → slow durations (P50 ~2800ms)"
echo "  4. App version 3.1.0 → elevated crash rate (~8%)"
echo ""
echo "To test:"
echo "  1. Open Pulse UI → interaction-details/app_launch"
echo "  2. Set time range to 'Last 24 hours'"
echo "  3. Go to 'Root Cause' tab → click 'Analyze'"
echo ""
