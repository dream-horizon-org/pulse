# Multi-stage build for pulse-alerts-cron (Java/Vert.x)
FROM maven:3.9-eclipse-temurin-17 AS builder

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
FROM eclipse-temurin:17-jre

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/pulse-alerts-cron/pulse-alerts-cron.jar ./pulse-alerts-cron.jar

COPY --from=builder /app/src/main/resources/config ./config

# Expose the application port
EXPOSE 4000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:4000/healthcheck || exit 1

# Run the application
ENTRYPOINT ["java", "-Dlogback.configurationFile=logback/logback-docker.xml", "-jar", "pulse-alerts-cron.jar", "run", "org.dreamhorizon.pulsealertscron.verticle.MainVerticle"]

