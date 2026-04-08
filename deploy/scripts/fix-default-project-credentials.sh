#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Fix ClickHouse Credentials for default-project${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Check if containers are running
MYSQL_CONTAINER="pulse-mysql"
CLICKHOUSE_CONTAINER="pulse-clickhouse"

if ! docker ps | grep -q "$MYSQL_CONTAINER"; then
    echo -e "${RED}✗ MySQL container ($MYSQL_CONTAINER) is not running${NC}"
    exit 1
fi

if ! docker ps | grep -q "$CLICKHOUSE_CONTAINER"; then
    echo -e "${RED}✗ ClickHouse container ($CLICKHOUSE_CONTAINER) is not running${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All containers running${NC}"
echo ""

# Get MySQL credentials from .env
if [ -f "$(dirname "$0")/../.env" ]; then
    source "$(dirname "$0")/../.env"
else
    echo -e "${RED}✗ .env file not found${NC}"
    exit 1
fi

MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
MYSQL_DATABASE="${MYSQL_DATABASE:-pulse_db}"
PROJECT_ID="default-project"

# Check if credentials already exist
echo -e "${BLUE}Checking for existing credentials...${NC}"
EXISTING_COUNT=$(docker exec pulse-mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -N -e \
    "SELECT COUNT(*) FROM clickhouse_project_credentials WHERE project_id = '$PROJECT_ID';" 2>/dev/null || echo "0")

if [ "$EXISTING_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}⚠ Credentials already exist for $PROJECT_ID${NC}"
    echo -e "${YELLOW}Skipping creation...${NC}"
    exit 0
fi

echo -e "${YELLOW}No existing credentials found - creating new ones${NC}"
echo ""

# Generate ClickHouse username and password (mimic the ClickhouseProjectService logic)
# Username: project_default_project (project_ID with - converted to _)
CLICKHOUSE_USERNAME="project_default_project"

# Generate a 32-byte random password and encode as base64 (without padding)
# Using openssl for cross-platform compatibility
RANDOM_BYTES=$(openssl rand -base64 32 | tr -d '\n')
# Remove padding if exists
CLICKHOUSE_PASSWORD="${RANDOM_BYTES%=*}"

echo -e "${BLUE}Generated Credentials:${NC}"
echo "  Username: $CLICKHOUSE_USERNAME"
echo "  Password: (hidden)"
echo ""

# Create ClickHouse user in the container
echo -e "${YELLOW}Creating ClickHouse user...${NC}"
ONCLUSTER=""
CREATE_USER_SQL="CREATE USER IF NOT EXISTS $CLICKHOUSE_USERNAME IDENTIFIED WITH sha256_password BY '$CLICKHOUSE_PASSWORD'"

docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query "$CREATE_USER_SQL" 2>&1 || {
    echo -e "${RED}✗ Failed to create ClickHouse user${NC}"
    exit 1
}

echo -e "${GREEN}✓ ClickHouse user created${NC}"
echo ""

# Create row policy
echo -e "${YELLOW}Creating row policy...${NC}"
POLICY_NAME="policy_default_project"
CREATE_POLICY_SQL="CREATE ROW POLICY IF NOT EXISTS $POLICY_NAME ON otel.* AS PERMISSIVE FOR SELECT USING ProjectId = '$PROJECT_ID' TO $CLICKHOUSE_USERNAME"

docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query "$CREATE_POLICY_SQL" 2>&1 || {
    echo -e "${RED}✗ Failed to create row policy${NC}"
    exit 1
}

echo -e "${GREEN}✓ Row policy created${NC}"
echo ""

# Grant SELECT permissions
echo -e "${YELLOW}Granting SELECT permissions...${NC}"
GRANT_SELECT_SQL="GRANT SELECT ON otel.* TO $CLICKHOUSE_USERNAME"

docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query "$GRANT_SELECT_SQL" 2>&1 || {
    echo -e "${RED}✗ Failed to grant SELECT permissions${NC}"
    exit 1
}

echo -e "${GREEN}✓ SELECT permissions granted${NC}"
echo ""

# Grant INSERT permissions on root_cause_cache
echo -e "${YELLOW}Granting INSERT permissions on root_cause_cache...${NC}"
GRANT_INSERT_SQL="GRANT INSERT ON otel.root_cause_cache TO $CLICKHOUSE_USERNAME"

docker exec "$CLICKHOUSE_CONTAINER" clickhouse-client --query "$GRANT_INSERT_SQL" 2>&1 || {
    echo -e "${YELLOW}⚠ Warning: Failed to grant INSERT permissions (may not be needed)${NC}"
}

echo ""

# Now we need to encrypt the password and save to MySQL
# We'll use a Java utility or create encrypted values directly
echo -e "${BLUE}Saving credentials to MySQL...${NC}"

# For now, we'll restart the application to trigger dev mode init which should do the encryption
# But first, let's check if we can manually insert encrypted data
# The issue is we need AES-GCM encryption which is done in Java

# Alternative: Restart the application which will trigger DevModeInitService
echo -e "${YELLOW}Restarting pulse-server to trigger dev mode initialization...${NC}"

docker-compose -f "$(dirname "$0")/../docker-compose.yml" restart pulse-server

echo -e "${YELLOW}Waiting for pulse-server to be healthy...${NC}"
RETRIES=0
MAX_RETRIES=30
while [ $RETRIES -lt $MAX_RETRIES ]; do
    if curl -sf http://localhost:8080/healthcheck > /dev/null 2>&1; then
        echo -e "${GREEN}✓ pulse-server is healthy${NC}"
        break
    fi
    RETRIES=$((RETRIES + 1))
    sleep 2
done

if [ $RETRIES -eq $MAX_RETRIES ]; then
    echo -e "${YELLOW}⚠ pulse-server took longer than expected to start${NC}"
fi

echo ""
echo -e "${YELLOW}Verifying credentials were created...${NC}"
sleep 3

NEW_COUNT=$(docker exec pulse-mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -N -e \
    "SELECT COUNT(*) FROM clickhouse_project_credentials WHERE project_id = '$PROJECT_ID';" 2>/dev/null || echo "0")

if [ "$NEW_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ Credentials successfully created for $PROJECT_ID${NC}"
    docker exec pulse-mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -N -e \
        "SELECT project_id, clickhouse_username, is_active FROM clickhouse_project_credentials WHERE project_id = '$PROJECT_ID';" 2>/dev/null
else
    echo -e "${RED}✗ Credentials were not created${NC}"
    echo -e "${YELLOW}Please check the server logs:${NC}"
    echo "  docker logs pulse-server | grep -i 'devmode\|clickhouse'"
    exit 1
fi

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}✓ ClickHouse credentials fixed!${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Verify the API now works: curl http://localhost:8080/v1/interactions/performance-metric/distribution"
echo "  2. Check the logs if issues persist: docker logs pulse-server"
echo ""
