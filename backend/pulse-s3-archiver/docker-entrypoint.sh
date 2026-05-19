#!/bin/sh
set -e

JAVA_AGENT=""
if [ "${PULSE_BACKEND_OTEL_ENABLED}" = "true" ] && [ -r /otel/opentelemetry-javaagent.jar ]; then
  JAVA_AGENT="-javaagent:/otel/opentelemetry-javaagent.jar"
  export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-http/protobuf}"
  export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-otlp}"
  export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
  export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-none}"
  export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://host.docker.internal:14318}"
  export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-pulse-s3-archiver}"
else
  unset OTEL_EXPORTER_OTLP_ENDPOINT OTEL_SERVICE_NAME OTEL_METRICS_EXPORTER \
        OTEL_TRACES_EXPORTER OTEL_LOGS_EXPORTER OTEL_EXPORTER_OTLP_PROTOCOL 2>/dev/null || true
fi

exec java ${JAVA_AGENT} \
  -Dlogback.configurationFile=logback.xml \
  -Dvertx.disableDnsResolver=true \
  -jar pulse-s3-archiver.jar run \
  org.dreamhorizon.pulses3archiver.verticle.MainVerticle
