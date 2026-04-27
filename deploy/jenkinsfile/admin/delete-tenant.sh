#!/bin/bash
# Deletes all data for a tenant from MySQL and OpenFGA.
# REQUIRES: no projects exist for this tenant (delete each project first).
# Set DRY_RUN=true (default) to preview; DRY_RUN=false to execute.
# The Jenkinsfile runs delete_tenant.py; this script is the bash equivalent. Keep in sync.
set -euo pipefail

# ── Required env vars ────────────────────────────────────────────────────────
: "${TENANT_ID:?TENANT_ID must be set}"
: "${DRY_RUN:=true}"
: "${MYSQL_HOST:?MYSQL_HOST must be set}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:?MYSQL_USER must be set}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD must be set}"
: "${MYSQL_DATABASE:=pulse_db}"
: "${OPENFGA_API_URL:?OPENFGA_API_URL must be set}"
: "${OPENFGA_STORE_ID:?OPENFGA_STORE_ID must be set}"

# ── Input validation ─────────────────────────────────────────────────────────
if ! [[ "$TENANT_ID" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "ERROR: TENANT_ID contains invalid characters: $TENANT_ID" >&2
    exit 1
fi

# ── Helpers ──────────────────────────────────────────────────────────────────
info() { echo -e "\033[0;34m[INFO]\033[0m  $*"; }
ok()   { echo -e "\033[0;32m[ OK ]\033[0m  $*"; }
warn() { echo -e "\033[1;33m[WARN]\033[0m  $*"; }
err()  { echo -e "\033[0;31m[ERR ]\033[0m  $*" >&2; }
step() { echo -e "\n\033[0;35m── $* ──\033[0m"; }

run_mysql() {
    MYSQL_PWD="$MYSQL_PASSWORD" mysql \
        -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DATABASE" \
        --batch --skip-column-names --raw 2>/dev/null \
        -e "$1"
}

# Reads all tuples for a given OpenFGA object key and deletes them in pages of 100.
delete_openfga_tuples() {
    local object_key="$1"
    local page_token=""
    local total=0

    while true; do
        local request_body
        request_body=$(jq -n \
            --arg obj "$object_key" \
            --arg tok "$page_token" \
            '{"tuple_key": {"object": $obj}} | if $tok != "" then .continuation_token = $tok else . end')

        local response
        response=$(curl -sf -X POST \
            "${OPENFGA_API_URL}/stores/${OPENFGA_STORE_ID}/read" \
            -H "Content-Type: application/json" \
            -d "$request_body") || {
            err "OpenFGA read failed for object: $object_key"
            return 1
        }

        local keys_json
        keys_json=$(echo "$response" | jq -c '[.tuples[]? | .key | {user, relation, object}]')
        local count
        count=$(echo "$keys_json" | jq 'length')

        if [ "$count" -gt 0 ]; then
            if [ "$DRY_RUN" = "true" ]; then
                info "[DRY RUN] Would delete $count OpenFGA tuple(s) for $object_key:"
                echo "$keys_json" | jq -r '.[] | "    " + .user + "  --[" + .relation + "]-->  " + .object'
            else
                local delete_body
                delete_body=$(jq -n --argjson k "$keys_json" '{"deletes": {"tuple_keys": $k}}')
                curl -sf -X POST \
                    "${OPENFGA_API_URL}/stores/${OPENFGA_STORE_ID}/write" \
                    -H "Content-Type: application/json" \
                    -d "$delete_body" > /dev/null || {
                    err "OpenFGA delete failed for object: $object_key"
                    return 1
                }
                ok "Deleted $count OpenFGA tuple(s) for $object_key"
            fi
            total=$((total + count))
        fi

        page_token=$(echo "$response" | jq -r '.continuation_token // ""')
        [ -z "$page_token" ] || [ "$page_token" = "null" ] && break
    done

    if [ "$total" -eq 0 ]; then
        warn "No OpenFGA tuples found for $object_key"
    fi
}

for _cmd in mysql jq curl; do
    if ! command -v "$_cmd" &>/dev/null; then
        err "Required command not on PATH: ${_cmd} (e.g. install mysql client, jq, curl on the Jenkins agent)"
        exit 127
    fi
done
unset _cmd

# ── Preflight ─────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  Delete Tenant  •  DRY_RUN=${DRY_RUN}"
echo "╚══════════════════════════════════════════════════════════╝"

TENANT_NAME=$(run_mysql "SELECT name FROM tenants WHERE tenant_id = '${TENANT_ID}' LIMIT 1")
if [ -z "$TENANT_NAME" ]; then
    err "Tenant not found in MySQL: $TENANT_ID"
    exit 1
fi
IS_ACTIVE=$(run_mysql "SELECT is_active FROM tenants WHERE tenant_id = '${TENANT_ID}' LIMIT 1")

echo ""
info "Tenant:  $TENANT_NAME  ($TENANT_ID)"
info "Active:  $IS_ACTIVE"
echo ""

# ── Safety check: no projects must remain ─────────────────────────────────────
step "Pre-check: remaining projects for tenant"

REMAINING_PROJECTS=$(run_mysql "SELECT project_id, name FROM projects WHERE tenant_id = '${TENANT_ID}'")

if [ -n "$REMAINING_PROJECTS" ]; then
    err "Cannot delete tenant — the following projects still exist:"
    echo ""
    echo "$REMAINING_PROJECTS" | while IFS=$'\t' read -r pid pname; do
        echo "    $pid  ($pname)"
    done
    echo ""
    err "Run the delete-project job (delete-project.sh) for each project first, then retry."
    exit 1
fi

ok "No remaining projects — safe to proceed"

echo ""

if [ "$DRY_RUN" = "true" ]; then
    info "DRY RUN — would delete:"
    info "  MySQL: tenants row for $TENANT_ID"
    info "  OpenFGA: all tuples where object = tenant:$TENANT_ID"
    echo ""
    step "OpenFGA tuples (preview)"
    delete_openfga_tuples "tenant:${TENANT_ID}"
    echo ""
    info "DRY RUN complete — no changes made."
    info "Re-run with DRY_RUN=false to execute the deletion."
    exit 0
fi

# ── Step 1: MySQL ─────────────────────────────────────────────────────────────
step "Step 1/2: MySQL"

run_mysql "DELETE FROM tenants WHERE tenant_id = '${TENANT_ID}'"

ok "MySQL: deleted tenant row ($TENANT_ID)"

# ── Step 2: OpenFGA ───────────────────────────────────────────────────────────
step "Step 2/2: OpenFGA"

delete_openfga_tuples "tenant:${TENANT_ID}"

ok "OpenFGA cleanup complete"

echo ""
ok "══════════════════════════════════════════════════════════"
ok "  Tenant deletion complete: $TENANT_NAME ($TENANT_ID)"
ok "══════════════════════════════════════════════════════════"
