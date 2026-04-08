#!/bin/bash
set +H

BASE=$(date +%s)

echo "=== Sending 7 batches (5 for session 1, 2 for session 2) ==="
echo ""

# Session 1, Batch 1: App launch (t=0s to t=2s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-ravi-91",
    "properties": {
      "session_id": "ses-mobile-abc123",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 4, "data": {"href": "app://com.shopapp/home", "width": 390, "height": 844}, "timestamp": '$BASE'000},
        {"type": 2, "data": {"node": {"type": 0, "childNodes": [{"type": 1, "name": "body", "childNodes": [{"type": 1, "name": "div", "attributes": {"class": "home"}, "childNodes": [{"type": 3, "textContent": "Welcome to ShopApp"}]}]}]}}, "timestamp": '$((BASE+1))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [], "removes": [], "adds": [{"parentId": 3, "node": {"type": 1, "name": "div", "attributes": {"class": "banner"}, "childNodes": [{"type": 3, "textContent": "Summer Sale 50 Percent Off"}]}}]}, "timestamp": '$((BASE+2))'000}
      ]
    }
  }' && echo " <- Batch 1 sent (t=0s to t=2s)"

# Session 1, Batch 2: User scrolls (t=10s to t=15s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-ravi-91",
    "properties": {
      "session_id": "ses-mobile-abc123",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 3, "data": {"source": 1, "positions": [{"x": 195, "y": 600, "id": 5, "timeOffset": 0}, {"x": 195, "y": 400, "id": 5, "timeOffset": 200}]}, "timestamp": '$((BASE+10))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [], "removes": [], "adds": [{"parentId": 3, "node": {"type": 1, "name": "div", "attributes": {"class": "product-card"}, "childNodes": [{"type": 3, "textContent": "Nike Air Max - Rs 12999"}]}}]}, "timestamp": '$((BASE+12))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [], "removes": [], "adds": [{"parentId": 3, "node": {"type": 1, "name": "div", "attributes": {"class": "product-card"}, "childNodes": [{"type": 3, "textContent": "Adidas Ultra Boost - Rs 15499"}]}}]}, "timestamp": '$((BASE+15))'000}
      ]
    }
  }' && echo " <- Batch 2 sent (t=10s to t=15s)"

# Session 1, Batch 3: User taps product (t=20s to t=28s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-ravi-91",
    "properties": {
      "session_id": "ses-mobile-abc123",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 3, "data": {"source": 2, "type": 2, "id": 10, "x": 195, "y": 350}, "timestamp": '$((BASE+20))'000},
        {"type": 4, "data": {"href": "app://com.shopapp/product/nike-air-max", "width": 390, "height": 844}, "timestamp": '$((BASE+21))'000},
        {"type": 2, "data": {"node": {"type": 0, "childNodes": [{"type": 1, "name": "body", "childNodes": [{"type": 1, "name": "div", "attributes": {"class": "pdp"}, "childNodes": [{"type": 3, "textContent": "Nike Air Max 90"}, {"type": 1, "name": "span", "attributes": {"class": "price"}, "childNodes": [{"type": 3, "textContent": "Rs 12999"}]}, {"type": 1, "name": "button", "attributes": {"class": "add-to-cart"}, "childNodes": [{"type": 3, "textContent": "Add to Cart"}]}]}]}]}}, "timestamp": '$((BASE+22))'000},
        {"type": 3, "data": {"source": 1, "positions": [{"x": 195, "y": 500, "id": 14, "timeOffset": 0}, {"x": 195, "y": 300, "id": 14, "timeOffset": 300}]}, "timestamp": '$((BASE+25))'000},
        {"type": 3, "data": {"source": 5, "id": 14, "x": 0, "y": -200}, "timestamp": '$((BASE+28))'000}
      ]
    }
  }' && echo " <- Batch 3 sent (t=20s to t=28s)"

# Session 1, Batch 4: Add to cart (t=35s to t=42s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-ravi-91",
    "properties": {
      "session_id": "ses-mobile-abc123",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 3, "data": {"source": 2, "type": 2, "id": 18, "x": 195, "y": 700}, "timestamp": '$((BASE+35))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [], "removes": [], "adds": [{"parentId": 12, "node": {"type": 1, "name": "div", "attributes": {"class": "toast"}, "childNodes": [{"type": 3, "textContent": "Added to cart"}]}}]}, "timestamp": '$((BASE+36))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [{"id": 20, "attributes": {"class": "cart-badge visible"}}], "removes": [], "adds": []}, "timestamp": '$((BASE+38))'000},
        {"type": 3, "data": {"source": 0, "texts": [{"id": 21, "value": "1"}], "attributes": [], "removes": [{"parentId": 12, "id": 22}], "adds": []}, "timestamp": '$((BASE+42))'000}
      ]
    }
  }' && echo " <- Batch 4 sent (t=35s to t=42s)"

# Session 1, Batch 5: Cart page and session end (t=50s to t=60s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "default-project",
    "user_id": "usr-ravi-91",
    "properties": {
      "session_id": "ses-mobile-abc123",
      "snapshot_source": "mobile",
      "snapshot_data": [
        {"type": 3, "data": {"source": 2, "type": 2, "id": 20, "x": 350, "y": 50}, "timestamp": '$((BASE+50))'000},
        {"type": 4, "data": {"href": "app://com.shopapp/cart", "width": 390, "height": 844}, "timestamp": '$((BASE+51))'000},
        {"type": 2, "data": {"node": {"type": 0, "childNodes": [{"type": 1, "name": "body", "childNodes": [{"type": 1, "name": "div", "attributes": {"class": "cart"}, "childNodes": [{"type": 3, "textContent": "Your Cart (1 item)"}, {"type": 1, "name": "div", "attributes": {"class": "cart-item"}, "childNodes": [{"type": 3, "textContent": "Nike Air Max 90 - Rs 12999"}]}]}]}]}}, "timestamp": '$((BASE+55))'000},
        {"type": 3, "data": {"source": 1, "positions": [{"x": 195, "y": 600, "id": 30, "timeOffset": 0}]}, "timestamp": '$((BASE+60))'000}
      ]
    }
  }' && echo " <- Batch 5 sent (t=50s to t=60s)"

# Session 2, Batch 1: Dashboard load (t=0s to t=5s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "proj-saas-99",
    "user_id": "usr-admin-007",
    "properties": {
      "session_id": "ses-web-xyz789",
      "snapshot_source": "web",
      "snapshot_data": [
        {"type": 4, "data": {"href": "https://app.saas.io/dashboard", "width": 1920, "height": 1080}, "timestamp": '$BASE'000},
        {"type": 2, "data": {"node": {"type": 0, "childNodes": [{"type": 1, "name": "body", "childNodes": [{"type": 1, "name": "div", "attributes": {"class": "dashboard"}, "childNodes": [{"type": 3, "textContent": "Revenue: Rs 452000"}, {"type": 3, "textContent": "Users: 12340"}]}]}]}}, "timestamp": '$((BASE+2))'000},
        {"type": 3, "data": {"source": 1, "positions": [{"x": 500, "y": 300, "id": 8, "timeOffset": 0}]}, "timestamp": '$((BASE+5))'000}
      ]
    }
  }' && echo " <- Session 2 Batch 1 sent (t=0s to t=5s)"

# Session 2, Batch 2: User clicks chart (t=12s to t=18s)
curl -s -X POST http://localhost:3400/s/ \
  -H "Content-Type: application/json" \
  -d '{
    "event": "$snapshot",
    "project_id": "proj-saas-99",
    "user_id": "usr-admin-007",
    "properties": {
      "session_id": "ses-web-xyz789",
      "snapshot_source": "web",
      "snapshot_data": [
        {"type": 3, "data": {"source": 2, "type": 2, "id": 8, "x": 600, "y": 400}, "timestamp": '$((BASE+12))'000},
        {"type": 3, "data": {"source": 0, "texts": [], "attributes": [], "removes": [], "adds": [{"parentId": 5, "node": {"type": 1, "name": "div", "attributes": {"class": "chart-tooltip"}, "childNodes": [{"type": 3, "textContent": "March 4: Rs 52000"}]}}]}, "timestamp": '$((BASE+15))'000},
        {"type": 3, "data": {"source": 1, "positions": [{"x": 700, "y": 350, "id": 8, "timeOffset": 0}]}, "timestamp": '$((BASE+18))'000}
      ]
    }
  }' && echo " <- Session 2 Batch 2 sent (t=12s to t=18s)"

echo ""
echo "=== All 7 batches sent ==="
echo "Waiting 20s for ingestion consumer to flush..."
sleep 20

echo ""
echo "=== ClickHouse: Aggregated session replay data ==="
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
WHERE session_id IN ('ses-mobile-abc123', 'ses-web-xyz789')
GROUP BY session_id, project_id, user_id,
         min_first_timestamp, max_last_timestamp,
         block_urls, block_first_timestamps, block_last_timestamps
ORDER BY project_id, session_id
FORMAT Vertical
"

echo ""
echo "=== Done (ingestion + ClickHouse check) ==="
echo ""

# -----------------------------------------------------------------------------
# E2E: Snapshots API (requires pulse-server and MinIO).
# E2E: Snapshots API (requires pulse-server and MinIO).
# default-project ClickHouse credentials are seeded in mysql-init.sql; use same
# VAULT_ENCRYPTION_MASTER_KEY as in .env.example so pulse-server can decrypt.
# -----------------------------------------------------------------------------
API_BASE="${PULSE_SERVER_URL:-http://localhost:8080}"
PROJECT_ID="default-project"
TENANT_ID="default"
SESSION_ID="ses-mobile-abc123"

echo "=== E2E: Fetch snapshots sources ==="
SOURCES_RESP=$(curl -s -w "\n%{http_code}" \
  -H "X-Project-ID: $PROJECT_ID" \
  -H "X-Tenant-ID: $TENANT_ID" \
  "$API_BASE/v1/sessions/$SESSION_ID/snapshots-source")
SOURCES_HTTP=$(echo "$SOURCES_RESP" | tail -n1)
SOURCES_BODY=$(echo "$SOURCES_RESP" | sed '$d')

if [ "$SOURCES_HTTP" != "200" ]; then
  echo "FAIL: GET /v1/sessions/$SESSION_ID/snapshots-source returned HTTP $SOURCES_HTTP"
  echo "Response: $SOURCES_BODY"
  echo "Tip: Ensure pulse-server is up, MinIO has session-recordings bucket, and VAULT_ENCRYPTION_MASTER_KEY matches .env.example (mysql-init seeds default-project credentials)."
  exit 1
fi

if ! echo "$SOURCES_BODY" | grep -q '"sources"'; then
  echo "FAIL: Response does not contain 'sources'"
  echo "Response: $SOURCES_BODY"
  exit 1
fi
echo "OK: Got 200 and sources in response"
echo ""
echo "--- Response (GET snapshots sources) ---"
if command -v jq >/dev/null 2>&1; then
  echo "$SOURCES_BODY" | jq .
else
  echo "$SOURCES_BODY"
fi
echo "---"

echo ""
echo "=== E2E: Fetch first block (blob 0) ==="
BLOB_RESP=$(curl -s -w "\n%{http_code}" \
  -H "X-Project-ID: $PROJECT_ID" \
  -H "X-Tenant-ID: $TENANT_ID" \
  "$API_BASE/v1/sessions/$SESSION_ID/snapshots-data?start_blob_key=0&end_blob_key=0")
BLOB_HTTP=$(echo "$BLOB_RESP" | tail -n1)
BLOB_BODY=$(echo "$BLOB_RESP" | sed '$d')

if [ "$BLOB_HTTP" != "200" ]; then
  echo "FAIL: GET .../snapshots-data?start_blob_key=0&end_blob_key=0 returned HTTP $BLOB_HTTP"
  echo "Response (first 500 chars): ${BLOB_BODY:0:500}"
  exit 1
fi

# Expect { "data": { "snapshots": [ { "timestamp", "type", "data" }, ... ] }, "error": null }
if ! echo "$BLOB_BODY" | grep -q '"snapshots"'; then
  echo "FAIL: Response does not contain 'snapshots' (expected { data: { snapshots: [...] } })"
  echo "First 500 chars: $(echo "$BLOB_BODY" | head -c 500)"
  exit 1
fi
if ! echo "$BLOB_BODY" | grep -q '"timestamp"'; then
  echo "FAIL: Response snapshots do not contain 'timestamp'"
  echo "First 500 chars: $(echo "$BLOB_BODY" | head -c 500)"
  exit 1
fi
echo "OK: Got 200 and data.snapshots for blob 0"
echo ""
echo "--- Response (GET blob 0) ---"
if command -v jq >/dev/null 2>&1; then
  echo "$BLOB_BODY" | jq .
else
  echo "$BLOB_BODY"
fi
echo "---"

echo ""
echo "=== E2E: All steps passed ==="
echo ""
echo "=== Done ==="
