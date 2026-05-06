#!/bin/bash

# Strict mode
set -euo pipefail

# ---------------------------------------------------------------------------
# Colors
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ---------------------------------------------------------------------------
# Path Variables
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$DEPLOY_DIR")"

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
print_success() { echo -e "${GREEN}✓ $1${NC}"; }
print_error()   { echo -e "${RED}✗ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠ $1${NC}"; }
print_info()    { echo -e "${BLUE}ℹ $1${NC}"; }

print_section() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

validate_env_against_example_and_compose() {
    local env_file="$DEPLOY_DIR/.env"
    local example_file="$DEPLOY_DIR/.env.example"
    local compose_file="$DEPLOY_DIR/docker-compose.yml"
    local failed=0

    if [ ! -f "$compose_file" ]; then
        print_warning "validate_env: docker-compose.yml not found, skipping compose var check"
        return 0
    fi
    if [ ! -f "$env_file" ]; then
        print_error ".env file not found at $env_file"
        return 1
    fi

    _validate_env_collect_env_keys() {
        local file="$1"
        local keys=""
        while IFS= read -r line; do
            line=$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
            [ -z "$line" ] && continue
            [[ "$line" =~ ^# ]] && continue
            if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)= ]]; then
                keys="$keys ${BASH_REMATCH[1]}"
            fi
        done < "$file"
        echo "$keys"
    }

    # Optional = vars that appear with a default in compose (${VAR:-...}). Skip commented lines.
    local optional_vars
    optional_vars=$(grep -v '^[[:space:]]*#' "$compose_file" 2>/dev/null | grep -oE '\$\{[A-Za-z0-9_]+:-' | sed 's/^\${//;s/:-$//' | sort -u || true)
    # All vars referenced in compose (skip commented lines)
    local all_vars
    all_vars=$(grep -v '^[[:space:]]*#' "$compose_file" 2>/dev/null | grep -oE '\$\{[A-Za-z0-9_]+' | sed 's/^\${//' | sort -u || true)
    # Required = vars that have no default in compose (must be present in .env)
    local required_vars
    required_vars=$(comm -23 <(echo "$all_vars") <(echo "$optional_vars") || true)

    local env_keys
    env_keys=$(_validate_env_collect_env_keys "$env_file")

    # Check only required vars: missing → error (do not copy from .env.example)
    local missing_required=""
    local newline=$'\n'
    while IFS= read -r key; do
        [ -z "$key" ] && continue
        local present=false
        for k in $env_keys; do
            [ "$k" = "$key" ] && present=true && break
        done
        if [ "$present" = false ]; then
            missing_required="$missing_required$key$newline"
        fi
    done << EOF
$required_vars
EOF

    if [ -n "$missing_required" ]; then
        while IFS= read -r key; do
            [ -z "$key" ] && continue
            print_error "Missing in .env (required by docker-compose.yml, no default): $key"
            failed=1
        done << EOF
$missing_required
EOF
    fi

    # Warn for optional vars (have default in compose) that are missing in .env
    while IFS= read -r key; do
        [ -z "$key" ] && continue
        local present=false
        for k in $env_keys; do
            [ "$k" = "$key" ] && present=true && break
        done
        if [ "$present" = false ]; then
            print_warning "Missing in .env (optional, docker-compose has default): $key"
        fi
    done << EOF
$optional_vars
EOF

    # Warn for keys in .env.example that are missing in .env (do not fail)
    if [ -f "$example_file" ]; then
        local example_keys
        example_keys=$(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$example_file" 2>/dev/null | cut -d= -f1 | sort -u || true)
        local all_set
        all_set=$(echo "$all_vars" | tr '\n' ' ')
        while IFS= read -r key; do
            [ -z "$key" ] && continue
            case " $all_set " in *" $key "*) continue ;; esac
            local present=false
            for k in $env_keys; do
                [ "$k" = "$key" ] && present=true && break
            done
            if [ "$present" = false ]; then
                print_warning "Missing in .env (defined in .env.example): $key"
            fi
        done << EOF
$example_keys
EOF
    fi

    if [ "$failed" -eq 1 ]; then
        echo ""
        print_info "Add the missing required variables to .env (see .env.example)."
        print_info "Variables without a default in docker-compose.yml must be set."
        return 1
    fi
    return 0
}

validate_env_against_example_and_compose