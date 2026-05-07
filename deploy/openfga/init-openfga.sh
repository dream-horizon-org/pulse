#!/bin/sh
# OpenFGA Initialization Script for Pulse
# This script creates store and authorization model (migrations handled separately)
# 
# IDEMPOTENT: Safe to run multiple times - existing stores/models are reused
#
# Usage:
#   ./init-openfga.sh                                    # Uses default URL
#   OPENFGA_URL=http://openfga:8080 ./init-openfga.sh   # Custom URL

set -e

OPENFGA_URL="${OPENFGA_URL:-http://localhost:8180}"
STORE_NAME="pulse-authorization"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="/tmp/.openfga-config"

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                     OpenFGA Initialization for Pulse"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo "OpenFGA URL: $OPENFGA_URL"
echo "Store Name:  $STORE_NAME"
echo "Idempotent:  Yes (safe to run multiple times)"
echo ""

# Install required tools (curl + jq: OpenFGA JSON is spaced; grep patterns miss matches)
echo "Installing required tools..."
apk add --no-cache curl jq > /dev/null 2>&1 || true
echo "  ✓ Tools installed"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# Step 0: Wait for OpenFGA server to be ready
# ═══════════════════════════════════════════════════════════════════════════════
echo "Step 0: Waiting for OpenFGA server to be ready..."
i=0
while [ $i -lt 60 ]; do
    if curl -sf "$OPENFGA_URL/healthz" > /dev/null 2>&1; then
        echo "  ✓ OpenFGA server is ready!"
        break
    fi
    i=$((i + 1))
    if [ $i -eq 60 ]; then
        echo "  ✗ ERROR: OpenFGA is not ready after 120 seconds"
        exit 1
    fi
    echo "  Attempt $i/60 - waiting..."
    sleep 2
done
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# Step 1: Get or Create Store (IDEMPOTENT)
# ═══════════════════════════════════════════════════════════════════════════════
echo "Step 1: Checking for existing store '$STORE_NAME'..."

# List existing stores and find one matching our name (JSON field order/spacing varies)
STORES_RESPONSE=$(curl -s "$OPENFGA_URL/stores")
EXISTING_STORE_ID=$(echo "$STORES_RESPONSE" | jq -r --arg STORE_NAME "$STORE_NAME" '
  (.stores // [])
  | map(select(.name == $STORE_NAME))
  | sort_by(.created_at // "")
  | .[0].id // empty
' 2>/dev/null || true)

if [ -n "$EXISTING_STORE_ID" ]; then
    STORE_ID="$EXISTING_STORE_ID"
    DUP_COUNT=$(echo "$STORES_RESPONSE" | jq --arg STORE_NAME "$STORE_NAME" '[.stores[]? | select(.name == $STORE_NAME)] | length' 2>/dev/null || echo "0")
    if [ "$DUP_COUNT" -gt 1 ] 2>/dev/null; then
        echo "  ⚠ Warning: $DUP_COUNT stores named '$STORE_NAME' exist; using oldest by created_at: $STORE_ID"
    fi
    echo "  ✓ Found existing store: $STORE_ID"
else
    echo "  Creating new store..."
    STORE_RESPONSE=$(curl -s -X POST "$OPENFGA_URL/stores" \
        -H "Content-Type: application/json" \
        -d '{"name": "'"$STORE_NAME"'"}')
    
    STORE_ID=$(echo "$STORE_RESPONSE" | jq -r '.id // empty' 2>/dev/null || true)
    
    if [ -z "$STORE_ID" ]; then
        echo "  ✗ ERROR: Failed to create store"
        echo "  Response: $STORE_RESPONSE"
        exit 1
    fi
    echo "  ✓ Created new store: $STORE_ID"
fi

echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# Step 2: Write Authorization Model (IDEMPOTENT - creates new version)
# ═══════════════════════════════════════════════════════════════════════════════
echo "Step 2: Writing authorization model..."

# List models (for logging); API returns JSON with spaces — use jq
MODELS_RESPONSE=$(curl -s "$OPENFGA_URL/stores/$STORE_ID/authorization-models")
EXISTING_MODEL_ID=$(echo "$MODELS_RESPONSE" | jq -r '(.authorization_models // [])[0].id // empty' 2>/dev/null || true)

if [ -n "$EXISTING_MODEL_ID" ]; then
    echo "  Found existing model: $EXISTING_MODEL_ID"
    echo "  Writing new model version (previous versions are preserved)..."
fi

MODEL_RESPONSE=$(curl -s -X POST "$OPENFGA_URL/stores/$STORE_ID/authorization-models" \
    -H "Content-Type: application/json" \
    -d @"$SCRIPT_DIR/pulse-authz-model.json")

MODEL_ID=$(echo "$MODEL_RESPONSE" | jq -r '.authorization_model_id // empty' 2>/dev/null || true)

if [ -z "$MODEL_ID" ]; then
    echo "  ✗ ERROR: Failed to write authorization model"
    echo "  Response: $MODEL_RESPONSE"
    exit 1
fi

echo "  ✓ Authorization model written: $MODEL_ID"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# Step 3: Write Tenant -> Project Relationships (IDEMPOTENT)
# ═══════════════════════════════════════════════════════════════════════════════
echo "Step 3: Writing tenant-project relationships..."

# Function to write a single tuple (ignores errors for idempotency)
write_tuple() {
    local user="$1"
    local relation="$2"
    local object="$3"
    
    curl -s -X POST "$OPENFGA_URL/stores/$STORE_ID/write" \
        -H "Content-Type: application/json" \
        -d '{
          "writes": {
            "tuple_keys": [{"user": "'"$user"'", "relation": "'"$relation"'", "object": "'"$object"'"}]
          },
          "authorization_model_id": "'"$MODEL_ID"'"
        }' > /dev/null 2>&1 || true
}

# Default tenant projects
write_tuple "tenant:default" "parent" "project:default-project"
write_tuple "tenant:default" "parent" "project:pulse-mobile-android"
write_tuple "tenant:default" "parent" "project:pulse-mobile-ios"
write_tuple "tenant:default" "parent" "project:pulse-web-dashboard"

# Fancode tenant projects
write_tuple "tenant:fancode" "parent" "project:fancode-mobile-android"
write_tuple "tenant:fancode" "parent" "project:fancode-mobile-ios"
write_tuple "tenant:fancode" "parent" "project:fancode-mobile-rn"
write_tuple "tenant:fancode" "parent" "project:fancode-web"
write_tuple "tenant:fancode" "parent" "project:fancode-tv"

# Dream11 tenant projects
write_tuple "tenant:dream11" "parent" "project:dream11-android"
write_tuple "tenant:dream11" "parent" "project:dream11-ios"
write_tuple "tenant:dream11" "parent" "project:dream11-web"
write_tuple "tenant:dream11" "parent" "project:dream11-pwa"

echo "  ✓ Project parent relationships written (or already exist)"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# Step 3b: Link tenants to system (superadmin bridge via system_parent)
# ═══════════════════════════════════════════════════════════════════════════════
echo "Step 3b: Linking seed tenants to system (superadmin bridge)..."
write_tuple "system:pulse" "system_parent" "tenant:default"
write_tuple "system:pulse" "system_parent" "tenant:fancode"
write_tuple "system:pulse" "system_parent" "tenant:dream11"
echo "  ✓ system_parent tuples written (or already exist)"
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# Step 4: Write Sample User Roles (IDEMPOTENT)
# ═══════════════════════════════════════════════════════════════════════════════
echo "Step 4: Writing sample user roles..."

# Tenant admins
write_tuple "user:admin@pulse.io" "admin" "tenant:default"
write_tuple "user:admin@fancode.com" "admin" "tenant:fancode"
write_tuple "user:admin@dream11.com" "admin" "tenant:dream11"

# Fancode sample users
write_tuple "user:developer@fancode.com" "member" "tenant:fancode"
write_tuple "user:developer@fancode.com" "editor" "project:fancode-mobile-android"
write_tuple "user:developer@fancode.com" "editor" "project:fancode-mobile-ios"
write_tuple "user:viewer@fancode.com" "member" "tenant:fancode"
write_tuple "user:viewer@fancode.com" "member" "project:fancode-web"

echo "  ✓ Sample user roles written (or already exist)"
echo ""

# ═══════════════════════════════════════════════════════════════════════════
# Step 5: Write Mock User Roles for Development (IDEMPOTENT)
# ═══════════════════════════════════════════════════════════════════════════
echo "Step 5: Writing mock user roles for development..."

# Mock users as admins/members on default tenant
write_tuple "user:mock-user-1" "admin" "tenant:default"
write_tuple "user:mock-user-2" "member" "tenant:default"

# Mock users have access to default-project
write_tuple "user:mock-user-1" "admin" "project:default-project"
write_tuple "user:mock-user-2" "viewer" "project:default-project"

# Seed a dev internal_viewer for local testing (cross-tenant read-only)
write_tuple "user:dev-viewer@dreamhorizon.org" "internal_viewer" "system:pulse"
echo "  ✓ Dev internal_viewer seeded (or already exists)"

echo "  ✓ Mock user roles written (or already exist)"
echo ""


# ═══════════════════════════════════════════════════════════════════════════════
# Save Configuration
# ═══════════════════════════════════════════════════════════════════════════════
cat > "$CONFIG_FILE" << EOF
# OpenFGA Configuration - Generated $(date)
# Add these to your .env file or docker-compose environment

OPENFGA_STORE_ID=$STORE_ID
OPENFGA_MODEL_ID=$MODEL_ID
OPENFGA_ENABLED=true
EOF

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                     OpenFGA Initialization Complete!"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo "Configuration:"
echo "  OPENFGA_STORE_ID=$STORE_ID"
echo "  OPENFGA_MODEL_ID=$MODEL_ID"
echo "  OPENFGA_ENABLED=true"
echo ""
echo "Sample data created:"
echo ""
echo "  TENANTS:"
echo "    - default (admin: admin@pulse.io)"
echo "    - fancode (admin: admin@fancode.com)"
echo "    - dream11 (admin: admin@dream11.com)"
echo ""
echo "  SAMPLE USERS (fancode tenant):"
echo "    - admin@fancode.com     → Tenant Admin (full access)"
echo "    - developer@fancode.com → Editor on Android/iOS projects"
echo "    - viewer@fancode.com    → Viewer on Web project"
echo ""
echo "  MOCK USERS (default tenant - for dev mode):"
echo "    - mock-user-1 (user1@example.com) → Tenant Admin, Admin on default-project"
echo "    - mock-user-2 (user2@example.com) → Tenant Member, Viewer on default-project"
echo ""
echo "Config saved to: $CONFIG_FILE"
echo ""
echo "To test, open the OpenFGA Playground at: http://localhost:3001"
echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
