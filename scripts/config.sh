#!/usr/bin/env bash
# POST /v1/configs to local pulse-server. Payload matches a minimal DevMode curl
# (no signals.filters, no sampling.signalsToSample — older REST models reject those).
# Edit SETTINGS, then: ./scripts/config.sh
# Requires: curl, jq

set -euo pipefail

# =============================================================================
# SETTINGS — edit here only
# =============================================================================
ACCESS_TOKEN='eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtb2NrLXVzZXItMSIsImVtYWlsIjoidXNlcjFAZXhhbXBsZS5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIDEiLCJ0eXBlIjoiYWNjZXNzIiwidGVuYW50SWQiOiJkZWZhdWx0IiwiaWF0IjoxNzc1NjM2MjAxLCJleHAiOjE3NzU3MjI2MDF9.7t2CPoFk1t7Soox5vJZDs7F_DWO-9st2rv9ahBLUQg8'
BASE_URL='http://localhost:8080'
PROJECT_ID='default-project'
USER_EMAIL='user1@example.com'
DESCRIPTION='Default initial configuration'
SESSION_SAMPLE_RATE='1'
SCHEDULE_DURATION_MS='5000'

METRICS_TO_ADD_PRESET='histogram_log_metric_histogram_test_rn'
# Core: empty | counter_span_ios | counter_log_ios | counter_http_status_suffix_false |
#   counter_http_method_status_suffix_true | gauge_http_status | histogram_http_duration |
#   histogram_log_metric_histogram_test_rn — RN EventExample: event histogram_metric_demo + attr metric.histogram.test (1–100)
#   sum_response_content_length | sum_request_response_suffix_true | multi_counters |
#   android_counter_span | all_types_smoke | stress_10_counters
# attributesToPick (copies matching span attrs onto metric points — see PulseSamplingSignalProcessors.buildAttributesToPick):
#   counter_span_pick_method — name counter + pick http.method on point
#   counter_span_pick_method_status — name counter + pick method + status_code on point
#   histogram_duration_pick_method — duration histogram + pick http.method on point
#   counter_http_suffix_true_pick_route — suffix counter + pick http.route on each derived series
#   pick_two_or_conditions — name counter + OR pick (method OR scheme) for points
# =============================================================================

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required (brew install jq)" >&2; exit 1; }

AUTH_HEADER="$ACCESS_TOKEN"
[[ "$AUTH_HEADER" == Bearer\ * ]] || AUTH_HEADER="Bearer $AUTH_HEADER"

preset_metrics_to_add_json() {
  case "$1" in
    empty) echo '[]' ;;
    counter_span_ios)
      echo '[{"name":"span_count","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}}]'
      ;;
    counter_log_ios)
      echo '[{"name":"log_event_count","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["logs"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}}]'
      ;;
    counter_http_status_suffix_false)
      echo '[{"name":"http_req_by_status","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.status_code","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}}]'
      ;;
    counter_http_method_status_suffix_true)
      echo '[{"name":"http_count","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.method","value":".*"},{"name":"http\\.status_code","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":true},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}}]'
      ;;
    gauge_http_status)
      echo '[{"name":"http_status_gauge","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.status_code","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"gauge","isFraction":true}}]'
      ;;
    histogram_http_duration)
      echo '[{"name":"http_duration_ms","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.duration","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"histogram","bucket":[50,100,250,500,1000,2500],"isFraction":true}}]'
      ;;
    histogram_log_metric_histogram_test_rn)
      echo '[{"name":"histogram_demo_metric","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"metric\\.histogram\\.test","value":".*"}],"scopes":["logs"],"sdks":["pulse_ios_rn","pulse_android_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":"histogram_metric_demo","props":[],"scopes":["logs"],"sdks":["pulse_ios_rn","pulse_android_rn"]},"type":{"type":"histogram","bucket":[1,5,10,25,40,55,70,85,100],"isFraction":true}}]'
      ;;
    sum_response_content_length)
      echo '[{"name":"bytes_response","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.response_content_length","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"sum","isFraction":false,"isMonotonic":true}}]'
      ;;
    sum_request_response_suffix_true)
      echo '[{"name":"bytes","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.request_content_length","value":".*"},{"name":"http\\.response_content_length","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":true},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"sum","isFraction":false,"isMonotonic":true}}]'
      ;;
    multi_counters)
      echo '[{"name":"screen_views","target":{"type":"name"},"condition":{"name":"^Screen\\..*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}},{"name":"http_spans","target":{"type":"name"},"condition":{"name":"^HTTP.*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}},{"name":"db_spans","target":{"type":"name"},"condition":{"name":".*db.*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}}]'
      ;;
    android_counter_span)
      echo '[{"name":"android_span_count","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_android_java","pulse_android_rn"]},"type":{"type":"counter"}}]'
      ;;
    all_types_smoke)
      echo '[{"name":"smoke_counter","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"}},{"name":"smoke_gauge","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.status_code","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"gauge","isFraction":true}},{"name":"smoke_hist","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.duration","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"histogram","bucket":[1,10,100],"isFraction":true}},{"name":"smoke_sum","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.response_content_length","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"sum","isFraction":false,"isMonotonic":true}}]'
      ;;
    stress_10_counters)
      local arr i
      arr='['
      for i in $(seq 0 9); do
        [[ $i -gt 0 ]] && arr+=','
        arr+="{\"name\":\"stress_counter_${i}\",\"target\":{\"type\":\"name\"},\"condition\":{\"name\":\"^STRESS_${i}_.*\",\"props\":[],\"scopes\":[\"traces\"],\"sdks\":[\"pulse_ios_swift\",\"pulse_ios_rn\"]},\"type\":{\"type\":\"counter\"}}"
      done
      arr+=']'
      echo "$arr"
      ;;
    counter_span_pick_method)
      echo '[{"name":"span_with_method_on_point","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"},"attributesToPick":[{"name":".*","props":[{"name":"http\\.method","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]}]}]'
      ;;
    counter_span_pick_method_status)
      echo '[{"name":"span_with_method_status_on_point","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"},"attributesToPick":[{"name":".*","props":[{"name":"http\\.method","value":".*"},{"name":"http\\.status_code","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]}]}]'
      ;;
    histogram_duration_pick_method)
      echo '[{"name":"http_duration_ms","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.duration","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":false},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"histogram","bucket":[50,100,250,500,1000,2500],"isFraction":true},"attributesToPick":[{"name":".*","props":[{"name":"http\\.method","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]}]}]'
      ;;
    counter_http_suffix_true_pick_route)
      echo '[{"name":"http_count","target":{"type":"attribute","condition":{"name":".*","props":[{"name":"http\\.method","value":".*"},{"name":"http\\.status_code","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"shouldAddPropNameAsSuffix":true},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"},"attributesToPick":[{"name":".*","props":[{"name":"http\\.route","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]}]}]'
      ;;
    pick_two_or_conditions)
      echo '[{"name":"span_pick_method_or_scheme","target":{"type":"name"},"condition":{"name":".*","props":[],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},"type":{"type":"counter"},"attributesToPick":[{"name":".*","props":[{"name":"http\\.method","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]},{"name":".*","props":[{"name":"http\\.scheme","value":".*"}],"scopes":["traces"],"sdks":["pulse_ios_swift","pulse_ios_rn"]}]}]'
      ;;
    *)
      echo "Unknown METRICS_TO_ADD_PRESET: $1" >&2
      exit 1
      ;;
  esac
}

METRICS_TO_ADD_JSON="$(preset_metrics_to_add_json "$METRICS_TO_ADD_PRESET")"
if ! jq -e . >/dev/null 2>&1 <<<"$METRICS_TO_ADD_JSON"; then
  echo "METRICS_TO_ADD_JSON is not valid JSON." >&2
  exit 1
fi

SESSION_SAMPLE_RATE_JSON=$(jq -n --arg s "$SESSION_SAMPLE_RATE" '($s | tonumber)')
SCHEDULE_DURATION_MS_JSON=$(jq -n --arg s "$SCHEDULE_DURATION_MS" '($s | tonumber)')

read -r -d '' CONFIG_JSON <<'PULSE_CONFIG_EOF' || true
{
  "description": "PLACEHOLDER_DESCRIPTION",
  "sampling": {
    "default": { "sessionSampleRate": 1 },
    "rules": [],
    "criticalSessionPolicies": { "alwaysSend": [] }
  },
  "signals": {
    "scheduleDurationMs": 5000,
    "logsCollectorUrl": "http://127.0.0.1:4318/v1/logs",
    "metricCollectorUrl": "http://127.0.0.1:4318/v1/metrics",
    "spanCollectorUrl": "http://127.0.0.1:4318/v1/traces",
    "customEventCollectorUrl": "http://127.0.0.1:4318/v1/logs",
    "attributesToDrop": [],
    "attributesToAdd": [],
    "metricsToAdd": []
  },
  "interaction": {
    "collectorUrl": "http://localhost:4318/v1/traces/v1/interactions",
    "configUrl": "http://127.0.0.1:4318/v1/interaction-configs/",
    "beforeInitQueueSize": 100
  },
  "features": [
    { "featureName": "interaction", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "java_crash", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "js_crash", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "java_anr", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "network_change", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "network_instrumentation", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "screen_session", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "custom_events", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "rn_screen_load", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "rn_screen_interactive", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": null },
    { "featureName": "session_replay", "sessionSampleRate": 1, "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"], "config": { "featureName": "session_replay", "textAndInputPrivacy": "MASK_ALL", "imagePrivacy": "MASK_ALL", "maxBatchSize": 50, "flushIntervalSeconds": 60, "flushAt": 10, "throttleDelayMs": 1000, "screenshotScale": 1, "screenshotQuality": 30, "replayApiBaseUrl": "http://10.0.2.2:3400" } }
  ]
}
PULSE_CONFIG_EOF

PAYLOAD=$(jq \
  --arg desc "$DESCRIPTION" \
  --argjson sessionSampleRate "$SESSION_SAMPLE_RATE_JSON" \
  --argjson metricsToAdd "$METRICS_TO_ADD_JSON" \
  --argjson scheduleDurationMs "$SCHEDULE_DURATION_MS_JSON" \
  '.description = $desc
   | .sampling.default.sessionSampleRate = $sessionSampleRate
   | .signals.metricsToAdd = $metricsToAdd
   | .signals.scheduleDurationMs = $scheduleDurationMs' \
  <<<"$CONFIG_JSON")

echo "POST ${BASE_URL}/v1/configs (project=${PROJECT_ID}, user=${USER_EMAIL})" >&2
echo "  metrics=${METRICS_TO_ADD_PRESET}  sessionRate=${SESSION_SAMPLE_RATE}  scheduleMs=${SCHEDULE_DURATION_MS}" >&2

RESPONSE=$(curl -sS -X POST "${BASE_URL}/v1/configs" \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -H "Authorization: ${AUTH_HEADER}" \
  -H "X-Project-ID: ${PROJECT_ID}" \
  -H "user-email: ${USER_EMAIL}" \
  -d "$PAYLOAD")

echo "$RESPONSE" | jq .

VERSION=$(echo "$RESPONSE" | jq -r '.data.version // empty')
OK=$(echo "$RESPONSE" | jq -r 'if (.error == null) and (.data != null) and (.data.version != null) then "yes" else "no" end')

if [[ "$OK" == "yes" ]]; then
  echo "" >&2
  echo "OK — config version ${VERSION}" >&2
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
  echo " Active config (GET ${BASE_URL}/v1/configs/active)" >&2
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" >&2
  curl -sS "${BASE_URL}/v1/configs/active" \
    -H 'Accept: application/json' \
    -H "Authorization: ${AUTH_HEADER}" \
    -H "X-Project-ID: ${PROJECT_ID}" \
    | jq .
fi
