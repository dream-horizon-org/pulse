# Multi-stage build for pulse-alerts-cron (Java/Vert.x)
FROM maven:3.9-eclipse-temurin-17 AS builder

# Create a non-root user
RUN mkdir -p /app && chown -R 10001:0 /app

# Set working directory
WORKDIR /app

# Copy pom.xml and lombok config first for better caching
COPY pom.xml lombok.config ./

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -Dcheckstyle.skip=true

# Pin to an existing release: https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases
ARG OTEL_JAVA_AGENT_VERSION=2.26.1
RUN curl -fsSL -o /tmp/opentelemetry-javaagent.jar \
  "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar"

# Runtime stage
# Using full JRE image instead of alpine for better compatibility
FROM eclipse-temurin:17-jre

# Install curl for healthcheck
RUN set -eux; \
    apt-get update; \
    curl_ver="$(apt-cache policy curl | awk '/Candidate:/ {print $2}')"; \
    apt-get install --no-install-recommends -y "curl=${curl_ver}"; \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/pulse-alerts-cron/pulse-alerts-cron.jar ./pulse-alerts-cron.jar

COPY --from=builder /app/src/main/resources/conf ./conf

RUN mkdir -p /otel
COPY --from=builder /tmp/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 444 /otel/opentelemetry-javaagent.jar

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 755 /app/docker-entrypoint.sh

# Expose the application port
EXPOSE 4000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:4000/healthcheck || exit 1

USER 10001

ENTRYPOINT ["/app/docker-entrypoint.sh"]
