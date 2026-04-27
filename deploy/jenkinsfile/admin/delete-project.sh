#!/bin/bash
# Deletes all data for a single project from MySQL, ClickHouse, and OpenFGA.
# Set DRY_RUN=true (default) to preview; DRY_RUN=false to execute.
# Invoked from deploy/jenkinsfile/admin/delete-project.jenkinsfile
set -euo pipefail

# ── Required env vars ────────────────────────────────────────────────────────
: "${PROJECT_ID:?PROJECT_ID must be set}"
: "${DRY_RUN:=true}"
: "${MYSQL_HOST:?MYSQL_HOST must be set}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:?MYSQL_USER must be set}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD must be set}"
: "${MYSQL_DATABASE:=pulse_db}"
: "${CH_ADMIN_HOST:?CH_ADMIN_HOST must be set}"
: "${CH_ADMIN_PORT:=8123}"
: "${CH_ADMIN_USER:?CH_ADMIN_USER must be set}"
: "${CH_ADMIN_PASSWORD:?CH_ADMIN_PASSWORD must be set}"
: "${OPENFGA_API_URL:?OPENFGA_API_URL must be set}"
: "${OPENFGA_STORE_ID:?OPENFGA_STORE_ID must be set}"

# ── Input validation ─────────────────────────────────────────────────────────
if ! [[ "$PROJECT_ID" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "ERROR: PROJECT_ID contains invalid characters: $PROJECT_ID" >&2
    exit 1
fi

# ── Derived ClickHouse identifiers (mirrors ClickhouseProjectService.java) ───
SANITIZED=$(echo "$PROJECT_ID" | tr '-' '_' | sed 's/proj_//')
CH_USERNAME="project_${SANITIZED}"
CH_POLICY_NAME="policy_${SANITIZED}"
CH_LEGACY_POLICY="policy_${SANITIZED}_root_cause_cache"
CH_ON_CLUSTER=""
if [ -n "${CH_CLUSTER_NAME:-}" ]; then
    CH_ON_CLUSTER=" ON CLUSTER ${CH_CLUSTER_NAME}"
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

run_ch_ddl() {
    local query="$1"
    if [ "$DRY_RUN" = "true" ]; then
        info "[DRY RUN] ClickHouse: $query"
        return 0
    fi
    local body
    body=$(curl -sf -X POST "http://${CH_ADMIN_HOST}:${CH_ADMIN_PORT}/" \
        -u "${CH_ADMIN_USER}:${CH_ADMIN_PASSWORD}" \
        --data-binary "$query" 2>&1) || {
        err "ClickHouse DDL failed: $query"
        err "$body"
        return 1
    }
    info "ClickHouse: $query"
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
echo "║  Delete Project  •  DRY_RUN=${DRY_RUN}"
echo "╚══════════════════════════════════════════════════════════╝"

PROJECT_NAME=$(run_mysql "SELECT name FROM projects WHERE project_id = '${PROJECT_ID}' LIMIT 1")
if [ -z "$PROJECT_NAME" ]; then
    err "Project not found in MySQL: $PROJECT_ID"
    exit 1
fi
TENANT_ID=$(run_mysql "SELECT tenant_id FROM projects WHERE project_id = '${PROJECT_ID}' LIMIT 1")
IS_ACTIVE=$(run_mysql "SELECT is_active FROM projects WHERE project_id = '${PROJECT_ID}' LIMIT 1")

echo ""
info "Project:      $PROJECT_NAME  ($PROJECT_ID)"
info "Tenant:       $TENANT_ID"
info "Active:       $IS_ACTIVE"
info "CH username:  $CH_USERNAME"
info "CH policy:    $CH_POLICY_NAME"
echo ""

step "MySQL row counts (preview)"
for TABLE in interaction alerts notification_channels pulse_sdk_configs symbol_files funnel journey rca_report_jobs event_definitions; do
    COUNT=$(run_mysql "SELECT COUNT(*) FROM ${TABLE} WHERE project_id = '${PROJECT_ID}'" 2>/dev/null || echo "?")
    printf "  %-36s %s rows\n" "$TABLE" "$COUNT"
done

echo ""

if [ "$DRY_RUN" = "true" ]; then
    echo ""
    info "DRY RUN complete — no changes made."
    info "Re-run with DRY_RUN=false to execute the deletion."
    exit 0
fi

# ── Step 1: MySQL ─────────────────────────────────────────────────────────────
step "Step 1/3: MySQL"

MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$MYSQL_DATABASE" \
    2>/dev/null <<SQL
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_projects;
CREATE TEMPORARY TABLE tmp_cleanup_projects (project_id VARCHAR(64) NOT NULL PRIMARY KEY);
INSERT INTO tmp_cleanup_projects VALUES ('${PROJECT_ID}');

-- Alert subsystem (no direct CASCADE from project through alert_scope)
DELETE aeh FROM alert_evaluation_history aeh
  INNER JOIN alert_scope sc ON aeh.scope_id = sc.id
  INNER JOIN alerts a ON sc.alert_id = a.id
  INNER JOIN tmp_cleanup_projects t ON t.project_id = a.project_id;

DELETE sc FROM alert_scope sc
  INNER JOIN alerts a ON sc.alert_id = a.id
  INNER JOIN tmp_cleanup_projects t ON t.project_id = a.project_id;

DELETE a FROM alerts a
  INNER JOIN tmp_cleanup_projects t ON t.project_id = a.project_id;

DELETE nco FROM notification_channels_old nco
  INNER JOIN tmp_cleanup_projects t ON nco.project_id = t.project_id;

-- Event catalog (no FK to projects)
DELETE ead FROM event_attribute_definitions ead
  INNER JOIN event_definitions ed ON ead.event_definition_id = ed.id
  INNER JOIN tmp_cleanup_projects t ON ed.project_id = t.project_id;

DELETE ed FROM event_definitions ed
  INNER JOIN tmp_cleanup_projects t ON ed.project_id = t.project_id;

-- usage_limit_notifications has RESTRICT FK; must delete before project_usage_limits cascade
DELETE uln FROM usage_limit_notifications uln
  INNER JOIN tmp_cleanup_projects t ON uln.project_id = t.project_id;

-- RCA tables (no FK to projects)
DELETE FROM rca_report_cache  WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);
DELETE FROM rca_report_jobs   WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);

-- analytics_jobs has no FK to funnel/journey; remove before funnel/journey rows drop via CASCADE
DELETE aj FROM analytics_jobs aj
  INNER JOIN funnel f ON aj.reference_id = f.id
  INNER JOIN tmp_cleanup_projects t ON f.project_id = t.project_id
  WHERE aj.job_type IN ('FUNNEL', 'BULK_FUNNEL');

DELETE aj FROM analytics_jobs aj
  INNER JOIN journey j ON aj.reference_id = j.id
  INNER JOIN tmp_cleanup_projects t ON j.project_id = t.project_id
  WHERE aj.job_type IN ('JOURNEY', 'BULK_JOURNEY');

DELETE fjt FROM funnel_journey_tag fjt
  INNER JOIN tmp_cleanup_projects t ON fjt.project_id = t.project_id;

DELETE FROM funnel   WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);
DELETE FROM journey  WHERE project_id IN (SELECT project_id FROM tmp_cleanup_projects);

-- Final: CASCADE removes interaction, symbol_files, pulse_sdk_configs, athena_job,
-- project_usage_limits, project_api_keys, clickhouse_project_credentials,
-- clickhouse_project_credential_audit, notification_channels, channel_event_mapping,
-- notification_logs, email_suppression_list
DELETE p FROM projects p
  INNER JOIN tmp_cleanup_projects t ON p.project_id = t.project_id;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_projects;
COMMIT;
SQL

ok "MySQL cleanup complete"

# ── Step 2: ClickHouse ────────────────────────────────────────────────────────
step "Step 2/3: ClickHouse"

run_ch_ddl "DROP ROW POLICY IF EXISTS ${CH_POLICY_NAME}${CH_ON_CLUSTER} ON otel.*"
run_ch_ddl "DROP ROW POLICY IF EXISTS ${CH_LEGACY_POLICY}${CH_ON_CLUSTER} ON otel.root_cause_cache"
run_ch_ddl "DROP USER IF EXISTS ${CH_USERNAME}${CH_ON_CLUSTER}"

ok "ClickHouse cleanup complete"

# ── Step 3: OpenFGA ───────────────────────────────────────────────────────────
step "Step 3/3: OpenFGA"

delete_openfga_tuples "project:${PROJECT_ID}"

ok "OpenFGA cleanup complete"

echo ""
ok "══════════════════════════════════════════════════════════"
ok "  Project deletion complete: $PROJECT_NAME ($PROJECT_ID)"
ok "══════════════════════════════════════════════════════════"
