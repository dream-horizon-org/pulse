#!/bin/bash

BASE=$(date +%s)

echo "=== Multi-block aggregation demo ==="
echo "Sending 3 batches for session 'ses-multiblock-demo' across separate flush windows"
echo ""

echo "--- Batch 1: Home screen (t=0s to t=2s) ---"
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-priya-55",
    "properties": {
      "session_id": "ses-multiblock-demo",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 4, "data": {"href": "app://shopapp/home", "width": 390, "height": 844}, "timestamp": '$BASE'000},
        {"type": 2, "data": {"node": {"type": 0, "childNodes": [{"type": 1, "name": "body", "childNodes": [{"type": 3, "textContent": "Home Screen"}]}]}}, "timestamp": '$((BASE+2))'000}
      ]
    }
  }'
echo ""
echo "Waiting 15s for consumer to flush batch 1..."
sleep 15

echo ""
echo "--- Batch 2: Product detail (t=20s to t=25s) ---"
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-priya-55",
    "properties": {
      "session_id": "ses-multiblock-demo",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 3, "data": {"source": 2, "type": 2, "id": 5, "x": 195, "y": 400}, "timestamp": '$((BASE+20))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [], "removes": [], "adds": [{"parentId": 3, "node": {"type": 1, "name": "div", "childNodes": [{"type": 3, "textContent": "Product Detail Page"}]}}]}, "timestamp": '$((BASE+25))'000}
      ]
    }
  }'
echo ""
echo "Waiting 15s for consumer to flush batch 2..."
sleep 15

echo ""
echo "--- Batch 3: Checkout complete (t=40s to t=50s) ---"
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-priya-55",
    "properties": {
      "session_id": "ses-multiblock-demo",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 3, "data": {"source": 2, "type": 2, "id": 10, "x": 195, "y": 700}, "timestamp": '$((BASE+40))'000},
        {"type": 2, "data": {"node": {"type": 0, "childNodes": [{"type": 1, "name": "body", "childNodes": [{"type": 3, "textContent": "Checkout Complete"}]}]}}, "timestamp": '$((BASE+50))'000}
      ]
    }
  }'
echo ""
echo "Waiting 20s for consumer to flush batch 3..."
sleep 20

echo ""
echo "=========================================="
echo "=== ClickHouse: Aggregated result      ==="
echo "=========================================="
echo ""

docker exec pulse-clickhouse clickhouse-client \
  --user pulse_user --password pulse_password --database otel \
  -q "
SELECT
    session_id,
    project_id,
    user_id,
    min_first_timestamp,
    max_last_timestamp,
    dateDiff('second', min_first_timestamp, max_last_timestamp) AS duration_seconds,
    length(block_urls) AS num_blocks,
    block_urls,
    block_first_timestamps,
    block_last_timestamps,
    argMinMerge(snapshot_source) AS snapshot_source
FROM session_replay_events
WHERE session_id = 'ses-multiblock-demo'
GROUP BY session_id, project_id, user_id,
         min_first_timestamp, max_last_timestamp,
         block_urls, block_first_timestamps, block_last_timestamps
FORMAT Vertical
"

echo ""
echo "=== Force merge and query again ==="
docker exec pulse-clickhouse clickhouse-client \
  --user pulse_user --password pulse_password --database otel \
  -q "OPTIMIZE TABLE session_replay_events FINAL"

echo ""
echo "After OPTIMIZE FINAL:"
docker exec pulse-clickhouse clickhouse-client \
  --user pulse_user --password pulse_password --database otel \
  -q "
SELECT
    session_id,
    min_first_timestamp,
    max_last_timestamp,
    dateDiff('second', min_first_timestamp, max_last_timestamp) AS duration_seconds,
    length(block_urls) AS num_blocks,
    block_urls,
    block_first_timestamps,
    block_last_timestamps,
    argMinMerge(snapshot_source) AS snapshot_source
FROM session_replay_events
WHERE session_id = 'ses-multiblock-demo'
GROUP BY session_id, min_first_timestamp, max_last_timestamp,
         block_urls, block_first_timestamps, block_last_timestamps
FORMAT Vertical
"
