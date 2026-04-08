#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Migrate ClickHouse ProjectId${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

CLICKHOUSE_CONTAINER="pulse-clickhouse"
SOURCE_PROJECT=""
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

# Count them
PROJECT_COUNT=$(echo "$PROJECTS" | wc -l)
echo "  Found $PROJECT_COUNT project(s):"
echo "$PROJECTS" | sed 's/^/    - /'
echo ""

# Get the first non-empty project if not specified
if [ -z "$SOURCE_PROJECT" ]; then
    SOURCE_PROJECT=$(echo "$PROJECTS" | head -1)
    echo -e "${YELLOW}Using first project as source: $SOURCE_PROJECT${NC}"
fi

# Get row counts before migration
echo -e "${BLUE}Row counts before migration:${NC}"
TABLES=("otel_traces" "otel_logs" "otel_metrics_gauge" "stack_trace_events" "session_summary")

for table in "${TABLES[@]}"; do
    count=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM $table WHERE ProjectId = '$SOURCE_PROJECT';" 2>/dev/null || echo "0")
    echo "  $table: $count rows with ProjectId='$SOURCE_PROJECT'"
done

echo ""
echo -e "${YELLOW}Migrating ProjectId from '$SOURCE_PROJECT' to '$TARGET_PROJECT'...${NC}"
echo ""

# Update each table
for table in "${TABLES[@]}"; do
    echo -e "${YELLOW}Updating $table...${NC}"
    docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "ALTER TABLE $table UPDATE ProjectId = '$TARGET_PROJECT' WHERE ProjectId = '$SOURCE_PROJECT';" 2>&1 | grep -v "^Code:" || true
    sleep 1
done

echo ""
echo -e "${YELLOW}Verifying migration...${NC}"
for table in "${TABLES[@]}"; do
    count=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM $table WHERE ProjectId = '$TARGET_PROJECT';" 2>/dev/null || echo "0")
    if [ "$count" -gt 0 ]; then
        echo -e "${GREEN}✓ $table: $count rows with ProjectId='$TARGET_PROJECT'${NC}"
    fi
done

echo ""
echo -e "${BLUE}Checking other tables with ProjectId column:${NC}"

# Also check session_replay_events and other tables that might need migration
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "
  SELECT name FROM system.columns 
  WHERE database = 'otel' AND table IN ('session_replay_events', 'root_cause_cache') 
  AND name = 'ProjectId' FORMAT CSV;
" 2>/dev/null || true

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}✓ ProjectId migration complete!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Verify data: clickhouse-client --database otel --query \"SELECT COUNT() FROM otel_traces WHERE ProjectId = 'default-project';\""
echo "  2. Test API: curl http://localhost:8080/healthcheck"
echo ""
