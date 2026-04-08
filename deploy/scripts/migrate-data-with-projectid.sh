#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Migrate ClickHouse Data (ProjectId is Key)${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

CLICKHOUSE_CONTAINER="pulse-clickhouse"
TARGET_PROJECT="default-project"

# Check if container is running
if ! docker ps | grep -q "$CLICKHOUSE_CONTAINER"; then
    echo -e "${RED}✗ ClickHouse container ($CLICKHOUSE_CONTAINER) is not running${NC}"
    exit 1
fi

echo -e "${GREEN}✓ ClickHouse container is running${NC}"
echo ""

# Get list of distinct ProjectIds
echo -e "${BLUE}Available ProjectIds in ClickHouse:${NC}"
PROJECTS=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT DISTINCT ProjectId FROM otel_traces FORMAT CSV;" 2>/dev/null | tr -d '"')

echo "$PROJECTS" | sed 's/^/  - /'
echo ""

# Get the first project to migrate from
SOURCE_PROJECT=$(echo "$PROJECTS" | grep -v "^$TARGET_PROJECT$" | head -1)

if [ -z "$SOURCE_PROJECT" ]; then
    echo -e "${YELLOW}Data already uses ProjectId='$TARGET_PROJECT'${NC}"
    exit 0
fi

echo -e "${YELLOW}Source ProjectId: $SOURCE_PROJECT${NC}"
echo -e "${YELLOW}Target ProjectId: $TARGET_PROJECT${NC}"
echo ""

# Since ProjectId is a key, we need to use INSERT...SELECT with REPLACE strategy
# First, let's check the table structure
echo -e "${YELLOW}Analyzing table structure...${NC}"

# Get row counts before
echo -e "${BLUE}Row counts before migration:${NC}"
TRACE_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM otel_traces WHERE ProjectId = '$SOURCE_PROJECT';" 2>/dev/null)
LOG_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM otel_logs WHERE ProjectId = '$SOURCE_PROJECT';" 2>/dev/null)
TRACE_EVENT_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM stack_trace_events WHERE ProjectId = '$SOURCE_PROJECT';" 2>/dev/null)
SESSION_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM session_summary WHERE ProjectId = '$SOURCE_PROJECT';" 2>/dev/null)

echo "  otel_traces: $TRACE_COUNT"
echo "  otel_logs: $LOG_COUNT"
echo "  stack_trace_events: $TRACE_EVENT_COUNT"
echo "  session_summary: $SESSION_COUNT"
echo ""

# We'll recreate the data using REPLACE strategy
echo -e "${YELLOW}Migrating otel_traces (this may take a moment)...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel << EOSQL 2>&1 | tail -5
INSERT INTO otel_traces 
SELECT 
  replaceOne(TraceId, arrayJoin([]), '') as TraceId,
  SpanId, ParentSpanId, TraceState, Timestamp, 
  instrumentationLibraryName, instrumentationLibraryVersion,
  Duration, Attributes, Events, Links, Status_Message, Status_Code, 
  StatusCode, ResourceAttributes, ResourceSchemaUrl, SpanName, SpanKind,
  ScopeName, ScopeVersion, SpanAttributes, PulseType, SessionId, 
  '$TARGET_PROJECT' as ProjectId
FROM otel_traces 
WHERE ProjectId = '$SOURCE_PROJECT'
EOSQL

echo -e "${YELLOW}Migrating otel_logs...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel << EOSQL 2>&1 | tail -5
INSERT INTO otel_logs
SELECT 
  Timestamp, ObservedTimestamp, TraceId, SpanId, SeverityNumber, 
  SeverityText, Body, Attributes, ResourceAttributes, ResourceSchemaUrl,
  '$TARGET_PROJECT' as ProjectId
FROM otel_logs
WHERE ProjectId = '$SOURCE_PROJECT'
EOSQL

echo -e "${YELLOW}Migrating stack_trace_events...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel << EOSQL 2>&1 | tail -5
INSERT INTO stack_trace_events
SELECT 
  ErrorGroupId, StackTraceHash, StackTraceString, Timestamp, 
  ErrorMessage, ErrorCode, ExceptionName, Framework, 
  '$TARGET_PROJECT' as ProjectId
FROM stack_trace_events
WHERE ProjectId = '$SOURCE_PROJECT'
EOSQL

echo -e "${YELLOW}Migrating session_summary...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel << EOSQL 2>&1 | tail -5
INSERT INTO session_summary
SELECT 
  SessionId, StartTime, EndTime, CreatedAt, UpdatedAt, Duration,
  EventCount, CrashCount, ANRCount, FrozenFrameCount, SlowFrameCount,
  SegmentCount, '$TARGET_PROJECT' as ProjectId, InteractionCount,
  StateCount, LogCount
FROM session_summary
WHERE ProjectId = '$SOURCE_PROJECT'
EOSQL

echo ""
echo -e "${YELLOW}Verifying migrated data...${NC}"
sleep 3

NEW_TRACE_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM otel_traces WHERE ProjectId = '$TARGET_PROJECT';" 2>/dev/null)
NEW_LOG_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM otel_logs WHERE ProjectId = '$TARGET_PROJECT';" 2>/dev/null)
NEW_TRACE_EVENT_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM stack_trace_events WHERE ProjectId = '$TARGET_PROJECT';" 2>/dev/null)
NEW_SESSION_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM session_summary WHERE ProjectId = '$TARGET_PROJECT';" 2>/dev/null)

echo -e "${BLUE}Row counts after migration:${NC}"
echo -e "  otel_traces: ${TRACE_COUNT} → ${NEW_TRACE_COUNT}${NC}"
echo -e "  otel_logs: ${LOG_COUNT} → ${NEW_LOG_COUNT}${NC}"
echo -e "  stack_trace_events: ${TRACE_EVENT_COUNT} → ${NEW_TRACE_EVENT_COUNT}${NC}"
echo -e "  session_summary: ${SESSION_COUNT} → ${NEW_SESSION_COUNT}${NC}"

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}✓ Data migration complete!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Verify in UI: http://localhost:3000"
echo "  2. Check logs: docker logs pulse-server | grep -i error"
echo ""
