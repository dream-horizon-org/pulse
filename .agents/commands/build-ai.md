Build the Pulse AI agent Docker image.

**Deploy-integrated:** `cd deploy && ./scripts/build.sh ai` (or default `build.sh` builds the full stack including **pulse-s3-archiver** and AI). Start with `./scripts/start.sh -d`. Health: `curl -sf http://localhost:8000/health`.

**Standalone:** `cd pulse_ai/` — ensure `.env` exists (from `.env.example`); set `GOOGLE_API_KEY`. Run `./setup.sh` to build and start. Verify with `curl -sf http://localhost:8000/health`. On failure, `./setup.sh logs`.
