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

# Install required tools (curl for API calls)
echo "Installing required tools..."
apk add --no-cache curl > /dev/null 2>&1 || true
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

# List existing stores and find one matching our name
STORES_RESPONSE=$(curl -s "$OPENFGA_URL/stores")
EXISTING_STORE_ID=$(echo "$STORES_RESPONSE" | grep -o '"id":"[^"]*","name":"'"$STORE_NAME"'"' | head -1 | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || true)

if [ -n "$EXISTING_STORE_ID" ]; then
    STORE_ID="$EXISTING_STORE_ID"
    echo "  ✓ Found existing store: $STORE_ID"
else
    echo "  Creating new store..."
    STORE_RESPONSE=$(curl -s -X POST "$OPENFGA_URL/stores" \
        -H "Content-Type: application/json" \
        -d '{"name": "'"$STORE_NAME"'"}')
    
    STORE_ID=$(echo "$STORE_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    
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

# Get latest model to check if we need to write
MODELS_RESPONSE=$(curl -s "$OPENFGA_URL/stores/$STORE_ID/authorization-models")
EXISTING_MODEL_ID=$(echo "$MODELS_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || true)

if [ -n "$EXISTING_MODEL_ID" ]; then
    echo "  Found existing model: $EXISTING_MODEL_ID"
    echo "  Writing new model version (previous versions are preserved)..."
fi

MODEL_RESPONSE=$(curl -s -X POST "$OPENFGA_URL/stores/$STORE_ID/authorization-models" \
    -H "Content-Type: application/json" \
    -d @"$SCRIPT_DIR/pulse-authz-model.json")

MODEL_ID=$(echo "$MODEL_RESPONSE" | grep -o '"authorization_model_id":"[^"]*"' | cut -d'"' -f4)

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
echo "Config saved to: $CONFIG_FILE"
echo ""
echo "To test, open the OpenFGA Playground at: http://localhost:3001"
echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
