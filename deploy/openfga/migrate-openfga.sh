#!/bin/sh
# OpenFGA Database Migration Script
# 
# IDEMPOTENT: Safe to run multiple times - migrations are idempotent
#
# This script runs OpenFGA database migrations only.
# It downloads the OpenFGA binary and runs the migrate command.

set -e

OPENFGA_VERSION="${OPENFGA_VERSION:-1.5.3}"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-pulse_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-pulse_password}"
MYSQL_DATABASE="${MYSQL_DATABASE:-openfga}"

echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                     OpenFGA Database Migration"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo "OpenFGA Version: $OPENFGA_VERSION"
echo "MySQL Host:      $MYSQL_HOST:$MYSQL_PORT"
echo "Database:        $MYSQL_DATABASE"
echo "Idempotent:      Yes (safe to run multiple times)"
echo ""

# Install required tools
echo "Installing required tools..."
apk add --no-cache curl > /dev/null 2>&1 || true
echo "  ✓ Tools installed"
echo ""

# Build datastore URI
DATASTORE_URI="${MYSQL_USER}:${MYSQL_PASSWORD}@tcp(${MYSQL_HOST}:${MYSQL_PORT})/${MYSQL_DATABASE}?parseTime=true"

# Download OpenFGA binary
OPENFGA_BIN="/tmp/openfga"
echo "Downloading OpenFGA binary..."

# Detect architecture
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)  ARCH_SUFFIX="amd64" ;;
    aarch64) ARCH_SUFFIX="arm64" ;;
    arm64)   ARCH_SUFFIX="arm64" ;;
    *)       echo "  ✗ Unsupported architecture: $ARCH"; exit 1 ;;
esac

echo "  Architecture: linux_${ARCH_SUFFIX}"
DOWNLOAD_URL="https://github.com/openfga/openfga/releases/download/v${OPENFGA_VERSION}/openfga_${OPENFGA_VERSION}_linux_${ARCH_SUFFIX}.tar.gz"
curl -sL "$DOWNLOAD_URL" | tar -xz -C /tmp openfga
chmod +x "$OPENFGA_BIN"
echo "  ✓ OpenFGA v${OPENFGA_VERSION} downloaded"
echo ""

# Run migrations
echo "Running database migrations..."
"$OPENFGA_BIN" migrate \
    --datastore-engine mysql \
    --datastore-uri "$DATASTORE_URI" 2>&1

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo "                     ✓ Database Migration Complete!"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""

