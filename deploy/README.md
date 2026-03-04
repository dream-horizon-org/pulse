# Pulse - Docker Deployment Guide

This guide explains how to build and run the Pulse platform locally using Docker.

All scripts live under `deploy/scripts/` and automatically detect whether
Docker Compose is available. If it is, they use Compose; otherwise they fall
back to pure Docker CLI commands -- no additional setup needed.


## 🚀 Getting Started

### Prerequisites

- **Docker Engine**: 20.10+ (Docker Compose 2.0+ optional -- scripts fall back to Docker CLI)
- **macOS users**: [Colima](https://github.com/abiosoft/colima) (open-source, lightweight container runtime). Scripts auto-install it if Docker is missing.
- **Linux users**: Docker Engine CE (auto-installed if missing).
- **Java**: 17+ (for backend development)
- **Node.js**: 18+ (for frontend development)
- **Android Studio**: Latest version (for Android SDK development)
- **Memory**: 8GB RAM (4GB available for Docker)
- **Disk**: 20GB free space

> If Docker is not found, the quickstart scripts will offer to install
> **Docker CLI + Colima** (macOS) or **Docker Engine CE** (Linux) automatically.
>
> **Manual Colima setup on macOS** (if you prefer to install manually):
> ```bash
> brew install docker colima
> colima start --cpu 4 --memory 8 --disk 60
> # Add to ~/.zshrc so the CLI finds the Colima socket:
> export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
> # Verify
> docker version
> ```

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
chmod +x scripts/*.sh
./scripts/quickstart.sh
```

4. **Access the Platform**

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Health Check**: http://localhost:8080/healthcheck
- **MySQL**: localhost:3307
- **ClickHouse HTTP**: http://localhost:8123
- **ClickHouse Native**: localhost:9000
- **OTEL Collector (gRPC)**: localhost:4317
- **OTEL Collector (HTTP)**: localhost:4318

5. **Verify Installation**

```bash
# Check running containers
docker ps --filter network=pulse-network

# Check backend health
curl http://localhost:8080/healthcheck
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

## 🐳 Deploy Scripts

All scripts live under `deploy/scripts/`. They auto-detect Docker Compose and
fall back to pure Docker CLI when Compose is not available.

| Script | Purpose |
|--------|---------|
| `quickstart.sh` | End-to-end setup: prerequisites, build, start, verify |
| `build.sh` | Build custom Docker images |
| `start.sh` | Start all containers in dependency order |
| `stop.sh` | Stop and remove containers |
| `logs.sh` | View container logs |
| `reset-databases.sh` | Wipe database data and restart from scratch |
| `common.sh` | Shared library (not run directly) |
| `init-clickhouse.sh` | ClickHouse table initialiser (runs inside a container, not run directly) |

---

### `quickstart.sh`

One-command setup that walks through the entire process: checks prerequisites
(Docker, disk, memory), sets up `.env`, builds all images, starts every
service, and verifies the deployment.

```bash
./deploy/scripts/quickstart.sh
```

- Interactive -- prompts for confirmation before proceeding.
- Installs Docker + Colima automatically on macOS if missing.
- Installs Docker Engine CE on Linux if missing.
- Safe to re-run; skips steps that are already done (`.env` exists, images cached, etc.).

---

### `build.sh`

Builds the three custom Docker images: `pulse-ui`, `pulse-server`, and
`pulse-alerts-cron`. When multiple services are built, they run in parallel.

```
Usage: ./deploy/scripts/build.sh [--no-cache] [ui|server|cron|all] [-h|--help]
```

| Flag / Argument | Description |
|-----------------|-------------|
| _(no args)_ | Build all three images |
| `ui` | Build `pulse-ui` only |
| `server` | Build `pulse-server` only |
| `cron` | Build `pulse-alerts-cron` only |
| `all` | Explicitly build all (same as no args) |
| `--no-cache` | Build from scratch, ignoring Docker layer cache |
| `-h`, `--help` | Show usage and exit |

```bash
# Build everything (parallel)
./deploy/scripts/build.sh

# Rebuild UI from scratch
./deploy/scripts/build.sh --no-cache ui

# Build server and cron only
./deploy/scripts/build.sh server cron
```

---

### `start.sh`

Creates the Docker network and volumes (if needed), then starts all containers
in the correct dependency order: databases first, then OTEL collector, then
the backend server, and finally the UI and alerts cron.

```
Usage: ./deploy/scripts/start.sh [-d|--detach] [--build] [-h|--help]
```

| Flag | Description |
|------|-------------|
| `-d`, `--detach` | Run containers in the background and print a status summary |
| `--build` | Build images before starting (calls `build.sh` in CLI mode, or passes `--build` to Compose) |
| `-h`, `--help` | Show usage and exit |

```bash
# Start all services in the background
./deploy/scripts/start.sh -d

# Build images first, then start
./deploy/scripts/start.sh -d --build

# Start in foreground (attaches to logs, Ctrl+C to detach)
./deploy/scripts/start.sh
```

---

### `stop.sh`

Stops and removes containers. In CLI mode, containers are stopped in reverse
dependency order. Optionally removes data volumes and the Docker network.

```
Usage: ./deploy/scripts/stop.sh [-v|--volumes] [--all] [SERVICE...] [-h|--help]
```

| Flag / Argument | Description |
|-----------------|-------------|
| _(no args)_ | Stop and remove all Pulse containers |
| `-v`, `--volumes` | Also remove database data volumes (`pulse-mysql-data`, `pulse-clickhouse-data`) |
| `--all` | Remove containers, volumes, **and** the Docker network |
| `ui` | Stop `pulse-ui` only |
| `server` | Stop `pulse-server` only |
| `cron` | Stop `pulse-alerts-cron` only |
| `mysql` | Stop `pulse-mysql` only |
| `clickhouse` | Stop `pulse-clickhouse` only |
| `otel` | Stop `pulse-otel-collector` only |
| `-h`, `--help` | Show usage and exit |

```bash
# Stop all services (keeps data volumes)
./deploy/scripts/stop.sh

# Stop all and wipe database data
./deploy/scripts/stop.sh -v

# Full teardown (containers + volumes + network)
./deploy/scripts/stop.sh --all

# Stop only the UI (other services keep running)
./deploy/scripts/stop.sh ui

# Stop backend server and cron
./deploy/scripts/stop.sh server cron
```

---

### `logs.sh`

Streams or prints container logs. Defaults to following all containers in
real time. Supports filtering by service and limiting output.

```
Usage: ./deploy/scripts/logs.sh [--no-follow] [--tail N] [SERVICE] [-h|--help]
```

| Flag / Argument | Description |
|-----------------|-------------|
| _(no args)_ | Follow logs from all running containers |
| `--no-follow` | Print logs and exit (do not stream) |
| `--tail N` | Show only the last N lines per container |
| `ui` | Show logs for `pulse-ui` |
| `server` | Show logs for `pulse-server` |
| `cron` | Show logs for `pulse-alerts-cron` |
| `mysql` | Show logs for `pulse-mysql` |
| `clickhouse` | Show logs for `pulse-clickhouse` |
| `otel` | Show logs for `pulse-otel-collector` |
| `-h`, `--help` | Show usage and exit |

```bash
# Stream all logs (Ctrl+C to stop)
./deploy/scripts/logs.sh

# Last 50 lines of backend server, then exit
./deploy/scripts/logs.sh --no-follow --tail 50 server

# Follow only MySQL logs
./deploy/scripts/logs.sh mysql

# Quick peek at all containers (last 20 lines, no follow)
./deploy/scripts/logs.sh --no-follow --tail 20
```

---

### `reset-databases.sh`

Destructive operation that wipes all database data and re-initialises from
scratch. Useful when schema changes or you want a clean slate.

```bash
./deploy/scripts/reset-databases.sh
```

- Prompts for confirmation (you must type `yes`).
- Stops all services.
- Deletes `pulse-mysql-data` and `pulse-clickhouse-data` volumes.
- Restarts all services (MySQL init SQL and ClickHouse schema run automatically on fresh volumes).
- Verifies tables are created.

---

### `common.sh` _(library -- not run directly)_

Shared library sourced by every other script. Provides:

- Path constants (`DEPLOY_DIR`, `ROOT_DIR`)
- Container / image / network / volume names
- Coloured output helpers (`print_success`, `print_error`, `print_warning`, `print_info`)
- Docker Compose detection (`has_compose`, `run_compose`)
- Environment loader (`load_env`) -- reads `.env` and applies defaults
- Docker install / start / health-check functions
- Network, volume, and container management helpers

---

### `init-clickhouse.sh` _(internal -- not run directly)_

Runs inside a ClickHouse container as a one-shot job. Waits for ClickHouse to
accept connections, then applies `backend/ingestion/clickhouse-otel-schema.sql`
to create the OTEL tables. Called automatically by `start.sh` and
`docker-compose.yml`; you should never need to run it manually.

## 📊 Monitoring & Observability

### Health Checks

```bash
# Backend health
curl http://localhost:8080/healthcheck

# Database connectivity (Compose)
docker-compose exec pulse-server curl http://localhost:8080/healthcheck

# Database connectivity (Docker CLI)
docker exec pulse-server curl http://localhost:8080/healthcheck

# OTEL Collector health
curl http://localhost:13133/
```

### Logs

```bash
# All services
./deploy/scripts/logs.sh

# Specific service
./deploy/scripts/logs.sh server
docker logs -f pulse-server
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

### Pulse Alerts Cron

- **Technology**: Java 17, Vert.x
- **Port**: 4000
- **Purpose**: Scheduled alert evaluations and webhook dispatching
- **Health Check**: http://localhost:4000/healthcheck

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

### Full Stack (recommended)

```bash
./deploy/scripts/quickstart.sh
# UI at http://localhost:3000
# API at http://localhost:8080
```

### Start Individual Services

```bash
# Build and start only what you need
./deploy/scripts/build.sh ui
./deploy/scripts/start.sh -d

# Or with Docker Compose (if available)
docker compose -f deploy/docker-compose.yml up pulse-ui
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

1. Add Redis for session caching
2. Add integration tests
3. Set up CI/CD pipelines

## 🆘 Support

For issues or questions:
1. Check logs: `./deploy/scripts/logs.sh`
2. Verify environment variables in `.env`
3. Ensure all required services are running: `docker ps --filter network=pulse-network`
4. Check Docker daemon is running: `docker info`

## 📚 Additional Resources

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Vert.x Documentation](https://vertx.io/docs/)
- [React Documentation](https://react.dev/)

