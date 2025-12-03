# Multi-stage build for pulse-server (Java/Vert.x)
FROM maven:3.9-eclipse-temurin-17@sha256:e4a7ace3dc0d645ed97f8d9ad0b0d3f0b14fa8d150138f27f116d7105a639b82 AS builder

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

# Runtime stage
# Using full JRE image instead of alpine for better compatibility
FROM eclipse-temurin:17-jre@sha256:75ab7d1b4b18483e9245342cbee253b558952c1def5c1c18956196330a01683e

# Install curl for healthcheck
RUN set -eux; \
    apt-get update; \
    curl_ver="$(apt-cache policy curl | awk '/Candidate:/ {print $2}')"; \
    apt-get install --no-install-recommends -y "curl=${curl_ver}"; \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/pulse-server/pulse-server.jar ./pulse-server.jar

COPY --from=builder /app/src/main/resources/conf ./config/conf

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/healthcheck || exit 1

USER 10001

# Run the application
ENTRYPOINT ["java", "-Dlogback.configurationFile=logback/logback-docker.xml" , "-jar", "pulse-server.jar", "run", "org.dreamhorizon.pulseserver.verticle.MainVerticle"]

