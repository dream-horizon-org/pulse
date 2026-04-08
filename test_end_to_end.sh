#!/bin/bash

set -e

echo "=================================================="
echo "REBUILDING DOCKER IMAGES"
echo "=================================================="

cd /Users/abhishekkumar/Desktop/pulse/deploy

# Build backend
echo "Building backend server..."
./scripts/build.sh server

echo "Starting services..."
./scripts/stop.sh -v 2>/dev/null || true
./scripts/start.sh --build -d

echo "Waiting for services to start..."
sleep 15

echo ""
echo "=================================================="
echo "CHECKING SERVICE STATUS"
echo "=================================================="

docker ps --format "table {{.Names}}\t{{.Status}}"

echo ""
echo "=================================================="
echo "MAKING RCA REQUEST"
echo "=================================================="

REQUEST_BODY='{
  "interactionName": "MatchCardClickedToMatchDetailLoaded",
  "date": "2026-04-07"
}'

echo "Request:"
echo "$REQUEST_BODY" | jq .

echo ""
echo "Response:"
curl -X POST http://localhost:8080/v2/rca/report \
  -H "Content-Type: application/json" \
  -H "X-Project-ID: default-project" \
  -d "$REQUEST_BODY" 2>/dev/null | jq .

echo ""
echo "=================================================="
echo "CHECKING LOGS"
echo "=================================================="

echo ""
echo "=== Backend logs (last 100 lines) ==="
docker logs pulse-server 2>&1 | tail -100 | grep -i "session\|evidence\|error" || echo "No matching logs found"

echo ""
echo "Done!"
