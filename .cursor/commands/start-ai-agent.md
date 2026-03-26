Start the Pulse AI agent for development.

**Option A — integrated deploy stack (with pulse-server on `pulse-network`):**

1. Set `GOOGLE_API_KEY` in `deploy/.env` when you need Gemini (optional for container start)
2. `cd deploy && ./scripts/build.sh` (includes `pulse-ai-agent`) or `./scripts/build.sh ai` only
3. `cd deploy && ./scripts/start.sh -d`
4. Verify: `curl -sf http://localhost:8000/health`
5. Logs: `./scripts/logs.sh ai` — stop AI only: `./scripts/stop.sh ai`

**Option B — standalone `pulse_ai` compose (isolated AI dev):**

1. Change to `pulse_ai/`
2. Check if `.env` exists, if not copy from `.env.example` and remind user to set `GOOGLE_API_KEY`
3. Run `./setup.sh` to build and start the Docker container (port 8000)
4. Verify health: `curl -sf http://localhost:8000/health`
5. If already running, use `./setup.sh restart` to rebuild and restart
6. To view logs: `./setup.sh logs`
7. To stop: `./setup.sh stop`
