#!/bin/bash

# =============================================================================
# Multi-tenancy Row-Level Security (RLS) Test Script
# =============================================================================
# This script tests the complete multi-tenancy flow:
# 1. Creates two tenants via API
# 2. Creates ClickHouse credentials for each tenant
# 3. Creates ClickHouse users with row-level policies
# 4. Inserts test data for each tenant
# 5. Verifies data isolation - each tenant only sees their own data
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
CLICKHOUSE_HOST="${CLICKHOUSE_HOST:-localhost}"
CLICKHOUSE_PORT="${CLICKHOUSE_PORT:-8123}"
CLICKHOUSE_ADMIN_USER="${CLICKHOUSE_ADMIN_USER:-pulse_user}"
CLICKHOUSE_ADMIN_PASSWORD="${CLICKHOUSE_ADMIN_PASSWORD:-pulse_password}"
DATABASE="${CLICKHOUSE_DATABASE:-otel}"

# Test tenants
TENANT_1_ID="tenant_alpha"
TENANT_1_NAME="Alpha Corp"
TENANT_1_PASSWORD="AlphaSecure123!"

TENANT_2_ID="tenant_beta"
TENANT_2_NAME="Beta Inc"
TENANT_2_PASSWORD="BetaSecure456!"

USER_EMAIL="admin@pulse.io"

# =============================================================================
# Helper Functions
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo ""
    echo -e "${CYAN}=============================================================================${NC}"
    echo -e "${CYAN} $1${NC}"
    echo -e "${CYAN}=============================================================================${NC}"
}

# Execute ClickHouse query with admin credentials
clickhouse_admin_query() {
    local query="$1"
    if [ -n "$CLICKHOUSE_ADMIN_PASSWORD" ]; then
        curl -s "${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/?user=${CLICKHOUSE_ADMIN_USER}&password=${CLICKHOUSE_ADMIN_PASSWORD}" \
            --data-binary "$query"
    else
        curl -s "${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/?user=${CLICKHOUSE_ADMIN_USER}" \
            --data-binary "$query"
    fi
}

# Execute ClickHouse query with tenant credentials
clickhouse_tenant_query() {
    local user="$1"
    local password="$2"
    local query="$3"
    curl -s "${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/?user=${user}&password=${password}" \
        --data-binary "$query"
}

# API call helper
api_call() {
    local method="$1"
    local endpoint="$2"
    local data="$3"
    local extra_headers="$4"
    
    if [ "$method" == "GET" ]; then
        curl -s -X GET "${API_BASE_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            $extra_headers
    else
        curl -s -X "$method" "${API_BASE_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -H "user-email: ${USER_EMAIL}" \
            $extra_headers \
            -d "$data"
    fi
}

# =============================================================================
# Step 0: Verify ClickHouse connectivity
# =============================================================================

verify_clickhouse_connection() {
    log_section "Step 0: Verify ClickHouse Connectivity"
    
    log_info "Testing connection to ClickHouse at ${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}..."
    
    local result=$(clickhouse_admin_query "SELECT 1")
    if [ "$result" == "1" ]; then
        log_success "ClickHouse connection successful"
    else
        log_error "Failed to connect to ClickHouse. Response: $result"
        log_info "Make sure ClickHouse is running and credentials are correct:"
        log_info "  CLICKHOUSE_HOST=$CLICKHOUSE_HOST"
        log_info "  CLICKHOUSE_PORT=$CLICKHOUSE_PORT"
        log_info "  CLICKHOUSE_ADMIN_USER=$CLICKHOUSE_ADMIN_USER"
        log_info "  CLICKHOUSE_ADMIN_PASSWORD=****"
        exit 1
    fi
    
    # Check if database exists
    log_info "Checking if database '${DATABASE}' exists..."
    local db_exists=$(clickhouse_admin_query "SELECT count() FROM system.databases WHERE name = '${DATABASE}'")
    if [ "$db_exists" == "1" ]; then
        log_success "Database '${DATABASE}' exists"
    else
        log_warn "Database '${DATABASE}' does not exist. Creating it..."
        clickhouse_admin_query "CREATE DATABASE IF NOT EXISTS ${DATABASE}"
        log_success "Database '${DATABASE}' created"
    fi
}

# =============================================================================
# Step 1: Cleanup Previous Test Data
# =============================================================================

cleanup() {
    log_section "Step 1: Cleanup Previous Test Data"
    
    log_info "Dropping existing tenant users if they exist..."
    clickhouse_admin_query "DROP USER IF EXISTS '${TENANT_1_ID}';" 2>/dev/null || true
    clickhouse_admin_query "DROP USER IF EXISTS '${TENANT_2_ID}';" 2>/dev/null || true
    
    log_info "Dropping existing row policies if they exist..."
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_1_ID}_traces_policy ON ${DATABASE}.otel_traces;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_1_ID}_logs_policy ON ${DATABASE}.otel_logs;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_1_ID}_metrics_policy ON ${DATABASE}.otel_metrics_gauge;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_1_ID}_exceptions_policy ON ${DATABASE}.stack_trace_events;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_2_ID}_traces_policy ON ${DATABASE}.otel_traces;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_2_ID}_logs_policy ON ${DATABASE}.otel_logs;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_2_ID}_metrics_policy ON ${DATABASE}.otel_metrics_gauge;" 2>/dev/null || true
    clickhouse_admin_query "DROP ROW POLICY IF EXISTS ${TENANT_2_ID}_exceptions_policy ON ${DATABASE}.stack_trace_events;" 2>/dev/null || true
    
    log_info "Cleaning up test data..."
    clickhouse_admin_query "ALTER TABLE ${DATABASE}.otel_traces DELETE WHERE TenantId IN ('${TENANT_1_ID}', '${TENANT_2_ID}');" 2>/dev/null || true
    clickhouse_admin_query "ALTER TABLE ${DATABASE}.otel_logs DELETE WHERE TenantId IN ('${TENANT_1_ID}', '${TENANT_2_ID}');" 2>/dev/null || true
    clickhouse_admin_query "ALTER TABLE ${DATABASE}.otel_metrics_gauge DELETE WHERE TenantId IN ('${TENANT_1_ID}', '${TENANT_2_ID}');" 2>/dev/null || true
    
    # Wait for mutations to complete
    sleep 2
    
    log_success "Cleanup completed"
}

# =============================================================================
# Step 2: Create Tenants via API
# =============================================================================

create_tenants() {
    log_section "Step 2: Create Tenants via API"
    
    # Create Tenant 1
    log_info "Creating tenant: ${TENANT_1_ID}..."
    local response=$(api_call "POST" "/v1/tenants" "{
        \"tenantId\": \"${TENANT_1_ID}\",
        \"name\": \"${TENANT_1_NAME}\",
        \"description\": \"Test tenant Alpha for RLS testing\",
        \"gcpTenantId\": \"gcp-${TENANT_1_ID}\",
        \"domainName\": \"alpha.example.com\"
    }")
    echo "  Response: $response"
    
    if echo "$response" | grep -q "error\|Error\|ERROR"; then
        log_warn "Tenant ${TENANT_1_ID} may already exist or error occurred"
    else
        log_success "Tenant ${TENANT_1_ID} created"
    fi
    
    # Create Tenant 2
    log_info "Creating tenant: ${TENANT_2_ID}..."
    response=$(api_call "POST" "/v1/tenants" "{
        \"tenantId\": \"${TENANT_2_ID}\",
        \"name\": \"${TENANT_2_NAME}\",
        \"description\": \"Test tenant Beta for RLS testing\",
        \"gcpTenantId\": \"gcp-${TENANT_2_ID}\",
        \"domainName\": \"beta.example.com\"
    }")
    echo "  Response: $response"
    
    if echo "$response" | grep -q "error\|Error\|ERROR"; then
        log_warn "Tenant ${TENANT_2_ID} may already exist or error occurred"
    else
        log_success "Tenant ${TENANT_2_ID} created"
    fi
}

# =============================================================================
# Step 3: Create ClickHouse Credentials via API
# =============================================================================

create_credentials() {
    log_section "Step 3: Create ClickHouse Credentials via API"
    
    # Create credentials for Tenant 1
    log_info "Creating ClickHouse credentials for tenant: ${TENANT_1_ID}..."
    local response=$(api_call "POST" "/v1/tenants/${TENANT_1_ID}/credentials" "{
        \"clickhousePassword\": \"${TENANT_1_PASSWORD}\"
    }")
    echo "  Response: $response"
    log_success "Credentials created for ${TENANT_1_ID}"
    
    # Create credentials for Tenant 2
    log_info "Creating ClickHouse credentials for tenant: ${TENANT_2_ID}..."
    response=$(api_call "POST" "/v1/tenants/${TENANT_2_ID}/credentials" "{
        \"clickhousePassword\": \"${TENANT_2_PASSWORD}\"
    }")
    echo "  Response: $response"
    log_success "Credentials created for ${TENANT_2_ID}"
}

# =============================================================================
# Step 4: Create ClickHouse Users with Row-Level Policies
# =============================================================================

create_clickhouse_users_and_policies() {
    log_section "Step 4: Create ClickHouse Users and Row-Level Policies"
    
    # Create user for Tenant 1
    log_info "Creating ClickHouse user for ${TENANT_1_ID}..."
    clickhouse_admin_query "CREATE USER IF NOT EXISTS '${TENANT_1_ID}' IDENTIFIED BY '${TENANT_1_PASSWORD}';"
    log_success "User ${TENANT_1_ID} created"
    
    # Create user for Tenant 2
    log_info "Creating ClickHouse user for ${TENANT_2_ID}..."
    clickhouse_admin_query "CREATE USER IF NOT EXISTS '${TENANT_2_ID}' IDENTIFIED BY '${TENANT_2_PASSWORD}';"
    log_success "User ${TENANT_2_ID} created"
    
    # Grant SELECT permissions
    log_info "Granting SELECT permissions..."
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.otel_traces TO '${TENANT_1_ID}';"
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.otel_logs TO '${TENANT_1_ID}';"
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.otel_metrics_gauge TO '${TENANT_1_ID}';"
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.stack_trace_events TO '${TENANT_1_ID}';" 2>/dev/null || true
    
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.otel_traces TO '${TENANT_2_ID}';"
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.otel_logs TO '${TENANT_2_ID}';"
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.otel_metrics_gauge TO '${TENANT_2_ID}';"
    clickhouse_admin_query "GRANT SELECT ON ${DATABASE}.stack_trace_events TO '${TENANT_2_ID}';" 2>/dev/null || true
    log_success "Permissions granted"
    
    # Create Row-Level Policies for Tenant 1
    log_info "Creating row-level policies for ${TENANT_1_ID}..."
    echo ""
    echo -e "  ${CYAN}Policy: TenantId = '${TENANT_1_ID}'${NC}"
    echo ""
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_1_ID}_traces_policy ON ${DATABASE}.otel_traces FOR SELECT USING TenantId = '${TENANT_1_ID}' TO '${TENANT_1_ID}';"
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_1_ID}_logs_policy ON ${DATABASE}.otel_logs FOR SELECT USING TenantId = '${TENANT_1_ID}' TO '${TENANT_1_ID}';"
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_1_ID}_metrics_policy ON ${DATABASE}.otel_metrics_gauge FOR SELECT USING TenantId = '${TENANT_1_ID}' TO '${TENANT_1_ID}';"
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_1_ID}_exceptions_policy ON ${DATABASE}.stack_trace_events FOR SELECT USING TenantId = '${TENANT_1_ID}' TO '${TENANT_1_ID}';" 2>/dev/null || true
    log_success "Row policies created for ${TENANT_1_ID}"
    
    # Create Row-Level Policies for Tenant 2
    log_info "Creating row-level policies for ${TENANT_2_ID}..."
    echo ""
    echo -e "  ${CYAN}Policy: TenantId = '${TENANT_2_ID}'${NC}"
    echo ""
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_2_ID}_traces_policy ON ${DATABASE}.otel_traces FOR SELECT USING TenantId = '${TENANT_2_ID}' TO '${TENANT_2_ID}';"
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_2_ID}_logs_policy ON ${DATABASE}.otel_logs FOR SELECT USING TenantId = '${TENANT_2_ID}' TO '${TENANT_2_ID}';"
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_2_ID}_metrics_policy ON ${DATABASE}.otel_metrics_gauge FOR SELECT USING TenantId = '${TENANT_2_ID}' TO '${TENANT_2_ID}';"
    clickhouse_admin_query "CREATE ROW POLICY IF NOT EXISTS ${TENANT_2_ID}_exceptions_policy ON ${DATABASE}.stack_trace_events FOR SELECT USING TenantId = '${TENANT_2_ID}' TO '${TENANT_2_ID}';" 2>/dev/null || true
    log_success "Row policies created for ${TENANT_2_ID}"
    
    # Verify policies created
    echo ""
    log_info "Verifying row policies..."
    clickhouse_admin_query "SELECT name, select_filter FROM system.row_policies WHERE name LIKE '%tenant_%' FORMAT Pretty"
}

# =============================================================================
# Step 5: Insert Test Data for Each Tenant
# =============================================================================

insert_test_data() {
    log_section "Step 5: Insert Test Data for Each Tenant"
    
    local timestamp=$(date -u +"%Y-%m-%d %H:%M:%S")
    
    # Note: TenantId is a MATERIALIZED column derived from ResourceAttributes['tenant.id']
    # We do NOT insert TenantId directly - it's auto-populated from ResourceAttributes
    
    # Insert traces for Tenant 1
    log_info "Inserting test traces for ${TENANT_1_ID}..."
    clickhouse_admin_query "INSERT INTO ${DATABASE}.otel_traces (
        Timestamp, TraceId, SpanId, ParentSpanId, SpanName, SpanKind, ServiceName,
        ResourceAttributes, SpanAttributes, Duration, StatusCode
    ) VALUES
        (now64(9), 'trace-alpha-001', toFixedString('span-a1', 16), toFixedString('', 16), 'alpha-operation-1', 'SERVER', 'alpha-service',
         {'tenant.id': '${TENANT_1_ID}', 'app.build_name': '1.0.0'}, {'pulse.type': 'http'}, 150000000, 'OK'),
        (now64(9), 'trace-alpha-002', toFixedString('span-a2', 16), toFixedString('', 16), 'alpha-operation-2', 'CLIENT', 'alpha-service',
         {'tenant.id': '${TENANT_1_ID}', 'app.build_name': '1.0.0'}, {'pulse.type': 'db'}, 250000000, 'OK'),
        (now64(9), 'trace-alpha-003', toFixedString('span-a3', 16), toFixedString('', 16), 'alpha-operation-3', 'SERVER', 'alpha-service',
         {'tenant.id': '${TENANT_1_ID}', 'app.build_name': '1.0.0'}, {'pulse.type': 'http'}, 100000000, 'ERROR');"
    log_success "Inserted 3 traces for ${TENANT_1_ID}"
    
    # Insert traces for Tenant 2
    log_info "Inserting test traces for ${TENANT_2_ID}..."
    clickhouse_admin_query "INSERT INTO ${DATABASE}.otel_traces (
        Timestamp, TraceId, SpanId, ParentSpanId, SpanName, SpanKind, ServiceName,
        ResourceAttributes, SpanAttributes, Duration, StatusCode
    ) VALUES
        (now64(9), 'trace-beta-001', toFixedString('span-b1', 16), toFixedString('', 16), 'beta-operation-1', 'SERVER', 'beta-service',
         {'tenant.id': '${TENANT_2_ID}', 'app.build_name': '2.0.0'}, {'pulse.type': 'http'}, 200000000, 'OK'),
        (now64(9), 'trace-beta-002', toFixedString('span-b2', 16), toFixedString('', 16), 'beta-operation-2', 'CLIENT', 'beta-service',
         {'tenant.id': '${TENANT_2_ID}', 'app.build_name': '2.0.0'}, {'pulse.type': 'grpc'}, 300000000, 'OK');"
    log_success "Inserted 2 traces for ${TENANT_2_ID}"
    
    # Insert logs for Tenant 1
    log_info "Inserting test logs for ${TENANT_1_ID}..."
    clickhouse_admin_query "INSERT INTO ${DATABASE}.otel_logs (
        Timestamp, TraceId, SpanId, SeverityText, SeverityNumber, ServiceName, Body,
        ResourceAttributes, LogAttributes
    ) VALUES
        (now64(9), 'trace-alpha-001', toFixedString('span-a1', 16), 'INFO', 9, 'alpha-service', 'Alpha log message 1',
         {'tenant.id': '${TENANT_1_ID}'}, {'pulse.type': 'app_log'}),
        (now64(9), 'trace-alpha-002', toFixedString('span-a2', 16), 'ERROR', 17, 'alpha-service', 'Alpha error log',
         {'tenant.id': '${TENANT_1_ID}'}, {'pulse.type': 'app_log'});"
    log_success "Inserted 2 logs for ${TENANT_1_ID}"
    
    # Insert logs for Tenant 2
    log_info "Inserting test logs for ${TENANT_2_ID}..."
    clickhouse_admin_query "INSERT INTO ${DATABASE}.otel_logs (
        Timestamp, TraceId, SpanId, SeverityText, SeverityNumber, ServiceName, Body,
        ResourceAttributes, LogAttributes
    ) VALUES
        (now64(9), 'trace-beta-001', toFixedString('span-b1', 16), 'INFO', 9, 'beta-service', 'Beta log message 1',
         {'tenant.id': '${TENANT_2_ID}'}, {'pulse.type': 'app_log'}),
        (now64(9), 'trace-beta-002', toFixedString('span-b2', 16), 'WARN', 13, 'beta-service', 'Beta warning log',
         {'tenant.id': '${TENANT_2_ID}'}, {'pulse.type': 'app_log'}),
        (now64(9), 'trace-beta-003', toFixedString('span-b3', 16), 'DEBUG', 5, 'beta-service', 'Beta debug log',
         {'tenant.id': '${TENANT_2_ID}'}, {'pulse.type': 'app_log'});"
    log_success "Inserted 3 logs for ${TENANT_2_ID}"
    
    # Wait for data to be visible
    sleep 2
    
    # Verify data was inserted (admin view)
    log_info "Verifying data insertion (admin view)..."
    local alpha_traces=$(clickhouse_admin_query "SELECT count() FROM ${DATABASE}.otel_traces WHERE TenantId = '${TENANT_1_ID}'")
    local beta_traces=$(clickhouse_admin_query "SELECT count() FROM ${DATABASE}.otel_traces WHERE TenantId = '${TENANT_2_ID}'")
    local alpha_logs=$(clickhouse_admin_query "SELECT count() FROM ${DATABASE}.otel_logs WHERE TenantId = '${TENANT_1_ID}'")
    local beta_logs=$(clickhouse_admin_query "SELECT count() FROM ${DATABASE}.otel_logs WHERE TenantId = '${TENANT_2_ID}'")
    
    echo ""
    echo -e "  ${CYAN}Admin View (all data):${NC}"
    echo "    ${TENANT_1_ID}: ${alpha_traces} traces, ${alpha_logs} logs"
    echo "    ${TENANT_2_ID}: ${beta_traces} traces, ${beta_logs} logs"
    echo ""
    log_success "Test data inserted successfully"
}

# =============================================================================
# Step 6: Test Data Isolation with Direct ClickHouse Queries
# =============================================================================

test_direct_clickhouse_isolation() {
    log_section "Step 6: Test Data Isolation (Direct ClickHouse Queries)"
    
    local failed=0
    
    echo ""
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  TESTING ROW-LEVEL SECURITY ISOLATION${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    
    # Test Tenant 1 can only see their data
    log_info "Testing ${TENANT_1_ID} data isolation..."
    
    local alpha_sees_traces=$(clickhouse_tenant_query "${TENANT_1_ID}" "${TENANT_1_PASSWORD}" \
        "SELECT count() FROM ${DATABASE}.otel_traces")
    local alpha_sees_beta=$(clickhouse_tenant_query "${TENANT_1_ID}" "${TENANT_1_PASSWORD}" \
        "SELECT count() FROM ${DATABASE}.otel_traces WHERE TenantId = '${TENANT_2_ID}'")
    
    echo ""
    echo -e "  ${CYAN}${TENANT_1_ID} query results:${NC}"
    echo "    Total traces visible: ${alpha_sees_traces} (expected: 3)"
    echo "    ${TENANT_2_ID}'s traces visible: ${alpha_sees_beta} (expected: 0)"
    
    # Trim whitespace from results
    alpha_sees_traces=$(echo "$alpha_sees_traces" | tr -d '[:space:]')
    alpha_sees_beta=$(echo "$alpha_sees_beta" | tr -d '[:space:]')
    
    if [ "$alpha_sees_traces" == "3" ] && [ "$alpha_sees_beta" == "0" ]; then
        log_success "${TENANT_1_ID} correctly isolated - sees only own 3 traces ✓"
    else
        log_error "${TENANT_1_ID} isolation FAILED! Sees ${alpha_sees_traces} traces (expected 3), ${alpha_sees_beta} from ${TENANT_2_ID} (expected 0)"
        failed=1
    fi
    
    # Test Tenant 2 can only see their data
    log_info "Testing ${TENANT_2_ID} data isolation..."
    
    local beta_sees_traces=$(clickhouse_tenant_query "${TENANT_2_ID}" "${TENANT_2_PASSWORD}" \
        "SELECT count() FROM ${DATABASE}.otel_traces")
    local beta_sees_alpha=$(clickhouse_tenant_query "${TENANT_2_ID}" "${TENANT_2_PASSWORD}" \
        "SELECT count() FROM ${DATABASE}.otel_traces WHERE TenantId = '${TENANT_1_ID}'")
    
    echo ""
    echo -e "  ${CYAN}${TENANT_2_ID} query results:${NC}"
    echo "    Total traces visible: ${beta_sees_traces} (expected: 2)"
    echo "    ${TENANT_1_ID}'s traces visible: ${beta_sees_alpha} (expected: 0)"
    
    # Trim whitespace from results
    beta_sees_traces=$(echo "$beta_sees_traces" | tr -d '[:space:]')
    beta_sees_alpha=$(echo "$beta_sees_alpha" | tr -d '[:space:]')
    
    if [ "$beta_sees_traces" == "2" ] && [ "$beta_sees_alpha" == "0" ]; then
        log_success "${TENANT_2_ID} correctly isolated - sees only own 2 traces ✓"
    else
        log_error "${TENANT_2_ID} isolation FAILED! Sees ${beta_sees_traces} traces (expected 2), ${beta_sees_alpha} from ${TENANT_1_ID} (expected 0)"
        failed=1
    fi
    
    # Test logs isolation
    log_info "Testing logs isolation..."
    
    local alpha_logs=$(clickhouse_tenant_query "${TENANT_1_ID}" "${TENANT_1_PASSWORD}" \
        "SELECT count() FROM ${DATABASE}.otel_logs")
    local beta_logs=$(clickhouse_tenant_query "${TENANT_2_ID}" "${TENANT_2_PASSWORD}" \
        "SELECT count() FROM ${DATABASE}.otel_logs")
    
    # Trim whitespace
    alpha_logs=$(echo "$alpha_logs" | tr -d '[:space:]')
    beta_logs=$(echo "$beta_logs" | tr -d '[:space:]')
    
    echo ""
    echo -e "  ${CYAN}Logs isolation:${NC}"
    echo "    ${TENANT_1_ID} sees ${alpha_logs} logs (expected: 2)"
    echo "    ${TENANT_2_ID} sees ${beta_logs} logs (expected: 3)"
    
    if [ "$alpha_logs" == "2" ] && [ "$beta_logs" == "3" ]; then
        log_success "Logs isolation working correctly ✓"
    else
        log_error "Logs isolation FAILED!"
        failed=1
    fi
    
    echo ""
    return $failed
}

# =============================================================================
# Step 7: Test via Performance API
# =============================================================================

test_api_isolation() {
    log_section "Step 7: Test Data Isolation via Performance API"
    
    local failed=0
    
    echo ""
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  TESTING API-LEVEL TENANT ISOLATION${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    
    # Test API call for Tenant 1 using INTERACTION_SUCCESS_COUNT function
    log_info "Testing API call for ${TENANT_1_ID}..."
    local response=$(curl -s -X POST "${API_BASE_URL}/v1/interactions/performance-metric/distribution" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: ${TENANT_1_ID}" \
        -d '{
            "dataType": "TRACES",
            "timeRange": {
                "start": "2020-01-01T00:00:00Z",
                "end": "2030-12-31T23:59:59Z"
            },
            "select": [
                {"function": "INTERACTION_SUCCESS_COUNT", "alias": "successCount"}
            ],
            "filters": [],
            "limit": 100
        }')
    
    echo ""
    echo -e "  ${CYAN}Response for ${TENANT_1_ID}:${NC}"
    echo "$response" | head -c 1000
    echo ""
    
    # Test API call for Tenant 2
    log_info "Testing API call for ${TENANT_2_ID}..."
    response=$(curl -s -X POST "${API_BASE_URL}/v1/interactions/performance-metric/distribution" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: ${TENANT_2_ID}" \
        -d '{
            "dataType": "TRACES",
            "timeRange": {
                "start": "2020-01-01T00:00:00Z",
                "end": "2030-12-31T23:59:59Z"
            },
            "select": [
                {"function": "INTERACTION_SUCCESS_COUNT", "alias": "successCount"}
            ],
            "filters": [],
            "limit": 100
        }')
    
    echo ""
    echo -e "  ${CYAN}Response for ${TENANT_2_ID}:${NC}"
    echo "$response" | head -c 1000
    echo ""
    
    # Test without tenant header (should fail)
    log_info "Testing API call without X-Tenant-Id header (should fail)..."
    response=$(curl -s -X POST "${API_BASE_URL}/v1/interactions/performance-metric/distribution" \
        -H "Content-Type: application/json" \
        -d '{
            "dataType": "TRACES",
            "timeRange": {
                "start": "2020-01-01T00:00:00Z",
                "end": "2030-12-31T23:59:59Z"
            },
            "select": [
                {"function": "INTERACTION_SUCCESS_COUNT", "alias": "successCount"}
            ],
            "filters": [],
            "limit": 100
        }')
    
    echo ""
    echo -e "  ${CYAN}Response without tenant header:${NC}"
    echo "$response" | head -c 500
    echo ""
    
    if echo "$response" | grep -q "required\|Required\|missing\|Missing\|error\|Error"; then
        log_success "API correctly requires X-Tenant-Id header ✓"
    else
        log_warn "API may not be validating tenant header properly"
    fi
    
    log_info "API isolation test completed - verify counts in responses above"
    
    return $failed
}

# =============================================================================
# Step 8: Show Summary
# =============================================================================

show_summary() {
    log_section "Test Summary"
    
    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                           TEST CONFIGURATION                              ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "Tenants Created:"
    echo "  - ${TENANT_1_ID} (${TENANT_1_NAME})"
    echo "  - ${TENANT_2_ID} (${TENANT_2_NAME})"
    echo ""
    echo "ClickHouse Users:"
    echo "  - User: ${TENANT_1_ID}, Password: ${TENANT_1_PASSWORD}"
    echo "  - User: ${TENANT_2_ID}, Password: ${TENANT_2_PASSWORD}"
    echo ""
    echo "Row-Level Policies (on TenantId column):"
    echo "  - ${TENANT_1_ID}_traces_policy: TenantId = '${TENANT_1_ID}'"
    echo "  - ${TENANT_1_ID}_logs_policy: TenantId = '${TENANT_1_ID}'"
    echo "  - ${TENANT_1_ID}_metrics_policy: TenantId = '${TENANT_1_ID}'"
    echo "  - ${TENANT_2_ID}_traces_policy: TenantId = '${TENANT_2_ID}'"
    echo "  - ${TENANT_2_ID}_logs_policy: TenantId = '${TENANT_2_ID}'"
    echo "  - ${TENANT_2_ID}_metrics_policy: TenantId = '${TENANT_2_ID}'"
    echo ""
    echo "Test Data:"
    echo "  - ${TENANT_1_ID}: 3 traces, 2 logs"
    echo "  - ${TENANT_2_ID}: 2 traces, 3 logs"
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  Manual Verification Commands${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  # As ${TENANT_1_ID} (should see 3 traces):"
    echo "  curl '${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/?user=${TENANT_1_ID}&password=${TENANT_1_PASSWORD}' \\"
    echo "    --data-binary 'SELECT count() FROM ${DATABASE}.otel_traces'"
    echo ""
    echo "  # As ${TENANT_2_ID} (should see 2 traces):"
    echo "  curl '${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/?user=${TENANT_2_ID}&password=${TENANT_2_PASSWORD}' \\"
    echo "    --data-binary 'SELECT count() FROM ${DATABASE}.otel_traces'"
    echo ""
    echo "  # Via Performance API (with tenant header):"
    echo "  curl -X POST '${API_BASE_URL}/v1/interactions/performance-metric/distribution' \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -H 'X-Tenant-Id: ${TENANT_1_ID}' \\"
    echo "    -d '{\"dataType\":\"TRACES\",\"timeRange\":{\"start\":\"2020-01-01T00:00:00Z\",\"end\":\"2030-12-31T23:59:59Z\"},\"select\":[{\"function\":\"INTERACTION_SUCCESS_COUNT\",\"alias\":\"count\"}],\"limit\":100}'"
    echo ""
}

# =============================================================================
# Main Execution
# =============================================================================

main() {
    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║          Multi-Tenancy Row-Level Security (RLS) Test Script              ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Parse arguments
    SKIP_CLEANUP=false
    SKIP_API=false
    SKIP_TENANT_CREATION=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-cleanup)
                SKIP_CLEANUP=true
                shift
                ;;
            --skip-api)
                SKIP_API=true
                shift
                ;;
            --skip-tenant-creation)
                SKIP_TENANT_CREATION=true
                shift
                ;;
            --help)
                echo "Usage: $0 [options]"
                echo ""
                echo "Options:"
                echo "  --skip-cleanup          Skip cleanup of previous test data"
                echo "  --skip-api              Skip API tests (only test direct ClickHouse)"
                echo "  --skip-tenant-creation  Skip tenant/credentials creation via API"
                echo "  --help                  Show this help message"
                echo ""
                echo "Environment Variables:"
                echo "  API_BASE_URL               API base URL (default: http://localhost:8080)"
                echo "  CLICKHOUSE_HOST            ClickHouse host (default: localhost)"
                echo "  CLICKHOUSE_PORT            ClickHouse HTTP port (default: 8123)"
                echo "  CLICKHOUSE_ADMIN_USER      ClickHouse admin user (default: pulse_user)"
                echo "  CLICKHOUSE_ADMIN_PASSWORD  ClickHouse admin password (default: pulse_password)"
                echo "  CLICKHOUSE_DATABASE        ClickHouse database (default: otel)"
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Run tests
    verify_clickhouse_connection
    
    if [ "$SKIP_CLEANUP" != "true" ]; then
        cleanup
    fi
    
    if [ "$SKIP_TENANT_CREATION" != "true" ]; then
        create_tenants
        create_credentials
    fi
    
    create_clickhouse_users_and_policies
    insert_test_data
    
    local direct_test_result=0
    test_direct_clickhouse_isolation || direct_test_result=$?
    
    if [ "$SKIP_API" != "true" ]; then
        test_api_isolation
    fi
    
    show_summary
    
    # Final result
    if [ $direct_test_result -eq 0 ]; then
        echo ""
        echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║                    ✓ ALL ISOLATION TESTS PASSED!                         ║${NC}"
        echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════════════════╝${NC}"
        echo ""
        exit 0
    else
        echo ""
        echo -e "${RED}╔═══════════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║                    ✗ SOME ISOLATION TESTS FAILED!                         ║${NC}"
        echo -e "${RED}╚═══════════════════════════════════════════════════════════════════════════╝${NC}"
        echo ""
        exit 1
    fi
}

main "$@"
