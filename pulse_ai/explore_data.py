"""Quick data exploration to understand what's in ClickHouse before running causal analysis."""
import os
from dotenv import load_dotenv
import clickhouse_connect

load_dotenv()

# Strip http:// or https:// from host if present
ch_host = os.getenv('CLICKHOUSE_HOST', 'localhost')
ch_host = ch_host.replace('https://', '').replace('http://', '').rstrip('/')

client = clickhouse_connect.get_client(
    host=ch_host,
    port=int(os.getenv('CLICKHOUSE_PORT', '8123')),
    username=os.getenv('CLICKHOUSE_USER'),
    password=os.getenv('CLICKHOUSE_PASSWORD'),
    database=os.getenv('CLICKHOUSE_DATABASE', 'otel'),
)

print("=" * 60)
print("  PULSE DATA EXPLORATION")
print("=" * 60)

# 1. Find project IDs
print("\n[1] Project IDs in otel_traces:")
r = client.query("SELECT DISTINCT ProjectId, count() as cnt FROM otel_traces GROUP BY ProjectId ORDER BY cnt DESC LIMIT 10")
for row in r.result_rows:
    print(f"    {row[0]}  ({row[1]:,} spans)")

# 2. Session counts per project
print("\n[2] Sessions per project:")
r = client.query("SELECT ProjectId, uniqCombined64(SessionId) as sessions FROM otel_traces GROUP BY ProjectId ORDER BY sessions DESC LIMIT 10")
for row in r.result_rows:
    print(f"    {row[0]}  ({row[1]:,} sessions)")

# 3. PulseType distribution for the top project
r = client.query("SELECT ProjectId FROM otel_traces GROUP BY ProjectId ORDER BY count() DESC LIMIT 1")
if r.result_rows:
    top_project = r.result_rows[0][0]
    print(f"\n[3] PulseType distribution for project '{top_project}':")
    r2 = client.query(f"SELECT PulseType, count() as cnt FROM otel_traces WHERE ProjectId = %(pid)s GROUP BY PulseType ORDER BY cnt DESC", parameters={"pid": top_project})
    for row in r2.result_rows:
        print(f"    {row[0]:40s} {row[1]:>8,}")

    # 4. Crash/ANR data in stack_trace_events
    print(f"\n[4] stack_trace_events for project '{top_project}':")
    r3 = client.query("SELECT PulseType, count() as cnt, uniqCombined64(SessionId) as sessions FROM stack_trace_events WHERE ProjectId = %(pid)s GROUP BY PulseType ORDER BY cnt DESC", parameters={"pid": top_project})
    for row in r3.result_rows:
        print(f"    {row[0]:40s} {row[1]:>8,} events  ({row[2]:,} sessions)")

    # 5. Network endpoints (POST/PUT)
    print(f"\n[5] Top POST/PUT network endpoints:")
    r4 = client.query("""
        SELECT SpanName, SpanAttributes['http.request.method'] as method, count() as cnt, uniqCombined64(SessionId) as sessions
        FROM otel_traces
        WHERE ProjectId = %(pid)s AND PulseType LIKE 'network.%%'
        GROUP BY SpanName, method
        ORDER BY sessions DESC
        LIMIT 20
    """, parameters={"pid": top_project})
    for row in r4.result_rows:
        print(f"    {row[1] or '?':6s} {row[0]:60s} {row[3]:>5,} sessions")

    # 6. Screens
    print(f"\n[6] Top screens (screen_session):")
    r5 = client.query("""
        SELECT SpanName, count() as cnt, uniqCombined64(SessionId) as sessions
        FROM otel_traces
        WHERE ProjectId = %(pid)s AND PulseType IN ('screen_session', 'screen_load')
        GROUP BY SpanName
        ORDER BY sessions DESC
        LIMIT 15
    """, parameters={"pid": top_project})
    for row in r5.result_rows:
        print(f"    {row[0]:50s} {row[2]:>5,} sessions")

    # 7. Crash screens from stack_trace_events
    print(f"\n[7] Crash details (stack_trace_events):")
    r6 = client.query("""
        SELECT ScreenName, ExceptionType, PulseType, count() as cnt, uniqCombined64(SessionId) as sessions
        FROM stack_trace_events
        WHERE ProjectId = %(pid)s
        GROUP BY ScreenName, ExceptionType, PulseType
        ORDER BY cnt DESC
        LIMIT 15
    """, parameters={"pid": top_project})
    for row in r6.result_rows:
        print(f"    [{row[2]}] {row[0]:30s} {row[1]:40s} {row[3]:>5} events ({row[4]} sessions)")

    # 8. Date range
    print(f"\n[8] Data date range:")
    r7 = client.query("SELECT min(Timestamp), max(Timestamp) FROM otel_traces WHERE ProjectId = %(pid)s", parameters={"pid": top_project})
    for row in r7.result_rows:
        print(f"    From: {row[0]}  To: {row[1]}")

print("\n" + "=" * 60)
print("  Done! Use the project ID above for the prototype script.")
print("=" * 60)
