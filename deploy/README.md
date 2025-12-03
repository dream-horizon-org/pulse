# Pulse - Docker Deployment Guide

This guide explains how to build and run the Pulse platform locally using Docker.


## 🚀 Getting Started

### Prerequisites

- **Docker**: 20.10+ and Docker Compose 2.0+
- **Java**: 17+ (for backend development)
- **Node.js**: 18+ (for frontend development)
- **Android Studio**: Latest version (for Android SDK development)
- **Memory**: 8GB RAM (4GB available for Docker)
- **Disk**: 20GB free space

### Quick Start (5 Minutes)

1. **Clone the Repository**

```bash
git clone https://github.com/dream-horizon-org/pulse.git
cd pulse
```

2. **Setup Environment**

```bash
cd deploy
cp .env.example .env
# Edit .env with your configuration (defaults work for local development)
```

3. **Start All Services**

```bash
# Make scripts executable
chmod +x scripts/*.sh

# Build and start
./scripts/quickstart.sh
```

4. **Access the Platform**

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Health Check**: http://localhost:8080/healthcheck
- **MySQL**: http://localhost:3307
- **ClickHouse HTTP**: http://localhost:8123
- **ClickHouse Native**: http://localhost:9000
- **OTEL Collector (gRPC)**: http://localhost:4317
- **OTEL Collector (HTTP)**: http://localhost:4318

5. **Verify Installation**

```bash
# Check all services are running
docker-compose ps

# Check backend health
curl http://localhost:8080/healthcheck

# View logs
./scripts/logs.sh
```

## 🛠️ Development Commands

### Frontend Development

```bash
cd pulse-ui
yarn install
yarn start           # Start dev server (http://localhost:3000)
```

### Backend Development

```bash
cd backend/server
rm -rf src/main/generated
mvn clean install   # Build project
mvn test           # Run tests
mvn package        # Create JAR

# export all the env variables
# then Run locally
java -jar target/pulse-server/pulse-server.jar
```

### Android SDK Development

```bash
cd pulse-android-otel
./gradlew assemble  # Build SDK
./gradlew check     # Run tests and checks
./gradlew spotlessApply  # Format code
```

### React Native SDK Development

```bash
cd pulse-react-native-otel
npm install
npm run build      # Build TypeScript
cd example
npm install
npm run android    # Run example app (Android)
npm run ios        # Run example app (iOS)
```

## 📊 Monitoring & Observability

### Health Checks

```bash
# Backend health
curl http://localhost:8080/healthcheck

# Database connectivity
docker-compose exec pulse-server curl http://localhost:8080/healthcheck

# OTEL Collector health
curl http://localhost:13133/
```

### Logs

```bash
# View all logs
docker-compose logs -f

# View specific service
docker-compose logs -f pulse-server
docker-compose logs -f pulse-ui
docker-compose logs -f otel-collector

# View with timestamp
docker-compose logs -f -t pulse-server
```

### Metrics

```bash
# Container stats
docker stats

# OTEL Collector metrics
curl http://localhost:8888/metrics
```

## 📊 Service Details

### Pulse UI (Frontend)

- **Technology**: React 18, TypeScript
- **Build Tool**: Webpack
- **Web Server**: Nginx
- **Port**: 3000 (mapped to container port 80)
- **Build Time**: ~5-10 minutes (first build)
- **Image Size**: ~50-100MB (production)

**Environment Variables:**
- `REACT_APP_GOOGLE_CLIENT_ID`: Google OAuth client ID
- `GOOGLE_OAUTH_ENABLED`: Common variable to enable/disable Google OAuth (set to `false` to use dummy login). If not set, defaults based on whether client ID is configured.

### Pulse Server (Backend)

- **Technology**: Java 17, Vert.x
- **Build Tool**: Maven
- **Port**: 8080
- **Build Time**: ~10-15 minutes (first build)
- **Image Size**: ~200-300MB

**Key Environment Variables:**
- See `.env.example` for complete list
- All variables prefixed with `CONFIG_SERVICE_APPLICATION_*`
- All variables prefixed with `VAULT_SERVICE_*`
- `GOOGLE_OAUTH_ENABLED`: Common variable to enable/disable Google OAuth (shared with frontend)

### Pulse Alerting (Future)

- **Status**: Placeholder (not yet implemented)
- **Port**: 8081 (when implemented)

## 🏗️ Build Process Explanation

### Frontend Build (pulse-ui)

1. **Stage 1 - Builder**:
   - Uses Node.js 18 Alpine image
   - Installs npm dependencies
   - Runs production build
   - Output: Optimized static files

2. **Stage 2 - Runtime**:
   - Uses Nginx Alpine image
   - Copies built files from Stage 1
   - Configures Nginx for React Router
   - Serves static content

### Backend Build (pulse-server)

1. **Stage 1 - Builder**:
   - Uses Maven with JDK 17
   - Downloads dependencies (cached)
   - Compiles Java code
   - Packages as fat JAR

2. **Stage 2 - Runtime**:
   - Uses JRE 17 Alpine (smaller image)
   - Copies JAR from Stage 1
   - Copies configuration files
   - Runs Vert.x application

## 🔐 Security Notes

- Never commit `.env` file to version control
- Use `.env.example` as a template only
- Rotate sensitive credentials regularly
- Use Docker secrets for production deployments

## 📈 Performance Tips

### Optimize Build Time

```bash
# Use BuildKit for faster builds
DOCKER_BUILDKIT=1 docker-compose build

# Build in parallel
docker-compose build --parallel
```

### Reduce Image Size

- Multi-stage builds are already implemented
- Alpine Linux base images are used
- Only production dependencies are included

### Resource Limits

Add resource limits in `docker-compose.yml`:

```yaml
services:
  pulse-server:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

## 🧪 Testing Locally

### Test Frontend Only

```bash
docker-compose up pulse-ui
# Access at http://localhost:3000
```

### Test Backend Only

```bash
docker-compose up pulse-server
# Access at http://localhost:8080/health
```

### Test Full Stack

```bash
docker-compose up
# UI at http://localhost:3000
# API at http://localhost:8080
```

## 🚢 Production Considerations

This setup is designed for **local development and testing**. For production:

1. **Use proper secrets management** (e.g., Docker Secrets, Vault)
2. **Add reverse proxy** (e.g., Traefik, Nginx)
3. **Enable HTTPS/TLS**
4. **Set up monitoring** (e.g., Prometheus, Grafana)
5. **Configure log aggregation** (e.g., ELK stack)
6. **Use orchestration** (e.g., Kubernetes, Docker Swarm)
7. **Implement CI/CD pipelines**

## 📝 Next Steps

1. Set up database services (MySQL, ClickHouse) in docker-compose
2. Add Redis for caching
3. Implement the alerting service
4. Add integration tests
5. Set up CI/CD pipelines

## 🆘 Support

For issues or questions:
1. Check logs: `docker-compose logs -f`
2. Verify environment variables in `.env`
3. Ensure all required services are running
4. Check Docker daemon is running

## 📚 Additional Resources

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Vert.x Documentation](https://vertx.io/docs/)
- [React Documentation](https://react.dev/)

