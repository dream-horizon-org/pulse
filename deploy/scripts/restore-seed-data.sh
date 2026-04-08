#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
CLICKHOUSE_BACKUP_TAR="/Users/abhishekkumar/Downloads/clickhouse_backup.tar"
MYSQL_DUMP="/Users/abhishekkumar/Downloads/pulse_prod_dump.sql"
MYSQL_CONTAINER="pulse-mysql"
CLICKHOUSE_CONTAINER="pulse-clickhouse"
CLICKHOUSE_BACKUP_DIR="/var/lib/clickhouse/backups"

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Pulse Seed Data Restoration Script${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Check if backup files exist
if [ ! -f "$CLICKHOUSE_BACKUP_TAR" ]; then
    echo -e "${RED}✗ ClickHouse backup not found: $CLICKHOUSE_BACKUP_TAR${NC}"
    exit 1
fi

if [ ! -f "$MYSQL_DUMP" ]; then
    echo -e "${RED}✗ MySQL dump not found: $MYSQL_DUMP${NC}"
    exit 1
fi

echo -e "${YELLOW}Found backup files:${NC}"
echo "  ClickHouse: $(du -h "$CLICKHOUSE_BACKUP_TAR" | cut -f1)"
echo "  MySQL: $(du -h "$MYSQL_DUMP" | cut -f1)"
echo ""

# Check if Docker containers are running
echo -e "${BLUE}Checking Docker containers...${NC}"
if ! docker ps | grep -q "$MYSQL_CONTAINER"; then
    echo -e "${RED}✗ MySQL container ($MYSQL_CONTAINER) is not running${NC}"
    exit 1
fi

if ! docker ps | grep -q "$CLICKHOUSE_CONTAINER"; then
    echo -e "${RED}✗ ClickHouse container ($CLICKHOUSE_CONTAINER) is not running${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Both containers are running${NC}"
echo ""

# =============================================================================
# RESTORE MYSQL DATABASE
# =============================================================================
echo -e "${BLUE}=== Restoring MySQL Database ===${NC}"
echo -e "${YELLOW}This may take a few minutes...${NC}"

# First, get the MySQL credentials from .env
if [ -f "$(dirname "$0")/../.env" ]; then
    source "$(dirname "$0")/../.env"
else
    echo -e "${RED}✗ .env file not found${NC}"
    exit 1
fi

MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
MYSQL_DATABASE="${MYSQL_DATABASE:-pulse_db}"

# Drop and recreate database
echo -e "${YELLOW}Dropping existing database...${NC}"
docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    -e "DROP DATABASE IF EXISTS $MYSQL_DATABASE;" 2>/dev/null || true

echo -e "${YELLOW}Creating fresh database...${NC}"
docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    -e "CREATE DATABASE $MYSQL_DATABASE;"

echo -e "${YELLOW}Restoring MySQL dump (this may take several minutes)...${NC}"
docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
    < "$MYSQL_DUMP"

echo -e "${GREEN}✓ MySQL database restored successfully${NC}"
echo ""

# =============================================================================
# RESTORE CLICKHOUSE DATABASE
# =============================================================================
echo -e "${BLUE}=== Restoring ClickHouse Database ===${NC}"
echo -e "${YELLOW}This may take a few minutes...${NC}"

# Extract the tar file to a temporary location
TEMP_EXTRACT_DIR="/tmp/clickhouse_backup_$$"
mkdir -p "$TEMP_EXTRACT_DIR"

echo -e "${YELLOW}Extracting backup archive...${NC}"
tar -xf "$CLICKHOUSE_BACKUP_TAR" -C "$TEMP_EXTRACT_DIR"

echo -e "${YELLOW}Copying backup to ClickHouse container...${NC}"
# Create backup directory in container
docker exec "$CLICKHOUSE_CONTAINER" mkdir -p "$CLICKHOUSE_BACKUP_DIR" 2>/dev/null || true

# Copy the extracted backup to the container
docker cp "$TEMP_EXTRACT_DIR/" "$CLICKHOUSE_CONTAINER:$CLICKHOUSE_BACKUP_DIR"

echo -e "${YELLOW}Restoring ClickHouse backup...${NC}"
# The backup structure is typically: backups/{backup_name}/...
# We need to find and restore it
BACKUP_NAME=$(ls "$TEMP_EXTRACT_DIR" | head -1)

if [ -z "$BACKUP_NAME" ]; then
    echo -e "${RED}✗ Could not find backup in extracted archive${NC}"
    rm -rf "$TEMP_EXTRACT_DIR"
    exit 1
fi

echo -e "${YELLOW}Restore command for backup: $BACKUP_NAME${NC}"

# Execute the restore using clickhouse-client
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query \
    "RESTORE DATABASE otel FROM Disk('backups', '$BACKUP_NAME') ASYNC" 2>&1 || true

# Wait for restore to complete
echo -e "${YELLOW}Waiting for ClickHouse restore to complete...${NC}"
for i in {1..60}; do
    RESTORE_STATUS=$(docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query \
        "SELECT status FROM system.backup_actions WHERE action = 'RESTORE' ORDER BY start_time DESC LIMIT 1 FORMAT CSV" 2>/dev/null || echo "")
    
    if [ -z "$RESTORE_STATUS" ]; then
        echo -e "${YELLOW}Status check: waiting... ($i/60)${NC}"
        sleep 5
    else
        echo -e "${YELLOW}Status: $RESTORE_STATUS${NC}"
        if [ "$RESTORE_STATUS" = "COMPLETED" ]; then
            echo -e "${GREEN}✓ ClickHouse restore completed${NC}"
            break
        fi
    fi
done

# Verify tables exist
echo -e "${YELLOW}Verifying ClickHouse tables...${NC}"
docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query "SHOW TABLES FROM otel FORMAT TSV" 2>/dev/null || true

# Cleanup
echo -e "${YELLOW}Cleaning up temporary files...${NC}"
rm -rf "$TEMP_EXTRACT_DIR"

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}✓ Seed data restoration complete!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Verify data in MySQL: docker exec -it pulse-mysql mysql -uroot -p\$MYSQL_ROOT_PASSWORD pulse_db"
echo "  2. Verify data in ClickHouse: docker exec -it pulse-clickhouse clickhouse-client"
echo "  3. Restart services if needed: cd deploy && ./scripts/start.sh"
echo ""
