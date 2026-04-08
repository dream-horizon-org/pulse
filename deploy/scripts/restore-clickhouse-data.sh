#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Restore ClickHouse Data${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Configuration
CLICKHOUSE_BACKUP_TAR="/Users/abhishekkumar/Downloads/clickhouse_backup.tar"
CLICKHOUSE_CONTAINER="pulse-clickhouse"
TEMP_DIR="/tmp/clickhouse_restore_$$"

# Check if backup file exists
if [ ! -f "$CLICKHOUSE_BACKUP_TAR" ]; then
    echo -e "${RED}✗ ClickHouse backup not found: $CLICKHOUSE_BACKUP_TAR${NC}"
    exit 1
fi

echo -e "${YELLOW}Backup file: $(du -h "$CLICKHOUSE_BACKUP_TAR" | cut -f1)${NC}"
echo ""

# Check if container is running
if ! docker ps | grep -q "$CLICKHOUSE_CONTAINER"; then
    echo -e "${RED}✗ ClickHouse container ($CLICKHOUSE_CONTAINER) is not running${NC}"
    exit 1
fi

echo -e "${GREEN}✓ ClickHouse container is running${NC}"
echo ""

# Create temp directory
mkdir -p "$TEMP_DIR"
echo -e "${YELLOW}Extracting backup to $TEMP_DIR...${NC}"
tar -xzf "$CLICKHOUSE_BACKUP_TAR" -C "$TEMP_DIR" 2>/dev/null || tar -xf "$CLICKHOUSE_BACKUP_TAR" -C "$TEMP_DIR"

BACKUP_DIR="$TEMP_DIR/clickhouse_backup"
if [ ! -d "$BACKUP_DIR" ]; then
    echo -e "${RED}✗ Could not find backup directory in archive${NC}"
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo -e "${GREEN}✓ Backup extracted successfully${NC}"
echo ""

# List the native files
echo -e "${BLUE}Files to restore:${NC}"
ls -1 "$BACKUP_DIR" | head -10
echo "  ... (and more)"
echo ""

# Copy files to ClickHouse shadow directory for restoration
SHADOW_DIR="/var/lib/clickhouse/shadow"
echo -e "${YELLOW}Copying files to ClickHouse container...${NC}"

docker exec "$CLICKHOUSE_CONTAINER" mkdir -p "$SHADOW_DIR/backup_data"
docker cp "$BACKUP_DIR/." "$CLICKHOUSE_CONTAINER:$SHADOW_DIR/backup_data/"

echo -e "${GREEN}✓ Files copied to container${NC}"
echo ""

# Now restore each table using ClickHouse's attach functionality
echo -e "${YELLOW}Restoring data to ClickHouse tables...${NC}"

# We'll use the native format files directly
# Get list of .native files and restore them
docker exec "$CLICKHOUSE_CONTAINER" bash -c "
  cd $SHADOW_DIR/backup_data
  
  # Restore otel_traces
  if [ -f 'otel_traces.native' ]; then
    echo 'Restoring otel_traces...'
    clickhouse-client --database otel --query 'INSERT INTO otel_traces FORMAT Native' < 'otel_traces.native'
  fi
  
  # Restore otel_logs
  if [ -f 'otel_logs.native' ]; then
    echo 'Restoring otel_logs...'
    clickhouse-client --database otel --query 'INSERT INTO otel_logs FORMAT Native' < 'otel_logs.native'
  fi
  
  # Restore otel_metrics_gauge
  if [ -f 'otel_metrics_gauge.native' ]; then
    echo 'Restoring otel_metrics_gauge...'
    clickhouse-client --database otel --query 'INSERT INTO otel_metrics_gauge FORMAT Native' < 'otel_metrics_gauge.native'
  fi
  
  # Restore stack_trace_events
  if [ -f 'stack_trace_events.native' ]; then
    echo 'Restoring stack_trace_events...'
    clickhouse-client --database otel --query 'INSERT INTO stack_trace_events FORMAT Native' < 'stack_trace_events.native'
  fi
  
  # Restore session_summary
  if [ -f 'session_summary.native' ]; then
    echo 'Restoring session_summary...'
    clickhouse-client --database otel --query 'INSERT INTO session_summary FORMAT Native' < 'session_summary.native'
  fi
  
  # Restore session_replay_events
  if [ -f 'session_replay_events.native' ]; then
    echo 'Restoring session_replay_events...'
    clickhouse-client --database otel --query 'INSERT INTO session_replay_events FORMAT Native' < 'session_replay_events.native'
  fi
  
  echo 'Restoration complete'
" 2>&1 || echo "Note: Some tables may not have data in backup"

echo -e "${GREEN}✓ Data restoration initiated${NC}"
echo ""

# Wait a moment for restoration
sleep 5

# Verify restoration
echo -e "${YELLOW}Verifying restored data...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" bash -c "
  clickhouse-client --database otel --query 'SELECT table, COUNT() as rows FROM (
    SELECT \"otel_traces\" as table, COUNT() FROM otel_traces
    UNION ALL SELECT \"otel_logs\", COUNT() FROM otel_logs
    UNION ALL SELECT \"otel_metrics_gauge\", COUNT() FROM otel_metrics_gauge
    UNION ALL SELECT \"stack_trace_events\", COUNT() FROM stack_trace_events
    UNION ALL SELECT \"session_summary\", COUNT() FROM session_summary
  ) WHERE rows > 0 ORDER BY rows DESC'
" 2>&1

# Clean up
echo ""
echo -e "${YELLOW}Cleaning up temporary files...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" rm -rf "$SHADOW_DIR/backup_data"
rm -rf "$TEMP_DIR"

echo -e "${GREEN}✓ Temporary files cleaned${NC}"
echo ""

# Check for ProjectId = default-project
echo -e "${BLUE}Checking for default-project data...${NC}"
DEFAULT_PROJECT_COUNT=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT COUNT() FROM otel_traces WHERE ProjectId = 'default-project';" 2>/dev/null || echo "0")

if [ "$DEFAULT_PROJECT_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ Found $DEFAULT_PROJECT_COUNT rows with ProjectId='default-project' in otel_traces${NC}"
else
    echo -e "${YELLOW}⚠ No data found with ProjectId='default-project'${NC}"
    echo -e "${YELLOW}  Checking available ProjectIds...${NC}"
    docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --database otel --query "SELECT DISTINCT ProjectId FROM otel_traces LIMIT 5;" 2>/dev/null || echo "No data available"
fi

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}✓ ClickHouse data restoration complete!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo -e "${BLUE}Important Notes:${NC}"
echo "  1. If data uses a different ProjectId, you need to either:"
echo "     a) Update ProjectId column to 'default-project'"
echo "     b) Update your project to match the ProjectId in data"
echo "  2. To update ProjectId to 'default-project', run:"
echo "     docker exec pulse-clickhouse clickhouse-client --database otel --query \"ALTER TABLE otel_traces UPDATE ProjectId = 'default-project' WHERE 1\""
echo ""
