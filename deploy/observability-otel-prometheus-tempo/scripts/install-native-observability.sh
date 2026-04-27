#!/usr/bin/env bash
# Native observability wiring for Pulse-style OTEL → Prometheus (+ optional Tempo traces).
#
# Modes
#   Default: copy OTEL collector config + Prometheus rules + merge prometheus.yml (no binaries).
#   --production: Linux only — install Tempo (Amazon S3 block store) + OTEL Collector Contrib
#                 binaries + systemd units; OTEL config with traces to local Tempo; Prometheus
#                 merge + rules; optional Grafana datasource files (Prometheus + Tempo).
#
# Paths (defaults)
#   OTEL:  /etc/otelcol-contrib/otelcol.yaml, binary /usr/local/bin/otelcol-contrib
#   Tempo: /etc/tempo/tempo.yaml, binary /usr/local/bin/tempo, WAL /var/tempo/wal
#   Prom:  /etc/prometheus/prometheus.yml + /etc/prometheus/rules/
#
# Requires root (sudo). Prometheus merge and --production need mikefarah yq v4:
#   https://github.com/mikefarah/yq/#install
#
# Production (example)
#   export TEMPO_S3_BUCKET=my-tempo-traces TEMPO_S3_REGION=us-east-1
#   # Optional: non-AWS S3-compatible
#   # export TEMPO_S3_ENDPOINT=https://minio.example.com TEMPO_S3_INSECURE=true
#   # Optional static keys (otherwise EC2 instance role / default chain)
#   # export TEMPO_S3_ACCESS_KEY_ID=... TEMPO_S3_SECRET_ACCESS_KEY=...
#   sudo ./install-native-observability.sh --production
#
# Usage (non-production)
#   sudo ./install-native-observability.sh
#   sudo ./install-native-observability.sh --dry-run
#   sudo ./install-native-observability.sh --grafana-datasource
#   sudo ./install-native-observability.sh --with-tempo --tempo-endpoint=127.0.0.1:4317
#   sudo ./install-native-observability.sh --no-prometheus-merge
#   sudo SKIP_BINARY_DOWNLOAD=true ./install-native-observability.sh --production  # configs only
#
# Bundle from S3 (upload deploy/observability-otel-prometheus-tempo/* to a prefix, then on EC2):
#   sudo PULSE_OBS_BUNDLE_S3=s3://my-bucket/path/observability-otel-prometheus-tempo/ ./install-native-observability.sh --production
#   sudo ./install-native-observability.sh --from-s3=s3://my-bucket/path/observability-otel-prometheus-tempo/ --production
# Optional: PULSE_OBS_BUNDLE_LOCAL=/opt/pulse-obs (default /opt/pulse-observability-otel-tempo). Requires aws CLI + IAM (or keys).
#
# Optional env vars (all have safe defaults)
#   TEMPO_HTTP_LISTEN_ADDR  Tempo HTTP listen address (default: 127.0.0.1).
#                           Set to 0.0.0.0 only if Grafana is on a separate host;
#                           restrict port 3200 in your security group accordingly.
#   OTELCOL_CONTRIB_VERSION OTel Collector Contrib version to download (default: 0.137.0)
#   TEMPO_VERSION           Tempo version to download (default: 2.6.1)
#   TEMPO_INTERNAL_GRPC     Tempo internal gRPC port for OTLP ingest (default: 4327)
#   TEMPO_INTERNAL_HTTP     Tempo internal HTTP port (default: 4328)
#   TEMPO_BLOCK_RETENTION   Tempo trace retention (default: 2160h = 90 days)
#   SKIP_BINARY_DOWNLOAD    Set to true to skip curl downloads (configs only)
#
# Fixes applied (vs original):
#   1. curl preflight check now runs before production_install_stack (not after)
#   2. otelcol-contrib systemd unit runs as dedicated 'otelcol' user, not root
#   3. otelcol systemd unit includes ProtectSystem/PrivateTmp/NoNewPrivileges hardening
#   4. Tempo config always written as root:tempo 640 (not world-readable 644)
#   5. apply_tempo_endpoint verifies sed replacement succeeded (catches template drift)
#   6. rule_files merge skips explicit path when an existing glob already covers it
#   7. Post-restart health checks for Tempo (/ready) and otelcol (metrics endpoint)
#   8. Tempo HTTP listen address is configurable via TEMPO_HTTP_LISTEN_ADDR

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OBS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SNIPPET_DIR="${SCRIPT_DIR}/snippets"
TS="$(date +%Y%m%d%H%M%S)"

# Pull repo subtree from S3 once, then re-exec from synced copy (paths must match: …/scripts/… parent has yaml + prometheus/).
PULSE_OBS_BUNDLE_URI="${PULSE_OBS_BUNDLE_S3:-}"
PULSE_OBS_ARGV=()
for _a in "$@"; do
  case "${_a}" in
    --from-s3=*) PULSE_OBS_BUNDLE_URI="${_a#*=}" ;;
    *) PULSE_OBS_ARGV+=("${_a}") ;;
  esac
done
if [[ ${#PULSE_OBS_ARGV[@]} -gt 0 ]]; then
  set -- "${PULSE_OBS_ARGV[@]}"
else
  set --
fi
if [[ -n "${PULSE_OBS_BUNDLE_URI}" ]] && [[ "${PULSE_OBS_BUNDLE_SYNCED:-}" != "1" ]]; then
  if [[ "${EUID:-}" -ne 0 ]]; then
    echo "error: sudo required for S3 bundle sync (writes PULSE_OBS_BUNDLE_LOCAL or /opt/...)" >&2
    exit 1
  fi
  if ! command -v aws >/dev/null 2>&1; then
    echo "error: aws CLI not found; install awscli v2 for --from-s3 / PULSE_OBS_BUNDLE_S3" >&2
    exit 1
  fi
  PULSE_OBS_BUNDLE_LOCAL="${PULSE_OBS_BUNDLE_LOCAL:-/opt/pulse-observability-otel-tempo}"
  _obs_installer="${PULSE_OBS_BUNDLE_LOCAL}/scripts/install-native-observability.sh"
  mkdir -p "${PULSE_OBS_BUNDLE_LOCAL}"
  echo "[install-native-observability] aws s3 sync ${PULSE_OBS_BUNDLE_URI} → ${PULSE_OBS_BUNDLE_LOCAL}/"
  aws s3 sync "${PULSE_OBS_BUNDLE_URI}" "${PULSE_OBS_BUNDLE_LOCAL}/"
  if [[ ! -f "${_obs_installer}" ]]; then
    echo "error: after sync, expected ${_obs_installer} (upload the observability-otel-prometheus-tempo folder layout)" >&2
    exit 1
  fi
  chmod +x "${_obs_installer}" 2>/dev/null || true
  export PULSE_OBS_BUNDLE_SYNCED=1
  unset PULSE_OBS_BUNDLE_S3 || true
  unset PULSE_OBS_BUNDLE_URI || true
  exec /bin/bash "${_obs_installer}" "$@"
fi
unset PULSE_OBS_BUNDLE_URI PULSE_OBS_ARGV _a _obs_installer 2>/dev/null || true

OTELCOL_CONTRIB_VERSION="${OTELCOL_CONTRIB_VERSION:-0.137.0}"
TEMPO_VERSION="${TEMPO_VERSION:-2.6.1}"
TEMPO_INTERNAL_GRPC="${TEMPO_INTERNAL_GRPC:-4327}"
TEMPO_INTERNAL_HTTP="${TEMPO_INTERNAL_HTTP:-4328}"
TEMPO_BLOCK_RETENTION="${TEMPO_BLOCK_RETENTION:-2160h}"
# Fix: make Tempo listen address configurable. Defaults to 127.0.0.1 (loopback-only,
# safe for single-node). Set to 0.0.0.0 if Grafana runs on a different host — but
# ensure your security group restricts port 3200 to trusted sources only.
TEMPO_HTTP_LISTEN_ADDR="${TEMPO_HTTP_LISTEN_ADDR:-127.0.0.1}"

DRY_RUN=false
PRODUCTION=false
WITH_TEMPO=false
TEMPO_ENDPOINT=""
GRAFANA_DATASOURCE=false
NO_GRAFANA_PROVISIONING=false
NO_PROMETHEUS_MERGE=false
RESTART_SERVICES=true
SKIP_BINARY_DOWNLOAD="${SKIP_BINARY_DOWNLOAD:-false}"

OTEL_CONFIG_DEST="${OTEL_CONFIG_DEST:-/etc/otelcol-contrib/otelcol.yaml}"
TEMPO_CONFIG_DEST="${TEMPO_CONFIG_DEST:-/etc/tempo/tempo.yaml}"
PROMETHEUS_CONFIG="${PROMETHEUS_CONFIG:-/etc/prometheus/prometheus.yml}"
PROMETHEUS_RULES_DIR="${PROMETHEUS_RULES_DIR:-/etc/prometheus/rules}"
PULSE_OTEL_RULES_DEST="${PULSE_OTEL_RULES_DEST:-${PROMETHEUS_RULES_DIR}/pulse-otel.yml}"
OTEL_PROMETHEUS_SCRAPE_TARGET="${OTEL_PROMETHEUS_SCRAPE_TARGET:-127.0.0.1:8889}"
GRAFANA_PROVISION_DIR="${GRAFANA_PROVISION_DIR:-/etc/grafana/provisioning/datasources}"

die() {
  echo "error: $*" >&2
  exit 1
}

log() {
  echo "[install-native-observability] $*"
}

require_root() {
  if [[ "${EUID:-}" -ne 0 ]]; then
    die "run as root (sudo)"
  fi
}

is_mikefarah_yq() {
  command -v yq >/dev/null 2>&1 && yq --version 2>&1 | grep -qi mikefarah
}

go_arch() {
  case "$(uname -m)" in
    x86_64) echo amd64 ;;
    aarch64 | arm64) echo arm64 ;;
    *) die "unsupported architecture: $(uname -m)" ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --production)
      PRODUCTION=true
      shift
      ;;
    --with-tempo)
      WITH_TEMPO=true
      shift
      ;;
    --tempo-endpoint=*)
      TEMPO_ENDPOINT="${1#*=}"
      shift
      ;;
    --grafana-datasource)
      GRAFANA_DATASOURCE=true
      shift
      ;;
    --no-grafana-provisioning)
      NO_GRAFANA_PROVISIONING=true
      shift
      ;;
    --no-prometheus-merge)
      NO_PROMETHEUS_MERGE=true
      shift
      ;;
    --no-restart)
      RESTART_SERVICES=false
      shift
      ;;
    -h | --help)
      sed -n '2,62p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      die "unknown option: $1 (try --help)"
      ;;
  esac
done

if [[ "$PRODUCTION" == true ]] && [[ "$WITH_TEMPO" == true ]]; then
  die "use either --production (local Tempo+S3+install) or --with-tempo (remote Tempo), not both"
fi

if [[ "$PRODUCTION" == true ]]; then
  [[ "$(uname -s)" == "Linux" ]] || die "--production requires Linux (for binary install)"
  if [[ "$NO_PROMETHEUS_MERGE" != true ]]; then
    is_mikefarah_yq || die "--production needs mikefarah yq v4 for Prometheus merge (or pass --no-prometheus-merge)"
  fi
  [[ -n "${TEMPO_S3_BUCKET:-}" ]] || die "set TEMPO_S3_BUCKET for --production"
  [[ -n "${TEMPO_S3_REGION:-}" ]] || die "set TEMPO_S3_REGION for --production"
  if [[ -n "${TEMPO_S3_ACCESS_KEY_ID:-}" ]] && [[ -z "${TEMPO_S3_SECRET_ACCESS_KEY:-}" ]]; then
    die "TEMPO_S3_SECRET_ACCESS_KEY required when TEMPO_S3_ACCESS_KEY_ID is set"
  fi
  OTEL_CONFIG_SRC="${OTEL_CONFIG_SRC:-${OBS_DIR}/otel-collector-production.yaml}"
  WITH_TEMPO=false
  TEMPO_ENDPOINT=""
elif [[ "$WITH_TEMPO" == true ]]; then
  OTEL_CONFIG_SRC="${OBS_DIR}/otel-collector.yaml"
elif [[ -z "${OTEL_CONFIG_SRC:-}" ]]; then
  OTEL_CONFIG_SRC="${OBS_DIR}/otel-collector-native.yaml"
fi

run() {
  if [[ "$DRY_RUN" == true ]]; then
    echo "dry-run: $*"
  else
    "$@"
  fi
}

backup_file() {
  local f="$1"
  if [[ -f "$f" ]] && [[ "$DRY_RUN" != true ]]; then
    cp -a "$f" "${f}.bak.${TS}"
    log "backup: ${f}.bak.${TS}"
  fi
}

merge_prometheus() {
  local prom="$1"
  local snippet_tmp
  snippet_tmp="$(mktemp)"
  sed "s|127.0.0.1:8889|${OTEL_PROMETHEUS_SCRAPE_TARGET}|g" \
    "${SNIPPET_DIR}/otel-scrape-job.yaml" >"${snippet_tmp}"

  local has_job
  has_job="$(yq 'any(.scrape_configs[]?; .job_name == "otel_collector")' "${prom}" | head -1 | tr -d '\r\n')"
  if [[ "${has_job}" == "true" ]]; then
    log "prometheus: scrape job otel_collector already present, skipping append"
  else
    log "prometheus: appending scrape job otel_collector → ${OTEL_PROMETHEUS_SCRAPE_TARGET}"
    yq -i ".scrape_configs = (.scrape_configs // []) + load(\"${snippet_tmp}\")" "${prom}"
  fi
  rm -f "${snippet_tmp}"

  local rule_path="$PULSE_OTEL_RULES_DEST"
  local has_rule
  has_rule="$(yq --arg p "${rule_path}" '(.rule_files // []) | any(. == $p)' "${prom}" | head -1 | tr -d '\r\n')"
  if [[ "${has_rule}" == "true" ]]; then
    log "prometheus: rule_files already references ${rule_path}"
  else
    # Fix: also check if an existing glob in rule_files already covers the path
    # (e.g. "rules/*.yml") — if so, inserting the explicit path causes duplicate
    # rule group loads and Prometheus will log a warning on reload.
    local rules_dir
    rules_dir="$(dirname "${rule_path}")"
    local glob_covers=false
    while IFS= read -r glob_entry; do
      # strip quotes that yq may emit
      glob_entry="${glob_entry//\"/}"
      glob_entry="${glob_entry//\'/}"
      # use bash glob matching
      # shellcheck disable=SC2053
      if [[ "${rule_path}" == ${glob_entry} ]]; then
        glob_covers=true
        log "prometheus: rule_files glob '${glob_entry}' already covers ${rule_path}, skipping explicit entry"
        break
      fi
    done < <(yq '(.rule_files // [])[]' "${prom}" 2>/dev/null | tr -d '\r')
    if [[ "$glob_covers" != true ]]; then
      log "prometheus: adding rule_files entry ${rule_path}"
      yq -i --arg r "${rule_path}" '.rule_files = ((.rule_files // []) + [$r]) | unique' "${prom}"
    fi
  fi
}

apply_tempo_endpoint() {
  local dest="$1"
  # Note: this function is only called when --with-tempo is active.
  # In --production mode WITH_TEMPO is forced false; patch_otel_tempo_port handles that path.
  if [[ "$WITH_TEMPO" != true ]]; then
    return 0
  fi
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: would set Tempo OTLP endpoint to ${TEMPO_ENDPOINT} in ${dest}"
    return 0
  fi
  sed -i.bak."${TS}" \
    -e "s|endpoint: tempo:4317|endpoint: ${TEMPO_ENDPOINT}|g" \
    "${dest}"
  # Fix: verify the replacement actually took — if the placeholder changed in the
  # template the sed silently does nothing and otelcol tries to reach tempo:4317.
  if ! grep -q "${TEMPO_ENDPOINT}" "${dest}"; then
    die "apply_tempo_endpoint: failed to patch Tempo endpoint in ${dest}; " \
        "expected placeholder 'endpoint: tempo:4317' not found in template — check OTEL_CONFIG_SRC"
  fi
  log "tempo: patched exporter endpoint → ${TEMPO_ENDPOINT} (${dest})"
}

ensure_otelcol_user() {
  # Fix: otelcol-contrib was running as root; create a dedicated system user instead.
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: ensure system user otelcol + /etc/otelcol-contrib"
    return 0
  fi
  if ! id otelcol &>/dev/null; then
    if ! useradd -r -M -d /etc/otelcol-contrib -s /usr/sbin/nologin otelcol 2>/dev/null; then
      useradd --system -M -d /etc/otelcol-contrib -s /usr/sbin/nologin otelcol || die "could not create system user otelcol"
    fi
    log "created system user: otelcol"
  fi
  mkdir -p /etc/otelcol-contrib
  chown root:otelcol /etc/otelcol-contrib
  chmod 750 /etc/otelcol-contrib
}

ensure_tempo_user() {
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: ensure system user tempo + /var/tempo"
    return 0
  fi
  if ! id tempo &>/dev/null; then
    if ! useradd -r -M -d /var/tempo -s /usr/sbin/nologin tempo 2>/dev/null; then
      useradd --system -M -d /var/tempo -s /usr/sbin/nologin tempo || die "could not create system user tempo"
    fi
    log "created system user: tempo"
  fi
  mkdir -p /var/tempo/wal
  chown -R tempo:tempo /var/tempo
}

write_tempo_s3_config() {
  local dest="$1"
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: would write ${dest} (S3 bucket=${TEMPO_S3_BUCKET} region=${TEMPO_S3_REGION})"
    return 0
  fi
  mkdir -p "$(dirname "${dest}")"
  if [[ -f "${dest}" ]]; then
    backup_file "${dest}"
  fi
  local tmp
  tmp="$(mktemp)"
  {
    echo "# Generated by Pulse install-native-observability.sh — do not edit by hand; re-run script."
    echo "stream_over_http_enabled: true"
    echo ""
    echo "server:"
    echo "  http_listen_address: ${TEMPO_HTTP_LISTEN_ADDR}"
    echo "  http_listen_port: 3200"
    echo "  log_level: info"
    echo ""
    echo "distributor:"
    echo "  receivers:"
    echo "    otlp:"
    echo "      protocols:"
    echo "        grpc:"
    echo "          endpoint: 127.0.0.1:${TEMPO_INTERNAL_GRPC}"
    echo "        http:"
    echo "          endpoint: 127.0.0.1:${TEMPO_INTERNAL_HTTP}"
    echo ""
    echo "ingester:"
    echo "  max_block_duration: 5m"
    echo ""
    echo "compactor:"
    echo "  compaction:"
    echo "    block_retention: ${TEMPO_BLOCK_RETENTION}"
    echo ""
    echo "storage:"
    echo "  trace:"
    echo "    backend: s3"
    echo "    wal:"
    echo "      path: /var/tempo/wal"
    echo "    s3:"
    echo "      bucket: ${TEMPO_S3_BUCKET}"
    echo "      region: ${TEMPO_S3_REGION}"
    if [[ -n "${TEMPO_S3_ENDPOINT:-}" ]]; then
      echo "      endpoint: ${TEMPO_S3_ENDPOINT}"
      echo "      insecure: ${TEMPO_S3_INSECURE:-true}"
      echo "      forcepathstyle: ${TEMPO_S3_FORCE_PATH_STYLE:-true}"
    fi
    if [[ -n "${TEMPO_S3_ACCESS_KEY_ID:-}" ]]; then
      echo "      access_key: ${TEMPO_S3_ACCESS_KEY_ID}"
      echo "      secret_key: ${TEMPO_S3_SECRET_ACCESS_KEY}"
    fi
  } >"${tmp}"
  mv "${tmp}" "${dest}"
  # Fix: always use 640 (not 644) — config contains bucket name and region
  # even when no static credentials are present; no need to make it world-readable.
  chown root:tempo "${dest}"
  chmod 640 "${dest}"
  log "wrote ${dest}"
}

install_tempo_binary() {
  [[ "${SKIP_BINARY_DOWNLOAD}" == "true" ]] && {
    log "skip Tempo download (SKIP_BINARY_DOWNLOAD=true)"
    return 0
  }
  local arch goarch url tmpdir
  goarch="$(go_arch)"
  arch="$(uname -m)"
  url="https://github.com/grafana/tempo/releases/download/v${TEMPO_VERSION}/tempo_${TEMPO_VERSION}_linux_${goarch}.tar.gz"
  log "downloading Tempo ${TEMPO_VERSION} (${arch}) from ${url}"
  if [[ "$DRY_RUN" == true ]]; then
    return 0
  fi
  tmpdir="$(mktemp -d)"
  curl -fsSL "${url}" -o "${tmpdir}/t.tgz"
  tar -xzf "${tmpdir}/t.tgz" -C "${tmpdir}"
  install -m 0755 "${tmpdir}/tempo" /usr/local/bin/tempo
  rm -rf "${tmpdir}"
  log "installed /usr/local/bin/tempo"
}

install_otelcol_contrib_binary() {
  [[ "${SKIP_BINARY_DOWNLOAD}" == "true" ]] && {
    log "skip OTEL Collector download (SKIP_BINARY_DOWNLOAD=true)"
    return 0
  }
  local goarch url tmpdir
  goarch="$(go_arch)"
  url="https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v${OTELCOL_CONTRIB_VERSION}/otelcol-contrib_${OTELCOL_CONTRIB_VERSION}_linux_${goarch}.tar.gz"
  log "downloading otelcol-contrib ${OTELCOL_CONTRIB_VERSION} from ${url}"
  if [[ "$DRY_RUN" == true ]]; then
    return 0
  fi
  tmpdir="$(mktemp -d)"
  curl -fsSL "${url}" -o "${tmpdir}/o.tgz"
  tar -xzf "${tmpdir}/o.tgz" -C "${tmpdir}"
  install -m 0755 "${tmpdir}/otelcol-contrib" /usr/local/bin/otelcol-contrib
  rm -rf "${tmpdir}"
  log "installed /usr/local/bin/otelcol-contrib"
}

write_systemd_tempo() {
  local unit="/etc/systemd/system/tempo.service"
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: would write ${unit}"
    return 0
  fi
  [[ -f "${unit}" ]] && backup_file "${unit}"
  cat >"${unit}" <<'UNIT'
[Unit]
Description=Grafana Tempo (trace backend)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=tempo
Group=tempo
ExecStart=/usr/local/bin/tempo -config.file=/etc/tempo/tempo.yaml
Restart=on-failure
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
UNIT
  log "wrote ${unit}"
}

write_systemd_otelcol() {
  local unit="/etc/systemd/system/otelcol-contrib.service"
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: would write ${unit}"
    return 0
  fi
  [[ -f "${unit}" ]] && backup_file "${unit}"
  cat >"${unit}" <<'UNIT'
[Unit]
Description=OpenTelemetry Collector Contrib (Pulse observability)
After=network-online.target tempo.service
Wants=network-online.target

[Service]
Type=simple
# Fix: run as dedicated otelcol user, not root
User=otelcol
Group=otelcol
ExecStart=/usr/local/bin/otelcol-contrib --config=/etc/otelcol-contrib/otelcol.yaml
Restart=on-failure
LimitNOFILE=65536
# Systemd hardening
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
NoNewPrivileges=true
ReadWritePaths=/etc/otelcol-contrib

[Install]
WantedBy=multi-user.target
UNIT
  log "wrote ${unit}"
}

patch_otel_tempo_port() {
  local dest="$1"
  if [[ "$DRY_RUN" == true ]]; then
    return 0
  fi
  sed -i.bak."${TS}"-otel \
    -e "s|127.0.0.1:4327|127.0.0.1:${TEMPO_INTERNAL_GRPC}|g" \
    "${dest}"
  rm -f "${dest}.bak.${TS}-otel"
  log "otel: set Tempo gRPC export → 127.0.0.1:${TEMPO_INTERNAL_GRPC}"
}

production_install_stack() {
  ensure_tempo_user
  ensure_otelcol_user
  install_tempo_binary
  write_tempo_s3_config "${TEMPO_CONFIG_DEST}"
  install_otelcol_contrib_binary
  write_systemd_tempo
  write_systemd_otelcol
  if [[ "$DRY_RUN" == true ]]; then
    return 0
  fi
  systemctl daemon-reload
  systemctl enable tempo.service
  systemctl enable otelcol-contrib.service
  log "production: systemd units enabled (tempo, otelcol-contrib)"
}

restart_if_enabled() {
  [[ "$RESTART_SERVICES" == true ]] || return 0
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: skip service restarts"
    return 0
  fi

  if [[ "$PRODUCTION" == true ]]; then
    run systemctl restart tempo.service || die "tempo failed to start; check journalctl -u tempo"
    log "restarted tempo.service"
    # Fix: verify Tempo is actually healthy, not just that systemd accepted the restart
    local tempo_ready=false
    for i in 1 2 3 4 5; do
      if curl -sf http://127.0.0.1:3200/ready >/dev/null 2>&1; then
        tempo_ready=true
        break
      fi
      log "tempo: waiting for /ready (attempt ${i}/5)..."
      sleep 2
    done
    if [[ "$tempo_ready" == true ]]; then
      log "tempo: health check passed (http://127.0.0.1:3200/ready)"
    else
      log "warning: tempo did not become ready within 10s; check: journalctl -u tempo -n 50"
    fi

    run systemctl restart otelcol-contrib.service || die "otelcol-contrib failed to start; check journalctl -u otelcol-contrib"
    log "restarted otelcol-contrib.service"
    # Fix: verify otelcol metrics endpoint is up
    local otel_ready=false
    for i in 1 2 3 4 5; do
      if curl -sf "http://${OTEL_PROMETHEUS_SCRAPE_TARGET}/metrics" >/dev/null 2>&1; then
        otel_ready=true
        break
      fi
      log "otelcol: waiting for metrics endpoint (attempt ${i}/5)..."
      sleep 2
    done
    if [[ "$otel_ready" == true ]]; then
      log "otelcol: health check passed (http://${OTEL_PROMETHEUS_SCRAPE_TARGET}/metrics)"
    else
      log "warning: otelcol metrics endpoint not ready within 10s; check: journalctl -u otelcol-contrib -n 50"
    fi
  else
    local restarted=false
    for u in otelcol-contrib otel-collector opentelemetry-collector; do
      if systemctl is-active --quiet "$u" 2>/dev/null || systemctl is-enabled --quiet "$u" 2>/dev/null; then
        run systemctl restart "$u"
        log "restarted systemd unit: $u"
        restarted=true
        break
      fi
    done
    if [[ "$restarted" != true ]]; then
      log "no known otel collector systemd unit found; restart the collector manually"
    fi
  fi

  if systemctl is-active --quiet prometheus 2>/dev/null || systemctl is-enabled --quiet prometheus 2>/dev/null; then
    if systemctl reload prometheus 2>/dev/null; then
      log "reloaded systemd unit: prometheus"
    else
      run systemctl restart prometheus
      log "restarted systemd unit: prometheus"
    fi
  else
    log "prometheus systemd unit not found; reload or restart prometheus manually"
  fi

  local restart_grafana=false
  if [[ "$GRAFANA_DATASOURCE" == true ]]; then
    restart_grafana=true
  fi
  if [[ "$PRODUCTION" == true ]] && [[ "$NO_GRAFANA_PROVISIONING" != true ]]; then
    restart_grafana=true
  fi
  if [[ "$restart_grafana" == true ]]; then
    if systemctl is-active --quiet grafana-server 2>/dev/null || systemctl is-enabled --quiet grafana-server 2>/dev/null; then
      run systemctl restart grafana-server
      log "restarted systemd unit: grafana-server"
    else
      log "grafana-server unit not found; restart grafana manually if you added provisioning"
    fi
  fi
}

require_root

if [[ ! -f "${OBS_DIR}/prometheus/rules/pulse-otel.yml" ]]; then
  die "missing ${OBS_DIR}/prometheus/rules/pulse-otel.yml (run from repo checkout)"
fi

if [[ ! -f "${OTEL_CONFIG_SRC}" ]]; then
  die "missing collector config: ${OTEL_CONFIG_SRC}"
fi

# --- Preflight checks (all must pass before any state is modified) ---
if [[ "$PRODUCTION" == true ]]; then
  # Fix: curl check moved here — before production_install_stack — so a missing
  # curl is caught early with a clear message instead of failing mid-download.
  if [[ "${SKIP_BINARY_DOWNLOAD}" != "true" ]]; then
    command -v curl >/dev/null 2>&1 || die "curl required for --production binary download (apt install curl)"
  fi
fi

if [[ "$WITH_TEMPO" == true ]]; then
  [[ "${OTEL_CONFIG_SRC}" == *otel-collector.yaml ]] || die "--with-tempo expects ${OBS_DIR}/otel-collector.yaml (unset OTEL_CONFIG_SRC)"
  [[ -n "${TEMPO_ENDPOINT}" ]] || die "--with-tempo requires --tempo-endpoint=HOST:PORT (gRPC, no scheme; e.g. 127.0.0.1:4317)"
fi
# --- End preflight ---

if [[ "$PRODUCTION" == true ]]; then
  production_install_stack
fi

run mkdir -p "$(dirname "${OTEL_CONFIG_DEST}")"
run mkdir -p "${PROMETHEUS_RULES_DIR}"
if [[ "$GRAFANA_DATASOURCE" == true ]] || { [[ "$PRODUCTION" == true ]] && [[ "$NO_GRAFANA_PROVISIONING" != true ]]; }; then
  run mkdir -p "${GRAFANA_PROVISION_DIR}"
fi

log "installing OTEL collector config → ${OTEL_CONFIG_DEST}"
if [[ -f "${OTEL_CONFIG_DEST}" ]]; then
  backup_file "${OTEL_CONFIG_DEST}"
fi
if [[ "$DRY_RUN" == true ]]; then
  log "dry-run: would copy ${OTEL_CONFIG_SRC} → ${OTEL_CONFIG_DEST}"
else
  cp -a "${OTEL_CONFIG_SRC}" "${OTEL_CONFIG_DEST}"
  if [[ "$WITH_TEMPO" == true ]]; then
    apply_tempo_endpoint "${OTEL_CONFIG_DEST}"
  fi
  if [[ "$PRODUCTION" == true ]]; then
    patch_otel_tempo_port "${OTEL_CONFIG_DEST}"
  fi
fi

log "installing Prometheus recording rules → ${PULSE_OTEL_RULES_DEST}"
if [[ -f "${PULSE_OTEL_RULES_DEST}" ]]; then
  backup_file "${PULSE_OTEL_RULES_DEST}"
fi
run cp -a "${OBS_DIR}/prometheus/rules/pulse-otel.yml" "${PULSE_OTEL_RULES_DEST}"

if [[ "$NO_PROMETHEUS_MERGE" == true ]]; then
  log "skipping prometheus.yml merge (--no-prometheus-merge)"
  log "add scrape ${OTEL_PROMETHEUS_SCRAPE_TARGET} and rule_files manually if needed"
else
  [[ -f "${PROMETHEUS_CONFIG}" ]] || die "prometheus config not found: ${PROMETHEUS_CONFIG}"
  is_mikefarah_yq || die "mikefarah yq v4 required for prometheus merge; install yq or use --no-prometheus-merge"
  backup_file "${PROMETHEUS_CONFIG}"
  if [[ "$DRY_RUN" == true ]]; then
    log "dry-run: would merge scrape + rule_files into ${PROMETHEUS_CONFIG}"
  else
    merge_prometheus "${PROMETHEUS_CONFIG}"
  fi
fi

if [[ "$GRAFANA_DATASOURCE" == true ]]; then
  ds_dest="${GRAFANA_PROVISION_DIR}/pulse-otel-prometheus.yaml"
  log "installing Grafana datasource provisioning → ${ds_dest}"
  if [[ -f "${ds_dest}" ]]; then
    backup_file "${ds_dest}"
  fi
  run cp -a "${SNIPPET_DIR}/grafana-datasource-prometheus.yaml" "${ds_dest}"
  log "if Prometheus is not on this host, edit url: in ${ds_dest}"
fi

if [[ "$PRODUCTION" == true ]] && [[ "$NO_GRAFANA_PROVISIONING" != true ]]; then
  ds_prom="${GRAFANA_PROVISION_DIR}/pulse-otel-prometheus.yaml"
  if [[ ! -f "${ds_prom}" ]]; then
    log "installing Grafana datasource provisioning → ${ds_prom}"
    run cp -a "${SNIPPET_DIR}/grafana-datasource-prometheus.yaml" "${ds_prom}"
  fi
  ds_tempo="${GRAFANA_PROVISION_DIR}/pulse-otel-tempo.yaml"
  log "installing Grafana Tempo datasource → ${ds_tempo}"
  if [[ -f "${ds_tempo}" ]]; then
    backup_file "${ds_tempo}"
  fi
  run cp -a "${SNIPPET_DIR}/grafana-datasource-tempo.yaml" "${ds_tempo}"
  log "if Grafana is not on this host, edit url: in ${ds_tempo} (Tempo HTTP default :3200)"
fi

restart_if_enabled

log "done."
log "verify: curl -s http://${OTEL_PROMETHEUS_SCRAPE_TARGET}/metrics | head"
log "verify: Prometheus UI → Status → Targets (otel_collector) and Status → Rules (pulse-otel)"
if [[ "$PRODUCTION" == true ]]; then
  log "verify: systemctl status tempo otelcol-contrib; curl -s http://127.0.0.1:3200/ready"
fi