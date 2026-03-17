"""
Generates and inserts realistic test telemetry data into ClickHouse for
testing the AI root cause analysis feature.

Intentional bad segments planted:
  1. Android 10 + Jio → high error rate (~35%), poor APDEX
  2. Android 10 + Jio + Andhra Pradesh → worst (~50% errors, crashes, ANRs)
  3. Samsung SM-A135F → slow durations (P50 ~2800ms)
  4. App version 3.1.0 → elevated crash rate (~8%)
"""

import random
import uuid
from datetime import datetime, timedelta, timezone
import urllib.request
import urllib.error
import sys

CH_HOST = "127.0.0.1"
CH_PORT = "8123"
CH_USER = "pulse_user"
CH_PASSWORD = "pulse_password"
CH_DB = "otel"

random.seed(42)

def ch_query(query):
    url = f"http://{CH_HOST}:{CH_PORT}/?database={CH_DB}&user={CH_USER}&password={CH_PASSWORD}"
    req = urllib.request.Request(url, data=query.encode("utf-8"))
    try:
        resp = urllib.request.urlopen(req)
        return resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        print(f"ClickHouse error ({e.code}): {body[:500]}")
        sys.exit(1)

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


def escape(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


print("Generating interaction traces...")
rows = []

for i in range(NUM_SPANS):
    ts = start_time + timedelta(seconds=random.randint(0, 86400))
    ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")

    trace_id = uuid.uuid4().hex
    span_id = uuid.uuid4().hex[:16]
    session_id = f"session_{random.randint(1, 500)}"
    user_id = f"user_{random.randint(1, 200)}"

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

    is_error = random.random() < 0.05
    base_duration_ms = random.gauss(1200, 400)
    has_crash = False
    has_anr = False
    frozen_frames = 0

    # BAD SEGMENT 1: Android 10 + Jio
    if platform == "android" and os_version == "10" and network == "Jio":
        is_error = random.random() < 0.35
        base_duration_ms = random.gauss(3500, 800)
        has_crash = random.random() < 0.12
        has_anr = random.random() < 0.08
        frozen_frames = random.randint(2, 8) if random.random() < 0.4 else 0
        # BAD SEGMENT 2: + Andhra Pradesh
        if state == "IN-AP":
            is_error = random.random() < 0.50
            base_duration_ms = random.gauss(4500, 1000)
            has_crash = random.random() < 0.20
            has_anr = random.random() < 0.15
            frozen_frames = random.randint(5, 15) if random.random() < 0.6 else 0
    # BAD SEGMENT 3: Samsung SM-A135F
    elif device == "SM-A135F":
        base_duration_ms = random.gauss(2800, 600)
        is_error = random.random() < 0.12
        frozen_frames = random.randint(1, 5) if random.random() < 0.3 else 0
    # BAD SEGMENT 4: App version 3.1.0
    elif app_version == "3.1.0":
        has_crash = random.random() < 0.08
        base_duration_ms = random.gauss(1800, 500)

    duration_ms = max(200, base_duration_ms)
    duration_ns = int(duration_ms * 1e6)

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

    event_names = []
    if has_crash:
        event_names.append("device.crash")
    if has_anr:
        event_names.append("device.anr")

    events_name_str = "[" + ",".join(f"'{e}'" for e in event_names) + "]"
    events_ts_str = "[" + ",".join(f"'{ts_str}'" for _ in event_names) + "]"
    events_attr_str = "[" + ",".join("map()" for _ in event_names) + "]"

    analysed = frozen_frames + random.randint(50, 200) if frozen_frames > 0 else 0
    unanalysed = random.randint(0, 10) if frozen_frames > 0 else 0

    span_attrs_parts = [
        f"'pulse.type','interaction'",
        f"'session.id','{escape(session_id)}'",
        f"'user.id','{escape(user_id)}'",
        f"'geo.region.iso_code','{escape(state)}'",
        f"'geo.country.iso_code','IN'",
        f"'network.carrier.name','{escape(network)}'",
        f"'pulse.interaction.apdex_score','{apdex_score}'",
    ]
    if user_category:
        span_attrs_parts.append(f"'pulse.interaction.user_category','{user_category}'")
    if frozen_frames > 0:
        span_attrs_parts.append(f"'app.interaction.frozen_frame_count','{frozen_frames}'")
        span_attrs_parts.append(f"'app.interaction.analysed_frame_count','{analysed}'")
        span_attrs_parts.append(f"'app.interaction.unanalysed_frame_count','{unanalysed}'")

    span_attr_str = "map(" + ",".join(span_attrs_parts) + ")"

    resource_attrs_parts = [
        f"'os.name','{escape(platform)}'",
        f"'os.version','{escape(os_version)}'",
        f"'app.build_name','{escape(app_version)}'",
        f"'device.model.name','{escape(device)}'",
        f"'project.id','default'",
        f"'rum.sdk.version','1.0.0'",
    ]
    resource_attr_str = "map(" + ",".join(resource_attrs_parts) + ")"

    row = (
        f"('{ts_str}','{trace_id}','{span_id}','','','app_launch','CLIENT','pulse-sdk',"
        f"{resource_attr_str},'pulse','1.0',{span_attr_str},"
        f"{duration_ns},'{status_code}','',{events_ts_str},{events_name_str},{events_attr_str},"
        f"[],[],[],[])"
    )
    rows.append(row)

# Generate standalone crash/ANR RUM events
print("Generating crash/ANR RUM events...")
for i in range(200):
    ts = start_time + timedelta(seconds=random.randint(0, 86400))
    ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f000")
    trace_id = uuid.uuid4().hex
    span_id = uuid.uuid4().hex[:16]
    session_id = f"session_{random.randint(1, 500)}"
    user_id = f"user_{random.randint(1, 200)}"

    platform = weighted_choice(platforms, [80, 20])
    if platform == "android":
        os_version = weighted_choice(android_versions, [40, 15, 20, 15, 10])
        device = weighted_choice(android_devices, [35, 10, 15, 10, 15, 15])
    else:
        os_version = weighted_choice(ios_versions, [15, 30, 35, 20])
        device = weighted_choice(ios_devices, [25, 30, 25, 20])

    if platform == "android" and os_version == "10":
        network = weighted_choice(["Jio", "Airtel", "Vi"], [60, 25, 15])
    else:
        network = weighted_choice(networks, [30, 25, 15, 10, 20])

    state = weighted_choice(states, [20, 12, 12, 10, 10, 10, 8, 8, 5, 5])
    app_version = weighted_choice(app_versions, [15, 40, 30, 15])

    pulse_type = weighted_choice(["device.crash", "device.anr"], [65, 35])

    span_attrs_parts = [
        f"'pulse.type','{pulse_type}'",
        f"'session.id','{escape(session_id)}'",
        f"'user.id','{escape(user_id)}'",
        f"'geo.region.iso_code','{escape(state)}'",
        f"'geo.country.iso_code','IN'",
        f"'network.carrier.name','{escape(network)}'",
    ]
    span_attr_str = "map(" + ",".join(span_attrs_parts) + ")"

    resource_attrs_parts = [
        f"'os.name','{escape(platform)}'",
        f"'os.version','{escape(os_version)}'",
        f"'app.build_name','{escape(app_version)}'",
        f"'device.model.name','{escape(device)}'",
        f"'project.id','default'",
        f"'rum.sdk.version','1.0.0'",
    ]
    resource_attr_str = "map(" + ",".join(resource_attrs_parts) + ")"

    row = (
        f"('{ts_str}','{trace_id}','{span_id}','','','{pulse_type}','CLIENT','pulse-sdk',"
        f"{resource_attr_str},'pulse','1.0',{span_attr_str},"
        f"0,'Ok','',[],[],[],[],[],[],[])"
    )
    rows.append(row)

# Insert in batches
print(f"Inserting {len(rows)} rows into ClickHouse...")
BATCH_SIZE = 500
total = len(rows)
for batch_start in range(0, total, BATCH_SIZE):
    batch = rows[batch_start : batch_start + BATCH_SIZE]
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
    print(f"  {done}/{total} rows...")

print("\nVerifying...")
count = ch_query("SELECT count() FROM otel_traces WHERE SpanName = 'app_launch'").strip()
print(f"  Interaction traces: {count}")
crash_count = ch_query("SELECT count() FROM otel_traces WHERE PulseType = 'device.crash'").strip()
print(f"  Crash events: {crash_count}")
anr_count = ch_query("SELECT count() FROM otel_traces WHERE PulseType = 'device.anr'").strip()
print(f"  ANR events: {anr_count}")

print("\nSample APDEX by Platform:")
result = ch_query(
    "SELECT Platform, count() as vol, "
    "avgIf(toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']), StatusCode != 'Error') as apdex, "
    "if(count() = 0, NULL, (countIf(StatusCode = 'Error')/count()) * 100) as error_rate "
    "FROM otel_traces WHERE SpanName = 'app_launch' GROUP BY Platform ORDER BY vol DESC FORMAT Pretty"
)
print(result)

print("Done!")
