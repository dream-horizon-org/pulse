# Multi-stage build for pulse-s3-archiver (Java/Vert.x)
FROM maven:3.9-eclipse-temurin-17 AS builder

RUN mkdir -p /app && chown -R 10001:0 /app

WORKDIR /app

COPY pom.xml lombok.config ./
COPY src ./src

RUN mvn clean package -DskipTests -Dcheckstyle.skip=true

ARG OTEL_JAVA_AGENT_VERSION=2.26.1
RUN curl -fsSL -o /tmp/opentelemetry-javaagent.jar \
  "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar"

FROM eclipse-temurin:17-jre

RUN set -eux; \
    apt-get update; \
    curl_ver="$(apt-cache policy curl | awk '/Candidate:/ {print $2}')"; \
    apt-get install --no-install-recommends -y "curl=${curl_ver}"; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/pulse-s3-archiver/pulse-s3-archiver.jar ./pulse-s3-archiver.jar
COPY --from=builder /app/src/main/resources/conf ./conf
COPY --from=builder /app/src/main/resources/schemas ./schemas
COPY --from=builder /app/src/main/resources/logback ./logback

RUN mkdir -p /otel /tmp/pulse-archiver
COPY --from=builder /tmp/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 444 /otel/opentelemetry-javaagent.jar

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 755 /app/docker-entrypoint.sh

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/healthcheck || exit 1

USER 10001

ENTRYPOINT ["/app/docker-entrypoint.sh"]
