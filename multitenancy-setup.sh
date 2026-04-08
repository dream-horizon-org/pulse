#!/bin/bash

##############################################################################
# Multi-Tenancy Setup Script
# Automates: Encryption key generation, DB migrations, ClickHouse setup
# Usage: ./multitenancy-setup.sh [--quick] [--interactive]
##############################################################################

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="${SCRIPT_DIR}"
SERVER_DIR="${PROJECT_ROOT}/backend/server"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
QUICK_MODE=false
INTERACTIVE_MODE=true

##############################################################################
# Helper Functions
##############################################################################

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

confirm() {
    local prompt="$1"
    local response
    read -p "$(echo -e ${YELLOW}$prompt${NC})" response
    case "$response" in
        [yY][eE][sS]|[yY]) return 0 ;;
        *) return 1 ;;
    esac
}

##############################################################################
# Step 1: Generate Encryption Key
##############################################################################

step_generate_encryption_key() {
    log_info "================================"
    log_info "Step 1: Generate Encryption Key"
    log_info "================================"
    
    if [ -z "$ENCRYPTION_MASTER_KEY" ]; then
        log_info "Generating new ENCRYPTION_MASTER_KEY..."
        ENCRYPTION_MASTER_KEY=$(openssl rand -hex 32)
        log_success "Generated: $ENCRYPTION_MASTER_KEY"
    else
        log_warn "ENCRYPTION_MASTER_KEY already set: ${ENCRYPTION_MASTER_KEY:0:16}..."
        if ! confirm "Use existing key? (y/n) "; then
            ENCRYPTION_MASTER_KEY=$(openssl rand -hex 32)
            log_success "Generated new: $ENCRYPTION_MASTER_KEY"
        fi
    fi
    
    # Save to .env.local
    ENV_FILE="${PROJECT_ROOT}/.env.local"
    if [ -f "$ENV_FILE" ]; then
        # Update existing
        if grep -q "ENCRYPTION_MASTER_KEY" "$ENV_FILE"; then
            sed -i '' "s/ENCRYPTION_MASTER_KEY=.*/ENCRYPTION_MASTER_KEY=${ENCRYPTION_MASTER_KEY}/" "$ENV_FILE"
        else
            echo "export ENCRYPTION_MASTER_KEY=${ENCRYPTION_MASTER_KEY}" >> "$ENV_FILE"
        fi
    else
        # Create new
        cat > "$ENV_FILE" << EOF
# Multi-Tenancy Configuration
export ENCRYPTION_MASTER_KEY=${ENCRYPTION_MASTER_KEY}

# MySQL Configuration (update with your values)
export MYSQL_WRITER_HOST=localhost
export MYSQL_READER_HOST=localhost
export MYSQL_DATABASE=pulse
export MYSQL_USER=pulse_user
export MYSQL_PASSWORD=pulse_password
export MYSQL_WRITER_MAX_POOL_SIZE=10
export MYSQL_READER_MAX_POOL_SIZE=10

# ClickHouse Configuration (update with your values)
export CLICKHOUSE_HOST=localhost
export CLICKHOUSE_PORT=9000
export CLICKHOUSE_USERNAME=default
export CLICKHOUSE_PASSWORD=password
export CLICKHOUSE_R2DBC_URL=r2dbc:clickhouse://localhost:9000/default

# Multi-Tenancy Settings
export TENANT_POOL_MIN_SIZE=2
export TENANT_POOL_MAX_SIZE=5
export ADMIN_POOL_SIZE=10
export MULTITENANCY_ENABLED=true
EOF
        log_success "Created $ENV_FILE"
    fi
    
    log_success "Encryption key configured"
}

##############################################################################
# Step 2: Load Environment Variables
##############################################################################

step_load_env_vars() {
    log_info "=============================="
    log_info "Step 2: Load Environment Vars"
    log_info "=============================="
    
    if [ -f "$ENV_FILE" ]; then
        source "$ENV_FILE"
        log_success "Loaded environment variables from $ENV_FILE"
    else
        log_warn "No .env.local file found, using existing environment"
    fi
    
    # Verify critical variables
    if [ -z "$ENCRYPTION_MASTER_KEY" ]; then
        log_error "ENCRYPTION_MASTER_KEY not set!"
        return 1
    fi
    
    if [ -z "$MYSQL_DATABASE" ]; then
        log_warn "MYSQL_DATABASE not set, using 'pulse'"
        MYSQL_DATABASE="pulse"
    fi
    
    log_success "Environment variables verified"
}

##############################################################################
# Step 3: Run Database Migrations
##############################################################################

step_run_migrations() {
    log_info "============================="
    log_info "Step 3: Run DB Migrations"
    log_info "============================="
    
    if [ ! -d "$SERVER_DIR" ]; then
        log_error "Server directory not found: $SERVER_DIR"
        return 1
    fi
    
    MIGRATION_FILE="${SERVER_DIR}/src/main/resources/db/migration/V1_1__add_tenant_multitenancy.sql"
    
    if [ ! -f "$MIGRATION_FILE" ]; then
        log_error "Migration file not found: $MIGRATION_FILE"
        return 1
    fi
    
    log_info "Migration file found: $MIGRATION_FILE"
    
    if [ -z "$MYSQL_WRITER_HOST" ]; then
        log_error "MYSQL_WRITER_HOST not set"
        return 1
    fi
    
    # Verify MySQL connection
    log_info "Testing MySQL connection..."
    if ! mysql -h "$MYSQL_WRITER_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1;" &>/dev/null; then
        log_error "Failed to connect to MySQL at $MYSQL_WRITER_HOST"
        return 1
    fi
    log_success "MySQL connection verified"
    
    # Run migration
    log_info "Running migration..."
    mysql -h "$MYSQL_WRITER_HOST" \
          -u "$MYSQL_USER" \
          -p"$MYSQL_PASSWORD" \
          "$MYSQL_DATABASE" \
          < "$MIGRATION_FILE"
    
    log_success "Migration completed"
    
    # Verify tables
    log_info "Verifying tables..."
    mysql -h "$MYSQL_WRITER_HOST" \
          -u "$MYSQL_USER" \
          -p"$MYSQL_PASSWORD" \
          "$MYSQL_DATABASE" \
          -e "SHOW TABLES LIKE '%tenant%'; SHOW TABLES LIKE 'clickhouse_%';"
    
    log_success "Tables verified"
}

##############################################################################
# Step 4: Setup ClickHouse Tenant
##############################################################################

step_setup_clickhouse_tenant() {
    log_info "================================="
    log_info "Step 4: Setup ClickHouse Tenant"
    log_info "================================="
    
    if [ -z "$CLICKHOUSE_HOST" ]; then
        log_error "CLICKHOUSE_HOST not set"
        return 1
    fi
    
    # Verify ClickHouse connection
    log_info "Testing ClickHouse connection..."
    if ! clickhouse-client -h "$CLICKHOUSE_HOST" -u "$CLICKHOUSE_USERNAME" -p"$CLICKHOUSE_PASSWORD" -q "SELECT 1;" &>/dev/null; then
        log_error "Failed to connect to ClickHouse at $CLICKHOUSE_HOST"
        return 1
    fi
    log_success "ClickHouse connection verified"
    
    # Get tenant details
    if [ "$INTERACTIVE_MODE" = true ]; then
        read -p "Enter Tenant ID (e.g., acme_corp): " TENANT_ID
        read -p "Enter ClickHouse username (e.g., tenant_acme_corp): " CH_USERNAME
        read -p "Enter ClickHouse password: " CH_PASSWORD
        read -p "Enter Tenant Name (e.g., ACME Corporation): " TENANT_NAME
    else
        TENANT_ID="test_tenant"
        CH_USERNAME="tenant_test_tenant"
        CH_PASSWORD="test_password_123"
        TENANT_NAME="Test Tenant"
    fi
    
    log_info "Creating ClickHouse user: $CH_USERNAME"
    
    # Create ClickHouse user
    clickhouse-client -h "$CLICKHOUSE_HOST" \
                      -u "$CLICKHOUSE_USERNAME" \
                      -p"$CLICKHOUSE_PASSWORD" \
                      -q "CREATE USER IF NOT EXISTS ${CH_USERNAME} IDENTIFIED BY '${CH_PASSWORD}';"
    
    log_success "ClickHouse user created"
    
    # Grant permissions
    log_info "Granting permissions..."
    clickhouse-client -h "$CLICKHOUSE_HOST" \
                      -u "$CLICKHOUSE_USERNAME" \
                      -p"$CLICKHOUSE_PASSWORD" \
                      -q "GRANT SELECT ON otel_traces TO ${CH_USERNAME}; \
                          GRANT SELECT ON otel_logs TO ${CH_USERNAME}; \
                          GRANT SELECT ON otel_metrics TO ${CH_USERNAME};"
    
    log_success "Permissions granted"
    
    # Register tenant in MySQL
    log_info "Registering tenant in MySQL..."
    mysql -h "$MYSQL_WRITER_HOST" \
          -u "$MYSQL_USER" \
          -p"$MYSQL_PASSWORD" \
          "$MYSQL_DATABASE" \
          -e "INSERT INTO tenants (tenant_id, name, is_active) \
              VALUES ('${TENANT_ID}', '${TENANT_NAME}', 1) \
              ON DUPLICATE KEY UPDATE is_active=1;"
    
    log_success "Tenant registered in MySQL"
    
    # Display instructions for credential registration
    log_warn "======================================"
    log_warn "Manual Step Required:"
    log_warn "======================================"
    log_warn "Encrypt and register credentials in MySQL:"
    log_warn ""
    log_warn "1. Use PasswordEncryptionUtil to encrypt password"
    log_warn "2. Insert into clickhouse_tenant_credentials:"
    log_warn ""
    log_warn "INSERT INTO clickhouse_tenant_credentials"
    log_warn "  (tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active)"
    log_warn "VALUES"
    log_warn "  ('${TENANT_ID}', '${CH_USERNAME}', 'ENCRYPTED_PASSWORD', 'SALT', 'DIGEST', 1);"
    log_warn "======================================"
}

##############################################################################
# Step 5: Build Application
##############################################################################

step_build_application() {
    log_info "============================"
    log_info "Step 5: Build Application"
    log_info "============================"
    
    if [ ! -d "$SERVER_DIR" ]; then
        log_error "Server directory not found: $SERVER_DIR"
        return 1
    fi
    
    cd "$SERVER_DIR"
    
    # Set JAVA_HOME
    export JAVA_HOME=$(/usr/libexec/java_home -v 23)
    
    log_info "Building with Maven..."
    if mvn clean compile -q; then
        log_success "Application built successfully"
    else
        log_error "Build failed"
        return 1
    fi
    
    cd - > /dev/null
}

##############################################################################
# Main Execution
##############################################################################

main() {
    log_info "=============================================="
    log_info "Multi-Tenancy Setup Script"
    log_info "=============================================="
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --quick)
                QUICK_MODE=true
                INTERACTIVE_MODE=false
                shift
                ;;
            --interactive)
                INTERACTIVE_MODE=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Execute steps
    step_generate_encryption_key || exit 1
    step_load_env_vars || exit 1
    step_run_migrations || exit 1
    
    if [ "$INTERACTIVE_MODE" = true ]; then
        if confirm "Setup ClickHouse tenant? (y/n) "; then
            step_setup_clickhouse_tenant || log_warn "Tenant setup had issues"
        fi
    fi
    
    if confirm "Build application? (y/n) "; then
        step_build_application || exit 1
    fi
    
    log_success "=================================================="
    log_success "Multi-Tenancy Setup Complete!"
    log_success "=================================================="
    log_info ""
    log_info "Next steps:"
    log_info "1. Update .env.local with your database credentials"
    log_info "2. Run the application:"
    log_info "   cd $SERVER_DIR"
    log_info "   source $ENV_FILE"
    log_info "   java -jar target/pulse-server-*.jar"
    log_info ""
    log_info "For more details, see: MULTITENANCY_SETUP_GUIDE.md"
}

# Run main function
main "$@"
